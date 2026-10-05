package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.WidgetRefreshBus
import com.eliormachlev.currencix.repository.persistence.prefStore
import com.eliormachlev.currencix.util.KEY_RATES_BASE
import com.eliormachlev.currencix.util.KEY_RATES_DATE
import com.eliormachlev.currencix.util.KEY_RATES_FALLBACK_FROM
import com.eliormachlev.currencix.util.KEY_RATES_PROVIDER
import com.eliormachlev.currencix.util.KEY_RATES_TIME
import com.eliormachlev.currencix.util.NO_PROVIDER_ID
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime

// Metadata keys inside the RATES namespace start with "_" to distinguish them
// from currency-code entries (e.g. "USD", "EUR"). Matches the legacy
// SharedPreferences layout so BackupManager exports keep round-tripping.
private const val METADATA_KEY_PREFIX = "_"

/** Cached exchange rates: the latest set, and each pair's history. */
class RateStore(
    context: Context,
) {
    private val ratesStore: PrefStore = PersistenceKey.RATES.prefStore(context)
    private val timelinesStore: PrefStore = PersistenceKey.TIMELINES.prefStore(context)

    /*
     * current exchange rates from api =============================================================
     */

    fun insertExchangeRates(items: ExchangeRates) {
        // don't insert null-values. this would clear the cache
        val date = items.date ?: return
        ratesStore.edit {
            clear()
            this[stringPreferencesKey(KEY_RATES_DATE)] = date.toString()
            items.time?.let { this[stringPreferencesKey(KEY_RATES_TIME)] = it.toString() }
            items.base?.let { this[stringPreferencesKey(KEY_RATES_BASE)] = it.iso4217Alpha() }
            this[intPreferencesKey(KEY_RATES_PROVIDER)] = items.provider?.id ?: NO_PROVIDER_ID
            items.fallbackFrom?.let { this[intPreferencesKey(KEY_RATES_FALLBACK_FROM)] = it.id }
            items.rates?.forEach { rate ->
                this[stringPreferencesKey(rate.currency.iso4217Alpha())] = rate.value.toPlainString()
            }
        }
        WidgetRefreshBus.signal()
    }

    fun getExchangeRates(): LiveData<ExchangeRates?> = ratesStore.mappedLiveData(::parseExchangeRates)

    fun getExchangeRatesBlocking(): ExchangeRates? = parseExchangeRates(ratesStore.snapshot())

    /**
     * Non-null accessor for the currently-cached rate list. Returns an empty
     * list when no rates have been loaded yet (clean install, before the first
     * refresh) so callers can skip null-navigation.
     */
    fun getRateListBlocking(): List<Rate> = getExchangeRatesBlocking()?.rates.orEmpty()

    fun getDate(): LocalDate? = ratesStore.snapshot()[stringPreferencesKey(KEY_RATES_DATE)]?.let { LocalDate.parse(it) }

    private fun parseExchangeRates(prefs: Preferences): ExchangeRates? {
        val baseString = prefs[stringPreferencesKey(KEY_RATES_BASE)] ?: return null
        val dateString = prefs[stringPreferencesKey(KEY_RATES_DATE)] ?: return null
        val rates =
            prefs
                .asMap()
                .entries
                .filter { (k, _) -> !k.name.startsWith(METADATA_KEY_PREFIX) }
                .sortedBy { (k, _) -> k.name }
                .mapNotNull { (k, v) ->
                    val str = v as? String ?: return@mapNotNull null
                    Currency.fromString(k.name)?.let { Rate(it, str.toBigDecimal()) }
                }
        if (rates.isEmpty()) return null
        return ExchangeRates(
            success = true,
            error = null,
            base = Currency.fromString(baseString),
            date = LocalDate.parse(dateString),
            time = prefs[stringPreferencesKey(KEY_RATES_TIME)]?.let { LocalTime.parse(it) },
            rates = rates,
            provider = ApiProvider.fromId(prefs[intPreferencesKey(KEY_RATES_PROVIDER)] ?: NO_PROVIDER_ID),
            fallbackFrom = prefs[intPreferencesKey(KEY_RATES_FALLBACK_FROM)]?.let(ApiProvider::fromId),
        )
    }

    /*
     * cached timelines ============================================================================
     *
     * Historical rate observations are immutable once a business day closes,
     * so we persist per-pair timelines and refresh only the tail on each open.
     * Keyed by "<providerId>|<baseCode>|<symbolCode>"; the JSON value is a
     * flat {date -> plainString value} map (provider/base/symbol are already
     * in the key).
     */

    private fun timelineKey(
        providerId: Int,
        base: Currency,
        symbol: Currency,
    ): String = "$providerId|${base.iso4217Alpha()}|${symbol.iso4217Alpha()}"

    fun getCachedTimeline(
        provider: ApiProvider,
        base: Currency,
        symbol: Currency,
    ): Timeline? {
        val json =
            timelinesStore.snapshot()[stringPreferencesKey(timelineKey(provider.id, base, symbol))]
                ?: return null
        return try {
            val obj = JSONObject(json)
            val rates = sortedMapOf<LocalDate, Rate>()
            obj.keys().forEach { key ->
                val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@forEach
                val value = obj.optString(key).toBigDecimalOrNull() ?: return@forEach
                rates[date] = Rate(symbol, value)
            }
            if (rates.isEmpty()) return null
            Timeline(
                success = true,
                error = null,
                base = base.iso4217Alpha(),
                startDate = rates.keys.first(),
                endDate = rates.keys.last(),
                rates = rates,
                provider = provider,
            )
        } catch (e: JSONException) {
            Timber.tag("Database").w(e, "Malformed cached timeline, dropping")
            null
        }
    }

    fun putCachedTimeline(
        timeline: Timeline,
        base: Currency,
        symbol: Currency,
    ) {
        val provider = timeline.provider ?: return
        val rates = timeline.rates ?: return
        val obj = JSONObject()
        rates.forEach { (date, rate) ->
            obj.put(date.toString(), rate.value.toPlainString())
        }
        timelinesStore.edit {
            this[stringPreferencesKey(timelineKey(provider.id, base, symbol))] = obj.toString()
        }
    }
}
