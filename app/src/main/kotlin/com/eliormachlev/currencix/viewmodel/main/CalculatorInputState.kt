package com.eliormachlev.currencix.viewmodel.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.eliormachlev.currencix.util.CALC_TOKEN_REGEX
import com.eliormachlev.currencix.util.MULTI_ZERO_KEYS
import com.eliormachlev.currencix.util.OPERATOR_MULTIPLY
import com.eliormachlev.currencix.util.OPERATOR_REGEX
import com.eliormachlev.currencix.util.PAREN_CLOSE
import com.eliormachlev.currencix.util.PAREN_OPEN
import com.eliormachlev.currencix.util.ZERO
import com.eliormachlev.currencix.util.ZERO_KEYS
import com.eliormachlev.currencix.util.evaluateCalculatorExpression
import com.eliormachlev.currencix.util.unclosedParens
import java.math.BigDecimal

/**
 * Which keypad keys would do something with the input as it stands: "."
 * (no decimal point in the number yet), % (after a number), ")" (something
 * open to close, after a value) and = (a calculation to work out).
 */
data class KeyAvailability(
    val decimal: Boolean = true,
    val percent: Boolean = true,
    val closeParen: Boolean = false,
    val equals: Boolean = false,
)

/**
 * Holds the mutable keypad state — the lower "base" row and the optional upper
 * "calculation" row — and the operations the calculator UI performs on them
 * (digits, decimal point, operators, percent, delete, clear, paste).
 *
 * Extracted from MainViewModel so the string-manipulation edge cases (empty
 * operator slots, trailing decimals, "00"/"000" collapsing, operator swap on
 * a trailing operator) can be exercised without an Android Application.
 *
 * `currentBaseValueText` is never null once initialised; `currentCalculationValueText`
 * is null iff we are not in calculation mode.
 */
internal class CalculatorInputState {
    private val _baseValueText = MutableLiveData(ZERO)
    private val _calculationValueText = MutableLiveData<String?>()

    val baseValueText: LiveData<String?> = _baseValueText
    val calculationValueText: LiveData<String?> = _calculationValueText

    // Which keys would do something, for the keypad to dim the rest. Set on
    // every change (not `LiveData.map`) so `.value` is current even with no
    // observer, as unit tests read it.
    private val _keys = MutableLiveData(KeyAvailability())
    val keys: LiveData<KeyAvailability> = _keys

    private fun setCalc(value: String?) {
        _calculationValueText.value = value
        updateKeys()
    }

    private fun setBase(value: String) {
        _baseValueText.value = value
        updateKeys()
    }

    private fun updateKeys() {
        val calc = calculation()
        val tail = (calc ?: base()).trimEnd()
        val last = tail.lastOrNull()
        val next =
            KeyAvailability(
                decimal = !tail.substringAfterLast(' ').contains('.') && last != ')' && last != '%',
                percent = last != null && (last.isDigit() || last == '.'),
                closeParen = calc != null && calc.unclosedParens() > 0 && isCompletedValueTail(last),
                equals = calc != null,
            )
        if (_keys.value != next) _keys.value = next
    }

    fun isInCalculationMode(): Boolean = calculation() != null

    // The calculation row while in calculation mode, else null.
    private fun calculation(): String? = _calculationValueText.value?.takeIf { it.isNotBlank() }

    // The base row; never null once initialised, "0" as the fallback.
    private fun base(): String = _baseValueText.value ?: ZERO

    fun addNumber(value: String) {
        val current = calculation()
        if (current != null) {
            val lastToken = current.split(" ").last().trim()
            when {
                // last input was "0": replace it with any other number
                lastToken == ZERO -> {
                    if (value !in ZERO_KEYS) {
                        setCalc(current.trim().dropLast(1) + value)
                    }
                }

                // last input was an operator: collapse "00"/"000" down to "0"
                current.split(" ").last().isEmpty() &&
                    value in MULTI_ZERO_KEYS -> {
                    setCalc(current + ZERO)
                }

                else -> {
                    setCalc(current + value)
                }
            }
        } else {
            val base = base()
            setBase(
                if (base == ZERO) {
                    if (value in MULTI_ZERO_KEYS) ZERO else value
                } else {
                    base + value
                },
            )
        }
    }

    fun paste(value: Number) {
        // clear base value (but not calculation row!)
        setBase(ZERO)
        value.toString().forEach { addNumber(it.toString()) }
    }

