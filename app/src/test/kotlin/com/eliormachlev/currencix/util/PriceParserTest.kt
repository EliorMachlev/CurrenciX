package com.eliormachlev.currencix.util

import com.eliormachlev.currencix.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class PriceParserTest {
    private fun parse(
        text: String,
        preferred: List<Currency> = emptyList(),
    ) = PriceParser.parse(text, preferred)

    private fun assertPrice(
        amount: String,
        currency: Currency?,
        text: String,
        preferred: List<Currency> = emptyList(),
    ) {
        val parsed = parse(text, preferred)
        assertEquals(text, 0, BigDecimal(amount).compareTo(parsed?.amount))
        assertEquals(text, currency, parsed?.currency)
    }

    @Test
    fun `symbols and codes, before or after the number`() {
        assertPrice("49.99", Currency.EUR, "€49.99")
        assertPrice("49.99", Currency.EUR, "49,99 €")
        assertPrice("1234.56", Currency.USD, "USD 1,234.56")
        assertPrice("1234.56", Currency.EUR, "1.234,56 EUR")
        assertPrice("100", Currency.ILS, "₪100")
        assertPrice("12", Currency.GBP, "only £12 today!")
        // "all" isn't the lek (ALL): codes count only in capitals.
        assertPrice("12", Currency.GBP, "all for £12")
    }

    @Test
    fun `thousands separators vs decimal marks`() {
        assertEquals(BigDecimal("1234"), PriceParser.parseNumber("1,234"))
        assertEquals(BigDecimal("1234567"), PriceParser.parseNumber("1.234.567"))
        assertEquals(BigDecimal("49.9"), PriceParser.parseNumber("49,9"))
        assertEquals(BigDecimal("1234.5"), PriceParser.parseNumber("1 234,5"))
        assertEquals(BigDecimal("1234.5"), PriceParser.parseNumber("1'234.5"))
        assertEquals(BigDecimal("5"), PriceParser.parseNumber("5."))
    }

    @Test
    fun `a shared symbol prefers the user's own currencies`() {
        assertPrice("20", Currency.USD, "$20")
        assertPrice("20", Currency.CAD, "$20", preferred = listOf(Currency.ILS, Currency.CAD))
    }

    @Test
    fun `a number alone has no currency, and no number is no price`() {
        assertPrice("42.5", null, "42.5")
        assertNull(parse("no price here"))
    }
}
