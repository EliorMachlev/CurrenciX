package com.eliormachlev.currencix.viewmodel.cart

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.SpentPin
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.model.convert
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.withPlan
import com.eliormachlev.currencix.util.newId
import com.eliormachlev.currencix.util.todayEachDay
import com.eliormachlev.currencix.viewmodel.util.stateInWhileSubscribed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/** What the trip sheet saves: everything about a trip but its id. */
data class TripDraft(
    val name: String,
    val currency: Currency,
    val budget: BigDecimal? = null,
    val destination: Currency? = null,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
)

/** A trip with what its carts come to so far, and how many there are. */
data class TripSummary(
    val trip: Trip,
    // In the trip's currency, fees included; null when no cart could be converted.
    // An ended trip shows its pinned amount, not today's rates.
    val spent: BigDecimal?,
    val cartCount: Int,
    // For an ended trip not pinned yet (or whose carts changed since): the pin to save.
    val toPin: SpentPin? = null,
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

/**
 * A fingerprint of what [carts] come to in their own currencies: when a cart
 * is added, removed or edited, it changes, and a pin made before no longer holds.
 */
internal fun spendBasis(carts: List<SavedCart>): Int =
    carts
        .sortedBy { it.id }
        .map { listOf(it.id, it.currency, (subtotalOf(it) + tipOf(it)).stripTrailingZeros().toPlainString()) }
        .hashCode()

/**
 * Each of [trips] with its [carts] added up in its own currency. A trip still
 * on (or with no end) follows the current [rates] and fees; one that ended
 * before [today] keeps its pinned amount, as long as its carts are the ones
 * it was pinned for, and otherwise offers a new pin ([TripSummary.toPin]).
 */
internal fun summarizeTrips(
    trips: List<Trip>,
    carts: List<SavedCart>,
    rates: ExchangeRates?,
    today: LocalDate = LocalDate.now(),
    feeStack: (Currency, Currency) -> BigDecimal,
): List<TripSummary> =
    trips.map { trip ->
        val own = carts.filter { it.tripId == trip.id }
        val basis = spendBasis(own)
        val pinned = trip.spentPin?.takeIf { it.basis == basis }
        if (trip.hasEnded(today) && pinned != null) return@map TripSummary(trip, pinned.amount, own.size)
        val costs = own.mapNotNull { cartCostIn(it, trip.currency, rates, feeStack) }
        val spent = costs.takeIf { it.isNotEmpty() }?.reduce(BigDecimal::add)
        val toPin = if (trip.hasEnded(today) && spent != null) SpentPin(spent, basis) else null
        TripSummary(trip, spent, own.size, toPin)
    }

/**
 * The trips saved carts are grouped into ([CartViewModel.trips]): each with
 * its spending, and the edits to them.
 */
class CartTrips internal constructor(
    private val db: Database,
    private val ratesCache: CartRatesCache,
    private val scope: CoroutineScope,
) {
    // The rates, once the fees and the chosen exchange and card are in step
    // with them: the fee stack reads all of those.
    private val feeAwareRates: Flow<ExchangeRates?> by lazy {
        combine(ratesCache.rates, ratesCache.fees, ratesCache.activeExchangeId, ratesCache.activeBankId) { rates, _, _, _ -> rates }
    }

    // Redone when the trips, the saved carts, the rates, the fees or the day
    // change (a trip that ended overnight gets pinned). Empty until first
    // observed.
    private val summariesState: StateFlow<List<TripSummary>> by lazy {
        combine(db.carts.trips.flow, db.carts.savedCarts.flow, feeAwareRates, todayEachDay()) { trips, carts, rates, today ->
            summarizeTrips(trips = trips, carts = carts, rates = rates, feeStack = ratesCache::feeStackFor, today = today)
        }.onEach(::pinEndedTrips)
            .stateInWhileSubscribed(scope, emptyList())
    }

    // Saves the amount of each trip that has just ended; the trips list then
    // changes and the summaries are worked out again, now from the pins.
    private fun pinEndedTrips(summaries: List<TripSummary>) {
        summaries.forEach { summary -> summary.toPin?.let { db.carts.saveTrip(summary.trip.copy(spentPin = it)) } }
    }

    /** The trips with their spending, in the order made. */
    fun getSummaries(): StateFlow<List<TripSummary>> = summariesState

    /** Saves a new trip or the edits to one; returns its id. */
    fun save(
        id: String?,
        draft: TripDraft,
    ): String {
        val tripId = id ?: newId()
        db.carts.saveTrip(
            Trip(tripId, draft.name, draft.currency, draft.budget)
                .withPlan(draft.destination?.iso4217Alpha(), draft.start, draft.end),
        )
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
