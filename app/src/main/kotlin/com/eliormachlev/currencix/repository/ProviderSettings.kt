package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.RefreshInterval
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.booleanPref
import com.eliormachlev.currencix.repository.persistence.intPref
import com.eliormachlev.currencix.repository.persistence.optionalStringPref
import com.eliormachlev.currencix.repository.persistence.pref
import com.eliormachlev.currencix.repository.persistence.prefStore
import com.eliormachlev.currencix.util.NO_PROVIDER_ID

// APP (prefs) keys.
private const val KEY_API = "_api"
private const val KEY_FALLBACK_API = "_fallbackApi"
private const val KEY_OPEN_EXCHANGERATES_API_KEY = "_api_openExchangeratesApiKey"

// Whether the pickers list providers that are often down or inaccurate.
private const val KEY_SHOW_UNRELIABLE_PROVIDERS = "_showUnreliableProviders"

// Auto-refresh (#151): default OFF at first launch — opting-in is explicit
// via Settings (until #147 onboarding lands the hero opt-in).
private const val KEY_AUTO_REFRESH_ENABLED = "_autoRefreshEnabled"

// How often auto-refresh runs, in minutes (RefreshInterval.minutes); absent
// means the default. The name predates the setting: it's stored, so it stays.
private const val KEY_AUTO_REFRESH_INTERVAL = "_autoRefreshIntervalMinutesOverride"

private val apiProviderKey = intPreferencesKey(KEY_API)
private val fallbackApiKey = intPreferencesKey(KEY_FALLBACK_API)

private fun mainProvider(prefs: Preferences): ApiProvider = ApiProvider.fromId(prefs[apiProviderKey] ?: NO_PROVIDER_ID)

// The fallback as chosen, unless it has since become the main provider: then
// the main provider's default fallback.
private fun resolveFallback(prefs: Preferences): ApiProvider {
    val main = mainProvider(prefs)
    val chosen = prefs[fallbackApiKey]?.let(ApiProvider::fromId)
    return chosen?.takeIf { it != main } ?: ApiProvider.defaultFallback(main)
}

/** Which rate provider to use, its fallback and API key, and background refresh. */
class ProviderSettings(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    val apiProvider: Pref<ApiProvider> = store.pref(::mainProvider, write = { this[apiProviderKey] = it.id })

    /**
     * Fetched when the main provider fails. Stored as chosen; resolved against
     * the current main provider on read, so a choice that became the main
     * provider falls back to the default instead.
     */
    val fallbackProvider: Pref<ApiProvider> = store.pref(::resolveFallback, write = { this[fallbackApiKey] = it.id })

    /** The user's Open Exchange Rates key; null (and removed) when there is none. */
    val openExchangeRatesApiKey: Pref<String?> = store.optionalStringPref(KEY_OPEN_EXCHANGERATES_API_KEY)

    /**
     * Whether the provider pickers list the unreliable providers too
     * ([com.eliormachlev.currencix.model.ProviderReliability]); off by default.
     */
    val showUnreliable: Pref<Boolean> = store.booleanPref(KEY_SHOW_UNRELIABLE_PROVIDERS, default = false)

    /** Background rate refresh (#151), opt-in. */
    val autoRefresh: Pref<Boolean> = store.booleanPref(KEY_AUTO_REFRESH_ENABLED, default = false)

    /** How often auto-refresh runs; [RefreshInterval.DEFAULT] (an hour) until the user picks one. */
    val autoRefreshInterval: Pref<RefreshInterval> =
        store.intPref(
            KEY_AUTO_REFRESH_INTERVAL,
            decode = RefreshInterval::fromMinutes,
            encode = { it.minutes.toInt() },
        )
}
