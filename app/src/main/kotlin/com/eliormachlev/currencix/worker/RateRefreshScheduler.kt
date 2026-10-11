package com.eliormachlev.currencix.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.cache.providerRefreshIntervalMinutes
import java.util.concurrent.TimeUnit

// WorkManager enforces a minimum periodic interval of 15 minutes. Attempting
// a shorter cadence silently promotes to this floor — coerce here so
// per-provider defaults + user overrides both respect it, and so the value
// we log matches what actually gets scheduled.
private const val WORK_MANAGER_MIN_INTERVAL_MINUTES = 15L

// Exponential backoff seed. On retry the delay grows as
// (seed × 2^attempt), capped by WorkManager at 5 h. Chosen so a quick
// transient (DNS blip, captive portal handshake) recovers before the next
// scheduled slot, but a systemic outage doesn't hammer the upstream.
private const val BACKOFF_SECONDS = 30L

private const val UNIQUE_NAME = "rate-refresh"
private const val UNIQUE_NAME_NOW = "rate-refresh-now"

/**
 * Hoisted API for turning the periodic auto-refresh work on/off (#151).
 *
 * ### Cadence policy
 *  1. If the user set an explicit override → use it, coerced to the
 *     WorkManager 15-min floor.
 *  2. Otherwise fall back to the provider-recommended cadence from
 *     [providerRefreshIntervalMinutes], keeping the in-app cache TTL and the
 *     WorkManager cadence in lockstep.
 *
 * ### Uniqueness
 * Enqueued via [WorkManager.enqueueUniquePeriodicWork] with
 * [ExistingPeriodicWorkPolicy.UPDATE] so [schedule] doubles as "reschedule
 * with new interval" — a provider swap or override change transparently
 * updates the running work without producing duplicates.
 */
internal object RateRefreshScheduler {
    /**
     * Refresh every [intervalMinutes]. With [forAlerts] (rate alerts are
     * waiting) on any network and at any charge, since the alerts only fire
     * when rates arrive; otherwise only on an unmetered network and with the
     * battery not low.
     */
    fun schedule(
        context: Context,
        intervalMinutes: Long,
        forAlerts: Boolean = false,
    ) {
        val coerced = intervalMinutes.coerceAtLeast(WORK_MANAGER_MIN_INTERVAL_MINUTES)
        val request = buildPeriodicRequest(coerced, forAlerts)
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Convenience overload that resolves the effective interval from the
     * user's override (if any) or the current provider's default. Kept as a
     * separate entry point so callers that already know an explicit
     * interval (tests, future onboarding tuner) skip the DB read.
     */
    fun scheduleForCurrentProvider(context: Context) {
        val db = Database(context)
        val override = db.providers.autoRefreshIntervalOverride.get()
        val effective = override?.toLong() ?: providerRefreshIntervalMinutes(db.providers.apiProvider.get())
        schedule(
            context,
            effective,
            forAlerts =
                db.alerts.alerts
                    .get()
                    .isNotEmpty(),
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
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

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

    /**
     * Resolve the interval that would be used *right now* for [provider]
     * given a user [override]. Exposed as a pure helper so callers that
     * observe both flows can preview the outcome without touching
     * WorkManager. Coerces to the WorkManager 15-min floor.
     */
    fun effectiveIntervalMinutes(
        provider: ApiProvider,
        override: Int?,
    ): Long {
        val raw = override?.toLong() ?: providerRefreshIntervalMinutes(provider)
        return raw.coerceAtLeast(WORK_MANAGER_MIN_INTERVAL_MINUTES)
    }
}
