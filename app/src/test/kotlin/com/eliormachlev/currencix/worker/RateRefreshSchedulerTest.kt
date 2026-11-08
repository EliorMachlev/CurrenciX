package com.eliormachlev.currencix.worker

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.RefreshInterval
import com.eliormachlev.currencix.repository.Database
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

private const val PERIODIC = "rate-refresh"
private const val PUBLISHED = "rate-refresh-published"
private const val NOW = "rate-refresh-now"

// Frankfurter publishes at 16:00 Berlin (15:00 UTC in winter); the refresh follows 30 minutes later.
private val BEFORE_PUBLICATION: Instant = Instant.parse("2026-01-05T10:00:00Z")
private val FIVE_AND_A_HALF_HOURS: Duration = Duration.ofMinutes(330)

// Which work is queued, and how: the periodic refresh for a provider that
// updates all day, one timed to the publication for one that updates once.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RateRefreshSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val db = Database(context)
    private val frequent = ApiProvider.OPEN_EXCHANGERATES
    private val published = ApiProvider.FRANKFURTER_APP

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
        db.providers.apiProvider.set(published)
        db.alerts.alerts.set(emptyList())
    }

    private fun work(name: String): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    private fun queued(name: String): List<WorkInfo> = work(name).filter { it.state == WorkInfo.State.ENQUEUED }

    @Test
    fun `a frequent provider is refreshed periodically at the chosen interval`() {
        RateRefreshScheduler.schedule(context, frequent, RefreshInterval.HOURS_6)

        val info = queued(PERIODIC).single()
        assertEquals(TimeUnit.HOURS.toMillis(6), info.periodicityInfo?.repeatIntervalMillis)
        assertTrue(queued(PUBLISHED).isEmpty())
    }

    @Test
    fun `the interval is never below WorkManager's floor of 15 minutes`() {
        RateRefreshScheduler.schedule(context, frequent, RefreshInterval.MINUTES_15)

        val info = queued(PERIODIC).single()
        assertEquals(TimeUnit.MINUTES.toMillis(15), info.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun `scheduling again with another interval updates the one periodic refresh`() {
        RateRefreshScheduler.schedule(context, frequent, RefreshInterval.HOUR_1)
        RateRefreshScheduler.schedule(context, frequent, RefreshInterval.HOURS_12)

        val info = queued(PERIODIC).single()
        assertEquals(TimeUnit.HOURS.toMillis(12), info.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun `a provider with a publication time is refreshed once, just after it`() {
        RateRefreshScheduler.schedule(context, published, RefreshInterval.HOUR_1, now = BEFORE_PUBLICATION)

        val info = queued(PUBLISHED).single()
        assertNull(info.periodicityInfo)
        assertEquals(FIVE_AND_A_HALF_HOURS.toMillis(), info.initialDelayMillis)
        assertTrue(TAG_PUBLISHED_REFRESH in info.tags)
        assertTrue(queued(PERIODIC).isEmpty())
    }

    @Test
    fun `switching to a provider with a publication time drops the periodic refresh`() {
        RateRefreshScheduler.schedule(context, frequent, RefreshInterval.HOUR_1)
        RateRefreshScheduler.schedule(context, published, RefreshInterval.HOUR_1, now = BEFORE_PUBLICATION)

        assertTrue(queued(PERIODIC).isEmpty())
        assertEquals(1, queued(PUBLISHED).size)
    }

    @Test
    fun `switching to a frequent provider drops the refresh timed to a publication`() {
        RateRefreshScheduler.schedule(context, published, RefreshInterval.HOUR_1, now = BEFORE_PUBLICATION)
        RateRefreshScheduler.schedule(context, frequent, RefreshInterval.HOUR_1)

        assertTrue(queued(PUBLISHED).isEmpty())
        assertEquals(1, queued(PERIODIC).size)
    }

    @Test
    fun `scheduling for a provider twice replaces the earlier timed refresh`() {
        RateRefreshScheduler.schedule(context, published, RefreshInterval.HOUR_1, now = BEFORE_PUBLICATION)
        RateRefreshScheduler.schedule(context, published, RefreshInterval.HOUR_1, now = BEFORE_PUBLICATION.plusSeconds(60))

        val info = queued(PUBLISHED).single()
        assertEquals(FIVE_AND_A_HALF_HOURS.minusSeconds(60).toMillis(), info.initialDelayMillis)
    }

    @Test
    fun `without alerts the refresh waits for an unmetered network and a battery that is not low`() {
        RateRefreshScheduler.schedule(context, frequent, RefreshInterval.HOUR_1)

        val constraints = queued(PERIODIC).single().constraints
        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresBatteryNotLow())
    }

    @Test
    fun `with alerts waiting the refresh runs on any network and at any charge`() {
        RateRefreshScheduler.schedule(context, frequent, RefreshInterval.HOUR_1, forAlerts = true)

        val constraints = queued(PERIODIC).single().constraints
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertTrue(!constraints.requiresBatteryNotLow())
    }

    @Test
    fun `cancel removes both kinds of refresh`() {
        RateRefreshScheduler.schedule(context, frequent, RefreshInterval.HOUR_1)
        RateRefreshScheduler.schedule(context, published, RefreshInterval.HOUR_1, now = BEFORE_PUBLICATION)
        RateRefreshScheduler.schedule(context, frequent, RefreshInterval.HOUR_1)

        RateRefreshScheduler.cancel(context)

        assertTrue(queued(PERIODIC).isEmpty())
        assertTrue(queued(PUBLISHED).isEmpty())
    }

    @Test
    fun `the next publication is queued while auto-refresh is on and the provider has a publication time`() {
        db.providers.autoRefresh.set(true)
        db.providers.apiProvider.set(published)

        RateRefreshScheduler.scheduleNextPublication(context, BEFORE_PUBLICATION)

        val info = queued(PUBLISHED).single()
        assertEquals(FIVE_AND_A_HALF_HOURS.toMillis(), info.initialDelayMillis)
        assertNotNull(info.tags.firstOrNull { it == TAG_PUBLISHED_REFRESH })
    }

    @Test
    fun `no next publication is queued when auto-refresh is off`() {
        db.providers.autoRefresh.set(false)
        db.providers.apiProvider.set(published)

        RateRefreshScheduler.scheduleNextPublication(context, BEFORE_PUBLICATION)

        assertTrue(work(PUBLISHED).isEmpty())
    }

    @Test
    fun `no next publication is queued for a frequent provider`() {
        db.providers.autoRefresh.set(true)
        db.providers.apiProvider.set(frequent)

        RateRefreshScheduler.scheduleNextPublication(context, BEFORE_PUBLICATION)

        assertTrue(work(PUBLISHED).isEmpty())
    }

    @Test
    fun `refresh now waits only for a network, and a second tap joins the first`() {
        RateRefreshScheduler.refreshNow(context)
        val first = queued(NOW).single()
        RateRefreshScheduler.refreshNow(context)

        assertEquals(NetworkType.CONNECTED, first.constraints.requiredNetworkType)
        assertEquals(first.id, queued(NOW).single().id)
    }
}
