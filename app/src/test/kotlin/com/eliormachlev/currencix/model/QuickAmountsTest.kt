package com.eliormachlev.currencix.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private fun rates(vararg perEuro: Pair<Currency, String>) =
    ExchangeRates(
        success = true,
        error = null,
        base = Currency.EUR,
        date = null,
        rates = listOf(Rate(Currency.EUR, BigDecimal.ONE)) + perEuro.map { (currency, value) -> Rate(currency, BigDecimal(value)) },
    )

private val RATES =
    rates(
        Currency.USD to "1.08",
        Currency.ILS to "4.05",
        Currency.JPY to "162.5",
        Currency.KRW to "1495",
        Currency.IDR to "17400",
        Currency.KWD to "0.33",
    )

private fun amounts(vararg values: String) = values.map(::BigDecimal)

class QuickAmountsTest {
    @Test
    fun `the round amounts scale to what a dollar buys, by powers of ten`() {
        assertEquals(BigDecimal.ONE, quickAmountScale(Currency.USD, RATES))
        assertEquals(BigDecimal.ONE, quickAmountScale(Currency.ILS, RATES))
        assertEquals(BigDecimal("100"), quickAmountScale(Currency.JPY, RATES))
        assertEquals(BigDecimal("1000"), quickAmountScale(Currency.KRW, RATES))
        assertEquals(BigDecimal("10000"), quickAmountScale(Currency.IDR, RATES))
        // Worth more than a dollar: never below 1.
        assertEquals(BigDecimal.ONE, quickAmountScale(Currency.KWD, RATES))
    }

    @Test
    fun `without the dollar the euro stands in, and without either nothing scales`() {
        assertEquals(BigDecimal("1000"), quickAmountScale(Currency.KRW, rates(Currency.KRW to "1495")))
        val noReference =
            ExchangeRates(
                true,
                null,
                Currency.KRW,
                null,
                listOf(Rate(Currency.KRW, BigDecimal.ONE), Rate(Currency.JPY, BigDecimal("0.11"))),
            )
        assertEquals(BigDecimal.ONE, quickAmountScale(Currency.JPY, noReference))
        assertEquals(BigDecimal.ONE, quickAmountScale(Currency.THB, RATES))
    }

    @Test
    fun `KRW starts at a thousand, and the user's own amounts win`() {
        assertEquals(
            amounts(
                "1000",
                "5000",
                "10000",
                "20000",
                "50000",
                "100000",
                "500000",
                "1000000",
            ),
            quickAmounts(Currency.KRW, RATES, emptyMap()).map {
                it.setScale(0)
            },
        )
        val custom = mapOf(Currency.KRW to amounts("30000", "5000"))
        assertEquals(amounts("5000", "30000"), quickAmounts(Currency.KRW, RATES, custom))
        // Only for that currency.
        assertEquals(BigDecimal("100"), quickAmounts(Currency.JPY, RATES, custom).first().setScale(0))
    }

    @Test
    fun `the filler is twice the largest`() {
        assertEquals(0, BigDecimal("2000").compareTo(quickFillAmount(amounts("1", "1000"))))
    }

    @Test
    fun `changing an amount replaces it, adding keeps the rest, duplicates go`() {
        val auto = amounts("1000", "5000", "10000")
        val changed = emptyMap<Currency, List<BigDecimal>>().withQuickAmount(Currency.KRW, auto, BigDecimal("5000"), BigDecimal("3000"))
        assertEquals(amounts("1000", "3000", "10000"), changed[Currency.KRW])
        val added = changed.withQuickAmount(Currency.KRW, changed.getValue(Currency.KRW), null, BigDecimal("25000"))
        assertEquals(amounts("1000", "3000", "10000", "25000"), added[Currency.KRW])
        val same = added.withQuickAmount(Currency.KRW, added.getValue(Currency.KRW), null, BigDecimal("1000.0"))
        assertEquals(4, same.getValue(Currency.KRW).size)
    }

    @Test
    fun `stored amounts round-trip and bad entries are skipped`() {
        val custom = mapOf(Currency.KRW to amounts("1000", "5000"), Currency.JPY to amounts("100.5"))
        assertEquals(custom, decodeQuickAmounts(encodeQuickAmounts(custom)))
        assertNull(encodeQuickAmounts(emptyMap()))
        assertTrue(decodeQuickAmounts(null).isEmpty())
        val read = decodeQuickAmounts("XXX:1;KRW:-5,abc,0,1000,1000.00,1E20;JPY:")
        assertEquals(mapOf(Currency.KRW to amounts("1000")), read)
    }

    @Test
    fun `at most sixteen amounts per currency`() {
        val many = (1..40).joinToString(",")
        assertEquals(MAX_QUICK_AMOUNTS, decodeQuickAmounts("KRW:$many").getValue(Currency.KRW).size)
    }
}
