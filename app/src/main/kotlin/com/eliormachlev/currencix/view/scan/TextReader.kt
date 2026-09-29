package com.eliormachlev.currencix.view.scan

import android.content.Context
import android.net.Uri

/**
 * Reads the text in a photo — the camera price scan. Only the play flavor has
 * one ([flavorTextReader]); F-Droid builds can't ship the proprietary
 * recogniser, so there the scan isn't offered at all.
 */
fun interface TextReader {
    suspend fun read(
        context: Context,
        image: Uri,
    ): Result<String>
}

/** This build's [TextReader]; null where the scan isn't available. */
val textReader: TextReader? get() = flavorTextReader
