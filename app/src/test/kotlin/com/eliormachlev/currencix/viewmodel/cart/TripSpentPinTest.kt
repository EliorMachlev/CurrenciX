package com.eliormachlev.currencix.viewmodel.cart

import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.SpentPin
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.repository.throughJson
import com.eliormachlev.currencix.repository.throughProto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

// What a trip is shown to have spent: live while it goes on or has no end,
// pinned once it is over.
class TripSpentPinTest {
    private val today = LocalDate.of(2026, 11, 7)
    private val noFees = { _: Currency, _: Currency -> BigDecimal.ONE }

    private fun ratesWithIls(ils: String) =
        ExchangeRates(
            true,
            null,
            Currency.EUR,
            null,
            listOf(Rate(Currency.USD, BigDecimal("1.1")), Rate(Currency.ILS, BigDecimal(ils))),
        )

    private fun cart(
        price: String,
        id: String = "c1",
    ) = SavedCart(id, "", "USD", listOf(CartItem("i$id", "", price)), 0, tripId = "t")

    private fun trip(
        end: LocalDate?,
        pin: SpentPin? = null,
    ) = Trip("t", "Trip", Currency.ILS, end = end, spentPin = pin)

    private fun spentOf(
        trip: Trip,
        carts: List<SavedCart>,
        rates: ExchangeRates,
    ) = summarizeTrips(listOf(trip), carts, rates, today, noFees).single()

    @Test
    fun `a trip still on follows the rates and offers no pin`() {
        val trip = trip(end = today.plusDays(3))
        val before = spentOf(trip, listOf(cart("10")), ratesWithIls("3.96"))
        val after = spentOf(trip, listOf(cart("10")), ratesWithIls("4.40"))
        assertNotEquals(0, before.spent!!.compareTo(after.spent))
        assertNull(before.toPin)
    }

    @Test
    fun `a trip with no end follows the rates and offers no pin`() {
        val trip = trip(end = null)
        val before = spentOf(trip, listOf(cart("10")), ratesWithIls("3.96"))
        val after = spentOf(trip, listOf(cart("10")), ratesWithIls("4.40"))
        assertNotEquals(0, before.spent!!.compareTo(after.spent))
        assertNull(after.toPin)
    }

    @Test
    fun `a trip that has just ended offers its spending as the pin`() {
        val ended = spentOf(trip(end = today.minusDays(1)), listOf(cart("10")), ratesWithIls("3.96"))
        assertEquals(ended.spent, ended.toPin?.amount)
    }

    @Test
    fun `an ended trip keeps its pinned amount when the rates move`() {
        val carts = listOf(cart("10"))
        val first = spentOf(trip(end = today.minusDays(1)), carts, ratesWithIls("3.96"))
        val pinned = trip(end = today.minusDays(1), pin = first.toPin)
        val later = spentOf(pinned, carts, ratesWithIls("4.40"))
        assertEquals(0, first.spent!!.compareTo(later.spent))
        assertNull(later.toPin)
    }

    @Test
    fun `an ended trip is worked out again when its carts change`() {
        val first = spentOf(trip(end = today.minusDays(1)), listOf(cart("10")), ratesWithIls("3.96"))
        val pinned = trip(end = today.minusDays(1), pin = first.toPin)
        val withMore = spentOf(pinned, listOf(cart("10"), cart("5", id = "c2")), ratesWithIls("3.96"))
        assertNotEquals(0, first.spent!!.compareTo(withMore.spent))
        assertNotNull(withMore.toPin)
    }

    @Test
    fun `an ended trip with a cart that has no rate yet is not pinned`() {
        val carts = listOf(cart("10"), cart("5", id = "c2").copy(currency = "JPY"))
        val summary = spentOf(trip(end = today.minusDays(1)), carts, ratesWithIls("3.96"))
        assertNotNull(summary.spent)
        assertNull(summary.toPin)
    }

    @Test
    fun `an ended trip without carts has nothing to pin`() {
        val summary = spentOf(trip(end = today.minusDays(1)), emptyList(), ratesWithIls("3.96"))
        assertNull(summary.spent)
        assertNull(summary.toPin)
    }

    @Test
    fun `a pin survives being saved and read back`() {
        val trip = trip(end = today.minusDays(1), pin = SpentPin(BigDecimal("36.72"), -1234567))
        assertEquals(trip, trip.throughProto())
        assertEquals(trip, trip.throughJson())
    }
}
