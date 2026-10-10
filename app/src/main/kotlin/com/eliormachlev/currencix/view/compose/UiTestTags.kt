package com.eliormachlev.currencix.view.compose

/**
 * Stable semantics tags for the UI journeys the :baselineprofile module drives
 * (baseline-profile generation and the frame-timing benchmarks). MainScreen
 * exposes them as view resource ids (`testTagsAsResourceId`) so UiAutomator
 * can find them; they carry no visual or behavioral meaning.
 *
 * That module is a separate APK and can't import app code — its copy lives in
 * baselineprofile/.../UiTags.kt. Keep the two in sync.
 */
object UiTestTags {
    const val KEY_DELETE = "key_delete"
    const val PILL_FROM = "pill_from"
    const val PILL_TO = "pill_to"
    const val ONBOARDING_SKIP = "onboarding_skip"
    const val CURRENCY_LIST = "currency_list"

    /** Keypad digit / decimal key, e.g. `key_7`. */
    fun key(label: String): String = "key_$label"

    /** Navigation-drawer row, e.g. `drawer_timeline`. */
    fun drawerEntry(name: String): String = "drawer_${name.lowercase()}"
}
