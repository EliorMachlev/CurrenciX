package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.eliormachlev.currencix.model.ApiProvider
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

// Auto-refresh (#151): default OFF at first launch — opting-in is explicit
// via Settings (until #147 onboarding lands the hero opt-in).
private const val KEY_AUTO_REFRESH_ENABLED = "_autoRefreshEnabled"

// Optional user override for the WorkManager refresh cadence. `null` means
// "use the provider-recommended default"; a positive Int overrides it.
private const val KEY_AUTO_REFRESH_INTERVAL_OVERRIDE = "_autoRefreshIntervalMinutesOverride"

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

    /** Background rate refresh (#151), opt-in. */
    val autoRefresh: Pref<Boolean> = store.booleanPref(KEY_AUTO_REFRESH_ENABLED, default = false)

    /**
     * The user's refresh interval in minutes, or null for the provider's
     * recommended one. Stored only when positive: DataStore has no nullable
     * Int, so an absent key (or an old 0) means "no override".
     */
    val autoRefreshIntervalOverride: Pref<Int?> =
        store.intPref(
            KEY_AUTO_REFRESH_INTERVAL_OVERRIDE,
            decode = { minutes -> minutes?.takeIf { it > 0 } },
            encode = { minutes -> minutes?.takeIf { it > 0 } },
        )
}
