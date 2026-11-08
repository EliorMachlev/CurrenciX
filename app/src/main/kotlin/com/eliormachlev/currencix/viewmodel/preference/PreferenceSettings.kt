package com.eliormachlev.currencix.viewmodel.preference

import android.app.Application
import android.os.LocaleList
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.AppTheme
import com.eliormachlev.currencix.model.Language
import com.eliormachlev.currencix.model.RefreshInterval
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.ExchangeRatesRepository
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.util.androidLanguageCode
import com.eliormachlev.currencix.util.appLocales
import com.eliormachlev.currencix.util.setAppLocales
import com.eliormachlev.currencix.util.setAppNightMode

/**
 * The settings screens' view of the stored settings: each setting as its [Pref]
 * (the screen follows it with `collectAsStateWithLifecycle`), and the few
 * changes that do more than store a value. Holds no state of its own, so
 * there is nothing to keep across a rotation: build it where it is used.
 */
class PreferenceSettings(
    private val app: Application,
) {
    private val db = Database(app)

    val apiProvider: Pref<ApiProvider> = db.providers.apiProvider
    val fallbackProvider: Pref<ApiProvider> = db.providers.fallbackProvider
    val isShowUnreliableProvidersEnabled: Pref<Boolean> = db.providers.showUnreliable

    /** The API key entered for each keyed provider. */
    val apiKeys: Pref<Map<ApiProvider, String>> = db.providers.apiKeys
    val isPreviewConversionEnabled: Pref<Boolean> = db.display.previewConversion
    val isExpandedKeypadEnabled: Pref<Boolean> = db.display.expandedKeypad
    val isHapticFeedbackEnabled: Pref<Boolean> = db.display.hapticFeedback

    val isClickSoundsEnabled: Pref<Boolean> = db.display.clickSounds

    val isHighRefreshRateEnabled: Pref<Boolean> = db.display.highRefreshRate

    val isAppLockEnabled: Pref<Boolean> = db.privacy.appLock

    val isPrivateRequestsEnabled: Pref<Boolean> = db.privacy.privateRequests

    val isBlockScreenshotsEnabled: Pref<Boolean> = db.privacy.blockScreenshots

    val isDynamicColorEnabled: Pref<Boolean> = db.display.dynamicColor
    val decimalPlaces: Pref<Int> = db.display.decimalPlaces
    val dateFormat: Pref<String> = db.display.dateFormat
    val isAutoRefreshEnabled: Pref<Boolean> = db.providers.autoRefresh
    val autoRefreshInterval: Pref<RefreshInterval> = db.providers.autoRefreshInterval

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
        ExchangeRatesRepository(app).refreshExchangeRates()
    }

    /**
     * Returns true when the caller must recreate the Activity to make the
     * change visible. `setApplicationNightMode` recreates the Activity when the
     * night mode changes, but a pure-black-only flip (Dark ↔ OLED, or
     * System ↔ System-OLED while system is dark) keeps the same night mode,
     * so `MainActivity.setTheme` doesn't rerun on its own.
     */
    fun setTheme(theme: AppTheme): Boolean {
        val old = db.display.theme.get()
        db.display.theme.set(theme)
        app.setAppNightMode(theme.nightMode)
        return old.nightMode == theme.nightMode &&
            old.isPureBlack != theme.isPureBlack &&
            theme.isDarkActive(app.resources.configuration)
    }

    fun setLanguage(language: Language) {
        val appLocale: LocaleList =
            if (language == Language.SYSTEM) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(
                    // pt_BR -> pt-BR, and modern BCP-47 -> Android's legacy
                    // form so what we hand Android matches what it hands back
                    // via getLanguage() later (avoids picker/lookup drift).
                    androidLanguageCode(language.iso).replace('_', '-'),
                )
            }
        app.setAppLocales(appLocale)
    }

    /** The app language picked in Settings, or [Language.SYSTEM] when it follows the phone's. */
    fun getLanguage(): Language = Language.byIso(appLocaleCode()) ?: Language.SYSTEM

    // The app locale as "de_DE", or "de" when no country is set; null when unset.
    private fun appLocaleCode(): String? {
        val appLocale = app.appLocales().get(0) ?: return null
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
