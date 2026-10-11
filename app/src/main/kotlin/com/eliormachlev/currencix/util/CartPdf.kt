package com.eliormachlev.currencix.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.createBitmap
import com.eliormachlev.currencix.viewmodel.cart.CartSnapshot
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import kotlin.math.roundToInt

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

// The receipt image: the PDF's layout at twice its size, as tall as the
// receipt needs (no page to run off), on white.
private const val IMAGE_SCALE = 2f
private const val PNG_QUALITY = 100

// Renders a cart snapshot into a single-page A4 PDF. Deliberately built on
// [PdfDocument] rather than a third-party generator so the app doesn't pick
// up an extra dependency for this narrow feature. Wide carts still fit
// because item names truncate at the value column; a follow-up can add
// multi-page pagination if user carts start exceeding ~40 rows.
fun CartSnapshot.toPdfBytes(
    title: String,
    labels: CartExportLabels,
    generatedAt: LocalDateTime = LocalDateTime.now(),
): ByteArray {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, 1).create()
    val page = document.startPage(pageInfo)
    drawSnapshot(page.canvas, title, labels, generatedAt)
    document.finishPage(page)

    val out = ByteArrayOutputStream()
    document.writeTo(out)
    document.close()
    return out.toByteArray()
}

/**
 * The same receipt as [toPdfBytes], as a PNG image as long as the cart: for
 * chats and apps that show a picture but not a PDF.
 */
fun CartSnapshot.toPngBytes(
    title: String,
    labels: CartExportLabels,
    generatedAt: LocalDateTime = LocalDateTime.now(),
): ByteArray {
    // Recorded first, to learn how tall the receipt is.
    val picture = Picture()
    val recordingHeight = PAGE_HEIGHT_PT + (evaluatedItems.size * LINE_HEIGHT_PT).roundToInt()
    val bottom = drawSnapshot(picture.beginRecording(PAGE_WIDTH_PT, recordingHeight), title, labels, generatedAt)
    picture.endRecording()
    val bitmap = createBitmap((PAGE_WIDTH_PT * IMAGE_SCALE).roundToInt(), ((bottom + PAGE_MARGIN_PT) * IMAGE_SCALE).roundToInt())
    Canvas(bitmap).apply {
        drawColor(Color.WHITE)
        scale(IMAGE_SCALE, IMAGE_SCALE)
        drawPicture(picture)
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
    bitmap.recycle()
    return out.toByteArray()
}

// Draws the receipt from the top of [canvas]; returns where its last line sits.
private fun CartSnapshot.drawSnapshot(
    canvas: Canvas,
    title: String,
    labels: CartExportLabels,
    generatedAt: LocalDateTime,
): Float {
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
        canvas.drawText("${labels.meta.getValue(field)}: $value", PAGE_MARGIN_PT, y, metaPaint)
        y += META_LINE_HEIGHT_PT
    }
    y += SECTION_GAP_PT + LINE_HEIGHT_PT - META_LINE_HEIGHT_PT

    canvas.drawText(labels.item, PAGE_MARGIN_PT, y, headerPaint)
    canvas.drawText(withUnit(labels.value, baseCurrency.iso4217Alpha()), rightX, y, headerPaint.apply { textAlign = Paint.Align.RIGHT })
    y += LINE_HEIGHT_PT

    evaluatedItems.forEach { (item, value) ->
        val label = item.name.ifBlank { item.expression }
        canvas.drawText(label, PAGE_MARGIN_PT, y, bodyPaint)
        canvas.drawText(value.toCartDisplayString(), rightX, y, bodyRight)
        y += LINE_HEIGHT_PT
    }

    y += SECTION_GAP_PT
    canvas.drawText(labels.subtotal, PAGE_MARGIN_PT, y, headerPaint.apply { textAlign = Paint.Align.LEFT })
    canvas.drawText("${subtotal.toCartDisplayString()} ${baseCurrency.iso4217Alpha()}", rightX, y, bodyRight)
    y += LINE_HEIGHT_PT

    if (isConverting) {
        canvas.drawText(labels.converted, PAGE_MARGIN_PT, y, bodyPaint)
        canvas.drawText("${convertedSubtotal.toCartDisplayString()} ${destinationCurrency.iso4217Alpha()}", rightX, y, bodyRight)
        y += LINE_HEIGHT_PT
    }
    val combinedStack = feeStack
    if (!combinedStack.isNeutralFeeStack()) {
        canvas.drawText(labels.fees, PAGE_MARGIN_PT, y, bodyPaint)
        canvas.drawText("${combinedStack.feePercentDelta().toPlainString()}%", rightX, y, bodyRight)
        y += LINE_HEIGHT_PT
    }

    y += SECTION_GAP_PT
    canvas.drawText(labels.total, PAGE_MARGIN_PT, y, headerPaint.apply { textAlign = Paint.Align.LEFT })
    canvas.drawText(
        "${total.toCartDisplayString()} ${destinationCurrency.iso4217Alpha()}",
        rightX,
        y,
        paint(BODY_TEXT_SIZE, bold = true).apply { textAlign = Paint.Align.RIGHT },
    )
    return y
}

private fun paint(
    size: Float,
    bold: Boolean = false,
): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        if (bold) isFakeBoldText = true
    }
