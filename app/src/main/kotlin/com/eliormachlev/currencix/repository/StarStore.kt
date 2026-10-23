package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.booleanPref
import com.eliormachlev.currencix.repository.persistence.keyedPref
import com.eliormachlev.currencix.repository.persistence.prefStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

// STARRED_CURRENCIES keys.
private const val KEY_STARS_ORDER = "_starsOrder"
private const val KEY_STARRED_ENABLED = "_starredActive"

// Stored as ISO codes, comma-separated, in the user's order.
private const val CODE_SEPARATOR = ","

private fun decodeStars(stored: String?): ImmutableList<Currency> =
    stored
        .orEmpty()
        .split(CODE_SEPARATOR)
        .filter { it.isNotEmpty() }
        .mapNotNull { Currency.fromString(it) }
        .toImmutableList()

private fun encodeStars(currencies: List<Currency>): String = currencies.joinToString(CODE_SEPARATOR) { it.iso4217Alpha() }

/** The starred currencies, in the user's order, and whether the picker shows only them. */
class StarStore(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.STARRED_CURRENCIES.prefStore(context)

    // ImmutableList so Compose stability inference can skip recomposition
    // when the collection identity changes but the content is equal (#161).
    val starredCurrencies: Pref<ImmutableList<Currency>> =
        store.keyedPref(stringPreferencesKey(KEY_STARS_ORDER), ::decodeStars, ::encodeStars)

    /** The currency picker shows only the starred currencies. */
    val filterStarred: Pref<Boolean> = store.booleanPref(KEY_STARRED_ENABLED, default = false)

    /** Stars [currency] (last in the order), or unstars it. */
    fun toggleCurrencyStar(currency: Currency) =
        starredCurrencies.update { stars -> (if (currency in stars) stars - currency else stars + currency).toImmutableList() }

    fun toggleStarredActive() = filterStarred.update { !it }
}
