package com.eliormachlev.currencix.viewmodel.preference

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.AppTheme
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
        db.providers.apiProvider.stateInWhileSubscribed(viewModelScope)
    val fallbackProvider: StateFlow<ApiProvider> =
        db.providers.fallbackProvider.stateInWhileSubscribed(viewModelScope)
    val openExchangeratesApiKey: StateFlow<String?> =
        db.providers.openExchangeRatesApiKey.stateInWhileSubscribed(viewModelScope)
    val isPreviewConversionEnabled: StateFlow<Boolean> =
        db.display.previewConversion.stateInWhileSubscribed(viewModelScope)
    val isExpandedKeypadEnabled: StateFlow<Boolean> =
        db.display.expandedKeypad.stateInWhileSubscribed(viewModelScope)
    val isHapticFeedbackEnabled: StateFlow<Boolean> =
        db.display.hapticFeedback.stateInWhileSubscribed(viewModelScope)

    val isClickSoundsEnabled: StateFlow<Boolean> =
        db.display.clickSounds.stateInWhileSubscribed(viewModelScope)
    val isDynamicColorEnabled: StateFlow<Boolean> =
        db.display.dynamicColor.stateInWhileSubscribed(viewModelScope)
    val decimalPlaces: StateFlow<Int> =
        db.display.decimalPlaces.stateInWhileSubscribed(viewModelScope)
    val dateFormat: StateFlow<String> =
        db.display.dateFormat.stateInWhileSubscribed(viewModelScope)
    val isAutoRefreshEnabled: StateFlow<Boolean> =
        db.providers.autoRefresh.stateInWhileSubscribed(viewModelScope)

    fun setApiProvider(api: ApiProvider) {
        persistAndRefreshRates { db.providers.apiProvider.set(api) }
    }

    fun setOpenExchangeratesApiKey(id: String) {
        persistAndRefreshRates { db.providers.openExchangeRatesApiKey.set(id) }
    }

    // Persist a provider-affecting change, then re-fetch rates so the UI
    // doesn't keep showing the previous provider's cached values.
    private fun persistAndRefreshRates(persist: () -> Unit) {
        persist()
        ExchangeRatesRepository(app).getExchangeRates()
    }

    /**
     * Returns true when the caller must recreate the Activity to make the
     * change visible. `setDefaultNightMode` auto-recreates when the
     * night mode changes, but a pure-black-only flip (Dark ↔ OLED, or
     * System ↔ System-OLED while system is dark) keeps the same night mode,
     * so `MainActivity.setTheme` doesn't rerun on its own.
     */
    fun setTheme(theme: AppTheme): Boolean {
        val old = db.display.theme.get()
        db.display.theme.set(theme)
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

    fun setPreviewConversionEnabled(enabled: Boolean) = db.display.previewConversion.set(enabled)

    fun setExpandedKeypadEnabled(enabled: Boolean) = db.display.expandedKeypad.set(enabled)

    fun getExpandedKeypadEnabledBlocking(): Boolean = db.display.expandedKeypad.get()

    fun setHapticFeedbackEnabled(enabled: Boolean) = db.display.hapticFeedback.set(enabled)

    fun setClickSoundsEnabled(enabled: Boolean) = db.display.clickSounds.set(enabled)

    fun setFallbackProvider(provider: ApiProvider) = db.providers.fallbackProvider.set(provider)

    fun setDynamicColorEnabled(enabled: Boolean) = db.display.dynamicColor.set(enabled)

    fun setDecimalPlaces(places: Int) = db.display.decimalPlaces.set(places)

    fun setDateFormat(pattern: String) = db.display.dateFormat.set(pattern)

    // Auto-refresh toggle (#151). Persistence-only — the actual WorkManager
    // schedule/cancel is driven from the Application observer so the source
    // of truth is DataStore rather than the UI's imperative flow.
    fun setAutoRefreshEnabled(enabled: Boolean) = db.providers.autoRefresh.set(enabled)

    fun getTheme(): AppTheme = db.display.theme.get()
}
