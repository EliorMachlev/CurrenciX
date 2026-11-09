package com.eliormachlev.currencix.viewmodel.cart

import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Person
import com.eliormachlev.currencix.model.SavedCart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CartMathTest {
    private fun amount(value: String) = BigDecimal(value)

    private fun assertAmount(
        expected: String,
        actual: BigDecimal,
    ) = assertEquals("expected $expected but was $actual", 0, amount(expected).compareTo(actual))

    private fun item(
        id: String,
        expression: String,
        quantity: Int = 1,
        checked: Boolean = false,
        personId: String? = null,
    ) = CartItem(id = id, name = id, expression = expression, quantity = quantity, checked = checked, personId = personId)

    private fun cartOf(
        vararg items: CartItem,
        currency: String = "USD",
        destination: String? = null,
        extras: CartExtras = CartExtras(),
    ) = SavedCart(
        id = "cart",
        name = "",
        currency = currency,
        items = items.toList(),
        createdAt = 0,
        destinationCurrency = destination,
        extras = extras,
    )

    @Test
    fun `a blank or malformed price is zero`() {
        assertAmount("0", evaluatePrice(""))
        assertAmount("0", evaluatePrice("   "))
        assertAmount("0", evaluatePrice("3 +"))
    }

    @Test
    fun `a price expression is worked out`() {
        assertAmount("3.75", evaluatePrice("3.25 + 0.50"))
    }

    @Test
    fun `an item is its price times its quantity`() {
        assertAmount("7.5", evaluateItem(item("a", "2.50", quantity = 3)))
    }

    @Test
    fun `subtotal sums the rows, and is zero without a cart`() {
        assertAmount("0", subtotalOf(null))
        assertAmount("12", subtotalOf(cartOf(item("a", "5"), item("b", "3", quantity = 2), item("c", ""))))
    }

    @Test
    fun `tip is a percent of the subtotal, zero without one`() {
        assertAmount("0", tipOf(null))
        assertAmount("0", tipOf(cartOf(item("a", "100"))))
        assertAmount("15", tipOf(cartOf(item("a", "100"), extras = CartExtras(tipPercent = amount("15")))))
    }

    @Test
    fun `converted subtotal adds the tip and keeps the amount when the pair has no rates`() {
        val cart = cartOf(item("a", "100"), destination = "EUR", extras = CartExtras(tipPercent = amount("10")))

        assertAmount("110", convertedSubtotalOf(cart, rates = null))
        assertAmount("0", convertedSubtotalOf(null, rates = null))
    }

    @Test
    fun `total multiplies by the fee stack`() {
        val cart = cartOf(item("a", "100"))

        assertAmount("100", totalOf(cart, rates = null))
        assertAmount("103", totalOf(cart, rates = null, feeStack = amount("1.03")))
    }

    @Test
    fun `basket share is the ticked items' part of the total`() {
        val cart = cartOf(item("a", "30", checked = true), item("b", "70"))

        assertAmount("30", basketShareOf(cart, total = amount("100")))
        assertAmount("33", basketShareOf(cart, total = amount("110")))
    }

    @Test
    fun `basket share is zero for no cart or an empty subtotal`() {
        assertAmount("0", basketShareOf(null, amount("100")))
        assertAmount("0", basketShareOf(cartOf(item("a", "")), amount("100")))
        assertAmount("0", basketShareOf(cartOf(item("a", "10")), amount("100")))
    }

    @Test
    fun `person shares split shared items equally and count own items fully`() {
        val people = listOf(Person("p1", "Ann"), Person("p2", "Bo"))
        val cart =
            cartOf(
                item("a", "40", personId = "p1"),
                item("b", "20", personId = "p2"),
                item("c", "40"),
                item("d", "0", personId = "gone"),
                extras = CartExtras(people = people),
            )

        val shares = personSharesOf(cart, total = amount("100")).toMap()

        assertAmount("60", shares.getValue(people[0]))
        assertAmount("40", shares.getValue(people[1]))
    }

    @Test
    fun `person shares are empty without people and zero without a subtotal`() {
        assertTrue(personSharesOf(null, amount("10")).isEmpty())
        assertTrue(personSharesOf(cartOf(item("a", "5")), amount("10")).isEmpty())

        val people = listOf(Person("p1", "Ann"))
        val zeros = personSharesOf(cartOf(item("a", ""), extras = CartExtras(people = people)), amount("10"))
        assertEquals(listOf(people[0]), zeros.map { it.first })
        assertAmount("0", zeros.single().second)
    }

    @Test
    fun `per person divides evenly and treats under one way as one`() {
        assertAmount("25", perPerson(amount("100"), 4))
        assertAmount("100", perPerson(amount("100"), 0))
        assertAmount("100", perPerson(amount("100"), -3))
    }

    @Test
    fun `budget left goes negative when over`() {
        assertAmount("20", budgetLeft(total = amount("80"), budget = amount("100")))
        assertAmount("-5", budgetLeft(total = amount("105"), budget = amount("100")))
    }

    @Test
    fun `an unknown currency code falls back to USD`() {
        assertEquals(Currency.USD, resolveCurrency("not-a-code"))
        assertEquals(Currency.USD, resolveCurrency("USD"))
    }

    @Test
    fun `resolved pair collapses an unset destination to the base`() {
        assertEquals(Currency.USD to Currency.USD, cartOf(currency = "USD").resolvedPair())
        assertEquals(Currency.USD to Currency.USD, cartOf(currency = "bogus", destination = "bogus").resolvedPair())
    }

    @Test
    fun `convert amount returns the amount unchanged when the pair has no rates`() {
        assertAmount("42", convertAmount(amount("42"), Currency.USD, Currency.USD, rates = null))
    }
}
