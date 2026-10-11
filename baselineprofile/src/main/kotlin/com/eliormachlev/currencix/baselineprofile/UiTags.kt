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
    const val DRAWER_SETTINGS = "drawer_settings"
    const val CART_LIST = "cart_list"
    const val CART_ADD = "cart_add"
    const val CART_NEXT = "cart_next"
    const val CART_PRICE = "cart_price"

    /** Settings' category rows, in the order they're listed. */
    val SETTINGS_TOPICS = listOf("converter", "rates", "appearance", "chart", "about").map { "settings_$it" }

    fun key(label: Char): String = "key_$label"
}
