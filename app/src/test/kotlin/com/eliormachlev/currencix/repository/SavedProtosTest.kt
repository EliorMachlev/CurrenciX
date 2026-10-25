package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.Person
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.proto.CartItemProto
import com.eliormachlev.currencix.proto.CartProto
import com.eliormachlev.currencix.proto.FeeProto
import com.eliormachlev.currencix.proto.TripProto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

// Carts, trips and fees as Protobuf: everything survives the bytes, and a
// message with values out of range reads the way a JSON file's would.
class SavedProtosTest {
    @Test
    fun `a full cart survives the bytes`() {
        val cart =
            SavedCart(
                id = "c",
                name = "Dinner",
                currency = "USD",
                items = listOf(CartItem("1", "soup", "8.50", pinned = true, quantity = 3, checked = true, personId = "p")),
                createdAt = 1_761_000_000_123L,
                destinationCurrency = "ILS",
                extras = CartExtras(BigDecimal("12.5"), 3, BigDecimal("0.000000001"), listOf(Person("p", "Noa"))),
                shoppingMode = true,
                tripId = "t",
            )
        assertEquals(cart, cart.throughProto())
        assertEquals(cart, cart.throughJson())
    }

    @Test
    fun `each kind of fee survives both ways`() {
        listOf(
            Fee.GlobalExchange("a", "Exchange", BigDecimal("2.5")),
            Fee.GlobalBank("b", "Bank", BigDecimal("1.25"), isActive = false),
            Fee.SpecificPair("c", "Pair", BigDecimal("0.3"), "USD", "EUR", bothWays = true),
        ).forEach { fee ->
            assertEquals(fee, fee.throughProto())
            assertEquals(fee, fee.throughJson())
        }
    }

    @Test
    fun `values out of range read as none`() {
        val message = CartProto(items = listOf(CartItemProto(id = "", quantity = 0)), split_ways = 0, tip_percent = "-1", budget = "x")
        val cart = message.toModel()
        assertEquals("an unset quantity is one", 1, cart.items.single().quantity)
        assertTrue(
            "an entry without an id gets one",
            cart.items
                .single()
                .id
                .isNotEmpty() && cart.id.isNotEmpty(),
        )
        assertEquals(CartExtras(), cart.extras)
        assertEquals(
            CartItem.MAX_QUANTITY,
            CartProto(items = listOf(CartItemProto(quantity = 500)))
                .toModel()
                .items
                .single()
                .quantity,
        )
    }

    @Test
    fun `entries that can't be read are skipped`() {
        assertNull("unknown kind", FeeProto(percent = "1", kind = FeeProto.Kind.KIND_UNSPECIFIED).toModel())
        assertNull("no percent", FeeProto(kind = FeeProto.Kind.GLOBAL_BANK).toModel())
        assertNull("unknown currency", TripProto(currency = "ZZZ").toModel())
        assertEquals(Trip("t", "", Currency.EUR), TripProto(id = "t", currency = "EUR", budget = "0").toModel())
    }
}
