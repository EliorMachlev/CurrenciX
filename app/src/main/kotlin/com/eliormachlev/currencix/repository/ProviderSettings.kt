package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.ApiSecrets
import com.eliormachlev.currencix.model.RefreshInterval
import com.eliormachlev.currencix.repository.persistence.LastResult
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
// means the default.
private const val KEY_AUTO_REFRESH_INTERVAL = "_autoRefreshIntervalMinutes"

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

/** The names API keys are stored under, for a backup to treat them apart. */
internal val API_KEY_PREF_NAMES: Set<String> = apiKeyKeys.values.mapTo(mutableSetOf()) { it.name }

/**
 * Which rate provider to use, its fallback and API key, and background
 * refresh. API keys are stored sealed by [secrets] (the Android Keystore).
 */
class ProviderSettings(
    context: Context,
    secrets: SecretBox = KeystoreSecretBox,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    val apiProvider: Pref<ApiProvider> = store.pref(::mainProvider, write = { this[apiProviderKey] = it.id })

    /**
     * Fetched when the main provider fails. Stored as chosen; resolved against
     * the current main provider on read, so a choice that became the main
     * provider falls back to the default instead.
     */
    val fallbackProvider: Pref<ApiProvider> = store.pref(::resolveFallback, write = { this[fallbackApiKey] = it.id })

    /**
     * The API key the user entered for each keyed provider, each stored on
     * its own, so the main and the fallback provider can both have one.
     */
    val apiKeys: Pref<Map<ApiProvider, String>> =
        store.pref(
            read = { prefs -> openKeys(apiKeyKeys.mapNotNull { (provider, key) -> prefs[key]?.let { provider to it } }.toMap()) },
            write = { keys ->
                apiKeyKeys.forEach { (provider, key) ->
                    val value = keys[provider]
                    if (value.isNullOrBlank()) remove(key) else this[key] = secrets.seal(value)
                }
            },
        )

    // Opened again only when what's stored changes, not on every write to the settings.
    private val openKeys =
        LastResult<Map<ApiProvider, String>, Map<ApiProvider, String>>({ a, b -> a == b }) { stored ->
            stored.mapNotNull { (provider, value) -> secrets.open(value)?.let { provider to it } }.toMap()
        }

    /** Stores [key] as [provider]'s API key; a blank one removes it. */
    fun setApiKey(
        provider: ApiProvider,
        key: String,
    ) = apiKeys.update { keys -> if (key.isBlank()) keys - provider else keys + (provider to key) }

    /** Every provider's key as it is now, for a fetch to hand its provider. */
    fun secrets(): ApiSecrets {
        val keys = apiKeys.get()
        return ApiSecrets(
            openExchangeRatesApiKey = keys[ApiProvider.OPEN_EXCHANGERATES],
            allRatesTodayApiKey = keys[ApiProvider.ALL_RATES_TODAY],
        )
    }

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
