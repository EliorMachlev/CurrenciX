package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.prefStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow

// STARRED_CURRENCIES keys.
private const val KEY_STARS_ORDER = "_starsOrder"
private const val KEY_STARRED_ENABLED = "_starredActive"

private val starredCurrenciesMapper: (Preferences) -> ImmutableList<Currency> = { prefs ->
    readOrderedStarCodes(prefs).mapNotNull { Currency.fromString(it) }.toImmutableList()
}

private val filterStarredEnabledMapper: (Preferences) -> Boolean = {
    it[booleanPreferencesKey(KEY_STARRED_ENABLED)] ?: false
}

// Ordered-star-codes parsing lives at file scope so the mapper above (invoked
// by both LiveData and Flow getters) can share it with the toggle/reorder
// mutators without threading a Database instance through.
private fun readOrderedStarCodes(prefs: Preferences): List<String> {
    val stored = prefs[stringPreferencesKey(KEY_STARS_ORDER)] ?: return emptyList()
    return if (stored.isEmpty()) emptyList() else stored.split(",")
}

/** The starred currencies, in the user's order, and whether the picker shows only them. */
class StarStore(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.STARRED_CURRENCIES.prefStore(context)

    /*
     * starred currencies ==========================================================================
     */

    private fun writeOrderedStarCodes(codes: List<String>) {
        store.edit { this[stringPreferencesKey(KEY_STARS_ORDER)] = codes.joinToString(",") }
    }

    fun toggleCurrencyStar(currency: Currency) {
        val code = currency.iso4217Alpha()
        val current = readOrderedStarCodes(store.snapshot())
        val next = if (current.contains(code)) current.minus(code) else current.plus(code)
        writeOrderedStarCodes(next)
    }

    // ImmutableList so Compose stability inference can skip recomposition
    // when the collection identity changes but the content is equal (#161).
    fun getStarredCurrencies(): LiveData<ImmutableList<Currency>> = store.mappedLiveData(starredCurrenciesMapper)

    fun getStarredCurrenciesFlow(): Flow<ImmutableList<Currency>> = store.mappedFlow(starredCurrenciesMapper)

    fun getStarredCurrenciesBlocking(): ImmutableList<Currency> = starredCurrenciesMapper(store.snapshot())

    fun setStarredCurrencyOrder(currencies: List<Currency>) {
        writeOrderedStarCodes(currencies.map { it.iso4217Alpha() })
    }

    fun isFilterStarredEnabled(): LiveData<Boolean> = store.mappedLiveData(filterStarredEnabledMapper)

    fun isFilterStarredEnabledFlow(): Flow<Boolean> = store.mappedFlow(filterStarredEnabledMapper)

    fun isFilterStarredEnabledBlocking(): Boolean = filterStarredEnabledMapper(store.snapshot())

    fun toggleStarredActive() {
        store.edit {
            val current = this[booleanPreferencesKey(KEY_STARRED_ENABLED)] ?: false
            this[booleanPreferencesKey(KEY_STARRED_ENABLED)] = !current
        }
    }
}
