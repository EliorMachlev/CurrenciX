package com.eliormachlev.currencix.view.preference.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import androidx.compose.foundation.text.KeyboardOptions as ComposeKeyboardOptions

// Fee percent field: unsigned decimals in [0, 100] with at most
// FEE_PERCENT_MAX_INT_DIGITS before and FEE_PERCENT_MAX_FRACTION_DIGITS
// after the decimal separator. The current locale's decimal separator is
// what actually appears in the field, but both "." and "," in typed or
// pasted input are auto-normalized to it so keyboards / spreadsheet paste
// from other locales still work. Paste that exceeds the digit budget is
// trimmed from the tail rather than rejected outright.
private val FEE_PERCENT_RANGE = BigDecimal.ZERO..BigDecimal("100")
private const val FEE_PERCENT_MAX_INT_DIGITS = 3
private const val FEE_PERCENT_MAX_FRACTION_DIGITS = 3

internal val feePercentSeparator: Char
    get() = DecimalFormatSymbols.getInstance().decimalSeparator

private fun feePercentFormat(sep: Char): Regex =
    Regex(
        "^\\d{0,$FEE_PERCENT_MAX_INT_DIGITS}" +
            "(?:\\Q$sep\\E\\d{0,$FEE_PERCENT_MAX_FRACTION_DIGITS})?$",
    )

private fun String.normalizeSeparatorsTo(sep: Char): String = replace('.', sep).replace(',', sep)

internal fun String.toFeePercentOrNull(sep: Char): BigDecimal? = replace(sep, '.').toBigDecimalOrNull()

private fun isAcceptable(
    text: String,
    sep: Char,
    format: Regex,
): Boolean =
    when {
        !format.matches(text) -> false
        text.isEmpty() || text.last() == sep -> true
        else -> text.toFeePercentOrNull(sep)?.let { it in FEE_PERCENT_RANGE } == true
    }

/**
 * Longest prefix of [candidate] that is a valid fee-percent field. Preserves
 * the graceful paste-truncation behavior of the XML input filter: e.g. a
 * pasted "1.23456" collapses to "1.234" instead of being silently rejected.
 * Returns `null` only when even the empty string is somehow invalid (never
 * happens from a valid starting state, but keeps the caller total).
 */
private fun longestAcceptablePrefix(
    candidate: String,
    sep: Char,
): String? {
    val format = feePercentFormat(sep)
    var current = candidate
    while (true) {
        if (isAcceptable(current, sep, format)) return current
        if (current.isEmpty()) return null
        current = current.dropLast(1)
    }
}

// Keeps the field a valid fee percent as the user types or pastes: both
// separators become the locale's, and input past the digit budget is cut
// from the tail (see [longestAcceptablePrefix]).
private class FeePercentInputTransformation(
    private val sep: Char,
) : InputTransformation {
    override val keyboardOptions = ComposeKeyboardOptions(keyboardType = KeyboardType.Decimal)

    override fun TextFieldBuffer.transformInput() {
        val typed = asCharSequence().toString()
        val accepted = longestAcceptablePrefix(typed.normalizeSeparatorsTo(sep), sep)
        if (accepted == null) {
            revertAllChanges()
        } else if (accepted != typed) {
            replace(0, length, accepted)
        }
    }
}

/**
 * Numeric percent field with a "%" suffix. Unsigned decimal input in [0, 100];
 * [state] only ever holds an accepted value, with the locale separator. Use
 * [toFeePercentOrNull] on its text to parse for persistence.
 */
@Composable
internal fun FeePercentField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
) {
    val sep = feePercentSeparator
    val transformation = remember(sep) { FeePercentInputTransformation(sep) }
    OutlinedTextField(
        state = state,
        inputTransformation = transformation,
        lineLimits = TextFieldLineLimits.SingleLine,
        suffix = { Text("%", style = MaterialTheme.typography.bodyLarge) },
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Remembers the percent field's state seeded from [initial], so the global-fee
 * and specific-pair editors share the seed conversion (BigDecimal →
 * locale-separator plain string).
 */
@Composable
internal fun rememberFeePercentState(initial: BigDecimal?): TextFieldState {
    val sep = feePercentSeparator
    return rememberSaveable(initial, saver = TextFieldState.Saver) {
        TextFieldState(initial?.toPlainString()?.replace('.', sep).orEmpty())
    }
}
