package com.eliormachlev.currencix.util

import java.math.BigDecimal
import java.math.MathContext

// Calculator operator glyphs shown to the user. Kept as constants so the
// "which operator?" check and the "insert this operator" call agree on the
// exact Unicode codepoint (typographical minus and multiplication signs
// differ from ASCII "-" and "*").
const val OPERATOR_PLUS = "\u002B" // +
const val OPERATOR_MINUS = "\u2212" // − (minus sign, not hyphen)
const val OPERATOR_MULTIPLY = "\u00D7" // ×
const val OPERATOR_DIVIDE = "\u00F7" // ÷

val OPERATOR_REGEX =
    Regex("[$OPERATOR_PLUS$OPERATOR_MINUS$OPERATOR_MULTIPLY$OPERATOR_DIVIDE]")

private val SMART_PERCENT_REGEX =
    Regex("""(\d+(?:\.\d+)?)([+\-])(\d+(?:\.\d+)?)%""")

// DECIMAL128 gives 34-digit precision — well beyond the ~15-digit accuracy of
// the Double-based parser this replaced, and enough headroom that any rounding
// error is invisible after stripTrailingZeros().
private val CALC_MATH_CONTEXT = MathContext.DECIMAL128

/**
 * Evaluate a user-facing calculator expression like `"1 + 2 × 4"` and return
 * the numeric result as a plain string. Understands the display glyphs above,
 * "smart percentage" (`A+B%` → `A+(A*B/100)`), simple percentage (`B%` → `B/100`),
 * and pads a trailing operator so a half-typed expression still evaluates.
 * Returns `"0"` on a malformed expression or a division by zero.
 */
fun String.evaluateCalculatorExpression(): String {
    var s =
        this
            .replace(" ", "")
            .replace(OPERATOR_MINUS, "-")
            .replace(OPERATOR_MULTIPLY, "*")
            .replace(OPERATOR_DIVIDE, "/")
    // smart percentage: A+B% = A+(A*B/100), A-B% = A-(A*B/100)
    s =
        s.replace(SMART_PERCENT_REGEX) { m ->
            "${m.groupValues[1]}${m.groupValues[2]}(${m.groupValues[1]}*${m.groupValues[3]}/100)"
        }
    // simple percentage: B% = B/100
    s = s.replace("%", "/100")
    // fill, if last character is an operator
    when (s.trim().last()) {
        '/' -> s += "1"
        '*' -> s += "1"
        '+' -> s += "0"
        '-' -> s += "0"
        '.' -> s += "0"
    }
    val result =
        try {
            ExpressionParser(s).parse()
        } catch (_: ArithmeticException) {
            // division by zero
            return "0"
        } catch (_: IllegalArgumentException) {
            // malformed expression (unexpected character, unbalanced parens, …)
            return "0"
        }
    return result.stripTrailingZeros().toPlainString()
}

/**
 * Minimal recursive-descent evaluator for the normalised calculator expression:
 * digits and `.` for numbers, `+ - * /` binary operators, unary `+`/`-`, and
 * parentheses. `BigDecimal` throughout — no `Double` rounding at intermediate
 * steps. Grammar:
 *
 * ```
 * expr    = term (('+' | '-') term)*
 * term    = factor (('*' | '/') factor)*
 * factor  = ('+' | '-') factor | primary
 * primary = number | '(' expr ')'
 * ```
 */
private class ExpressionParser(private val input: String) {
    private var pos = 0

    fun parse(): BigDecimal {
        val value = parseExpression()
        require(pos == input.length) { "unexpected char at $pos in '$input'" }
        return value
    }

    private fun parseExpression(): BigDecimal {
        var value = parseTerm()
        while (pos < input.length) {
            val c = input[pos]
            if (c != '+' && c != '-') break
            pos++
            val right = parseTerm()
            value = if (c == '+') value.add(right, CALC_MATH_CONTEXT) else value.subtract(right, CALC_MATH_CONTEXT)
        }
        return value
    }

    private fun parseTerm(): BigDecimal {
        var value = parseFactor()
        while (pos < input.length) {
            val c = input[pos]
            if (c != '*' && c != '/') break
            pos++
            val right = parseFactor()
            value = if (c == '*') value.multiply(right, CALC_MATH_CONTEXT) else value.divide(right, CALC_MATH_CONTEXT)
        }
        return value
    }

    private fun parseFactor(): BigDecimal {
        if (pos >= input.length) throw IllegalArgumentException("unexpected end of input")
        return when (input[pos]) {
            '-' -> { pos++; parseFactor().negate() }
            '+' -> { pos++; parseFactor() }
            else -> parsePrimary()
        }
    }

    private fun parsePrimary(): BigDecimal {
        if (pos >= input.length) throw IllegalArgumentException("unexpected end of input")
        if (input[pos] == '(') {
            pos++
            val value = parseExpression()
            require(pos < input.length && input[pos] == ')') { "expected ')' at $pos" }
            pos++
            return value
        }
        return parseNumber()
    }

    private fun parseNumber(): BigDecimal {
        val start = pos
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
        require(pos > start) { "expected number at $start" }
        return BigDecimal(input.substring(start, pos))
    }
}
