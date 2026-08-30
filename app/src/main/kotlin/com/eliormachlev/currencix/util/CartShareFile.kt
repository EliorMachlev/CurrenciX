package com.eliormachlev.currencix.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val EXPORT_SUBDIR = "cart-exports"
private const val AUTHORITY_SUFFIX = ".fileprovider"

// Filename-safe timestamp used as the JSON-export suffix and as the fallback
// name for share artefacts (CSV/PDF) when a cart has no user name. Stateless
// formatter, no need to instantiate per call.
private val FILENAME_TIMESTAMP: SimpleDateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

fun filenameTimestampNow(): String = FILENAME_TIMESTAMP.format(Date())

// Chars that can trip up FileProvider / OSes when embedded in a filename.
// Collapsed to a single underscore so a cart named "Café / July 2026" becomes
// "Café_July_2026", not "Café___July_2026".
private val FILENAME_UNSAFE = Regex("""[\\/:*?"<>|\p{Cntrl}]+""")
private val FILENAME_WHITESPACE = Regex("""\s+""")

fun String.sanitizeForFilename(): String =
    replace(FILENAME_UNSAFE, "_")
        .replace(FILENAME_WHITESPACE, "_")
        .trim('_', '.')
        .ifBlank { "cart" }

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
