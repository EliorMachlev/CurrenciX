package com.eliormachlev.currencix.ocr

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.Flow

/**
 * Reads the text in [image][recognize], in the languages given ("heb+eng"),
 * with the language files under a data path (Tesseract reads "tessdata/"
 * inside it).
 */
fun interface TextRecognizer {
    fun recognize(
        image: Bitmap,
        dataPath: String,
        languages: String,
    ): String
}

/** How getting the text recognizer onto the device is going ([OcrEngine.install]). */
sealed interface OcrInstall {
    /** Downloading or installing; [fraction] done when known. */
    data class Downloading(
        val fraction: Float?,
    ) : OcrInstall

    /** The store wants the user's go-ahead (a large download on mobile data): [confirm] shows its dialog. */
    class NeedsConfirmation(
        val confirm: (ActivityResultLauncher<IntentSenderRequest>) -> Unit,
    ) : OcrInstall

    data object Installed : OcrInstall

    data object Failed : OcrInstall
}

/**
 * Where this build's text recognizer comes from ([ocrEngine], per flavor):
 * built into the F-Droid app, or a Play feature module installed the first
 * time someone scans a receipt.
 */
interface OcrEngine {
    /** Whether the recognizer is on the device; when not, [install] gets it. */
    fun isInstalled(context: Context): Boolean

    /** Gets the recognizer, reporting how it goes; ends with [OcrInstall.Installed] or [OcrInstall.Failed]. */
    fun install(context: Context): Flow<OcrInstall>

    /** The recognizer, once [isInstalled]. */
    fun recognizer(context: Context): TextRecognizer
}
