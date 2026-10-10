package com.eliormachlev.currencix.util

import com.eliormachlev.currencix.BuildConfig
import timber.log.Timber

private const val URL_PULLS = "$URL_REPO/pulls"
private const val URL_COMMIT = "$URL_REPO/commit/"

// Debug APKs link to the PR they were built from, else the commit page for
// their SHA (GitHub shows the associated PR there), else the pulls list —
// when git wasn't available at build time.
internal fun releaseNotesUrl(): String =
    BuildConfig.PR_URL.takeIf { it.isNotBlank() }
        ?: BuildConfig.COMMIT_SHA.takeIf { it.isNotBlank() }?.let { "$URL_COMMIT$it" }
        ?: URL_PULLS

// Debug builds mirror the log to `adb logcat`.
internal fun plantConsoleLogging() {
    Timber.plant(Timber.DebugTree())
}
