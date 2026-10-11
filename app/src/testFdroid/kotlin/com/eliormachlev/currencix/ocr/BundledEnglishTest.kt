package com.eliormachlev.currencix.ocr

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The F-Droid app carries English and its recognizer: a scan needs nothing
// downloaded. (Plain names: detekt's test-name allowance covers src/test only.)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BundledEnglishTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun bundledEnglishIsTheExpectedFile() {
        assertTrue(Tessdata.ensureEnglish(context))
        assertTrue(Tessdata.isInstalled(context, TessLanguage.ENGLISH))
        assertEquals("eng", Tessdata.languages(context, null))
    }

    @Test
    fun readerIsReadyWithoutADownload() {
        assertTrue(isReceiptReaderReady(context))
    }
}
