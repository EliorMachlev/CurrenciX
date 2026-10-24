package com.eliormachlev.currencix.ocr

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.decodeKeepUntil
import com.eliormachlev.currencix.repository.encodeKeepUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

// Receipt languages the user manages: added only when agreed to, kept for
// good or until a time, and deleted when they say or when that time comes.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OcrLanguagesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val settings get() = Database(context).receiptLanguages
    private val now = Instant.parse("2026-11-01T10:00:00Z")

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        TessLanguage.entries.forEach { Tessdata.delete(context, it) }
        settings.allowed.set(emptySet())
        settings.declined.set(emptySet())
        settings.keepUntil.set(emptyMap())
    }

    // As if the download had finished (the real file isn't fetched in a test).
    private fun place(language: TessLanguage) = Tessdata.file(context, language).writeText("data")

    private fun expiryRuns(): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork("tessdata-expiry").get()

    @Test
    fun `adding a language for a trip records the yes and when it goes, and schedules that`() {
        val tripEnd = now.plus(Duration.ofDays(10))
        OcrLanguages.download(context, TessLanguage.KOREAN, tripEnd)
        assertEquals(true, settings.answerFor("kor"))
        assertEquals(mapOf(TessLanguage.KOREAN to tripEnd), OcrLanguages.keepUntil(context))
        assertEquals(WorkInfo.State.ENQUEUED, expiryRuns().single().state)
    }

    @Test
    fun `a language whose time has come is deleted, the others stay`() {
        place(TessLanguage.KOREAN)
        place(TessLanguage.JAPANESE)
        OcrLanguages.keep(context, TessLanguage.KOREAN, now.minusSeconds(1))
        OcrLanguages.keep(context, TessLanguage.JAPANESE, now.plus(Duration.ofDays(3)))

        assertEquals(listOf(TessLanguage.KOREAN), OcrLanguages.deleteExpired(context, now))
        assertFalse(Tessdata.isInstalled(context, TessLanguage.KOREAN))
        assertTrue(Tessdata.isInstalled(context, TessLanguage.JAPANESE))
        assertEquals(setOf(TessLanguage.JAPANESE), OcrLanguages.keepUntil(context).keys)
    }

    @Test
    fun `kept for good, nothing is scheduled`() {
        place(TessLanguage.KOREAN)
        OcrLanguages.keep(context, TessLanguage.KOREAN, now.plusSeconds(60))
        OcrLanguages.keep(context, TessLanguage.KOREAN, null)
        assertTrue(OcrLanguages.keepUntil(context).isEmpty())
        assertTrue(expiryRuns().all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `a deleted language isn't fetched again unasked, English is asked for again`() {
        place(TessLanguage.HEBREW)
        place(TessLanguage.ENGLISH)
        settings.setAllowed("heb", allowed = true)
        settings.setAllowed("eng", allowed = true)

        OcrLanguages.delete(context, TessLanguage.HEBREW)
        OcrLanguages.delete(context, TessLanguage.ENGLISH)

        assertFalse(Tessdata.isInstalled(context, TessLanguage.HEBREW))
        assertEquals(false, settings.answerFor("heb"))
        assertNull(settings.answerFor("eng"))
    }

    @Test
    fun `receipts are read in the app language first, then the others added, then English`() {
        place(TessLanguage.ENGLISH)
        place(TessLanguage.KOREAN)
        place(TessLanguage.HEBREW)
        assertEquals("heb+kor+eng", Tessdata.languages(context, TessLanguage.HEBREW))
        assertEquals("heb+kor+eng", Tessdata.languages(context, null))
    }

    @Test
    fun `keep-until dates survive their storage, and broken entries are skipped`() {
        val keep = mapOf("kor" to 1_767_225_600_000L, "jpn" to 1L)
        assertEquals(keep, decodeKeepUntil(encodeKeepUntil(keep)))
        assertEquals(mapOf("kor" to 5L), decodeKeepUntil(setOf("kor=5", "=7", "jpn=soon", "tha")))
    }
}
