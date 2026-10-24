package com.eliormachlev.currencix.ocr

import android.content.Context
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

// Tesseract wants a folder holding "tessdata/<code>.traineddata".
private const val OCR_DIR = "ocr"
private const val TESSDATA_DIR = "tessdata"
private const val TRAINEDDATA = ".traineddata"
private const val DOWNLOAD_SUFFIX = ".part"
private const val BUFFER_BYTES = 64 * 1024

/**
 * The OCR language files on the device, English included: none ships with
 * the app, each is downloaded into this folder once the user has agreed to
 * it ([TessdataDownloadWorker]). Every file is checked against its
 * [TessLanguage.sha256] before it's used.
 */
object Tessdata {
    /** The folder Tesseract is given (it reads "tessdata/" under it). */
    fun root(context: Context): File = File(context.filesDir, OCR_DIR)

    private fun dataDir(context: Context): File = File(root(context), TESSDATA_DIR).apply { mkdirs() }

    fun file(
        context: Context,
        language: TessLanguage,
    ): File = File(dataDir(context), language.code + TRAINEDDATA)

    /** Whether [language]'s file is on the device. */
    fun isInstalled(
        context: Context,
        language: TessLanguage,
    ): Boolean = file(context, language).length() > 0

    /**
     * Writes [source] to [target] through a temporary file, keeping it only
     * when its SHA-256 is [language]'s; returns whether it was kept.
     */
    fun install(
        source: InputStream,
        target: File,
        language: TessLanguage,
    ): Boolean {
        val part = File(target.parentFile, target.name + DOWNLOAD_SUFFIX)
        val digest = MessageDigest.getInstance("SHA-256")
        part.outputStream().use { out ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                out.write(buffer, 0, read)
            }
        }
        val matches = digest.digest().toHexString() == language.sha256
        if (matches) part.renameTo(target) else part.delete()
        return matches
    }

    /**
     * The languages to read with, as Tesseract takes them ("heb+eng"): the
     * app language's when its file is here, then English (prices and
     * Latin-script names).
     */
    fun languages(
        context: Context,
        local: TessLanguage?,
    ): String =
        listOfNotNull(local?.takeIf { it != TessLanguage.ENGLISH && isInstalled(context, it) }, TessLanguage.ENGLISH)
            .joinToString("+") { it.code }
}
