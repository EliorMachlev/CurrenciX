package de.salomax.currencies.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

private const val EXPORT_SUBDIR = "cart-exports"
private const val AUTHORITY_SUFFIX = ".fileprovider"

// Shared helper for cart share flows that produce a file (CSV, PDF, etc.).
// Writes [bytes] into the FileProvider-mapped [EXPORT_SUBDIR] under cacheDir
// and returns a chooser Intent already primed with the correct MIME type and
// per-URI read grant so any receiver can open the artifact.
fun buildCartShareChooser(
    context: Context,
    filename: String,
    mimeType: String,
    bytes: ByteArray,
): Intent {
    val exportDir = File(context.cacheDir, EXPORT_SUBDIR).apply { mkdirs() }
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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    return Intent.createChooser(sendIntent, null).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
