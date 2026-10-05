package com.eliormachlev.currencix.util

import com.eliormachlev.currencix.BuildConfig

private const val URL_RELEASES_TAG = "$URL_REPO/releases/tag/v"

// A release links to the GitHub release for the version it shipped as.
internal fun releaseNotesUrl(): String = "$URL_RELEASES_TAG${BuildConfig.VERSION_NAME}"

// Release builds log to the file only — no console, no remote sink.
internal fun plantConsoleLogging() = Unit
