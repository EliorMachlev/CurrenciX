package com.eliormachlev.currencix.viewmodel.preference

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.AppTheme
import com.eliormachlev.currencix.model.Language
import com.eliormachlev.currencix.model.RefreshInterval
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.ExchangeRatesRepository
import com.eliormachlev.currencix.util.androidLanguageCode
import com.eliormachlev.currencix.util.currentPins
import com.eliormachlev.currencix.viewmodel.util.stateInWhileSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PreferenceViewModel(
    private val app: Application,
) : AndroidViewModel(app) {
    private val db = Database(app)

    val apiProvider: StateFlow<ApiProvider> =
        db.providers.apiProvider.stateInWhileSubscribed(viewModelScope)
    val fallbackProvider: StateFlow<ApiProvider> =
        db.providers.fallbackProvider.stateInWhileSubscribed(viewModelScope)
    val isShowUnreliableProvidersEnabled: StateFlow<Boolean> =
        db.providers.showUnreliable.stateInWhileSubscribed(viewModelScope)

    /** The API key entered for each keyed provider. */
    val apiKeys: StateFlow<Map<ApiProvider, String>> =
        db.providers.apiKeys.stateInWhileSubscribed(viewModelScope)
    val isPreviewConversionEnabled: StateFlow<Boolean> =
        db.display.previewConversion.stateInWhileSubscribed(viewModelScope)
    val isExpandedKeypadEnabled: StateFlow<Boolean> =
        db.display.expandedKeypad.stateInWhileSubscribed(viewModelScope)
    val isHapticFeedbackEnabled: StateFlow<Boolean> =
        db.display.hapticFeedback.stateInWhileSubscribed(viewModelScope)

    val isClickSoundsEnabled: StateFlow<Boolean> =
        db.display.clickSounds.stateInWhileSubscribed(viewModelScope)

    val isHighRefreshRateEnabled: StateFlow<Boolean> =
        db.display.highRefreshRate.stateInWhileSubscribed(viewModelScope)

    val isAppLockEnabled: StateFlow<Boolean> =
        db.privacy.appLock.stateInWhileSubscribed(viewModelScope)

    val isPrivateRequestsEnabled: StateFlow<Boolean> =
        db.privacy.privateRequests.stateInWhileSubscribed(viewModelScope)

    val isBlockScreenshotsEnabled: StateFlow<Boolean> =
        db.privacy.blockScreenshots.stateInWhileSubscribed(viewModelScope)

    val isCertificatePinningEnabled: StateFlow<Boolean> =
        db.privacy.certificatePinning.stateInWhileSubscribed(viewModelScope)

    /** The pins taken on this phone (Re-pin), by host. */
    val certificatePins: StateFlow<Map<String, Set<String>>> =
        db.privacy.certificatePins.stateInWhileSubscribed(viewModelScope)

    private val repinning = MutableStateFlow(false)

    /** Whether Re-pin is connecting to the servers now. */
    val isRepinning: StateFlow<Boolean> = repinning.asStateFlow()
    val isDynamicColorEnabled: StateFlow<Boolean> =
        db.display.dynamicColor.stateInWhileSubscribed(viewModelScope)
    val decimalPlaces: StateFlow<Int> =
        db.display.decimalPlaces.stateInWhileSubscribed(viewModelScope)
    val dateFormat: StateFlow<String> =
        db.display.dateFormat.stateInWhileSubscribed(viewModelScope)
    val isAutoRefreshEnabled: StateFlow<Boolean> =
        db.providers.autoRefresh.stateInWhileSubscribed(viewModelScope)
    val autoRefreshInterval: StateFlow<RefreshInterval> =
        db.providers.autoRefreshInterval.stateInWhileSubscribed(viewModelScope)

    fun setApiProvider(api: ApiProvider) {
        persistAndRefreshRates { db.providers.apiProvider.set(api) }
    }

    /** Stores [key] as [provider]'s API key, then fetches rates with it. */
    fun setApiKey(
        provider: ApiProvider,
        key: String,
    ) {
        persistAndRefreshRates { db.providers.setApiKey(provider, key) }
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

    fun setLanguage(language: Language) {
        val appLocale: LocaleListCompat =
            if (language == Language.SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(
                    // pt_BR -> pt-BR, and modern BCP-47 -> Android's legacy
                    // form so what we hand Android matches what it hands back
                    // via getLanguage() later (avoids picker/lookup drift).
                    androidLanguageCode(language.iso).replace('_', '-'),
                )
            }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    /** The app language picked in Settings, or [Language.SYSTEM] when it follows the phone's. */
    fun getLanguage(): Language = Language.byIso(appLocaleCode()) ?: Language.SYSTEM

    // The app locale as "de_DE", or "de" when no country is set; null when unset.
    private fun appLocaleCode(): String? {
        val appLocale = AppCompatDelegate.getApplicationLocales()[0] ?: return null
        val parts = listOf(appLocale.language, appLocale.country).filter { it.isNotEmpty() }
        return parts.joinToString("_").ifEmpty { null }
    }

    fun setPreviewConversionEnabled(enabled: Boolean) = db.display.previewConversion.set(enabled)

    fun setExpandedKeypadEnabled(enabled: Boolean) = db.display.expandedKeypad.set(enabled)

    fun getExpandedKeypadEnabledBlocking(): Boolean = db.display.expandedKeypad.get()

    fun setHapticFeedbackEnabled(enabled: Boolean) = db.display.hapticFeedback.set(enabled)

    fun setClickSoundsEnabled(enabled: Boolean) = db.display.clickSounds.set(enabled)

    fun setHighRefreshRateEnabled(enabled: Boolean) = db.display.highRefreshRate.set(enabled)

    fun setShowUnreliableProvidersEnabled(enabled: Boolean) = db.providers.showUnreliable.set(enabled)

    fun setAppLockEnabled(enabled: Boolean) = db.privacy.appLock.set(enabled)

    fun setPrivateRequestsEnabled(enabled: Boolean) = db.privacy.privateRequests.set(enabled)

    fun setBlockScreenshotsEnabled(enabled: Boolean) = db.privacy.blockScreenshots.set(enabled)

    fun setCertificatePinningEnabled(enabled: Boolean) = db.privacy.certificatePinning.set(enabled)

    /**
     * Re-pin: takes the pins of the main and fallback providers' servers as
     * they serve them now, in place of the built-in ones. Only those two are
     * contacted; every other server, and one that doesn't answer, keeps the
     * pins it had.
     */
    fun repinCertificates() {
        if (!repinning.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val taken = currentPins(selectedProviderHosts())
                db.privacy.certificatePins.update { it + taken }
            } finally {
                repinning.value = false
            }
        }
    }

    // The servers of the providers in use: the main one and the fallback.
    private fun selectedProviderHosts(): Set<String> =
        listOf(db.providers.apiProvider.get(), db.providers.fallbackProvider.get())
            .flatMapTo(mutableSetOf()) { it.implementation.hosts }

    /** Back to the pins built into the app, for every server. */
    fun restoreBuiltInPins() = db.privacy.certificatePins.set(emptyMap())

    fun setFallbackProvider(provider: ApiProvider) = db.providers.fallbackProvider.set(provider)

    fun setDynamicColorEnabled(enabled: Boolean) = db.display.dynamicColor.set(enabled)

    fun setDecimalPlaces(places: Int) = db.display.decimalPlaces.set(places)

    fun setDateFormat(pattern: String) = db.display.dateFormat.set(pattern)

    // Auto-refresh toggle (#151). Persistence-only — the actual WorkManager
    // schedule/cancel is driven from the Application observer so the source
    // of truth is DataStore rather than the UI's imperative flow.
    fun setAutoRefreshEnabled(enabled: Boolean) = db.providers.autoRefresh.set(enabled)

    fun setAutoRefreshInterval(interval: RefreshInterval) = db.providers.autoRefreshInterval.set(interval)

    fun getTheme(): AppTheme = db.display.theme.get()
}
