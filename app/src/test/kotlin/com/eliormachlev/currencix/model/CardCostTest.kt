package com.eliormachlev.currencix.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

// Cards side by side: cheapest first, each with the fees that apply anyway.
class CardCostTest {
    private val visa = Fee.GlobalBank("visa", "Visa abroad", BigDecimal("2.5"))
    private val revolut = Fee.GlobalBank("rev", "Revolut", BigDecimal("0.5"))
    private val exchange = Fee.GlobalExchange("ex", "Exchange", BigDecimal("1"))

    private fun assertAmount(
        expected: String,
        actual: BigDecimal?,
    ) = assertEquals(0, BigDecimal(expected).compareTo(actual))

    @Test
    fun `cheapest card first, with the exchange fee in each`() {
        val costs =
            compareCards(listOf(visa, revolut, exchange), Currency.USD, Currency.ILS, activeExchangeId = null, amount = BigDecimal("100"))
        assertEquals(listOf("rev", "visa"), costs.map { it.card.id })
        // 100 × 1.01 × 1.005 and 100 × 1.01 × 1.025.
        assertAmount("101.505", costs[0].total)
        assertAmount("103.525", costs[1].total)
    }

    @Test
    fun `nothing to compare with one card, or no amount to price`() {
        assertTrue(compareCards(listOf(visa, exchange), Currency.USD, Currency.ILS, null, BigDecimal.TEN).isEmpty())
        val unpriced = compareCards(listOf(visa, revolut), Currency.USD, Currency.ILS, null, BigDecimal.ZERO)
        assertEquals(2, unpriced.size)
        assertNull(unpriced.first().total)
    }

    @Test
    fun `an inactive card isn't compared`() {
        val off = revolut.copy(isActive = false)
        assertTrue(compareCards(listOf(visa, off), Currency.USD, Currency.ILS, null, BigDecimal.TEN).isEmpty())
    }
}
