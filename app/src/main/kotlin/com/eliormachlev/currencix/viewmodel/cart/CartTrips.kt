package com.eliormachlev.currencix.viewmodel.cart

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.model.convert
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.newId
import java.math.BigDecimal
import java.math.MathContext

/** A trip with what its carts come to so far, and how many there are. */
data class TripSummary(
    val trip: Trip,
    // In the trip's currency, fees included; null when no cart could be converted.
    val spent: BigDecimal?,
    val cartCount: Int,
)

/**
 * What [cart] comes to in [home]: items plus tip, converted at [rates], with
 * the card fees in force for paying in its currency from home ([feeStack]).
 * Null when a rate is missing.
 */
internal fun cartCostIn(
    cart: SavedCart,
    home: Currency,
    rates: ExchangeRates?,
    feeStack: (Currency, Currency) -> BigDecimal,
): BigDecimal? {
    val base = resolveCurrency(cart.currency)
    val converted = rates?.convert(subtotalOf(cart) + tipOf(cart), base, home) ?: return null
    return converted.multiply(feeStack(base, home), MathContext.DECIMAL128)
}

/** Each of [trips] with its [carts] added up in its own currency. */
internal fun summarizeTrips(
    trips: List<Trip>,
    carts: List<SavedCart>,
    rates: ExchangeRates?,
    feeStack: (Currency, Currency) -> BigDecimal,
): List<TripSummary> =
    trips.map { trip ->
        val own = carts.filter { it.tripId == trip.id }
        val costs = own.mapNotNull { cartCostIn(it, trip.currency, rates, feeStack) }
        TripSummary(trip, costs.takeIf { it.isNotEmpty() }?.reduce(BigDecimal::add), own.size)
    }

/**
 * The trips saved carts are grouped into ([CartViewModel.trips]): each with
 * its spending, and the edits to them.
 */
class CartTrips internal constructor(
    private val db: Database,
    private val ratesCache: CartRatesCache,
) {
    private val summariesLive: LiveData<List<TripSummary>> by lazy {
        MediatorLiveData<List<TripSummary>>().apply {
            val recompute = {
                value =
                    summarizeTrips(
                        trips = db.carts.trips.get(),
                        carts = db.carts.savedCarts.get(),
                        rates = ratesCache.lastRates,
                        feeStack = ratesCache::feeStackFor,
                    )
            }
            addSource(db.carts.trips.liveData) { recompute() }
            addSource(db.carts.savedCarts.liveData) { recompute() }
            addSource(ratesCache.rates) { recompute() }
            addSource(ratesCache.fees) { recompute() }
        }
    }

    /** The trips with their spending, in the order made. */
    fun getSummaries(): LiveData<List<TripSummary>> = summariesLive

    /** Saves a new trip or the edits to one; returns its id. */
    fun save(
        id: String?,
        name: String,
        currency: Currency,
        budget: BigDecimal?,
    ): String {
        val tripId = id ?: newId()
        db.carts.saveTrip(Trip(tripId, name, currency, budget))
        return tripId
    }

    /** Deletes a trip; its carts stay, in no trip. */
    fun delete(id: String) = db.carts.deleteTrip(id)

    /** Puts a saved cart in a trip, or in none. */
    fun moveCart(
        cartId: String,
        tripId: String?,
    ) = db.carts.moveToTrip(cartId, tripId)
}
