package com.eliormachlev.currencix.util

import android.content.Context
import androidx.annotation.StringRes
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.viewmodel.cart.CartSnapshot
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Shared "money-facing" rounding scale used by every cart export renderer
// (CSV, PDF, share-text). Pinned here so a change to the display precision
// only needs one edit instead of one per format.
const val CART_EXPORT_DISPLAY_SCALE: Int = 2

// Timestamp format for the "Exported at" meta row. ISO local date-time is
// unambiguous across locales and sortable when a receiver dumps the meta
// rows into a spreadsheet.
val CART_EXPORT_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

// Meta fields rendered alongside a cart export (as CSV rows or PDF header
// lines). Keeping the labels on the enum guarantees CSV and PDF spell them
// identically.
enum class CartExportField(
    @StringRes val label: Int,
) {
    SOURCE(R.string.cart_export_source),
    RATES_DATE(R.string.cart_export_rates_date),
    EXPORTED_AT(R.string.cart_export_exported_at),
}

/**
 * The words a cart export (CSV, PDF) is written in: the app's language, as
 * the share text is. Resolved once from a [Context] so the renderers stay
 * plain functions.
 */
data class CartExportLabels(
    val cart: String,
    val item: String,
    val expression: String,
    val value: String,
    val subtotal: String,
    val converted: String,
    val fees: String,
    val total: String,
    val meta: Map<CartExportField, String>,
) {
    companion object {
        fun from(context: Context): CartExportLabels =
            CartExportLabels(
                cart = context.getString(R.string.cart_share_default_title),
                item = context.getString(R.string.cart_item_name_hint),
                expression = context.getString(R.string.cart_export_expression),
                value = context.getString(R.string.cart_export_value),
                subtotal = context.getString(R.string.cart_subtotal_label),
                converted = context.getString(R.string.cart_export_converted),
                fees = context.getString(R.string.cart_export_fees),
                total = context.getString(R.string.cart_total_label),
                meta = CartExportField.entries.associateWith { context.getString(it.label) },
            )
    }
}

/** "Value (EUR)": a column or row label with the currency it's counted in. */
internal fun withUnit(
    label: String,
    unit: String,
): String = "$label ($unit)"

/**
 * Ordered `(field, value)` pairs describing when/where a cart export was
 * produced. Nullable inputs (no rates loaded yet) drop out of the list so
 * callers can render the result without null-checking each field.
 */
fun CartSnapshot.cartExportMeta(generatedAt: LocalDateTime): List<Pair<CartExportField, String>> =
    buildList {
        providerName?.let { add(CartExportField.SOURCE to it) }
        ratesDate?.let { add(CartExportField.RATES_DATE to it.toString()) }
        add(CartExportField.EXPORTED_AT to generatedAt.format(CART_EXPORT_TIMESTAMP_FORMAT))
    }
