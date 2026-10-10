package com.eliormachlev.currencix.util

import com.eliormachlev.currencix.viewmodel.cart.CartSnapshot
import java.time.LocalDateTime

private const val CSV_QUOTE = '"'
private const val CSV_SEPARATOR = ','
private const val CSV_LINE_END = "\r\n"

// Builds an RFC 4180-compliant CSV rendering of a cart snapshot. Line
// terminator is CRLF so spreadsheet apps (Excel, Numbers, LibreOffice)
// recognise row boundaries even on macOS. Every field is quoted and inner
// quotes are doubled — safest form when item names may contain commas or
// quotes themselves. [title] is the cart name (or a datetime fallback);
// [generatedAt] is the local wall-clock time the export was produced.
fun CartSnapshot.toCsv(
    title: String,
    labels: CartExportLabels,
    generatedAt: LocalDateTime = LocalDateTime.now(),
): String =
    buildString {
        append(csvRow(labels.cart, title))
        cartExportMeta(generatedAt).forEach { (field, value) ->
            append(csvRow(labels.meta.getValue(field), value))
        }
        append(csvRow("", ""))
        append(csvRow(labels.item, labels.expression, withUnit(labels.value, baseCurrency.iso4217Alpha())))
        evaluatedItems.forEach { (item, value) ->
            append(csvRow(item.name, item.expression, value.toCartDisplayString()))
        }
        append(csvRow("", "", ""))
        append(csvRow(labels.subtotal, "", subtotal.toCartDisplayString()))
        if (isConverting) {
            append(csvRow(withUnit(labels.converted, destinationCurrency.iso4217Alpha()), "", convertedSubtotal.toCartDisplayString()))
        }
        val combinedStack = feeStack
        if (!combinedStack.isNeutralFeeStack()) {
            append(csvRow(withUnit(labels.fees, "${combinedStack.feePercentDelta().toPlainString()}%"), "", ""))
        }
        append(csvRow(withUnit(labels.total, destinationCurrency.iso4217Alpha()), "", total.toCartDisplayString()))
    }

private fun csvRow(vararg cells: String): String =
    cells.joinToString(separator = CSV_SEPARATOR.toString(), postfix = CSV_LINE_END) { it.csvQuote() }

private fun String.csvQuote(): String {
    val escaped = replace("$CSV_QUOTE", "$CSV_QUOTE$CSV_QUOTE")
    return "$CSV_QUOTE$escaped$CSV_QUOTE"
}
