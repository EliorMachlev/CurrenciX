package com.eliormachlev.currencix.viewmodel.cart

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Person
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.throughJson
import com.eliormachlev.currencix.repository.throughProto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

// Split by person and trips. Robolectric for the stores.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CartSplitTripTest {
    private val db = Database(ApplicationProvider.getApplicationContext())
    private val dana = Person("d", "Dana")
    private val avi = Person("a", "Avi")

    private fun cart(
        vararg items: CartItem,
        people: List<Person> = listOf(dana, avi),
        currency: String = "USD",
        id: String = "c1",
        tripId: String? = null,
    ) = SavedCart(id, "", currency, items.toList(), 0, extras = CartExtras(people = people), tripId = tripId)

    private fun assertAmount(
        expected: String,
        actual: BigDecimal?,
    ) = assertEquals("expected $expected, got $actual", 0, BigDecimal(expected).compareTo(actual))

    @After
    fun clear() {
        db.carts.trips.set(emptyList())
        db.carts.savedCarts.set(emptyList())
    }

    @Test
    fun `each pays their own items and half the shared ones, in proportion to the total`() {
        val c =
            cart(CartItem("1", "salad", "30", personId = "d"), CartItem("2", "wine", "40"), CartItem("3", "steak", "30", personId = "a"))
        // Subtotal 100 → total 220 (conversion, tip and fees): Dana 30 + 20, Avi 30 + 20.
        val shares = personSharesOf(c, BigDecimal("220")).toMap()
        assertAmount("110", shares.getValue(dana))
        assertAmount("110", shares.getValue(avi))
        val uneven = personSharesOf(cart(CartItem("1", "", "80", personId = "d"), CartItem("2", "", "20")), BigDecimal("100")).toMap()
        assertAmount("90", uneven.getValue(dana))
        assertAmount("10", uneven.getValue(avi))
    }

    @Test
    fun `someone no longer in the cart counts as shared, and no people means no shares`() {
        val c = cart(CartItem("1", "", "50", personId = "gone"), CartItem("2", "", "50", personId = "d"))
        val shares = personSharesOf(c, BigDecimal("100")).toMap()
        assertAmount("75", shares.getValue(dana))
        assertAmount("25", shares.getValue(avi))
        assertEquals(emptyList<Pair<Person, BigDecimal>>(), personSharesOf(cart(people = emptyList()), BigDecimal.TEN))
    }

    @Test
    fun `people, who an item is for, and the trip survive a save`() {
        val c = cart(CartItem("1", "salad", "30", personId = "d"), tripId = "t1")
        assertEquals(c, c.throughJson())
        assertEquals(c, c.throughProto())
        val trips = listOf(Trip("t1", "Japan", Currency.ILS, BigDecimal("3000")), Trip("t2", "", Currency.EUR))
        assertEquals(trips, trips.mapNotNull { it.throughJson() })
        assertEquals(trips, trips.mapNotNull { it.throughProto() })
    }

    @Test
    fun `a trip adds its carts up at today's rates with the fees`() {
        // EUR-based: 1 USD = 3.6 ILS.
        val rates =
            ExchangeRates(
                true,
                null,
                Currency.EUR,
                null,
                listOf(Rate(Currency.USD, BigDecimal("1.1")), Rate(Currency.ILS, BigDecimal("3.96"))),
            )
        val trip = Trip("t1", "Trip", Currency.ILS)
        val carts =
            listOf(
                cart(CartItem("1", "", "10"), people = emptyList(), tripId = "t1"),
                cart(CartItem("2", "", "5"), people = emptyList(), id = "c2"),
            )
        val summary = summarizeTrips(listOf(trip), carts, rates) { _, _ -> BigDecimal("1.02") }.single()
        assertEquals(1, summary.cartCount)
        assertAmount("36.72", summary.spent)
        assertNull(summarizeTrips(listOf(trip), emptyList(), rates) { _, _ -> BigDecimal.ONE }.single().spent)
    }

    @Test
    fun `saving a cart keeps its trip, and deleting a trip frees its carts`() {
        db.carts.saveCart(cart(id = "c1"))
        db.carts.saveTrip(Trip("t1", "Japan", Currency.ILS))
        db.carts.moveToTrip("c1", "t1")
        // The working cart doesn't know about the trip; saving it over keeps it.
        db.carts.saveCart(cart(CartItem("1", "", "5"), id = "c1"))
        assertEquals(
            "t1",
            db.carts.savedCarts
                .get()
                .single()
                .tripId,
        )
        db.carts.deleteTrip("t1")
        assertNull(
            db.carts.savedCarts
                .get()
                .single()
                .tripId,
        )
        assertEquals(emptyList<Trip>(), db.carts.trips.get())
    }
}
