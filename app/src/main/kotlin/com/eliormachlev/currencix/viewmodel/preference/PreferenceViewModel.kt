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
import com.eliormachlev.currencix.viewmodel.util.stateInWhileSubscribed
import kotlinx.coroutines.flow.StateFlow

// Language.SYSTEM.iso — matches the enum value that means "follow system
// locale" without pulling the enum into this file.
private const val LANGUAGE_SYSTEM = "system"

class PreferenceViewModel(
    private val app: Application,
) : AndroidViewModel(app) {
    private val db = Database(app)

    val apiProvider: StateFlow<ApiProvider> =
        db.getApiProviderFlow().stateInWhileSubscribed(viewModelScope, db.getApiProvider())
    val openExchangeratesApiKey: StateFlow<String?> =
        db.getOpenExchangeRatesApiKeyFlow().stateInWhileSubscribed(viewModelScope, db.getOpenExchangeRatesApiKey())
    val isPreviewConversionEnabled: StateFlow<Boolean> =
        db.isPreviewConversionEnabledFlow().stateInWhileSubscribed(viewModelScope, db.isPreviewConversionEnabledBlocking())
    val keyboardType: StateFlow<KeyboardType> =
        db.getKeyboardTypeFlow().stateInWhileSubscribed(viewModelScope, db.getKeyboardTypeBlocking())
    val isHapticFeedbackEnabled: StateFlow<Boolean> =
        db.isHapticFeedbackEnabledFlow().stateInWhileSubscribed(viewModelScope, db.isHapticFeedbackEnabledBlocking())
    val decimalPlaces: StateFlow<Int> =
        db.getDecimalPlacesFlow().stateInWhileSubscribed(viewModelScope, db.getDecimalPlacesBlocking())
    val dateFormat: StateFlow<String> =
        db.getDateFormatFlow().stateInWhileSubscribed(viewModelScope, db.getDateFormatBlocking())
    val isAutoRefreshEnabled: StateFlow<Boolean> =
        db.isAutoRefreshEnabledFlow().stateInWhileSubscribed(viewModelScope, db.isAutoRefreshEnabledBlocking())

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

    // Auto-refresh toggle (#151). Persistence-only — the actual WorkManager
    // schedule/cancel is driven from the Application observer so the source
    // of truth is DataStore rather than the UI's imperative flow.
    fun setAutoRefreshEnabled(enabled: Boolean) {
        db.setAutoRefreshEnabled(enabled)
    }

    fun getTheme(): AppTheme = db.getTheme()
}
