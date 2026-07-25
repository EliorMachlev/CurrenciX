package de.salomax.currencies.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import de.salomax.currencies.viewmodel.cart.CartSnapshot
import java.io.ByteArrayOutputStream
import java.math.RoundingMode

// A4 at 72 dpi. Android's PdfDocument treats page units as points; A4 is
// 8.27 × 11.69 in = 595 × 842 pt. Matches what every desktop PDF renderer
// expects, so the output prints without scaling.
private const val PAGE_WIDTH_PT = 595
private const val PAGE_HEIGHT_PT = 842
private const val PAGE_MARGIN_PT = 40f
private const val LINE_HEIGHT_PT = 18f
private const val SECTION_GAP_PT = 12f

private const val TITLE_TEXT_SIZE = 20f
private const val HEADER_TEXT_SIZE = 12f
private const val BODY_TEXT_SIZE = 11f
private const val DISPLAY_SCALE = 2

// Renders a cart snapshot into a single-page A4 PDF. Deliberately built on
// [PdfDocument] rather than a third-party generator so the app doesn't pick
// up an extra dependency for this narrow feature. Wide carts still fit
// because item names truncate at the value column; a follow-up can add
// multi-page pagination if user carts start exceeding ~40 rows.
fun CartSnapshot.toPdfBytes(title: String): ByteArray {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, 1).create()
    val page = document.startPage(pageInfo)
    drawSnapshot(page.canvas, title)
    document.finishPage(page)

    val out = ByteArrayOutputStream()
    document.writeTo(out)
    document.close()
    return out.toByteArray()
}

private fun CartSnapshot.drawSnapshot(
    canvas: Canvas,
    title: String,
) {
    val titlePaint = paint(TITLE_TEXT_SIZE, bold = true)
    val headerPaint = paint(HEADER_TEXT_SIZE, bold = true)
    val bodyPaint = paint(BODY_TEXT_SIZE)
    val bodyRight = paint(BODY_TEXT_SIZE).apply { textAlign = Paint.Align.RIGHT }

    val rightX = PAGE_WIDTH_PT - PAGE_MARGIN_PT
    var y = PAGE_MARGIN_PT + TITLE_TEXT_SIZE

    canvas.drawText(title, PAGE_MARGIN_PT, y, titlePaint)
    y += SECTION_GAP_PT + LINE_HEIGHT_PT

    canvas.drawText("Item", PAGE_MARGIN_PT, y, headerPaint)
    canvas.drawText("Value (${baseCurrency.iso4217Alpha()})", rightX, y, headerPaint.apply { textAlign = Paint.Align.RIGHT })
    y += LINE_HEIGHT_PT

    evaluatedItems.forEach { (item, value) ->
        val label = item.name.ifBlank { item.expression }
        canvas.drawText(label, PAGE_MARGIN_PT, y, bodyPaint)
        canvas.drawText(value.toDisplayString(), rightX, y, bodyRight)
        y += LINE_HEIGHT_PT
    }

    y += SECTION_GAP_PT
    canvas.drawText("Subtotal", PAGE_MARGIN_PT, y, headerPaint.apply { textAlign = Paint.Align.LEFT })
    canvas.drawText("${subtotal.toDisplayString()} ${baseCurrency.iso4217Alpha()}", rightX, y, bodyRight)
    y += LINE_HEIGHT_PT

    if (isConverting) {
        canvas.drawText("Converted", PAGE_MARGIN_PT, y, bodyPaint)
        canvas.drawText("${convertedSubtotal.toDisplayString()} ${destinationCurrency.iso4217Alpha()}", rightX, y, bodyRight)
        y += LINE_HEIGHT_PT
    }
    if (!feeStack.isNeutralFeeStack()) {
        canvas.drawText("Fees", PAGE_MARGIN_PT, y, bodyPaint)
        canvas.drawText("${feeStack.feePercentDelta().toPlainString()}%", rightX, y, bodyRight)
        y += LINE_HEIGHT_PT
    }

    y += SECTION_GAP_PT
    canvas.drawText("Total", PAGE_MARGIN_PT, y, headerPaint.apply { textAlign = Paint.Align.LEFT })
    canvas.drawText(
        "${total.toDisplayString()} ${destinationCurrency.iso4217Alpha()}",
        rightX,
        y,
        paint(BODY_TEXT_SIZE, bold = true).apply { textAlign = Paint.Align.RIGHT },
    )
}

private fun paint(
    size: Float,
    bold: Boolean = false,
): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        if (bold) isFakeBoldText = true
    }

private fun java.math.BigDecimal.toDisplayString(): String = setScale(DISPLAY_SCALE, RoundingMode.HALF_EVEN).toPlainString()
