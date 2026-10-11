package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.keyedPref
import com.eliormachlev.currencix.repository.persistence.prefStore

private const val KEY_CART_CURRENT_JSON = "_cart_current_json"
private const val KEY_CARTS_SAVED_JSON = "_carts_saved_json"

/** The working cart and the carts saved under a name. */
class CartStore(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    /** The cart being edited; null (and removed) when there is none. */
    val currentCart: Pref<SavedCart?> =
        store.keyedPref(stringPreferencesKey(KEY_CART_CURRENT_JSON), ::parseCart, { it?.let(::serializeCart)?.toString() })

    val savedCarts: Pref<List<SavedCart>> =
        store.keyedPref(stringPreferencesKey(KEY_CARTS_SAVED_JSON), ::parseCartList, ::serializeCartList)

    /** Saves [cart] over the saved cart with its id, or as a new one. */
    fun saveCart(cart: SavedCart) = savedCarts.update { it.replacingOrAppending(cart) { saved -> saved.id == cart.id } }

    fun deleteSavedCart(id: String) = savedCarts.update { list -> list.filterNot { it.id == id } }
}
