package com.eliormachlev.currencix.util

import android.app.LocaleManager
import android.app.UiModeManager
import android.content.Context
import android.os.LocaleList

/**
 * Makes the app dark, light or follow the phone's dark theme: a
 * [UiModeManager] mode (`MODE_NIGHT_NO`, `MODE_NIGHT_YES`, or
 * `MODE_NIGHT_AUTO` to follow the phone). The platform recreates the running
 * Activities when this changes the resolved mode.
 */
internal fun Context.setAppNightMode(mode: Int) = getSystemService(UiModeManager::class.java).setApplicationNightMode(mode)

/** The languages picked for this app in its settings, in order; empty when it follows the phone's. */
internal fun Context.appLocales(): LocaleList = getSystemService(LocaleManager::class.java).applicationLocales

/** Sets the app's languages, which the platform keeps across restarts; an empty list follows the phone's. */
internal fun Context.setAppLocales(locales: LocaleList) {
    getSystemService(LocaleManager::class.java).applicationLocales = locales
}
