package com.eliormachlev.currencix.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

// Cache subdirectories mapped in res/xml/file_provider_paths.xml. Kept as
// constants so both this helper and callers stay in lockstep with the
// FileProvider config.
internal const val CART_EXPORT_SUBDIR = "cart-exports"
internal const val SHARE_IMAGES_SUBDIR = "share-images"

private const val AUTHORITY_SUFFIX = ".fileprovider"

// PNG compression is lossless — the "quality" arg is ignored by the PNG
// encoder, but the API still requires it.
private const val PNG_QUALITY = 100

/**
 * Writes [bytes] into `<cacheDir>/<subdir>/<filename>` and returns an
 * `ACTION_SEND` chooser Intent primed with the resulting FileProvider URI,
 * MIME type, and per-URI read grant. Optional [extraText] is added as
 * `EXTRA_TEXT` so a recipient that renders text (SMS, email) still sees the
 * caption alongside the attachment.
 *
 * Central share-file plumbing — both CSV/PDF cart exports and hero-card PNG
 * snapshots go through here so filename hygiene, URI grants, and chooser
 * flags stay consistent.
 */
internal fun buildShareChooser(
    context: Context,
    subdir: String,
    filename: String,
    mimeType: String,
    bytes: ByteArray,
    extraText: String? = null,
): Intent {
    val exportDir = File(context.cacheDir, subdir).apply { mkdirs() }
    val outFile = File(exportDir, filename).apply { writeBytes(bytes) }
    val uri =
        FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            outFile,
        )
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            if (extraText != null) putExtra(Intent.EXTRA_TEXT, extraText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    return Intent.createChooser(sendIntent, null).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/**
 * Encode this [ImageBitmap] as PNG bytes. Colocated with the share helper
 * because the hero-snapshot flow is the only current caller and PNG is the
 * MIME the chooser advertises for it — keep the encode + wrap in one place.
 */
internal fun ImageBitmap.toPngBytes(): ByteArray {
    val out = ByteArrayOutputStream()
    asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
    return out.toByteArray()
}
