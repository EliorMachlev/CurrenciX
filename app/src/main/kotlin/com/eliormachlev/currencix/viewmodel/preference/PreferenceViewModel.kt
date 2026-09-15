package com.eliormachlev.currencix.viewmodel.preference

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.AppTheme
import com.eliormachlev.currencix.model.KeyboardType
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.ExchangeRatesRepository
import com.eliormachlev.currencix.util.androidLanguageCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

// Language.SYSTEM.iso — matches the enum value that means "follow system
// locale" without pulling the enum into this file.
private const val LANGUAGE_SYSTEM = "system"

// #149 pilot: how long the StateFlow keeps collecting the upstream after the
// last subscriber goes away. 5s covers config-change / navigation churn so we
// don't re-cold-start the upstream on every recomposition/rebind.
private const val STATE_FLOW_STOP_TIMEOUT_MS = 5_000L

class PreferenceViewModel(
    private val app: Application,
) : AndroidViewModel(app) {
    private val db = Database(app)

    val apiProvider: StateFlow<ApiProvider> =
        db.getApiProviderFlow().stateInWhileSubscribed(db.getApiProvider())
    val openExchangeratesApiKey: StateFlow<String?> =
        db.getOpenExchangeRatesApiKeyFlow().stateInWhileSubscribed(db.getOpenExchangeRatesApiKey())
    val isPreviewConversionEnabled: StateFlow<Boolean> =
        db.isPreviewConversionEnabledFlow().stateInWhileSubscribed(db.isPreviewConversionEnabledBlocking())
    val keyboardType: StateFlow<KeyboardType> =
        db.getKeyboardTypeFlow().stateInWhileSubscribed(db.getKeyboardTypeBlocking())
    val isHapticFeedbackEnabled: StateFlow<Boolean> =
        db.isHapticFeedbackEnabledFlow().stateInWhileSubscribed(db.isHapticFeedbackEnabledBlocking())
    val decimalPlaces: StateFlow<Int> =
        db.getDecimalPlacesFlow().stateInWhileSubscribed(db.getDecimalPlacesBlocking())
    val dateFormat: StateFlow<String> =
        db.getDateFormatFlow().stateInWhileSubscribed(db.getDateFormatBlocking())

    private fun <T> Flow<T>.stateInWhileSubscribed(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_STOP_TIMEOUT_MS), initial)

    fun setApiProvider(api: ApiProvider) {
        persistAndRefreshRates { db.setApiProvider(api) }
    }

    fun setOpenExchangeratesApiKey(id: String) {
        persistAndRefreshRates { db.setOpenExchangeRatesApiKey(id) }
    }

    // Persist a provider-affecting change, then re-fetch rates so the UI
    // doesn't keep showing the previous provider's cached values.
    private fun persistAndRefreshRates(persist: () -> Unit) {
        persist()
        ExchangeRatesRepository(app).getExchangeRates()
    }

    /**
     * Returns true when the caller must rebuild the activity stack to make
     * the change visible. `setDefaultNightMode` auto-recreates when the
     * night mode changes, but a pure-black-only flip (Dark ↔ OLED, or
     * System ↔ System-OLED while system is dark) keeps the same night mode,
     * so `BaseActivity.setTheme` doesn't rerun on its own.
     */
    fun setTheme(theme: AppTheme): Boolean {
        val old = db.getTheme()
        db.setTheme(theme)
        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
        return old.nightMode == theme.nightMode &&
            old.isPureBlack != theme.isPureBlack &&
            theme.isDarkActive(app.resources.configuration)
    }

    fun setLanguage(language: String) {
        val appLocale: LocaleListCompat =
            if (language == LANGUAGE_SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(
                    // pt_BR -> pt-BR, and modern BCP-47 -> Android's legacy
                    // form so what we hand Android matches what it hands back
                    // via getLanguage() later (avoids picker/lookup drift).
                    androidLanguageCode(language).replace('_', '-'),
                )
            }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    /**
     * returns the currently selected language in the following format:
     * "de_DE" or "de", if no country is set
     */
    fun getLanguage(): String? {
        val appLocale = AppCompatDelegate.getApplicationLocales()[0]
        return if (appLocale == null || (appLocale.language.isEmpty() && appLocale.country.isEmpty())) {
            null
        } else if (appLocale.country.isEmpty()) {
            appLocale.language
        } else if (appLocale.language.isEmpty()) {
            appLocale.country
        } else {
            "${appLocale.language}_${appLocale.country}"
        }
    }

    fun setPreviewConversionEnabled(enabled: Boolean) {
        db.setPreviewConversionEnabled(enabled)
    }

    fun setKeyboardType(type: KeyboardType) {
        db.setKeyboardType(type)
    }

    fun getKeyboardTypeBlocking(): KeyboardType = db.getKeyboardTypeBlocking()

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        db.setHapticFeedbackEnabled(enabled)
    }

    fun setDecimalPlaces(places: Int) {
        db.setDecimalPlaces(places)
    }

    fun setDateFormat(pattern: String) {
        db.setDateFormat(pattern)
    }

    fun getTheme(): AppTheme = db.getTheme()
}
