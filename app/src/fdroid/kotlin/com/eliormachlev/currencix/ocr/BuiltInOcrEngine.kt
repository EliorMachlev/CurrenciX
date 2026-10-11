package com.eliormachlev.currencix.ocr

import android.content.Context
import com.eliormachlev.currencix.ocr.engine.TesseractRecognizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The F-Droid app carries the recognizer (:ocr-engine): always installed, nothing to fetch. */
internal val ocrEngine: OcrEngine = BuiltInOcrEngine

private object BuiltInOcrEngine : OcrEngine {
    private val recognizer by lazy {
        val engine = TesseractRecognizer()
        TextRecognizer(engine::recognize)
    }

    override fun isInstalled(context: Context): Boolean = true

    override fun install(context: Context): Flow<OcrInstall> = flowOf(OcrInstall.Installed)

    override fun recognizer(context: Context): TextRecognizer = recognizer
}
