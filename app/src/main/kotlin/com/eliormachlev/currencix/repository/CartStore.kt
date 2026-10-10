package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.prefStore

private const val KEY_CART_CURRENT_JSON = "_cart_current_json"
private const val KEY_CARTS_SAVED_JSON = "_carts_saved_json"

/** The working cart and the carts saved under a name. */
class CartStore(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    // cart ==================================================================================

    fun getCurrentCart(): LiveData<SavedCart?> = store.mappedLiveData { parseCart(it[stringPreferencesKey(KEY_CART_CURRENT_JSON)]) }

    fun getCurrentCartBlocking(): SavedCart? = parseCart(store.snapshot()[stringPreferencesKey(KEY_CART_CURRENT_JSON)])

    fun setCurrentCart(cart: SavedCart?) {
        store.edit {
            if (cart == null) {
                remove(stringPreferencesKey(KEY_CART_CURRENT_JSON))
            } else {
                this[stringPreferencesKey(KEY_CART_CURRENT_JSON)] = serializeCart(cart).toString()
            }
        }
    }

    fun getSavedCarts(): LiveData<List<SavedCart>> =
        store.mappedLiveData { parseCartList(it[stringPreferencesKey(KEY_CARTS_SAVED_JSON)] ?: "[]") }

    fun getSavedCartsBlocking(): List<SavedCart> = parseCartList(store.snapshot()[stringPreferencesKey(KEY_CARTS_SAVED_JSON)] ?: "[]")

    fun saveCart(cart: SavedCart) {
        val existing = getSavedCartsBlocking()
        val next =
            if (existing.any { it.id == cart.id }) {
                existing.map { if (it.id == cart.id) cart else it }
            } else {
                existing + cart
            }
        writeSavedCarts(next)
    }

    fun deleteSavedCart(id: String) {
        writeSavedCarts(getSavedCartsBlocking().filter { it.id != id })
    }

    private fun writeSavedCarts(list: List<SavedCart>) {
        store.edit { this[stringPreferencesKey(KEY_CARTS_SAVED_JSON)] = serializeCartList(list) }
    }
}
