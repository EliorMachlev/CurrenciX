package com.eliormachlev.currencix.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.eliormachlev.currencix.ocr.engine.TesseractRecognizer
import com.eliormachlev.currencix.util.AppDispatchers
import kotlinx.coroutines.withContext

// Tesseract reads printed text well at this size; bigger is slower, not better.
private const val MAX_IMAGE_SIDE = 2400

// The recognizer (:ocr-engine), in the app for both flavors.
private val recognizer by lazy { TesseractRecognizer() }

/**
 * The text on a receipt photo ([uri]), read on the device by Tesseract in
 * [languages] ("heb+eng", see [Tessdata.languages]). Empty when the image
 * can't be read, or with no language to read in. Runs off the main thread:
 * it takes a few seconds.
 */
suspend fun readReceipt(
    context: Context,
    uri: Uri,
    languages: String,
    dispatchers: AppDispatchers = AppDispatchers.production,
): String =
    withContext(dispatchers.default) {
        if (languages.isEmpty()) return@withContext ""
        val bitmap = decodeUpright(context, uri) ?: return@withContext ""
        try {
            recognizer.recognize(bitmap, Tessdata.root(context).absolutePath, languages)
        } finally {
            bitmap.recycle()
        }
    }

/**
 * Waits for [languages] (agreed to and already downloading,
 * [OcrLanguages.download]) when reading has to have them: whether they all
 * arrived.
 */
suspend fun awaitReceiptLanguages(
    context: Context,
    languages: Set<TessLanguage>,
): Boolean = languages.filterNot { Tessdata.isInstalled(context, it) }.all { TessdataDownloadWorker.awaitInstalled(context, it) }

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
