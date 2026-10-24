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
import com.eliormachlev.currencix.repository.persistence.LastResult
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.WidgetRefreshBus
import com.eliormachlev.currencix.repository.persistence.prefStore
import com.eliormachlev.currencix.util.DroppedCacheEntries
import com.eliormachlev.currencix.util.KEY_RATES_BASE
import com.eliormachlev.currencix.util.KEY_RATES_DATE
import com.eliormachlev.currencix.util.KEY_RATES_FALLBACK_FROM
import com.eliormachlev.currencix.util.KEY_RATES_PROVIDER
import com.eliormachlev.currencix.util.KEY_RATES_TIME
import com.eliormachlev.currencix.util.NO_PROVIDER_ID
import java.time.LocalDate
import java.time.LocalTime

// Metadata keys inside the RATES namespace start with "_" to distinguish them
// from currency-code entries (e.g. "USD", "EUR"). Matches the legacy
// SharedPreferences layout so BackupManager exports keep round-tripping.
private const val METADATA_KEY_PREFIX = "_"
private const val TAG = "RateStore"

/** Cached exchange rates: the latest set, and each pair's history. */
class RateStore(
    context: Context,
) {
    private val ratesStore: PrefStore = PersistenceKey.RATES.prefStore(context)
    private val timelines = TimelineFiles.get(context)

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
        // Parse the new set here, on the refresh's background thread, so the
        // screens observing it find it already parsed.
        parsedRates(ratesStore.snapshot())
        WidgetRefreshBus.signal()
    }

    fun getExchangeRates(): LiveData<ExchangeRates?> = ratesStore.mappedLiveData(parsedRates::invoke)

    fun getExchangeRatesBlocking(): ExchangeRates? = parsedRates(ratesStore.snapshot())

    /**
     * Non-null accessor for the currently-cached rate list. Returns an empty
     * list when no rates have been loaded yet (clean install, before the first
     * refresh) so callers can skip null-navigation.
     */
    fun getRateListBlocking(): List<Rate> = getExchangeRatesBlocking()?.rates.orEmpty()

    fun getDate(): LocalDate? = ratesStore.snapshot()[stringPreferencesKey(KEY_RATES_DATE)]?.let { LocalDate.parse(it) }

    /*
     * cached timelines ============================================================================
     *
     * Historical rate observations are immutable once a business day closes,
     * so we persist per-pair timelines and refresh only the tail on each open,
     * one file per provider and pair ([TimelineFiles]). They're read from
     * disk: call these off the main thread.
     */

    fun getCachedTimeline(
        provider: ApiProvider,
        base: Currency,
        symbol: Currency,
    ): Timeline? = timelines.read(provider, base, symbol)

    fun putCachedTimeline(
        timeline: Timeline,
        base: Currency,
        symbol: Currency,
    ) = timelines.write(timeline, base, symbol)

    private companion object {
        // The cached set, parsed once per change of the store (an equal
        // snapshot until a refresh writes a new set), for every
        // observer and every RateStore: each screen and widget used to parse
        // the ~170 rates again on the main thread for each emission.
        val parsedRates = LastResult<Preferences, ExchangeRates?>(same = { a, b -> a === b || a == b }, compute = ::parseExchangeRates)

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
                        val currency = Currency.fromString(k.name) ?: return@mapNotNull null
                        // A rate that doesn't parse is skipped (and counted), not a crash.
                        val value = (v as? String)?.toBigDecimalOrNull() ?: return@mapNotNull null.also { DroppedCacheEntries.record(TAG) }
                        Rate(currency, value)
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
    }
}
