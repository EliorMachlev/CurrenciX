package com.eliormachlev.currencix.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.android.play.core.ktx.requestInstall
import com.google.android.play.core.ktx.requestProgressFlow
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallHelper
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformWhile

// The :ocr feature module's name, and the recognizer class it brings (in :ocr-engine).
private const val MODULE = "ocr"
private const val RECOGNIZER_CLASS = "com.eliormachlev.currencix.ocr.engine.TesseractRecognizer"
private const val RECOGNIZE_METHOD = "recognize"

// Tesseract's native libraries, in the order they depend on each other.
private val NATIVE_LIBRARIES = listOf("jpeg", "pngx", "leptonica", "tesseract")

/**
 * The Play app's recognizer comes from the :ocr feature module, installed
 * through Play Feature Delivery the first time someone scans a receipt, so
 * nobody downloads its native code without using it.
 */
internal val ocrEngine: OcrEngine = PlayOcrEngine

private object PlayOcrEngine : OcrEngine {
    @Volatile
    private var loaded: TextRecognizer? = null

    override fun isInstalled(context: Context): Boolean = MODULE in SplitInstallManagerFactory.create(context).installedModules

    override fun install(context: Context): Flow<OcrInstall> {
        val manager = SplitInstallManagerFactory.create(context)
        if (MODULE in manager.installedModules) return flow { emit(OcrInstall.Installed) }
        return manager
            .requestProgressFlow()
            .filter { MODULE in it.moduleNames() }
            .map { it.toOcrInstall(manager) }
            .onStart {
                // A session of 0: the module was already there, and no progress follows.
                if (manager.requestInstall(listOf(MODULE)) == 0) emit(OcrInstall.Installed)
            }.transformWhile { step ->
                emit(step)
                step !is OcrInstall.Installed && step !is OcrInstall.Failed
            }.catch { emit(OcrInstall.Failed) }
    }

    override fun recognizer(context: Context): TextRecognizer =
        loaded ?: synchronized(this) {
            loaded ?: load(context.applicationContext).also { loaded = it }
        }

    // The module's code and native libraries, reachable without a restart
    // (SplitCompat), then the recognizer, by name: the base app can't link
    // against a module it may not have.
    private fun load(context: Context): TextRecognizer {
        SplitCompat.install(context)
        NATIVE_LIBRARIES.forEach { SplitInstallHelper.loadLibrary(context, it) }
        val type = Class.forName(RECOGNIZER_CLASS, true, context.classLoader)
        val target = type.getConstructor().newInstance()
        val recognize = type.getMethod(RECOGNIZE_METHOD, Bitmap::class.java, String::class.java, String::class.java)
        return TextRecognizer { image, dataPath, languages -> recognize.invoke(target, image, dataPath, languages) as String }
    }
}

private fun SplitInstallSessionState.toOcrInstall(manager: SplitInstallManager): OcrInstall =
    when (status()) {
        SplitInstallSessionStatus.INSTALLED -> {
            OcrInstall.Installed
        }

        SplitInstallSessionStatus.FAILED, SplitInstallSessionStatus.CANCELED -> {
            OcrInstall.Failed
        }

        SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
            OcrInstall.NeedsConfirmation { launcher -> manager.startConfirmationDialogForResult(this, launcher) }
        }

        SplitInstallSessionStatus.DOWNLOADING -> {
            val total = totalBytesToDownload()
            OcrInstall.Downloading(if (total > 0) bytesDownloaded().toFloat() / total else null)
        }

        else -> {
            OcrInstall.Downloading(null)
        }
    }
