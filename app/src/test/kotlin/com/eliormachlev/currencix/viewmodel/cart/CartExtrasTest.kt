package com.eliormachlev.currencix.viewmodel.cart

import android.app.Application
import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.repository.parseCart
import com.eliormachlev.currencix.repository.serializeCart
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.RoundingMode

// Robolectric for org.json, which the JVM's android.jar only stubs.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CartExtrasTest {
    private fun cart(extras: CartExtras = CartExtras()) =
        SavedCart(
            id = "",
            name = "",
            currency = "USD",
            items = listOf(CartItem("1", "a", "60"), CartItem("2", "b", "40")),
            createdAt = 0,
            extras = extras,
        )

    private fun assertAmount(
        expected: String,
        actual: BigDecimal,
    ) = assertEquals(0, BigDecimal(expected).compareTo(actual))

    @Test
    fun `tip is a share of the items, and part of the total before fees`() {
        val cart = cart(CartExtras(tipPercent = BigDecimal("15")))
        assertAmount("15", tipOf(cart))
        // Same currency both sides: converted = items + tip; a 2% fee applies to all of it.
        assertAmount("115", convertedSubtotalOf(cart, null))
        assertAmount("117.3", totalOf(cart, null, BigDecimal("1.02")))
        assertAmount("0", tipOf(cart()))
    }

    @Test
    fun `split and budget`() {
        assertAmount("33.333333", perPerson(BigDecimal("100"), 3).setScale(6, RoundingMode.DOWN))
        assertAmount("100", perPerson(BigDecimal("100"), 0))
        assertAmount("20", budgetLeft(BigDecimal("80"), BigDecimal("100")))
        assertAmount("-5", budgetLeft(BigDecimal("105"), BigDecimal("100")))
    }

    @Test
    fun `extras survive a save and load, and old carts load without them`() {
        val extras = CartExtras(tipPercent = BigDecimal("12.5"), splitWays = 4, budget = BigDecimal("250"))
        assertEquals(extras, parseCart(serializeCart(cart(extras)).toString())?.extras)
        val old = serializeCart(cart()).toString()
        assertEquals(CartExtras(), parseCart(old)?.extras)
    }

    @Test
    fun `out-of-range extras from a file fall back to none`() {
        val json = serializeCart(cart()).put("extras", JSONObject("""{"tipPercent":"-3","splitWays":500,"budget":"abc"}"""))
        assertEquals(CartExtras(), parseCart(json.toString())?.extras)
    }
}
