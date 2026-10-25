package com.eliormachlev.currencix.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every bundled font is a font. The app's Inter files once held a saved
 * GitHub web page instead: the build took them, and Android quietly drew
 * the system font in their place. The website's fonts are checked the same
 * way.
 */
class FontFilesTest {
    private val root = repositoryRoot()

    @Test
    fun `every bundled font file starts with a font signature`() {
        val fonts = FONT_FOLDERS.flatMap { folder -> File(root, folder).listFiles().orEmpty().filter { it.extension in FONT_EXTENSIONS } }
        assertTrue("No fonts found in $FONT_FOLDERS", fonts.isNotEmpty())
        val broken = fonts.filterNot { file -> file.inputStream().use { it.readNBytes(SIGNATURE_LENGTH) }.toList() in SIGNATURES }
        assertEquals("Not font files (re-download the font itself, not its web page):", emptyList<File>(), broken)
    }

    private companion object {
        val FONT_FOLDERS = listOf("app/src/main/res/font", "docs/fonts")
        val FONT_EXTENSIONS = setOf("ttf", "otf", "woff2")
        const val SIGNATURE_LENGTH = 4

        // TrueType, OpenType (CFF), Apple TrueType and WOFF2.
        val SIGNATURES =
            listOf(byteArrayOf(0, 1, 0, 0), "OTTO".toByteArray(), "true".toByteArray(), "wOF2".toByteArray()).map { it.toList() }
    }
}
