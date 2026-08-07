package com.eliormachlev.currencix

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.FileLoggingTree
import java.net.InetAddress
import kotlin.concurrent.thread
import timber.log.Timber

class CurrenciesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installLogging()
        applyNightMode()
        warmSharedPreferences()
        prewarmProviderDns()
    }

    // Debug builds also get a console tree so `adb logcat` mirrors what the
    // file tree captures. Release builds are file-only — no remote sink.
    private fun installLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(FileLoggingTree(filesDir))
    }

    // Apply the persisted day/night mode before any Activity is created, so
    // BaseActivity.setTheme(AppTheme_PureBlack) resolves against the correct
    // night qualifier on the very first frame. Otherwise the pure-black
    // background renders as the day-mode color until setDefaultNightMode
    // triggers a recreate.
    private fun applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(Database(this).getTheme().nightMode)
    }

    // Force each SharedPreferences file the app uses to load from disk on a
    // background thread ahead of the first Activity. SharedPreferences is
    // in-memory after the first read, so subsequent main-thread reads in
    // BaseActivity.onCreate (theme, pure-black) hit RAM instead of blocking
    // on I/O during app startup.
    private fun warmSharedPreferences() {
        thread(name = "prefs-warm", isDaemon = true) {
            runCatching {
                Database(this).getTheme()
            }
        }
    }

    // Resolve the currently-selected provider's host on a background thread so
    // the first network request doesn't pay for DNS. The preference read and
    // resolution both run off the main thread. Failures (offline, DNS outage)
    // are silent — this is a best-effort warm-up, not a health check.
    private fun prewarmProviderDns() {
        thread(name = "dns-prewarm", isDaemon = true) {
            val host = Database(this).getApiProvider().getHost() ?: return@thread
            runCatching { InetAddress.getAllByName(host) }
        }
    }
}
