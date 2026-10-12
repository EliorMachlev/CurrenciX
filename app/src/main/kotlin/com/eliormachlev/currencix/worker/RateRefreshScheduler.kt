package com.eliormachlev.currencix.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.PublicationSchedule
import com.eliormachlev.currencix.model.RefreshInterval
import com.eliormachlev.currencix.repository.Database
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

// WorkManager enforces a minimum periodic interval of 15 minutes. Attempting
// a shorter cadence silently promotes to this floor — coerce here so the
// value we ask for matches what actually gets scheduled.
private const val WORK_MANAGER_MIN_INTERVAL_MINUTES = 15L

// Exponential backoff seed. On retry the delay grows as
// (seed × 2^attempt), capped by WorkManager at 5 h. Chosen so a quick
// transient (DNS blip, captive portal handshake) recovers before the next
// scheduled slot, but a systemic outage doesn't hammer the upstream.
private const val BACKOFF_SECONDS = 30L

private const val UNIQUE_NAME = "rate-refresh"
private const val UNIQUE_NAME_PUBLISHED = "rate-refresh-published"
private const val UNIQUE_NAME_NOW = "rate-refresh-now"

// Marks the one-off refresh timed to a publication, so the worker knows to
// queue the next one when it's done.
internal const val TAG_PUBLISHED_REFRESH = "rate-refresh-published"

/**
 * Turns the background auto-refresh (#151) on and off.
 *
 * ### Cadence
 * A provider that publishes on a known schedule
 * ([PublicationSchedule.Published]: once a day, once a month) is refreshed
 * just after each publication: one one-off request timed to it, which queues
 * the next when it finishes. Only a [PublicationSchedule.Frequent] one
 * follows the user's [RefreshInterval] (an hour by default), as periodic
 * work coerced to WorkManager's 15-minute floor.
 *
 * ### Uniqueness
 * Each kind is unique work, replaced on every [schedule], and scheduling one
 * kind cancels the other, so a provider or interval change never leaves two
 * refreshes running.
 */
internal object RateRefreshScheduler {
    /**
     * Refresh [provider]'s rates on its schedule (see Cadence above). With
     * [forAlerts] (rate alerts are waiting) on any network and at any charge,
     * since the alerts only fire when rates arrive; otherwise only on an
     * unmetered network and with the battery not low.
     */
    fun schedule(
        context: Context,
        provider: ApiProvider,
        interval: RefreshInterval,
        forAlerts: Boolean = false,
        now: Instant = Instant.now(),
    ) {
        val workManager = WorkManager.getInstance(context)
        when (val publication = provider.publication) {
            is PublicationSchedule.Frequent -> {
                workManager.cancelUniqueWork(UNIQUE_NAME_PUBLISHED)
                workManager.enqueueUniquePeriodicWork(
                    UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    buildPeriodicRequest(interval.minutes.coerceAtLeast(WORK_MANAGER_MIN_INTERVAL_MINUTES), forAlerts),
                )
            }

            is PublicationSchedule.Published -> {
                workManager.cancelUniqueWork(UNIQUE_NAME)
                workManager.enqueueUniqueWork(
                    UNIQUE_NAME_PUBLISHED,
                    ExistingWorkPolicy.REPLACE,
                    buildPublishedRequest(publication, forAlerts, now),
                )
            }
        }
    }

    /**
     * After a refresh timed to a publication: the one after the next
     * publication, if auto-refresh is still on and the provider still
     * publishes on a schedule. Appended, so it waits for the refresh that
     * queues it to finish rather than replacing it mid-run.
     */
    fun scheduleNextPublication(
        context: Context,
        now: Instant = Instant.now(),
    ) {
        val db = Database(context)
        if (!db.providers.autoRefresh.get()) return
        val publication =
            db.providers.apiProvider
                .get()
                .publication as? PublicationSchedule.Published ?: return
        val forAlerts =
            db.alerts.alerts
                .get()
                .isNotEmpty()
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                UNIQUE_NAME_PUBLISHED,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                buildPublishedRequest(publication, forAlerts, now),
            )
    }

    /**
     * One refresh as soon as there's a network (the Quick Settings tile's
     * tap). A second request while one is pending joins it.
     */
    fun refreshNow(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<RateRefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME_NOW, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(UNIQUE_NAME)
        workManager.cancelUniqueWork(UNIQUE_NAME_PUBLISHED)
    }

    // One refresh, [publication]'s next refresh time after [now].
    private fun buildPublishedRequest(
        publication: PublicationSchedule.Published,
        forAlerts: Boolean,
        now: Instant,
    ): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<RateRefreshWorker>()
            .setInitialDelay(Duration.between(now, publication.nextRefreshAfter(now)))
            .setConstraints(refreshConstraints(forAlerts))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG_PUBLISHED_REFRESH)
            .build()

    // Extracted so the constraints + backoff wiring lives in one obvious
    // place and the enqueue path stays a two-liner.
    private fun buildPeriodicRequest(
        intervalMinutes: Long,
        forAlerts: Boolean,
    ): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<RateRefreshWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(refreshConstraints(forAlerts))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

    /**
     * What the periodic refresh waits for: Wi-Fi (or another unmetered
     * network) and a battery that isn't low, so keeping rates warm costs
     * neither data nor charge; or, [forAlerts], just a network.
     */
    internal fun refreshConstraints(forAlerts: Boolean): Constraints =
        Constraints
            .Builder()
            .setRequiredNetworkType(if (forAlerts) NetworkType.CONNECTED else NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(!forAlerts)
            .build()
}
