package com.eliormachlev.currencix.util

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
// identically and gives one place to localise them later.
enum class CartExportField(
    val label: String,
) {
    SOURCE("Source"),
    RATES_DATE("Rates date"),
    EXPORTED_AT("Exported at"),
}

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
