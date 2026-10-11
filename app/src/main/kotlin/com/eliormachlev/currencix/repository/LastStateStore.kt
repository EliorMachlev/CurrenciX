package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.RecentPairs
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.WidgetRefreshBus
import com.eliormachlev.currencix.repository.persistence.keyedPref
import com.eliormachlev.currencix.repository.persistence.prefStore
import com.eliormachlev.currencix.util.toLocalDate
import com.eliormachlev.currencix.util.toMillis
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

/** Where the converter was left: the pair, the recent pairs, and a pinned past date. */
class LastStateStore(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.LAST_STATE.prefStore(context)

    // The converter's pair, by ISO code; the widget follows it, so it redraws on a change.
    private fun currencyPref(
        name: String,
        default: Currency,
    ): Pref<Currency?> =
        store.keyedPref(
            stringPreferencesKey(name),
            decode = { Currency.fromString(it ?: default.iso4217Alpha()) },
            encode = { it?.iso4217Alpha() },
            afterWrite = WidgetRefreshBus::signal,
        )

    /** The converter's "from" currency (USD before the first conversion). */
    val lastBaseCurrency: Pref<Currency?> = currencyPref(KEY_LAST_STATE_FROM, Currency.USD)

    /** The converter's "to" currency (EUR before the first conversion). */
    val lastDestinationCurrency: Pref<Currency?> = currencyPref(KEY_LAST_STATE_TO, Currency.EUR)

    /** The recent pairs, newest first (see [RecentPairs]). */
    val recentPairs: Pref<List<CurrencyPair>> =
        store.keyedPref(stringPreferencesKey(KEY_RECENT_PAIRS), RecentPairs::decode, RecentPairs::encode)

    /** The past date the converter is pinned to, or null for today's rates. */
    val historicalDate: Pref<LocalDate?> =
        store.keyedPref(
            longPreferencesKey(KEY_HISTORICAL_DATE),
            decode = { millis -> millis?.takeIf { it != NO_HISTORICAL_DATE }?.toLocalDate() },
            encode = { date -> date?.toMillis() ?: NO_HISTORICAL_DATE },
        )

    /** Remembers the converter's pair; a null side keeps what was stored. */
    fun saveLastUsedRates(
        from: Currency?,
        to: Currency?,
    ) {
        from?.let(lastBaseCurrency::set)
        to?.let(lastDestinationCurrency::set)
    }

    /** Records [pair] as the most recent one (see [RecentPairs]). */
    fun addRecentPair(pair: CurrencyPair) = recentPairs.update { RecentPairs.push(it, pair) }

    /** Forgets [pair] (either direction); returns the list as it was, for [restoreRecentPairs]. */
    fun removeRecentPair(pair: CurrencyPair): List<CurrencyPair> = removeRecent { RecentPairs.without(it, pair) }

    /** Forgets [currency]: every recent pair that uses it. Returns the list as it was, for [restoreRecentPairs]. */
    fun removeRecentCurrency(currency: Currency): List<CurrencyPair> = removeRecent { RecentPairs.without(it, currency) }

    /** Undoes a removal: puts back [before], the list a remove returned (see [RecentPairs.restore]). */
    fun restoreRecentPairs(before: List<CurrencyPair>) = recentPairs.update { RecentPairs.restore(before, it) }

    private fun removeRecent(change: (List<CurrencyPair>) -> List<CurrencyPair>): List<CurrencyPair> {
        val before = recentPairs.get()
        recentPairs.update(change)
        return before
    }
}
