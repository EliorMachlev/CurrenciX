package de.salomax.currencies.util

import de.salomax.currencies.viewmodel.cart.CartSnapshot
import java.math.RoundingMode

private const val DISPLAY_SCALE = 2
private const val CSV_QUOTE = '"'
private const val CSV_SEPARATOR = ','
private const val CSV_LINE_END = "\r\n"

// Builds an RFC 4180-compliant CSV rendering of a cart snapshot. Line
// terminator is CRLF so spreadsheet apps (Excel, Numbers, LibreOffice)
// recognise row boundaries even on macOS. Every field is quoted and inner
// quotes are doubled — safest form when item names may contain commas or
// quotes themselves.
fun CartSnapshot.toCsv(): String =
    buildString {
        append(csvRow("Item", "Expression", "Value (${baseCurrency.iso4217Alpha()})"))
        evaluatedItems.forEach { (item, value) ->
            append(csvRow(item.name, item.expression, value.toDisplayString()))
        }
        append(csvRow("", "", ""))
        append(csvRow("Subtotal", "", subtotal.toDisplayString()))
        if (isConverting) {
            append(csvRow("Converted (${destinationCurrency.iso4217Alpha()})", "", convertedSubtotal.toDisplayString()))
        }
        if (!feeStack.isNeutralFeeStack()) {
            append(csvRow("Fees (${feeStack.feePercentDelta().toPlainString()}%)", "", ""))
        }
        append(csvRow("Total (${destinationCurrency.iso4217Alpha()})", "", total.toDisplayString()))
    }

private fun csvRow(vararg cells: String): String =
    cells.joinToString(separator = CSV_SEPARATOR.toString(), postfix = CSV_LINE_END) { it.csvQuote() }

private fun String.csvQuote(): String {
    val escaped = replace("$CSV_QUOTE", "$CSV_QUOTE$CSV_QUOTE")
    return "$CSV_QUOTE$escaped$CSV_QUOTE"
}

private fun java.math.BigDecimal.toDisplayString(): String = setScale(DISPLAY_SCALE, RoundingMode.HALF_EVEN).toPlainString()
