package com.eliormachlev.currencix.ocr.engine

import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI

/**
 * Reads the text in a receipt photo with Tesseract, for both flavors of the
 * app (its native code is what makes the app 64-bit only, see
 * gradle.properties).
 */
class TesseractRecognizer {
    /**
     * The text in [image], read in [languages] ("heb+eng") with the language
     * files under [dataPath] (Tesseract reads "tessdata/" inside it); empty
     * when they can't be loaded.
     */
    fun recognize(
        image: Bitmap,
        dataPath: String,
        languages: String,
    ): String {
        val api = TessBaseAPI()
        try {
            if (!api.init(dataPath, languages)) return ""
            // A receipt is one column of lines.
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_COLUMN
            api.setImage(image)
            return api.utF8Text.orEmpty()
        } finally {
            api.recycle()
        }
    }
}
