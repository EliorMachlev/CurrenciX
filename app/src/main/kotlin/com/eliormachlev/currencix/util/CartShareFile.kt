package com.eliormachlev.currencix.util

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Filename-safe timestamp used as the JSON-export suffix and as the fallback
// name for share artefacts (CSV/PDF/PNG) when the caller has no better name.
// Stateless formatter, no need to instantiate per call.
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

// Cart-specific facade over [buildShareChooser] — cart artifacts (CSV/PDF)
// always land under the cart-exports subdir and never carry a text caption.
fun buildCartShareChooser(
    context: Context,
    filename: String,
    mimeType: String,
    bytes: ByteArray,
): Intent =
    buildShareChooser(
        context = context,
        subdir = CART_EXPORT_SUBDIR,
        filename = filename,
        mimeType = mimeType,
        bytes = bytes,
    )
