package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.prefStore
import com.eliormachlev.currencix.util.NO_PROVIDER_ID
import kotlinx.coroutines.flow.Flow

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

// Mappers shared by the LiveData and Flow getters for each preference so both
// paths derive from a single source of truth (see #149 pilot).
private val apiProviderMapper: (Preferences) -> ApiProvider = {
    ApiProvider.fromId(it[intPreferencesKey(KEY_API)] ?: NO_PROVIDER_ID)
}

private val openExchangeratesApiKeyMapper: (Preferences) -> String? = {
    it[stringPreferencesKey(KEY_OPEN_EXCHANGERATES_API_KEY)]
}

private val autoRefreshEnabledMapper: (Preferences) -> Boolean = {
    it[booleanPreferencesKey(KEY_AUTO_REFRESH_ENABLED)] ?: false
}

private val autoRefreshIntervalOverrideMapper: (Preferences) -> Int? = {
    // 0 sentinel means "no override" — DataStore has no `intOrNull` primitive
    // and we want the pref backing store to keep working with plain Ints.
    val v = it[intPreferencesKey(KEY_AUTO_REFRESH_INTERVAL_OVERRIDE)] ?: 0
    if (v <= 0) null else v
}

/** Which rate provider to use, its fallback and API key, and background refresh. */
class ProviderSettings(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    // api

    fun setApiProvider(api: ApiProvider) {
        store.edit { this[intPreferencesKey(KEY_API)] = api.id }
    }

    fun getApiProvider(): ApiProvider = ApiProvider.fromId(store.snapshot()[intPreferencesKey(KEY_API)] ?: NO_PROVIDER_ID)

    // Fallback provider: fetched when the main one fails. Stored as chosen;
    // resolved against the current main provider on read, so a choice that
    // became the main provider falls back to the default instead.

    fun setFallbackProvider(api: ApiProvider) {
        store.edit { this[intPreferencesKey(KEY_FALLBACK_API)] = api.id }
    }

    fun getFallbackProvider(): ApiProvider = resolveFallback(store.snapshot())

    fun getFallbackProviderFlow(): Flow<ApiProvider> = store.mappedFlow(::resolveFallback)

    private fun resolveFallback(prefs: Preferences): ApiProvider {
        val main = apiProviderMapper(prefs)
        val chosen = prefs[intPreferencesKey(KEY_FALLBACK_API)]?.let(ApiProvider::fromId)
        return chosen?.takeIf { it != main } ?: ApiProvider.defaultFallback(main)
    }

    fun getApiProviderAsync(): LiveData<ApiProvider> = store.mappedLiveData(apiProviderMapper)

    fun getApiProviderFlow(): Flow<ApiProvider> = store.mappedFlow(apiProviderMapper)

    fun setOpenExchangeRatesApiKey(id: String?) {
        store.edit {
            if (id == null) {
                remove(stringPreferencesKey(KEY_OPEN_EXCHANGERATES_API_KEY))
            } else {
                this[stringPreferencesKey(KEY_OPEN_EXCHANGERATES_API_KEY)] = id
            }
        }
    }

    fun getOpenExchangeRatesApiKey(): String? = store.snapshot()[stringPreferencesKey(KEY_OPEN_EXCHANGERATES_API_KEY)]

    fun getOpenExchangeRatesApiKeyAsync(): LiveData<String?> = store.mappedLiveData(openExchangeratesApiKeyMapper)

    fun getOpenExchangeRatesApiKeyFlow(): Flow<String?> = store.mappedFlow(openExchangeratesApiKeyMapper)

    // auto-refresh (#151) — WorkManager-driven background rate refresh, opt-in.

    fun setAutoRefreshEnabled(enabled: Boolean) {
        store.edit { this[booleanPreferencesKey(KEY_AUTO_REFRESH_ENABLED)] = enabled }
    }

    fun isAutoRefreshEnabledFlow(): Flow<Boolean> = store.mappedFlow(autoRefreshEnabledMapper)

    fun isAutoRefreshEnabledBlocking(): Boolean = autoRefreshEnabledMapper(store.snapshot())

    fun setAutoRefreshIntervalMinutesOverride(minutes: Int?) {
        store.edit {
            if (minutes == null || minutes <= 0) {
                remove(intPreferencesKey(KEY_AUTO_REFRESH_INTERVAL_OVERRIDE))
            } else {
                this[intPreferencesKey(KEY_AUTO_REFRESH_INTERVAL_OVERRIDE)] = minutes
            }
        }
    }

    fun getAutoRefreshIntervalMinutesOverrideFlow(): Flow<Int?> = store.mappedFlow(autoRefreshIntervalOverrideMapper)

    fun getAutoRefreshIntervalMinutesOverrideBlocking(): Int? = autoRefreshIntervalOverrideMapper(store.snapshot())
}
