package com.eliormachlev.currencix.view.convert

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SelectionConversionTest {
    // EUR-based: 1 EUR = 1.10 USD = 4.00 ILS.
    private val rates =
        ExchangeRates(
            success = true,
            error = null,
            base = Currency.EUR,
            date = null,
            rates =
                listOf(
                    Rate(Currency.EUR, BigDecimal.ONE),
                    Rate(Currency.USD, BigDecimal("1.10")),
                    Rate(Currency.ILS, BigDecimal("4.00")),
                ),
        )

    private fun convert(text: String) = convertSelection(text, rates, converterFrom = Currency.USD, converterTo = Currency.ILS)

    @Test
    fun `a price converts into the converter's destination currency`() {
        val result = convert("€10") as SelectionConversion.Converted
        assertEquals(Currency.EUR, result.from)
        assertEquals(Currency.ILS, result.to)
        assertEquals(0, BigDecimal("40").compareTo(result.result))
    }

    @Test
    fun `a price already in the destination converts back to the base`() {
        val result = convert("₪40") as SelectionConversion.Converted
        assertEquals(Currency.USD, result.to)
        assertEquals(0, BigDecimal("11").compareTo(result.result))
    }

    @Test
    fun `a bare number is read as the converter's base currency`() {
        val result = convert("11") as SelectionConversion.Converted
        assertEquals(Currency.USD, result.from)
        assertTrue(result.assumedFrom)
        assertEquals(0, BigDecimal("40").compareTo(result.result))
    }

    @Test
    fun `no number, or no rates, says so`() {
        assertEquals(SelectionConversion.NoAmount, convert("hello"))
        assertEquals(SelectionConversion.NoRates, convertSelection("€10", null, Currency.USD, Currency.ILS))
        assertEquals(SelectionConversion.NoRates, convert("£10"))
    }
}
