package de.salomax.currencies.util

import de.salomax.currencies.viewmodel.cart.CartSnapshot
import java.time.LocalDateTime

private const val CSV_QUOTE = '"'
private const val CSV_SEPARATOR = ','
private const val CSV_LINE_END = "\r\n"
private const val CART_LABEL = "Cart"

// Builds an RFC 4180-compliant CSV rendering of a cart snapshot. Line
// terminator is CRLF so spreadsheet apps (Excel, Numbers, LibreOffice)
// recognise row boundaries even on macOS. Every field is quoted and inner
// quotes are doubled — safest form when item names may contain commas or
// quotes themselves. [title] is the cart name (or a datetime fallback);
// [generatedAt] is the local wall-clock time the export was produced.
fun CartSnapshot.toCsv(
    title: String,
    generatedAt: LocalDateTime = LocalDateTime.now(),
): String =
    buildString {
        append(csvRow(CART_LABEL, title))
        cartExportMeta(generatedAt).forEach { (field, value) ->
            append(csvRow(field.label, value))
        }
        append(csvRow("", ""))
        append(csvRow("Item", "Expression", "Value (${baseCurrency.iso4217Alpha()})"))
        evaluatedItems.forEach { (item, value) ->
            append(csvRow(item.name, item.expression, value.toCartExportString()))
        }
        append(csvRow("", "", ""))
        append(csvRow("Subtotal", "", subtotal.toCartExportString()))
        if (isConverting) {
            append(csvRow("Converted (${destinationCurrency.iso4217Alpha()})", "", convertedSubtotal.toCartExportString()))
        }
        if (!feeStack.isNeutralFeeStack()) {
            append(csvRow("Fees (${feeStack.feePercentDelta().toPlainString()}%)", "", ""))
        }
        append(csvRow("Total (${destinationCurrency.iso4217Alpha()})", "", total.toCartExportString()))
    }

private fun csvRow(vararg cells: String): String =
    cells.joinToString(separator = CSV_SEPARATOR.toString(), postfix = CSV_LINE_END) { it.csvQuote() }

private fun String.csvQuote(): String {
    val escaped = replace("$CSV_QUOTE", "$CSV_QUOTE$CSV_QUOTE")
    return "$CSV_QUOTE$escaped$CSV_QUOTE"
}
