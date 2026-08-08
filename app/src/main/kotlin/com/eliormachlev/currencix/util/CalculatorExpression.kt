package com.eliormachlev.currencix.util

import com.ezylang.evalex.Expression

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

// Returned whenever the expression can't be evaluated (parse error, division
// by zero, unbalanced parens, …). Keeps the UI contract stable across parser
// swaps.
private const val FALLBACK_RESULT = "0"

// Neutral operand appended when the user hasn't finished typing the trailing
// token — e.g. `5+` becomes `5+0`, `5×` becomes `5×1`, `5.` becomes `5.0`.
private val TRAILING_TOKEN_PADDING: Map<Char, Char> =
    mapOf(
        '+' to '0',
        '-' to '0',
        '*' to '1',
        '/' to '1',
        '.' to '0',
    )

private val SMART_PERCENT_REGEX =
    Regex("""(\d+(?:\.\d+)?)([+\-])(\d+(?:\.\d+)?)%""")

/**
 * Evaluate a user-facing calculator expression like `"1 + 2 × 4"` and return
 * the numeric result as a plain string. Understands the display glyphs above,
 * "smart percentage" (`A+B%` → `A+(A*B/100)`), simple percentage (`B%` → `B/100`),
 * and pads a trailing operator so a half-typed expression still evaluates.
 * Returns `"0"` on a malformed expression or a division by zero.
 */
fun String.evaluateCalculatorExpression(): String {
    val normalised =
        normaliseGlyphsToAscii()
            .expandPercent()
            .padTrailingToken()
    // EvalEx.evaluate() throws checked ParseException/EvaluationException
    // (parse errors, unbalanced parens, division by zero, …). Every failure
    // collapses to FALLBACK_RESULT — same contract the UI relied on with
    // mXparser.
    return try {
        Expression(normalised)
            .evaluate()
            .numberValue
            .stripTrailingZeros()
            .toPlainString()
    } catch (_: Exception) {
        FALLBACK_RESULT
    }
}

private fun String.normaliseGlyphsToAscii(): String =
    this
        .replace(" ", "")
        .replace(OPERATOR_MINUS, "-")
        .replace(OPERATOR_MULTIPLY, "*")
        .replace(OPERATOR_DIVIDE, "/")

// `A+B%` → `A+(A*B/100)`, `A-B%` → `A-(A*B/100)`, standalone `B%` → `B/100`.
// The smart-percent rewrite has to run before the simple `%` → `/100` sweep,
// otherwise the anchor digits get consumed first.
private fun String.expandPercent(): String =
    replace(SMART_PERCENT_REGEX) { m ->
        "${m.groupValues[1]}${m.groupValues[2]}(${m.groupValues[1]}*${m.groupValues[3]}/100)"
    }.replace("%", "/100")

private fun String.padTrailingToken(): String {
    val neutral = TRAILING_TOKEN_PADDING[trim().lastOrNull()] ?: return this
    return this + neutral
}
