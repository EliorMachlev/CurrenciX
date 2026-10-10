package com.eliormachlev.currencix

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.eliormachlev.currencix.crash.installDebugCrashReporter
import com.eliormachlev.currencix.jank.installJankStats
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.FileLoggingTree
import com.eliormachlev.currencix.util.plantConsoleLogging
import com.eliormachlev.currencix.worker.RateRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.InetAddress
import kotlin.concurrent.thread

class CurrenciesApplication : Application() {
    // Process-lifetime scope for the auto-refresh observer. SupervisorJob so
    // a collector cancellation doesn't tear down the app-wide scope, and
    // Default because the work is a single distinct-until-changed pref read
    // plus a WorkManager enqueue — no IO on the hot path.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // The debug crash reporter hosts CrashReportActivity in a `:crash`
        // process (so it survives killProcess on the crashed main process).
        // Android re-runs Application.onCreate for that new process too — but
        // that process has WorkManager disabled and no need for logging,
        // theme, DNS prewarm, or the auto-refresh observer. Skip everything
        // except crash-reporter installation there.
        installDebugCrashReporter()
        if (!isMainProcess()) return
        installLogging()
        installJankStats()
        applyNightMode()
        prewarmProviderDns()
        observeAutoRefreshPreference()
    }

    // True iff this Application instance is running in the app's main
    // process (name == packageName, no `:suffix`).
    private fun isMainProcess(): Boolean = getProcessName() == packageName

    // Debug builds also get a console tree so `adb logcat` mirrors what the
    // file tree captures (plantConsoleLogging, per build type). Release
    // builds are file-only — no remote sink.
    private fun installLogging() {
        plantConsoleLogging()
        Timber.plant(FileLoggingTree(filesDir))
    }

    // Apply the persisted day/night mode before any Activity is created, so
    // MainActivity.setTheme(AppTheme_PureBlack) resolves against the correct
    // night qualifier on the very first frame. Otherwise the pure-black
    // background renders as the day-mode color until setDefaultNightMode
    // triggers a recreate. The read is synchronous by design — DataStore's
    // initial disk load is bounded (~1 KB file) and blocking here is
    // preferable to a recreate() flash mid-startup.
    private fun applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(Database(this).display.getTheme().nightMode)
    }

    // Resolve the currently-selected provider's host on a background thread so
    // the first network request doesn't pay for DNS. Failures (offline, DNS
    // outage) are silent — this is a best-effort warm-up, not a health check.
    private fun prewarmProviderDns() {
        thread(name = "dns-prewarm", isDaemon = true) {
            val host = Database(this).providers.getApiProvider().getHost() ?: return@thread
            runCatching { InetAddress.getAllByName(host) }
        }
    }

    // Observe the three inputs to the WorkManager schedule (opt-in flag,
    // user interval override, current provider). Any change re-enqueues or
    // cancels the periodic work — enqueueUniquePeriodicWork with UPDATE
    // makes the reschedule race-free. Distinct-until-changed on the tuple
    // avoids a redundant enqueue on every DataStore emit.
    private fun observeAutoRefreshPreference() {
        val db = Database(this)
        appScope.launch {
            combine(
                db.providers.isAutoRefreshEnabledFlow(),
                db.providers.getAutoRefreshIntervalMinutesOverrideFlow(),
                db.providers.getApiProviderFlow(),
            ) { enabled, override, provider ->
                Triple(enabled, override, provider)
            }.distinctUntilChanged()
                .collect { (enabled, override, provider) ->
                    if (enabled) {
                        val minutes = RateRefreshScheduler.effectiveIntervalMinutes(provider, override)
                        RateRefreshScheduler.schedule(this@CurrenciesApplication, minutes)
                    } else {
                        RateRefreshScheduler.cancel(this@CurrenciesApplication)
                    }
                }
        }
    }
}
