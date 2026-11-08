package com.eliormachlev.currencix.worker

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.repository.Database
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

private const val PUBLISHED = "rate-refresh-published"

private val RATES = ExchangeRates(success = true, error = null, base = null, date = LocalDate.of(2026, 1, 5), rates = emptyList())

// How a refresh ends: done, or tried again later; and a timed one queues the next.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RateRefreshWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val db = Database(context)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        resetSettings()
    }

    @After
    fun tearDown() = resetSettings()

    // The stores outlive a test, so each starts and ends on the defaults.
    private fun resetSettings() {
        db.providers.autoRefresh.set(false)
        db.providers.apiProvider.set(ApiProvider.FRANKFURTER_APP)
    }

    private fun worker(vararg tags: String) =
        TestListenableWorkerBuilder<RateRefreshWorker>(context)
            .setTags(tags.toList())
            .build()

    private fun queuedPublished(): List<WorkInfo> =
        WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWork(PUBLISHED)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }

    @Test
    fun `rates that arrive end the work as a success`() =
        runBlocking {
            assertEquals(Result.success(), worker().refresh { kotlin.Result.success(RATES) })
        }

    @Test
    fun `a failed fetch is tried again later`() =
        runBlocking {
            assertEquals(Result.retry(), worker().refresh { kotlin.Result.failure(IOException("5xx")) })
        }

    @Test
    fun `a network failure is tried again later`() =
        runBlocking {
            assertEquals(Result.retry(), worker().refresh { kotlin.Result.failure(UnknownHostException()) })
            assertEquals(Result.retry(), worker().refresh { kotlin.Result.failure(SocketTimeoutException()) })
        }

    @Test
    fun `an exception thrown by the fetch is tried again later too`() =
        runBlocking {
            assertEquals(Result.retry(), worker().refresh { throw IllegalStateException("boom") })
        }

    @Test
    fun `work stopped by WorkManager is cancelled, not retried`() {
        val stopped =
            runCatching {
                runBlocking { worker().refresh { throw CancellationException("stopped") } }
            }.exceptionOrNull()

        assertTrue(stopped is CancellationException)
    }

    @Test
    fun `a refresh timed to a publication queues the next one when it succeeds`() =
        runBlocking {
            db.providers.autoRefresh.set(true)

            worker(TAG_PUBLISHED_REFRESH).refresh { kotlin.Result.success(RATES) }

            assertEquals(1, queuedPublished().size)
        }

    @Test
    fun `a refresh timed to a publication queues nothing when it fails`() =
        runBlocking {
            db.providers.autoRefresh.set(true)

            worker(TAG_PUBLISHED_REFRESH).refresh { kotlin.Result.failure(IOException()) }

            assertTrue(queuedPublished().isEmpty())
        }

    @Test
    fun `a periodic refresh queues no publication refresh`() =
        runBlocking {
            db.providers.autoRefresh.set(true)

            worker().refresh { kotlin.Result.success(RATES) }

            assertTrue(queuedPublished().isEmpty())
        }
}
