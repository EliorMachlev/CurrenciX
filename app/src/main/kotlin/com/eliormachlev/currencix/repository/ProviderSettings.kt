package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.ApiSecrets
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
private const val KEY_ALL_RATES_TODAY_API_KEY = "_api_allRatesTodayApiKey"

// Whether the pickers list providers that are often down or inaccurate.
private const val KEY_SHOW_UNRELIABLE_PROVIDERS = "_showUnreliableProviders"

// Auto-refresh (#151): default OFF at first launch — opting-in is explicit
// via Settings (until #147 onboarding lands the hero opt-in).
private const val KEY_AUTO_REFRESH_ENABLED = "_autoRefreshEnabled"

// How often auto-refresh runs, in minutes (RefreshInterval.minutes); absent
// means the default. The name predates the setting: it's stored, so it stays.
private const val KEY_AUTO_REFRESH_INTERVAL = "_autoRefreshIntervalMinutesOverride"

private val apiProviderKey = intPreferencesKey(KEY_API)

// Where each keyed provider's API key is stored.
private val apiKeyKeys: Map<ApiProvider, Preferences.Key<String>> =
    mapOf(
        ApiProvider.OPEN_EXCHANGERATES to stringPreferencesKey(KEY_OPEN_EXCHANGERATES_API_KEY),
        ApiProvider.ALL_RATES_TODAY to stringPreferencesKey(KEY_ALL_RATES_TODAY_API_KEY),
    )
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

    /** The user's AllRatesToday key; null (and removed) when there is none. */
    val allRatesTodayApiKey: Pref<String?> = store.optionalStringPref(KEY_ALL_RATES_TODAY_API_KEY)

    /**
     * The main provider's own API key: null for one that needs none or has
     * none yet. Written to that provider's key, removed when blank, so
     * switching provider shows (and keeps) each one's key.
     */
    val mainProviderApiKey: Pref<String?> =
        store.pref(
            read = { prefs -> apiKeyKeys[mainProvider(prefs)]?.let { prefs[it] } },
            write = { key ->
                apiKeyKeys[mainProvider(this)]?.let { stored ->
                    if (key.isNullOrBlank()) remove(stored) else this[stored] = key
                }
            },
        )

    /** Every provider's key as it is now, for a fetch to hand its provider. */
    fun secrets(): ApiSecrets =
        ApiSecrets(
            openExchangeRatesApiKey = openExchangeRatesApiKey.get(),
            allRatesTodayApiKey = allRatesTodayApiKey.get(),
        )

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
