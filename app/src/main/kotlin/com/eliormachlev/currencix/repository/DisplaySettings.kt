package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.model.AppTheme
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.prefStore
import kotlinx.coroutines.flow.Flow

private const val KEY_THEME = "_theme"
private const val KEY_PREVIEW_CONVERSION_ENABLED = "_previewConversionEnabled"
private const val KEY_EXPANDED_KEYPAD = "_expandedKeypad"
private const val KEY_HAPTIC_FEEDBACK = "_hapticFeedback"
private const val KEY_DYNAMIC_COLOR = "_dynamicColor"
private const val KEY_DECIMAL_PLACES = "_decimalPlaces"
private const val KEY_DATE_FORMAT = "_dateFormat"
private const val DEFAULT_DATE_FORMAT = "dd/MM/yy HH:mm"

// First-run onboarding gate (#147). Default `false` — the spotlight tour runs
// on the very first cold-start, then flips to `true` on Skip/Finish so future
// launches skip straight to the hero. Debug builds can flip it back via the
// "Reset onboarding" preference so QA can replay the tour.
private const val KEY_HAS_SEEN_ONBOARDING = "_hasSeenOnboarding"

private val previewConversionEnabledMapper: (Preferences) -> Boolean = {
    it[booleanPreferencesKey(KEY_PREVIEW_CONVERSION_ENABLED)] ?: false
}

private val expandedKeypadEnabledMapper: (Preferences) -> Boolean = {
    it[booleanPreferencesKey(KEY_EXPANDED_KEYPAD)] ?: false
}

private val hapticFeedbackEnabledMapper: (Preferences) -> Boolean = {
    it[booleanPreferencesKey(KEY_HAPTIC_FEEDBACK)] ?: true
}

private val dynamicColorEnabledMapper: (Preferences) -> Boolean = {
    it[booleanPreferencesKey(KEY_DYNAMIC_COLOR)] ?: false
}

private val decimalPlacesMapper: (Preferences) -> Int = {
    (it[stringPreferencesKey(KEY_DECIMAL_PLACES)] ?: "2").toIntOrNull()?.coerceIn(0, 6) ?: 2
}

private val dateFormatMapper: (Preferences) -> String = {
    it[stringPreferencesKey(KEY_DATE_FORMAT)] ?: DEFAULT_DATE_FORMAT
}

private val hasSeenOnboardingMapper: (Preferences) -> Boolean = {
    it[booleanPreferencesKey(KEY_HAS_SEEN_ONBOARDING)] ?: false
}

/** How the app looks and behaves: theme, formats, keypad, haptics, and the onboarding gate. */
class DisplaySettings(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    // theme

    fun setTheme(theme: AppTheme) {
        store.edit { this[intPreferencesKey(KEY_THEME)] = theme.id }
    }

    fun getTheme(): AppTheme = AppTheme.fromId(store.snapshot()[intPreferencesKey(KEY_THEME)] ?: AppTheme.DEFAULT.id)

    fun isPureBlackEnabled(): Boolean = getTheme().isPureBlack

    // onboarding (#147) — first-run spotlight tour gate.

    fun setHasSeenOnboarding(seen: Boolean) {
        store.edit { this[booleanPreferencesKey(KEY_HAS_SEEN_ONBOARDING)] = seen }
    }

    fun getHasSeenOnboardingFlow(): Flow<Boolean> = store.mappedFlow(hasSeenOnboardingMapper)

    fun getHasSeenOnboardingBlocking(): Boolean = hasSeenOnboardingMapper(store.snapshot())

    // preview conversion

    fun setPreviewConversionEnabled(enabled: Boolean) {
        store.edit { this[booleanPreferencesKey(KEY_PREVIEW_CONVERSION_ENABLED)] = enabled }
    }

    fun isPreviewConversionEnabled(): LiveData<Boolean> = store.mappedLiveData(previewConversionEnabledMapper)

    fun isPreviewConversionEnabledFlow(): Flow<Boolean> = store.mappedFlow(previewConversionEnabledMapper)

    fun isPreviewConversionEnabledBlocking(): Boolean = previewConversionEnabledMapper(store.snapshot())

    // expanded keypad (boolean pref: true = expanded layout, false = basic/default)

    fun setExpandedKeypadEnabled(enabled: Boolean) {
        store.edit { this[booleanPreferencesKey(KEY_EXPANDED_KEYPAD)] = enabled }
    }

    fun getExpandedKeypadEnabled(): LiveData<Boolean> = store.mappedLiveData(expandedKeypadEnabledMapper)

    fun getExpandedKeypadEnabledFlow(): Flow<Boolean> = store.mappedFlow(expandedKeypadEnabledMapper)

    fun getExpandedKeypadEnabledBlocking(): Boolean = expandedKeypadEnabledMapper(store.snapshot())

    // haptic feedback

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        store.edit { this[booleanPreferencesKey(KEY_HAPTIC_FEEDBACK)] = enabled }
    }

    fun isHapticFeedbackEnabled(): LiveData<Boolean> = store.mappedLiveData(hapticFeedbackEnabledMapper)

    fun isHapticFeedbackEnabledFlow(): Flow<Boolean> = store.mappedFlow(hapticFeedbackEnabledMapper)

    fun isHapticFeedbackEnabledBlocking(): Boolean = store.snapshot()[booleanPreferencesKey(KEY_HAPTIC_FEEDBACK)] ?: true

    // Material You: wallpaper-derived colors instead of paper / ink

    fun setDynamicColorEnabled(enabled: Boolean) {
        store.edit { this[booleanPreferencesKey(KEY_DYNAMIC_COLOR)] = enabled }
    }

    fun isDynamicColorEnabledFlow(): Flow<Boolean> = store.mappedFlow(dynamicColorEnabledMapper)

    fun isDynamicColorEnabledBlocking(): Boolean = dynamicColorEnabledMapper(store.snapshot())

    // decimal places

    fun setDecimalPlaces(places: Int) {
        // Historical shape kept: stored as String so old backups round-trip
        // (the SharedPreferences preference-screen used to write via
        // ListPreference which stringifies its value).
        store.edit { this[stringPreferencesKey(KEY_DECIMAL_PLACES)] = places.toString() }
    }

    fun getDecimalPlaces(): LiveData<Int> = store.mappedLiveData(decimalPlacesMapper)

    fun getDecimalPlacesFlow(): Flow<Int> = store.mappedFlow(decimalPlacesMapper)

    fun getDecimalPlacesBlocking(): Int = decimalPlacesMapper(store.snapshot())

    fun setDateFormat(pattern: String) {
        store.edit { this[stringPreferencesKey(KEY_DATE_FORMAT)] = pattern }
    }

    fun getDateFormat(): LiveData<String> = store.mappedLiveData(dateFormatMapper)

    fun getDateFormatFlow(): Flow<String> = store.mappedFlow(dateFormatMapper)

    fun getDateFormatBlocking(): String = store.snapshot()[stringPreferencesKey(KEY_DATE_FORMAT)] ?: DEFAULT_DATE_FORMAT
}
