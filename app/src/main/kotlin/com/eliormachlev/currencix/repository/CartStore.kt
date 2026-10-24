package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.keyedPref
import com.eliormachlev.currencix.repository.persistence.prefStore

private const val KEY_CART_CURRENT_JSON = "_cart_current_json"
private const val KEY_CARTS_SAVED_JSON = "_carts_saved_json"
private const val KEY_TRIPS_JSON = "_trips_json"

/** The working cart and the carts saved under a name. */
class CartStore(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    /** The cart being edited; null (and removed) when there is none. */
    val currentCart: Pref<SavedCart?> =
        store.keyedPref(stringPreferencesKey(KEY_CART_CURRENT_JSON), ::parseCart, { it?.let(::serializeCartText) })

    val savedCarts: Pref<List<SavedCart>> =
        store.keyedPref(stringPreferencesKey(KEY_CARTS_SAVED_JSON), ::parseCartList, ::serializeCartList)

    /**
     * Saves [cart] over the saved cart with its id, or as a new one. The
     * saved one's trip stands: it's set from the saved-carts list, which the
     * working cart doesn't see.
     */
    fun saveCart(cart: SavedCart) =
        savedCarts.update { list ->
            val trip = list.firstOrNull { it.id == cart.id }?.tripId ?: cart.tripId
            list.replacingOrAppending(cart.copy(tripId = trip)) { saved -> saved.id == cart.id }
        }

    fun deleteSavedCart(id: String) = savedCarts.update { list -> list.filterNot { it.id == id } }

    /** The trips saved carts can be grouped into. */
    val trips: Pref<List<Trip>> =
        store.keyedPref(
            stringPreferencesKey(KEY_TRIPS_JSON),
            ::parseTrips,
            { list -> list.takeIf { it.isNotEmpty() }?.let(::serializeTrips) },
        )

    /** Saves [trip] over the trip with its id, or as a new one. */
    fun saveTrip(trip: Trip) = trips.update { it.replacingOrAppending(trip) { saved -> saved.id == trip.id } }

    /** Deletes trip [id]; its carts stay, in no trip. */
    fun deleteTrip(id: String) {
        trips.update { list -> list.filterNot { it.id == id } }
        savedCarts.update { list -> list.map { if (it.tripId == id) it.copy(tripId = null) else it } }
    }

    /** Puts saved cart [cartId] in trip [tripId], or in none (null). */
    fun moveToTrip(
        cartId: String,
        tripId: String?,
    ) = savedCarts.update { list -> list.map { if (it.id == cartId) it.copy(tripId = tripId) else it } }
}
