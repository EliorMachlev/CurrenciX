package com.eliormachlev.currencix.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.eliormachlev.currencix.viewmodel.cart.CartSnapshot
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime

// A4 at 72 dpi. Android's PdfDocument treats page units as points; A4 is
// 8.27 × 11.69 in = 595 × 842 pt. Matches what every desktop PDF renderer
// expects, so the output prints without scaling.
private const val PAGE_WIDTH_PT = 595
private const val PAGE_HEIGHT_PT = 842
private const val PAGE_MARGIN_PT = 40f
private const val LINE_HEIGHT_PT = 18f
private const val META_LINE_HEIGHT_PT = 14f
private const val SECTION_GAP_PT = 12f

private const val TITLE_TEXT_SIZE = 20f
private const val HEADER_TEXT_SIZE = 12f
private const val BODY_TEXT_SIZE = 11f
private const val META_TEXT_SIZE = 10f

// Renders a cart snapshot into a single-page A4 PDF. Deliberately built on
// [PdfDocument] rather than a third-party generator so the app doesn't pick
// up an extra dependency for this narrow feature. Wide carts still fit
// because item names truncate at the value column; a follow-up can add
// multi-page pagination if user carts start exceeding ~40 rows.
fun CartSnapshot.toPdfBytes(
    title: String,
    generatedAt: LocalDateTime = LocalDateTime.now(),
): ByteArray {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, 1).create()
    val page = document.startPage(pageInfo)
    drawSnapshot(page.canvas, title, generatedAt)
    document.finishPage(page)

    val out = ByteArrayOutputStream()
    document.writeTo(out)
    document.close()
    return out.toByteArray()
}

private fun CartSnapshot.drawSnapshot(
    canvas: Canvas,
    title: String,
    generatedAt: LocalDateTime,
) {
    val titlePaint = paint(TITLE_TEXT_SIZE, bold = true)
    val headerPaint = paint(HEADER_TEXT_SIZE, bold = true)
    val bodyPaint = paint(BODY_TEXT_SIZE)
    val metaPaint = paint(META_TEXT_SIZE)
    val bodyRight = paint(BODY_TEXT_SIZE).apply { textAlign = Paint.Align.RIGHT }

    val rightX = PAGE_WIDTH_PT - PAGE_MARGIN_PT
    var y = PAGE_MARGIN_PT + TITLE_TEXT_SIZE

    canvas.drawText(title, PAGE_MARGIN_PT, y, titlePaint)
    y += META_LINE_HEIGHT_PT

    cartExportMeta(generatedAt).forEach { (field, value) ->
        canvas.drawText("${field.label}: $value", PAGE_MARGIN_PT, y, metaPaint)
        y += META_LINE_HEIGHT_PT
    }
    y += SECTION_GAP_PT + LINE_HEIGHT_PT - META_LINE_HEIGHT_PT

    canvas.drawText("Item", PAGE_MARGIN_PT, y, headerPaint)
    canvas.drawText("Value (${baseCurrency.iso4217Alpha()})", rightX, y, headerPaint.apply { textAlign = Paint.Align.RIGHT })
    y += LINE_HEIGHT_PT

    evaluatedItems.forEach { (item, value) ->
        val label = item.name.ifBlank { item.expression }
        canvas.drawText(label, PAGE_MARGIN_PT, y, bodyPaint)
        canvas.drawText(value.toCartDisplayString(), rightX, y, bodyRight)
        y += LINE_HEIGHT_PT
    }

    y += SECTION_GAP_PT
    canvas.drawText("Subtotal", PAGE_MARGIN_PT, y, headerPaint.apply { textAlign = Paint.Align.LEFT })
    canvas.drawText("${subtotal.toCartDisplayString()} ${baseCurrency.iso4217Alpha()}", rightX, y, bodyRight)
    y += LINE_HEIGHT_PT

    if (isConverting) {
        canvas.drawText("Converted", PAGE_MARGIN_PT, y, bodyPaint)
        canvas.drawText("${convertedSubtotal.toCartDisplayString()} ${destinationCurrency.iso4217Alpha()}", rightX, y, bodyRight)
        y += LINE_HEIGHT_PT
    }
    val combinedStack = sideStacks.combined
    if (!combinedStack.isNeutralFeeStack()) {
        canvas.drawText("Fees", PAGE_MARGIN_PT, y, bodyPaint)
        canvas.drawText("${combinedStack.feePercentDelta().toPlainString()}%", rightX, y, bodyRight)
        y += LINE_HEIGHT_PT
    }

    y += SECTION_GAP_PT
    canvas.drawText("Total", PAGE_MARGIN_PT, y, headerPaint.apply { textAlign = Paint.Align.LEFT })
    canvas.drawText(
        "${total.toCartDisplayString()} ${destinationCurrency.iso4217Alpha()}",
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
