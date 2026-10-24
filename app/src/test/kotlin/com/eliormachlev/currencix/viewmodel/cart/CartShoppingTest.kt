package com.eliormachlev.currencix.viewmodel.cart

import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.repository.CART_ITEM_KEY_CHECKED
import com.eliormachlev.currencix.repository.CART_ITEM_KEY_EXPR
import com.eliormachlev.currencix.repository.CART_ITEM_KEY_ID
import com.eliormachlev.currencix.repository.CART_ITEM_KEY_QUANTITY
import com.eliormachlev.currencix.repository.CART_KEY_ITEMS
import com.eliormachlev.currencix.repository.CART_KEY_SHOPPING_MODE
import com.eliormachlev.currencix.repository.json.JsonObject
import com.eliormachlev.currencix.repository.parseCart
import com.eliormachlev.currencix.repository.serializeCart
import com.eliormachlev.currencix.repository.throughJson
import com.eliormachlev.currencix.repository.throughProto
import com.eliormachlev.currencix.view.cart.compose.RATES_OLD_AFTER_DAYS
import com.eliormachlev.currencix.view.cart.compose.ratesAreOld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

// Quantities, shopping-list ticks and the rates' age.
class CartShoppingTest {
    private fun cart(
        vararg items: CartItem,
        shoppingMode: Boolean = false,
    ) = SavedCart(id = "cart", name = "", currency = "USD", items = items.toList(), createdAt = 0, shoppingMode = shoppingMode)

    private fun assertAmount(
        expected: String,
        actual: BigDecimal,
    ) = assertEquals("expected $expected, got $actual", 0, BigDecimal(expected).compareTo(actual))

    @Test
    fun `a quantity multiplies the price, and shows in the calculation`() {
        val coffee = CartItem("1", "Coffee", "2 + 0.50", quantity = 3)
        assertAmount("7.5", evaluateItem(coffee))
        assertAmount("8.5", subtotalOf(cart(coffee, CartItem("2", "Bun", "1"))))
        assertEquals("2 + 0.50 × 3", coffee.expressionWithQuantity)
        assertEquals("2", CartItem("3", "", "2").expressionWithQuantity)
    }

    @Test
    fun `the basket is the ticked items' share of the total`() {
        val ticked = CartItem("1", "a", "30", checked = true)
        val other = CartItem("2", "b", "70")
        // A total of 220 (fees, tip and conversion in it) splits 30 : 70.
        assertAmount("66", basketShareOf(cart(ticked, other), BigDecimal("220")))
        assertAmount("0", basketShareOf(cart(other), BigDecimal("220")))
        assertAmount("0", basketShareOf(cart(), BigDecimal("220")))
    }

    @Test
    fun `quantity, ticks and shopping mode survive a save, and defaults aren't written`() {
        val saved = cart(CartItem("1", "a", "2", quantity = 4, checked = true), CartItem("2", "b", "3"), shoppingMode = true)
        assertEquals(saved, saved.throughJson())
        assertEquals(saved, saved.throughProto())
        val plain = serializeCart(cart(CartItem("2", "b", "3")))
        assertFalse(CART_KEY_SHOPPING_MODE in plain)
        val item = checkNotNull(JsonObject(plain).optObjects(CART_KEY_ITEMS).singleOrNull())
        assertFalse(item.has(CART_ITEM_KEY_QUANTITY))
        assertFalse(item.has(CART_ITEM_KEY_CHECKED))
    }

    @Test
    fun `a quantity out of range loads clamped`() {
        val item = mapOf(CART_ITEM_KEY_ID to "1", CART_ITEM_KEY_EXPR to "2")
        val withQuantity = { quantity: Int -> JsonObject(mapOf(CART_KEY_ITEMS to listOf(item + (CART_ITEM_KEY_QUANTITY to quantity)))) }
        assertEquals(CartItem.MAX_QUANTITY, parseCart(withQuantity(500))?.items?.single()?.quantity)
        assertEquals(CartItem.MIN_QUANTITY, parseCart(withQuantity(-2))?.items?.single()?.quantity)
    }

    @Test
    fun `rates count as old only past a long weekend`() {
        val today = LocalDate.of(2026, 10, 11)
        assertFalse(ratesAreOld(today.minusDays(RATES_OLD_AFTER_DAYS), today))
        assertTrue(ratesAreOld(today.minusDays(RATES_OLD_AFTER_DAYS + 1), today))
        assertFalse(ratesAreOld(null, today))
    }
}
