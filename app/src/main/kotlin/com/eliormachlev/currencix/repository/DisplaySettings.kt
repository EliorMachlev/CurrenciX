package com.eliormachlev.currencix.repository

import android.content.Context
import com.eliormachlev.currencix.model.AppTheme
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.booleanPref
import com.eliormachlev.currencix.repository.persistence.intPref
import com.eliormachlev.currencix.repository.persistence.prefStore
import com.eliormachlev.currencix.repository.persistence.stringPref

private const val KEY_THEME = "_theme"
private const val KEY_PREVIEW_CONVERSION_ENABLED = "_previewConversionEnabled"
private const val KEY_EXPANDED_KEYPAD = "_expandedKeypad"
private const val KEY_HAPTIC_FEEDBACK = "_hapticFeedback"
private const val KEY_CLICK_SOUNDS = "_clickSounds"
private const val KEY_HIGH_REFRESH_RATE = "_highRefreshRate"
private const val KEY_DYNAMIC_COLOR = "_dynamicColor"
private const val KEY_DECIMAL_PLACES = "_decimalPlaces"
private const val KEY_DATE_FORMAT = "_dateFormat"
private const val KEY_CART_TOTALS_COLLAPSED = "_cartTotalsCollapsed"
private const val KEY_KEYPAD_ON_LEFT = "_keypadOnLeft"

// First-run onboarding gate (#147). Default `false` — the spotlight tour runs
// on the very first cold-start, then flips to `true` on Skip/Finish so future
// launches skip straight to the hero. Debug builds can flip it back via the
// "Reset onboarding" preference so QA can replay the tour.
private const val KEY_HAS_SEEN_ONBOARDING = "_hasSeenOnboarding"

private const val DEFAULT_DATE_FORMAT = "dd/MM/yy HH:mm"
private const val DEFAULT_DECIMAL_PLACES = 2
private val DECIMAL_PLACES_RANGE = 0..6

/** How the app looks and behaves: theme, formats, keypad, haptics, click sounds, refresh rate, and the onboarding gate. */
class DisplaySettings(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    val theme: Pref<AppTheme> = store.intPref(KEY_THEME, { AppTheme.fromId(it ?: AppTheme.DEFAULT.id) }, { it.id })

    /** The first-run tour has been shown (skipped or finished). */
    val hasSeenOnboarding: Pref<Boolean> = store.booleanPref(KEY_HAS_SEEN_ONBOARDING, default = false)

    /** The currency picker previews each currency's converted amount. */
    val previewConversion: Pref<Boolean> = store.booleanPref(KEY_PREVIEW_CONVERSION_ENABLED, default = false)

    /** The keypad shows its extra keys (true) or the basic layout. */
    val expandedKeypad: Pref<Boolean> = store.booleanPref(KEY_EXPANDED_KEYPAD, default = false)

    val hapticFeedback: Pref<Boolean> = store.booleanPref(KEY_HAPTIC_FEEDBACK, default = true)

    /** The system click on taps (when the phone's touch sounds are on too). */
    val clickSounds: Pref<Boolean> = store.booleanPref(KEY_CLICK_SOUNDS, default = true)

    /** Ask for the screen's fastest refresh rate (120 Hz and up) rather than leave it to the system. */
    val highRefreshRate: Pref<Boolean> = store.booleanPref(KEY_HIGH_REFRESH_RATE, default = true)

    /**
     * On a wide screen the keypad keeps to one side, in reach of one thumb:
     * the left when true, else the right (its default).
     */
    val keypadOnLeft: Pref<Boolean> = store.booleanPref(KEY_KEYPAD_ON_LEFT, default = false)

    /**
     * Material You: wallpaper-derived colors instead of paper / ink, on by
     * default; it also colors the feature icons.
     */
    val dynamicColor: Pref<Boolean> = store.booleanPref(KEY_DYNAMIC_COLOR, default = true)

    val decimalPlaces: Pref<Int> =
        store.intPref(
            KEY_DECIMAL_PLACES,
            decode = { it?.coerceIn(DECIMAL_PLACES_RANGE) ?: DEFAULT_DECIMAL_PLACES },
            encode = { it },
        )

    val dateFormat: Pref<String> = store.stringPref(KEY_DATE_FORMAT, DEFAULT_DATE_FORMAT)

    /** The cart's totals card shows only the total (true) or every line. */
    val cartTotalsCollapsed: Pref<Boolean> = store.booleanPref(KEY_CART_TOTALS_COLLAPSED, default = false)

    fun isPureBlackEnabled(): Boolean = theme.get().isPureBlack
}
