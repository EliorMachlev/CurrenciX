package com.eliormachlev.currencix.ocr

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

// Which OCR language goes with the app's, and that only the expected file is kept.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TessdataTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `each app language has its OCR language`() {
        assertEquals(TessLanguage.HEBREW, TessLanguage.forLocale(Locale.forLanguageTag("iw")))
        assertEquals(TessLanguage.HEBREW, TessLanguage.forLocale(Locale.forLanguageTag("he-IL")))
        assertEquals(TessLanguage.CHINESE_TRADITIONAL, TessLanguage.forLocale(Locale.forLanguageTag("zh-TW")))
        assertEquals(TessLanguage.CHINESE_SIMPLIFIED, TessLanguage.forLocale(Locale.forLanguageTag("zh-CN")))
        assertEquals(TessLanguage.PORTUGUESE, TessLanguage.forLocale(Locale.forLanguageTag("pt-BR")))
        assertNull(TessLanguage.forLocale(Locale.ENGLISH))
        // Every app locale folder maps to one (English is bundled).
        listOf(
            "ar",
            "bg",
            "bn",
            "ca",
            "cs",
            "da",
            "de",
            "el",
            "eo",
            "es",
            "et",
            "fa",
            "fr",
            "hr",
            "hu",
            "in",
            "is",
            "it",
            "iw",
            "ml",
            "nb",
            "nl",
            "pl",
            "pt",
            "ru",
            "sv",
            "tr",
            "uk",
            "vi",
        ).forEach { assertTrue(it, TessLanguage.forLocale(Locale.forLanguageTag(it)) != null) }
    }

    @Test
    fun `a file is kept only when it's the one expected`() {
        val target = Tessdata.file(context, TessLanguage.HEBREW)
        assertFalse(Tessdata.install("not hebrew".byteInputStream(), target, TessLanguage.HEBREW))
        assertFalse(target.exists())
        assertEquals("eng", Tessdata.languages(context, TessLanguage.HEBREW))
    }

    @Test
    fun `the bundled English file is the expected one`() {
        Tessdata.ensureBundled(context)
        assertTrue(Tessdata.isInstalled(context, TessLanguage.ENGLISH))
        assertEquals("eng", Tessdata.languages(context, null))
    }
}
