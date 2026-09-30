package com.eliormachlev.currencix.baselineprofile

/**
 * Mirror of the app's `view/compose/UiTestTags.kt` — the semantics tags the
 * app exposes as view resource ids for these journeys. This module is a
 * separate APK and can't import app code, so keep the two files in sync.
 */
internal object UiTags {
    const val KEY_DELETE = "key_delete"
    const val PILL_TO = "pill_to"
    const val ONBOARDING_SKIP = "onboarding_skip"
    const val CURRENCY_LIST = "currency_list"
    const val DRAWER_TIMELINE = "drawer_timeline"
    const val DRAWER_CART = "drawer_cart"

    fun key(label: Char): String = "key_$label"
}
