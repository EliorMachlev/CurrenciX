package com.eliormachlev.currencix

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.eliormachlev.currencix.crash.installDebugCrashReporter
import com.eliormachlev.currencix.jank.installJankStats
import com.eliormachlev.currencix.leaks.configureLeakDetection
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.AppDispatchers
import com.eliormachlev.currencix.util.FileLoggingTree
import com.eliormachlev.currencix.util.plantConsoleLogging
import com.eliormachlev.currencix.view.widget.publishWidgetPreviews
import com.eliormachlev.currencix.worker.RateRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.InetAddress
import kotlin.concurrent.thread

class CurrenciesApplication : Application() {
    // Process-lifetime scope for the auto-refresh observer and the widget
    // preview publisher. SupervisorJob so a collector cancellation doesn't
    // tear down the app-wide scope, and Default because the work is a pref
    // read plus a WorkManager enqueue, and an hourly-at-most preview update.
    private val appScope = CoroutineScope(SupervisorJob() + AppDispatchers.production.default)

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
        configureLeakDetection()
        installLogging()
        installJankStats()
        applyNightMode()
        prewarmProviderDns()
        observeAutoRefreshPreference()
        appScope.publishWidgetPreviews(this)
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
        AppCompatDelegate.setDefaultNightMode(
            Database(this)
                .display.theme
                .get()
                .nightMode,
        )
    }

    // Resolve the currently-selected provider's host on a background thread so
    // the first network request doesn't pay for DNS. Failures (offline, DNS
    // outage) are silent — this is a best-effort warm-up, not a health check.
    private fun prewarmProviderDns() {
        thread(name = "dns-prewarm", isDaemon = true) {
            val host =
                Database(this)
                    .providers.apiProvider
                    .get()
                    .getHost() ?: return@thread
            runCatching { InetAddress.getAllByName(host) }
        }
    }

    // Observe the inputs to the WorkManager schedule (opt-in flag, user
    // interval override, current provider, whether rate alerts are waiting). Any change re-enqueues or
    // cancels the periodic work — enqueueUniquePeriodicWork with UPDATE
    // makes the reschedule race-free. Distinct-until-changed on the tuple
    // avoids a redundant enqueue on every DataStore emit.
    private fun observeAutoRefreshPreference() {
        val db = Database(this)
        appScope.launch {
            combine(
                db.providers.autoRefresh.flow,
                db.providers.autoRefreshIntervalOverride.flow,
                db.providers.apiProvider.flow,
                db.alerts.alerts.flow
                    .map { it.isNotEmpty() },
            ) { enabled, override, provider, forAlerts ->
                AutoRefreshPlan(enabled, override, provider, forAlerts)
            }.distinctUntilChanged()
                .collect { plan ->
                    if (plan.enabled) {
                        val minutes = RateRefreshScheduler.effectiveIntervalMinutes(plan.provider, plan.override)
                        RateRefreshScheduler.schedule(this@CurrenciesApplication, minutes, plan.forAlerts)
                    } else {
                        RateRefreshScheduler.cancel(this@CurrenciesApplication)
                    }
                }
        }
    }
}

// What the periodic refresh is scheduled from (see observeAutoRefreshPreference).
private data class AutoRefreshPlan(
    val enabled: Boolean,
    val override: Int?,
    val provider: ApiProvider,
    val forAlerts: Boolean,
)
