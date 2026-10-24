package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.decodeQuickAmounts
import com.eliormachlev.currencix.model.encodeQuickAmounts
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.keyedPref
import com.eliormachlev.currencix.repository.persistence.prefStore
import java.math.BigDecimal

private const val KEY_QUICK_AMOUNTS = "_quick_amounts"

/**
 * The quick conversions' amounts the user set for a currency (a tile's
 * long-press → Change amount), in place of the round ones scaled to it.
 * Kept with the settings, so backups carry them.
 */
class QuickAmountSettings(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    /** The user's own amounts by currency; a currency missing here uses the automatic ones. */
    val custom: Pref<Map<Currency, List<BigDecimal>>> =
        store.keyedPref(stringPreferencesKey(KEY_QUICK_AMOUNTS), ::decodeQuickAmounts, ::encodeQuickAmounts)
}
