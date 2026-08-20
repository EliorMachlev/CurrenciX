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

// Parentheses use plain ASCII in both display and evaluator — no glyph
// normalisation needed. Kept as constants so the paren-cycle button, the
// state-machine, and the auto-close padder all agree on the same character.
const val PAREN_OPEN = "("
const val PAREN_CLOSE = ")"

// Single source of truth pairing each display glyph with the ASCII operator
// EvalEx understands. Drives both [OPERATOR_REGEX] and [normaliseGlyphsToAscii]
// so adding an operator is a one-line change instead of three coordinated ones.
// linkedMapOf keeps the declaration order stable — the identity `+ → +` entry
// intentionally lands first so a future reader sees the natural PLUS-first order.
private val DISPLAY_TO_ASCII: Map<String, String> =
    linkedMapOf(
        OPERATOR_PLUS to "+",
        OPERATOR_MINUS to "-",
        OPERATOR_MULTIPLY to "*",
        OPERATOR_DIVIDE to "/",
    )

val OPERATOR_REGEX = Regex("[${DISPLAY_TO_ASCII.keys.joinToString("")}]")

// Any character that means "the calculation row still has structure worth
// keeping" — operators plus parentheses. Used by delete() to decide whether a
// trimmed calc string can drop back to base row, or must stay in calc mode.
val CALC_TOKEN_REGEX = Regex("[${DISPLAY_TO_ASCII.keys.joinToString("")}()]")

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
            .closeUnbalancedParens()
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

// Exposed to the system-IME seed path so a state already holding display glyphs
// like `5 − 3` can be re-typed into an EditText as ASCII `5-3`.
internal fun String.normaliseGlyphsToAscii(): String =
    DISPLAY_TO_ASCII.entries.fold(replace(" ", "")) { acc, (glyph, ascii) ->
        acc.replace(glyph, ascii)
    }

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

// Auto-close support: `(1+2` evaluates as `(1+2)`. Counts unclosed `(` (that
// have already been normalised to ASCII) and appends the missing `)`s so a
// half-typed expression still evaluates instead of collapsing to FALLBACK.
private fun String.closeUnbalancedParens(): String {
    val missing = unclosedParens()
    return if (missing > 0) this + ")".repeat(missing) else this
}

// Balance-count of `(` minus `)`. Positive = `(`s waiting to be closed;
// negative = extra `)`s (rejected upstream, so callers can treat <=0 as
// "already balanced"). One-pass fold keeps it cheap on the keystroke path.
internal fun String.unclosedParens(): Int =
    fold(0) { n, c ->
        when (c) {
            '(' -> n + 1
            ')' -> n - 1
            else -> n
        }
    }
