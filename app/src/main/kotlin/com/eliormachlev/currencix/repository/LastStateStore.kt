package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.RecentPairs
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.WidgetRefreshBus
import com.eliormachlev.currencix.repository.persistence.prefStore
import com.eliormachlev.currencix.util.toLocalDate
import com.eliormachlev.currencix.util.toMillis
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

// Sentinel for "no historical date stored" in the millis-since-epoch pref.
// -1L is used because it can't collide with any real epoch millis (1970-01-01
// stores as 0L; anything after is positive).
private const val NO_HISTORICAL_DATE = -1L

// LAST_STATE keys.
private const val KEY_LAST_STATE_FROM = "_last_from"
private const val KEY_LAST_STATE_TO = "_last_to"
private const val KEY_HISTORICAL_DATE = "_historical_date"
private const val KEY_RECENT_PAIRS = "_recent_pairs"
private const val DEFAULT_FROM_CURRENCY = "USD"
private const val DEFAULT_TO_CURRENCY = "EUR"

private val historicalLiveDateMapper: (Preferences) -> LocalDate? = { prefs ->
    val v = prefs[longPreferencesKey(KEY_HISTORICAL_DATE)] ?: NO_HISTORICAL_DATE
    if (v == NO_HISTORICAL_DATE) null else v.toLocalDate()
}

/** Where the converter was left: the pair, the recent pairs, and a pinned past date. */
class LastStateStore(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.LAST_STATE.prefStore(context)
    private val recentPairsKey = stringPreferencesKey(KEY_RECENT_PAIRS)

    /*
     * last state ==================================================================================
     */

    fun saveLastUsedRates(
        from: Currency?,
        to: Currency?,
    ) {
        store.edit {
            from?.let { this[stringPreferencesKey(KEY_LAST_STATE_FROM)] = it.iso4217Alpha() }
            to?.let { this[stringPreferencesKey(KEY_LAST_STATE_TO)] = it.iso4217Alpha() }
        }
        WidgetRefreshBus.signal()
    }

    fun getLastBaseCurrency(): LiveData<Currency?> =
        store.mappedLiveData { prefs ->
            Currency.fromString(prefs[stringPreferencesKey(KEY_LAST_STATE_FROM)] ?: DEFAULT_FROM_CURRENCY)
        }

    fun getLastDestinationCurrency(): LiveData<Currency?> =
        store.mappedLiveData { prefs ->
            Currency.fromString(prefs[stringPreferencesKey(KEY_LAST_STATE_TO)] ?: DEFAULT_TO_CURRENCY)
        }

    /** Records [pair] as the most recent one (see [RecentPairs]). */
    fun addRecentPair(pair: CurrencyPair) = editRecentPairs { RecentPairs.push(it, pair) }

    /** Forgets [pair] (either direction); returns the list as it was, for [restoreRecentPairs]. */
    fun removeRecentPair(pair: CurrencyPair): List<CurrencyPair> = removeRecent { RecentPairs.without(it, pair) }

    /** Forgets [currency]: every recent pair that uses it. Returns the list as it was, for [restoreRecentPairs]. */
    fun removeRecentCurrency(currency: Currency): List<CurrencyPair> = removeRecent { RecentPairs.without(it, currency) }

    /** Undoes a removal: puts back [before], the list a remove returned (see [RecentPairs.restore]). */
    fun restoreRecentPairs(before: List<CurrencyPair>) = editRecentPairs { RecentPairs.restore(before, it) }

    private fun removeRecent(change: (List<CurrencyPair>) -> List<CurrencyPair>): List<CurrencyPair> {
        val before = getRecentPairsBlocking()
        editRecentPairs(change)
        return before
    }

    /** The recent pairs as they are now, newest first, read synchronously. */
    fun getRecentPairsBlocking(): List<CurrencyPair> = RecentPairs.decode(store.snapshot()[recentPairsKey])

    private fun editRecentPairs(change: (List<CurrencyPair>) -> List<CurrencyPair>) {
        store.edit { this[recentPairsKey] = RecentPairs.encode(change(RecentPairs.decode(this[recentPairsKey]))) }
    }

    fun getRecentPairsFlow(): Flow<List<CurrencyPair>> = store.mappedFlow { prefs -> RecentPairs.decode(prefs[recentPairsKey]) }

    // Synchronous readers for callers that can't wait for the LiveData to
    // become active (e.g. the cart's initial state, built before any
    // observer is attached).
    fun getLastBaseCurrencyBlocking(): Currency? =
        Currency.fromString(store.snapshot()[stringPreferencesKey(KEY_LAST_STATE_FROM)] ?: DEFAULT_FROM_CURRENCY)

    fun getLastDestinationCurrencyBlocking(): Currency? =
        Currency.fromString(store.snapshot()[stringPreferencesKey(KEY_LAST_STATE_TO)] ?: DEFAULT_TO_CURRENCY)

    fun setHistoricalDate(date: LocalDate?) {
        store.edit { this[longPreferencesKey(KEY_HISTORICAL_DATE)] = date?.toMillis() ?: NO_HISTORICAL_DATE }
    }

    fun getHistoricalLiveDate(): LiveData<LocalDate?> = store.mappedLiveData(historicalLiveDateMapper)

    fun getHistoricalLiveDateFlow(): Flow<LocalDate?> = store.mappedFlow(historicalLiveDateMapper)

    fun getHistoricalLiveDateBlocking(): LocalDate? = historicalLiveDateMapper(store.snapshot())

    fun getHistoricalDate(): LocalDate? =
        when (val v = store.snapshot()[longPreferencesKey(KEY_HISTORICAL_DATE)] ?: NO_HISTORICAL_DATE) {
            NO_HISTORICAL_DATE -> null
            else -> v.toLocalDate()
        }
}
