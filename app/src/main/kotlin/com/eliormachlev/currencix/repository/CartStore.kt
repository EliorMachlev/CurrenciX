package com.eliormachlev.currencix.repository

import android.content.Context
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.proto.CartsData
import com.eliormachlev.currencix.repository.persistence.CachedStore
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.cartsStore
import com.eliormachlev.currencix.repository.persistence.fieldPref

/** The working cart and the carts saved under a name (Protobuf, in carts.pb). */
class CartStore(
    context: Context,
) {
    private val store: CachedStore<CartsData> = context.cartsStore()

    /** The cart being edited; null (and removed) when there is none. */
    val currentCart: Pref<SavedCart?> =
        store.fieldPref({ it.current }, { it?.toModel() }, { data, cart -> data.copy(current = cart?.toProto()) })

    val savedCarts: Pref<List<SavedCart>> =
        store.fieldPref({ it.saved }, { list -> list.map { it.toModel() } }, { data, list -> data.copy(saved = list.map { it.toProto() }) })

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
        store.fieldPref(
            { it.trips },
            { list -> list.mapNotNull { it.toModel() } },
            { data, list -> data.copy(trips = list.map { it.toProto() }) },
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
