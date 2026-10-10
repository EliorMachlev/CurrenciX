package com.eliormachlev.currencix.jank

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.metrics.performance.JankStats
import timber.log.Timber

// Debug variant: attach a JankStats collector per-Activity via lifecycle
// callbacks. Jank frames are logged through Timber (already routed to
// the on-device file tree) so the signal survives across app restarts
// without a remote telemetry sink. The release variant is a no-op — the
// build type picks that file over this one for non-debug variants.
fun Application.installJankStats() {
    registerActivityLifecycleCallbacks(JankStatsLifecycleCallbacks())
}

private class JankStatsLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    private val jankStatsByActivity = mutableMapOf<Activity, JankStats>()

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) {
        val stats =
            JankStats.createAndTrack(activity.window) { frameData ->
                if (frameData.isJank) {
                    val ms = frameData.frameDurationUiNanos / NANOS_PER_MILLI
                    Timber.w("jank ${ms}ms at ${activity::class.simpleName}")
                }
            }
        jankStatsByActivity[activity] = stats
    }

    override fun onActivityPaused(activity: Activity) {
        jankStatsByActivity[activity]?.isTrackingEnabled = false
    }

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        jankStatsByActivity.remove(activity)
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
