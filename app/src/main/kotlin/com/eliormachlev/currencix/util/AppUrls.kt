package com.eliormachlev.currencix.util

import com.eliormachlev.currencix.BuildConfig

internal const val URL_REPO = "https://github.com/EliorMachlev/CurrenciX"
internal const val URL_DOCS_BASE = "$URL_REPO/blob/master/docs/markDown/"
internal const val URL_RELEASES_TAG = "$URL_REPO/releases/tag/v"
internal const val URL_PULLS = "$URL_REPO/pulls"
internal const val URL_COMMIT = "$URL_REPO/commit/"

// Debug APKs prefer the PR URL, then fall back to the commit page for the
// SHA the APK was built from (GitHub renders the associated PR badge on the
// commit page — one click from the exact PR). If neither is available (git
// unavailable at build time), fall back to the repo pulls list.
internal fun releaseNotesUrl(): String =
    if (BuildConfig.DEBUG) {
        BuildConfig.PR_URL.takeIf { it.isNotBlank() }
            ?: BuildConfig.COMMIT_SHA.takeIf { it.isNotBlank() }?.let { "$URL_COMMIT$it" }
            ?: URL_PULLS
    } else {
        "$URL_RELEASES_TAG${BuildConfig.VERSION_NAME}"
    }
