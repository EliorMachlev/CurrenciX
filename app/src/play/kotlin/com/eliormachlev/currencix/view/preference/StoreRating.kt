package com.eliormachlev.currencix.view.preference

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import timber.log.Timber

private const val URL_PLAY_MARKET = "market://details?id=com.eliormachlev.currencix"
private const val URL_PLAY_WEB = "https://play.google.com/store/apps/details?id=com.eliormachlev.currencix"

// The Play build can be rated: Settings shows "Rate" and this opens the
// listing. The F-Droid build has no store to rate in (src/fdroid/…/StoreRating.kt).
internal val rateApp: ((Context) -> Unit)? = ::openPlayStore

private fun openPlayStore(context: Context) {
    try {
        context.startActivity(playIntent(URL_PLAY_MARKET))
    } catch (e: ActivityNotFoundException) {
        Timber.tag("StoreRating").d(e, "Play Store not available, opening browser")
        context.startActivity(playIntent(URL_PLAY_WEB))
    }
}

private fun playIntent(url: String): Intent =
    Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(
            Intent.FLAG_ACTIVITY_NO_HISTORY
                or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                or Intent.FLAG_ACTIVITY_NEW_DOCUMENT,
        )
    }
