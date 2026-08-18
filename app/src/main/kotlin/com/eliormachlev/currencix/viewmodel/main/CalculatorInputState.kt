package com.eliormachlev.currencix.viewmodel.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.eliormachlev.currencix.util.CALC_TOKEN_REGEX
import com.eliormachlev.currencix.util.OPERATOR_MULTIPLY
import com.eliormachlev.currencix.util.OPERATOR_REGEX
import com.eliormachlev.currencix.util.PAREN_CLOSE
import com.eliormachlev.currencix.util.PAREN_OPEN
import com.eliormachlev.currencix.util.unclosedParens

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
    private val _baseValueText = MutableLiveData("0")
    private val _calculationValueText = MutableLiveData<String?>()

    val baseValueText: LiveData<String?> = _baseValueText
    val calculationValueText: LiveData<String?> = _calculationValueText

    // Driven from `setCalc` (not `LiveData.map` / `MediatorLiveData.addSource`)
    // so the value stays current even without an active observer — otherwise
    // the button's XML `textColor` paints both glyphs green at rest and unit
    // tests reading `.value` see null.
    private val _nextParen = MutableLiveData(nextParenFor(null))
    val nextParen: LiveData<Char> = _nextParen

    private fun setCalc(value: String?) {
        _calculationValueText.value = value
        val newParen = nextParenFor(value)
        if (_nextParen.value != newParen) _nextParen.value = newParen
    }

    fun isInCalculationMode(): Boolean = _calculationValueText.value.isNullOrBlank().not()

    fun addNumber(value: String) {
        if (isInCalculationMode()) {
            val current = _calculationValueText.value!!
            val lastToken = current.split(" ").last().trim()
            when {
                // last input was "0": replace it with any other number
                lastToken == "0" -> {
                    if (value != "0" && value != "00" && value != "000") {
                        setCalc(current.trim().dropLast(1) + value)
                    }
                }
                // last input was an operator: collapse "00"/"000" down to "0"
                current.split(" ").last().isEmpty() &&
                    (value == "00" || value == "000") -> {
                    setCalc(current + "0")
                }
                else -> {
                    setCalc(current + value)
                }
            }
        } else {
            val current = _baseValueText.value
            _baseValueText.value =
                if (current == "0") {
                    if (value == "00" || value == "000") "0" else value
                } else {
                    current + value
                }
        }
    }

    fun paste(value: Number) {
        // clear base value (but not calculation row!)
        _baseValueText.value = "0"
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
        if (isInCalculationMode()) {
            val current = _calculationValueText.value!!
            if (!current.substringAfterLast(" ").contains(".")) {
                // if last char is not a number: add 0 first
                val prefix = if (!current.trim().last().isDigit()) current + "0" else current
                setCalc("$prefix.")
            }
        } else {
            val current = _baseValueText.value!!
            if (!current.contains(".")) {
                _baseValueText.value = "$current."
            }
        }
    }

    fun delete() {
        if (isInCalculationMode()) {
            var next = _calculationValueText.value!!.trim().dropLast(1)
            // if last char is a number: trim any dangling space
            if (next.isNotEmpty() && next.last().isDigit()) next = next.trim()
            // drop back to base row only once no operator or paren remains —
            // otherwise `(5)` deleting to `(` would collapse and lose the paren
            setCalc(if (!next.contains(CALC_TOKEN_REGEX)) null else next)
        } else {
            val current = _baseValueText.value!!
            if (current.length > 1) {
                _baseValueText.value = current.dropLast(1)
            } else {
                clear()
            }
        }
    }

    fun clear() {
        _baseValueText.value = "0"
        setCalc(null)
    }

    fun addOpenParen() {
        if (!isInCalculationMode()) {
            // seed calc row from base like operators do; drop base "0" so the
            // user gets a clean `(` instead of `0 × (`
            val base = _baseValueText.value.orEmpty()
            setCalc(if (base.isEmpty() || base == "0") PAREN_OPEN else withImplicitMultBeforeOpen(base))
            return
        }
        val current = _calculationValueText.value!!
        val trimmed = current.trimEnd()
        // after a value-continuation token (digit, `)`, `%`, `.`) insert an
        // implicit multiplication so EvalEx sees `5*(...)` instead of parse error
        setCalc(
            if (isValueContinuationTail(trimmed.lastOrNull())) withImplicitMultBeforeOpen(trimmed) else current + PAREN_OPEN,
        )
    }

    fun addCloseParen() {
        if (!isInCalculationMode()) return
        val current = _calculationValueText.value!!
        // only close when there is something to close AND the trailing token is
        // a completed value — refuse `(` -> `()` or `5+` -> `5+)` so unbalanced
        // junk never enters the expression
        val trimmed = current.trimEnd()
        if (current.unclosedParens() > 0 && isCompletedValueTail(trimmed.lastOrNull())) {
            setCalc(trimmed + PAREN_CLOSE)
        }
    }

    // Cycle-toggle entry point for the shared `()` keypad button — dispatches
    // to open/close using the same rule the button's highlight is drawn from,
    // so pressing the button always inserts whichever glyph is highlighted.
    fun applyNextParen() {
        if (_nextParen.value == ')') addCloseParen() else addOpenParen()
    }

    fun addOperator(operator: String) {
        if (isInCalculationMode()) {
            val current = _calculationValueText.value!!
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

    private fun nextParenFor(calc: String?): Char {
        if (calc.isNullOrBlank()) return '('
        val canClose = calc.unclosedParens() > 0 && isCompletedValueTail(calc.trimEnd().lastOrNull())
        return if (canClose) ')' else '('
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
