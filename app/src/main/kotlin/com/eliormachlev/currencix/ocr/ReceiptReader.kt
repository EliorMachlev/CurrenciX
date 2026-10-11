package com.eliormachlev.currencix.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.eliormachlev.currencix.util.AppDispatchers
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.withContext

// Tesseract reads printed text well at this size; bigger is slower, not better.
private const val MAX_IMAGE_SIDE = 2400

/**
 * The text on a receipt photo ([uri]), read on the device with Tesseract in
 * [languages] ("heb+eng", see [Tessdata.languages]). Empty when the image
 * can't be read. Runs off the main thread: it takes a few seconds.
 */
suspend fun readReceipt(
    context: Context,
    uri: Uri,
    languages: String,
    dispatchers: AppDispatchers = AppDispatchers.production,
): String =
    withContext(dispatchers.default) {
        Tessdata.ensureBundled(context)
        val bitmap = decodeUpright(context, uri) ?: return@withContext ""
        val api = TessBaseAPI()
        try {
            if (!api.init(Tessdata.root(context).absolutePath, languages)) return@withContext ""
            // A receipt is one column of lines.
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_COLUMN
            api.setImage(bitmap)
            api.utF8Text.orEmpty()
        } finally {
            api.recycle()
            bitmap.recycle()
        }
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
