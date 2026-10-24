package com.eliormachlev.currencix.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.eliormachlev.currencix.util.AppDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withContext

// Tesseract reads printed text well at this size; bigger is slower, not better.
private const val MAX_IMAGE_SIDE = 2400

/**
 * The text on a receipt photo ([uri]), read on the device by this build's
 * recognizer ([ocrEngine]) in [languages] ("heb+eng", see
 * [Tessdata.languages]). Empty when the image can't be read. Only once
 * [isReceiptReaderReady]. Runs off the main thread: it takes a few seconds.
 */
suspend fun readReceipt(
    context: Context,
    uri: Uri,
    languages: String,
    dispatchers: AppDispatchers = AppDispatchers.production,
): String =
    withContext(dispatchers.default) {
        val bitmap = decodeUpright(context, uri) ?: return@withContext ""
        try {
            ocrEngine.recognizer(context).recognize(bitmap, Tessdata.root(context).absolutePath, languages)
        } finally {
            bitmap.recycle()
        }
    }

/** Whether a receipt can be read now: the recognizer and the English data are on the device. */
fun isReceiptReaderReady(context: Context): Boolean = isRecognizerInstalled(context) && Tessdata.isInstalled(context, TessLanguage.ENGLISH)

/** Whether this build's recognizer is on the device (always in the F-Droid app; Play's is a module). */
fun isRecognizerInstalled(context: Context): Boolean = ocrEngine.isInstalled(context)

/**
 * Gets what reading a receipt needs that isn't on the device yet: the
 * recognizer ([OcrEngine.install]) and the English data, reporting how it
 * goes. Only once the user has agreed to the download. Ends with
 * [OcrInstall.Installed] or [OcrInstall.Failed].
 */
fun installReceiptReader(context: Context): Flow<OcrInstall> =
    flow {
        TessdataDownloadWorker.enqueue(context, TessLanguage.ENGLISH)
        var outcome: OcrInstall = OcrInstall.Failed
        ocrEngine
            .install(context)
            .transformWhile { step ->
                emit(step)
                step !is OcrInstall.Installed && step !is OcrInstall.Failed
            }.collect { step ->
                outcome = step
                emit(step)
            }
        if (outcome != OcrInstall.Installed) return@flow
        if (!Tessdata.isInstalled(context, TessLanguage.ENGLISH)) emit(OcrInstall.Downloading(null))
        emit(if (TessdataDownloadWorker.awaitInstalled(context, TessLanguage.ENGLISH)) OcrInstall.Installed else OcrInstall.Failed)
    }

// The photo, scaled down to at most MAX_IMAGE_SIDE and turned the way it was taken.
private fun decodeUpright(
    context: Context,
    uri: Uri,
): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
    val bitmap =
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return null
    val degrees = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
    if (degrees == 0) return bitmap
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}
