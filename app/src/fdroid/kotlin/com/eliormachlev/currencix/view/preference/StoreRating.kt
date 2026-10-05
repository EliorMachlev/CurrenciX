package com.eliormachlev.currencix.view.preference

import android.content.Context

// The F-Droid build has no store listing to rate, so Settings shows no
// "Rate" row. The Play build opens its listing (src/play/…/StoreRating.kt).
internal val rateApp: ((Context) -> Unit)? = null
