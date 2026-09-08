package com.eliormachlev.currencix.view.preference.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
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
 * pasted "1234.5678" collapses to "123.567" instead of being silently rejected.
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

/**
 * Numeric percent field with a "%" suffix. Unsigned decimal input in [0, 100].
 * [onValueChange] fires only for accepted values, so the caller sees the
 * field text with the locale separator preserved. Use [toFeePercentOrNull]
 * on the returned string to parse for persistence.
 */
@Composable
internal fun FeePercentField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sep = feePercentSeparator
    val suffix = @Composable { Text("%", style = MaterialTheme.typography.bodyLarge) }
    OutlinedTextField(
        value = value,
        onValueChange = { incoming ->
            val normalized = incoming.normalizeSeparatorsTo(sep)
            val accepted = longestAcceptablePrefix(normalized, sep)
            if (accepted != null && accepted != value) onValueChange(accepted)
        },
        singleLine = true,
        keyboardOptions = ComposeKeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = suffix,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Remembers a mutable percent-field state seeded from [initial]. Extracted so
 * both the global-fee and specific-pair editors get the same seed conversion
 * (BigDecimal → locale-separator plain string) without duplicating the logic.
 */
@Composable
internal fun rememberFeePercentState(initial: BigDecimal?): androidx.compose.runtime.MutableState<String> {
    val sep = feePercentSeparator
    return rememberSaveable(initial) {
        mutableStateOf(initial?.toPlainString()?.replace('.', sep).orEmpty())
    }
}
