package com.eliormachlev.currencix

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.eliormachlev.currencix.crash.installDebugCrashReporter
import com.eliormachlev.currencix.jank.installJankStats
import com.eliormachlev.currencix.leaks.configureLeakDetection
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.RefreshInterval
import com.eliormachlev.currencix.problems.ProblemReports
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.prefStore
import com.eliormachlev.currencix.repository.persistence.warmUpSavedData
import com.eliormachlev.currencix.strict.installStrictMode
import com.eliormachlev.currencix.util.AppDispatchers
import com.eliormachlev.currencix.util.FileLoggingTree
import com.eliormachlev.currencix.util.HttpClientProvider
import com.eliormachlev.currencix.util.PrivateRequests
import com.eliormachlev.currencix.util.plantConsoleLogging
import com.eliormachlev.currencix.view.lock.AppLock
import com.eliormachlev.currencix.view.widget.publishWidgetPreviews
import com.eliormachlev.currencix.worker.RateRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
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
        installStrictMode()
        ProblemReports.install(this)
        configureLeakDetection()
        installLogging()
        installJankStats()
        warmUpStores()
        applyNightMode()
        keepPrivacyInStep()
        prewarmProviderDns()
        observeAutoRefreshPreference()
        appScope.publishWidgetPreviews(this)
    }

    // Security & privacy: requests go out private from the first one when the
    // setting is on, and follow it after; the app lock starts following the
    // app coming and going.
    private fun keepPrivacyInStep() {
        val privacy = Database(this).privacy
        PrivateRequests.enabled = privacy.privateRequests.get()
        appScope.launch { privacy.privateRequests.flow.collect { PrivateRequests.enabled = it } }
        AppLock.install(this, privacy)
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

    // Reads each saved store once, in the background, as the process starts:
    // the first read of a store waits on the disk (PrefStore.snapshot), and
    // done here it's over before a screen asks, instead of on the main
    // thread mid-frame. The settings store is read on the main thread right
    // after anyway (the night mode needs it first).
    private fun warmUpStores() {
        val app = this
        CoroutineScope(SupervisorJob() + AppDispatchers.production.io).launch {
            PersistenceKey.entries.forEach { it.prefStore(app).snapshot() }
            app.warmUpSavedData()
        }
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
    // the first network request doesn't pay for DNS. Through the shared
    // client's own resolver, so the answer lands in the cache its requests
    // use (on Android 17+ that's AndroidDns, not the system's). Failures
    // (offline, DNS outage) are silent — this is a best-effort warm-up, not a
    // health check.
    private fun prewarmProviderDns() {
        thread(name = "dns-prewarm", isDaemon = true) {
            val host =
                Database(this)
                    .providers.apiProvider
                    .get()
                    .getHost() ?: return@thread
            runCatching { HttpClientProvider.client(null).dns.lookup(host) }
        }
    }

    // Observe the inputs to the WorkManager schedule (opt-in flag, the
    // provider, the user's interval, whether rate alerts are waiting). Any change re-enqueues or
    // cancels the periodic work — enqueueUniquePeriodicWork with UPDATE
    // makes the reschedule race-free. Distinct-until-changed on the tuple
    // avoids a redundant enqueue on every DataStore emit.
    private fun observeAutoRefreshPreference() {
        val db = Database(this)
        appScope.launch {
            combine(
                db.providers.autoRefresh.flow,
                db.providers.apiProvider.flow,
                db.providers.autoRefreshInterval.flow,
                db.alerts.alerts.flow
                    .map { it.isNotEmpty() },
            ) { enabled, provider, interval, forAlerts ->
                AutoRefreshPlan(enabled, provider, interval, forAlerts)
            }.distinctUntilChanged()
                .collect { plan ->
                    if (plan.enabled) {
                        RateRefreshScheduler.schedule(this@CurrenciesApplication, plan.provider, plan.interval, plan.forAlerts)
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
    val provider: ApiProvider,
    val interval: RefreshInterval,
    val forAlerts: Boolean,
)
