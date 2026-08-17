package com.eliormachlev.currencix.viewmodel.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.eliormachlev.currencix.util.CALC_TOKEN_REGEX
import com.eliormachlev.currencix.util.OPERATOR_MULTIPLY
import com.eliormachlev.currencix.util.OPERATOR_REGEX
import com.eliormachlev.currencix.util.PAREN_CLOSE
import com.eliormachlev.currencix.util.PAREN_OPEN

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

    // Which glyph the paren-cycle button should insert next — `(` while the
    // expression is either empty or in a position where an operand may start,
    // `)` once there is an unclosed `(` and the trailing token is value-like.
    // Derived from calculationValueText so the button lights up automatically
    // as the user types, without a separate counter to keep in sync.
    val nextParen: LiveData<Char> = _calculationValueText.map { nextParenFor(it) }

    fun isInCalculationMode(): Boolean = _calculationValueText.value.isNullOrBlank().not()

    fun addNumber(value: String) {
        if (isInCalculationMode()) {
            val current = _calculationValueText.value!!
            val lastToken = current.split(" ").last().trim()
            when {
                // last input was "0": replace it with any other number
                lastToken == "0" -> {
                    if (value != "0" && value != "00" && value != "000") {
                        _calculationValueText.value = current.trim().dropLast(1) + value
                    }
                }
                // last input was an operator: collapse "00"/"000" down to "0"
                current.split(" ").last().isEmpty() &&
                    (value == "00" || value == "000") -> {
                    _calculationValueText.value = current + "0"
                }
                else -> {
                    _calculationValueText.value = current + value
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
            _calculationValueText.value = _baseValueText.value
        }
        val current = _calculationValueText.value?.trim() ?: return
        if (current.isNotEmpty() && (current.last().isDigit() || current.last() == '.')) {
            _calculationValueText.value =
                if (current.last() == '.') current.dropLast(1) + "%" else current + "%"
        }
    }

    fun addDecimal() {
        if (isInCalculationMode()) {
            val current = _calculationValueText.value!!
            if (!current.substringAfterLast(" ").contains(".")) {
                // if last char is not a number: add 0 first
                val prefix = if (!current.trim().last().isDigit()) current + "0" else current
                _calculationValueText.value = "$prefix."
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
            _calculationValueText.value =
                if (!next.contains(CALC_TOKEN_REGEX)) null else next
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
        _calculationValueText.value = null
    }

    fun addOpenParen() {
        if (!isInCalculationMode()) {
            // seed calc row from base like operators do; drop base "0" so the
            // user gets a clean `(` instead of `0 × (`
            val base = _baseValueText.value.orEmpty()
            _calculationValueText.value =
                if (base.isEmpty() || base == "0") PAREN_OPEN else withImplicitMultBeforeOpen(base)
            return
        }
        val current = _calculationValueText.value!!
        val trimmed = current.trimEnd()
        // after a value-continuation token (digit, `)`, `%`, `.`) insert an
        // implicit multiplication so EvalEx sees `5*(...)` instead of parse error
        _calculationValueText.value =
            if (isValueContinuationTail(trimmed.lastOrNull())) withImplicitMultBeforeOpen(trimmed) else current + PAREN_OPEN
    }

    fun addCloseParen() {
        if (!isInCalculationMode()) return
        val current = _calculationValueText.value!!
        // only close when there is something to close AND the trailing token is
        // a completed value — refuse `(` -> `()` or `5+` -> `5+)` so unbalanced
        // junk never enters the expression
        val trimmed = current.trimEnd()
        if (unclosedParens(current) > 0 && isCompletedValueTail(trimmed.lastOrNull())) {
            _calculationValueText.value = trimmed + PAREN_CLOSE
        }
    }

    // Cycle-toggle entry point for the shared `()` keypad button — dispatches
    // to open/close using the same rule the button's highlight is drawn from,
    // so pressing the button always inserts whichever glyph is highlighted.
    // Recomputes fresh instead of reading `nextParen.value` so the dispatch
    // stays correct even when the LiveData has no observers attached yet.
    fun applyNextParen() {
        if (nextParenFor(_calculationValueText.value) == ')') addCloseParen() else addOpenParen()
    }

    fun addOperator(operator: String) {
        if (isInCalculationMode()) {
            val current = _calculationValueText.value!!
            val lastChar = current.trim().last()
            when {
                // already an operator at the end: swap it
                lastChar.toString().matches(OPERATOR_REGEX) -> {
                    _calculationValueText.value = current.trim().dropLast(1) + "$operator "
                }
                // trailing '.': drop it, then append operator
                lastChar == '.' -> {
                    _calculationValueText.value = current.trim().dropLast(1) + " $operator "
                }
                else -> {
                    _calculationValueText.value = current.trim() + " $operator "
                }
            }
        } else {
            // switch to calculation mode, seeded from the base row
            _calculationValueText.value = _baseValueText.value + " $operator "
        }
    }

    private fun nextParenFor(calc: String?): Char {
        if (calc.isNullOrBlank()) return '('
        val canClose = unclosedParens(calc) > 0 && isCompletedValueTail(calc.trimEnd().lastOrNull())
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

    private fun unclosedParens(s: String): Int = s.count { it == '(' } - s.count { it == ')' }
}
