package com.eliormachlev.currencix.util

import android.content.Context
import com.eliormachlev.currencix.model.Currency
import java.math.BigDecimal

// Shared display helpers used by every cart surface (footer view binding,
// share text, PDF, CSV). The scale is aliased onto [CART_EXPORT_DISPLAY_SCALE]
// so a change to the money-facing precision only needs one edit.

/** Rounded, plain-string form of a monetary value at the cart's display scale. */
fun BigDecimal.toCartDisplayString(): String = roundForDisplay(CART_EXPORT_DISPLAY_SCALE).toPlainString()

/**
 * Percentage delta of a fee stack ("2.50" for a 1.025 stack), pinned to the
 * cart's display scale. Shared by the on-screen fee line and the "Fees:" row
 * in shared text so both surfaces render identically.
 */
fun BigDecimal.toCartFeePercentDisplay(): String = feePercentDelta(CART_EXPORT_DISPLAY_SCALE).toPlainString()

/**
 * Locale-aware amount + currency marker in the shape the cart's footer rows
 * show ("1,234.56 $"). Null value collapses to zero — cart callers routinely
 * pass a still-loading LiveData value.
 */
fun Context.formatCartAmount(
    value: BigDecimal?,
    currency: Currency?,
): String {
    val amount =
        (value ?: BigDecimal.ZERO)
            .roundForDisplay(CART_EXPORT_DISPLAY_SCALE)
            .toHumanReadableNumber(this, decimalPlaces = CART_EXPORT_DISPLAY_SCALE)
    val marker = currency?.symbolOrIso()
    return if (marker.isNullOrEmpty()) amount else "$amount $marker"
}