    fun addPercent() {
        if (!isInCalculationMode()) {
            setCalc(_baseValueText.value)
        }
        val current = _calculationValueText.value?.trim() ?: return
        if (current.isNotEmpty() && (current.last().isDigit() || current.last() == '.')) {
            setCalc(if (current.last() == '.') current.dropLast(1) + "%" else current + "%")
        }
    }

    fun addDecimal() {
        val current = calculation()
        if (current != null) {
            if (!current.substringAfterLast(" ").contains(".")) {
                // if last char is not a number: add 0 first
                val prefix = if (!current.trim().last().isDigit()) current + ZERO else current
                setCalc("$prefix.")
            }
        } else {
            val base = base()
            if (!base.contains(".")) {
                setBase("$base.")
            }
        }
    }

    fun delete() {
        val current = calculation()
        if (current != null) {
            var next = current.trim().dropLast(1)
            // if last char is a number: trim any dangling space
            if (next.isNotEmpty() && next.last().isDigit()) next = next.trim()
            // drop back to base row only once no operator or paren remains —
            // otherwise `(5)` deleting to `(` would collapse and lose the paren
            setCalc(if (!next.contains(CALC_TOKEN_REGEX)) null else next)
        } else {
            val base = base()
            if (base.length > 1) {
                setBase(base.dropLast(1))
            } else {
                clear()
            }
        }
    }

    fun clear() {
        setBase(ZERO)
        setCalc(null)
    }

    /** Replaces the input with [value] (any calculation is dropped) — an amount handed in from outside. */
    fun setAmount(value: BigDecimal) {
        setCalc(null)
        setBase(value.abs().stripTrailingZeros().toPlainString())
    }

    /**
     * "=": the calculation's result becomes the amount, so the next key
     * carries on from it ("12 × 3 + 5" becomes 41). Nothing to do without a
     * calculation.
     */
    fun evaluate() {
        val current = calculation() ?: return
        current.evaluateCalculatorExpression().toBigDecimalOrNull()?.let(::setAmount)
    }

    fun addOpenParen() {
        val current = calculation()
        if (current == null) {
            // seed calc row from base like operators do; drop base "0" so the
            // user gets a clean `(` instead of `0 × (`
            val base = base()
            setCalc(if (base.isEmpty() || base == ZERO) PAREN_OPEN else withImplicitMultBeforeOpen(base))
            return
        }
        val trimmed = current.trimEnd()
        // after a value-continuation token (digit, `)`, `%`, `.`) insert an
        // explicit multiplication, so the row reads `5 × (…)` rather than `5(…)`
        setCalc(
            if (isValueContinuationTail(trimmed.lastOrNull())) withImplicitMultBeforeOpen(trimmed) else current + PAREN_OPEN,
        )
    }

    fun addCloseParen() {
        val current = calculation() ?: return
        // only close when there is something to close AND the trailing token is
        // a completed value — refuse `(` -> `()` or `5+` -> `5+)` so unbalanced
        // junk never enters the expression
        val trimmed = current.trimEnd()
        if (current.unclosedParens() > 0 && isCompletedValueTail(trimmed.lastOrNull())) {
            setCalc(trimmed + PAREN_CLOSE)
        }
    }

    fun addOperator(operator: String) {
        val current = calculation()
        if (current != null) {
            val lastChar = current.trim().last()
            when {
                // already an operator at the end: swap it
                lastChar.toString().matches(OPERATOR_REGEX) -> {
                    setCalc(current.trim().dropLast(1) + "$operator ")
                }

                // trailing '.': drop it, then append operator
                lastChar == '.' -> {
                    setCalc(current.trim().dropLast(1) + " $operator ")
                }

                else -> {
                    setCalc(current.trim() + " $operator ")
                }
            }
        } else {
            // switch to calculation mode, seeded from the base row
            setCalc(_baseValueText.value + " $operator ")
        }
    }

    // A "completed value" tail is safe to append `)` after — a digit, a `)`
    // already there, or a `%` (which resolves to a number). A trailing `.` is
    // *not* completed (`2.` needs padding first), and neither is an operator.
    private fun isCompletedValueTail(c: Char?): Boolean = c != null && (c.isDigit() || c == ')' || c == '%')

    // A "value continuation" tail is one where `(` is only legal if we insert
    // an implicit `×` first — i.e. the completed-value cases plus a trailing
    // decimal point that would otherwise collide with `(`.
    private fun isValueContinuationTail(c: Char?): Boolean = isCompletedValueTail(c) || c == '.'

    private fun withImplicitMultBeforeOpen(prefix: String): String = "$prefix $OPERATOR_MULTIPLY $PAREN_OPEN"
}
