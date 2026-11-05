package com.eliormachlev.currencix.ocr

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Long enough for a wrong answer to come back; a right one never does.
private const val STILL_WAITING_MS = 500L

// A wait that should end: failing after this, not hanging the run.
private const val ENDS_WITHIN_MS = 10_000L

// The first scan waits for the languages the user agreed to; it reports
// failure only when a download really ended without its file.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TessdataDownloadWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val language = TessLanguage.KOREAN

    @Before
    fun setUp() {
        // Nothing runs on its own: the test WorkManager keeps work ENQUEUED.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        Tessdata.delete(context, language)
    }

    private fun downloads(): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork("tessdata-${language.code}").get()

    @Test
    fun `a language already here needs no wait`() =
        runBlocking {
            Tessdata.file(context, language).writeText("data")
            assertTrue(TessdataDownloadWorker.awaitInstalled(context, language))
        }

    @Test
    fun `a download not queued yet is queued and waited for, not taken as failed`() =
        runBlocking {
            assertNull(withTimeoutOrNull(STILL_WAITING_MS) { TessdataDownloadWorker.awaitInstalled(context, language) })
            assertEquals(WorkInfo.State.ENQUEUED, downloads().single().state)
        }

    @Test
    fun `an earlier download that was cancelled doesn't end the wait for a new one`() =
        runBlocking {
            TessdataDownloadWorker.enqueue(context, language)
            TessdataDownloadWorker.cancel(context, language)
            assertNull(withTimeoutOrNull(STILL_WAITING_MS) { TessdataDownloadWorker.awaitInstalled(context, language) })
            assertEquals(WorkInfo.State.ENQUEUED, downloads().single().state)
        }

    @Test
    fun `a download that ends without the file ends the wait with false`() =
        runBlocking {
            withTimeout(ENDS_WITHIN_MS) {
                val waiting = async { TessdataDownloadWorker.awaitInstalled(context, language) }
                // Once it's queued, stop it, as a cancelled download would.
                while (downloads().isEmpty()) yield()
                TessdataDownloadWorker.cancel(context, language)
                assertFalse(waiting.await())
            }
        }
}
