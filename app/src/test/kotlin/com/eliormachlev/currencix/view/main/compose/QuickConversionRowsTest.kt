package com.eliormachlev.currencix.view.main.compose

import android.app.Application
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal

private val RATES =
    ExchangeRates(
        success = true,
        error = null,
        base = Currency.EUR,
        date = null,
        rates = listOf(Rate(Currency.EUR, BigDecimal.ONE), Rate(Currency.USD, BigDecimal("1.25"))),
    )

// Formatting is the app's locale-aware number formatting: Robolectric gives it a Context.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class QuickConversionRowsTest {
    private fun rows(
        typed: BigDecimal? = null,
        feeStack: BigDecimal = BigDecimal.ONE,
        decimalPlaces: Int = 2,
    ) = buildQuickConversionRows(
        QuickConversionRowInputs(RuntimeEnvironment.getApplication(), Currency.USD, Currency.EUR, RATES, feeStack, typed, decimalPlaces),
    )

    @Test
    fun `the typed amount gets its own row, in order, marked`() {
        val rows = rows(typed = BigDecimal("37.5"))
        val typed = rows.single { it.isTyped }
        assertEquals(BigDecimal("37.5"), typed.amount)
        assertEquals(rows.sortedBy { it.amount }, rows)
        // Nine would leave a tile alone: 2,000 fills the last line.
        assertEquals(10, rows.size)
        assertEquals(BigDecimal("2000"), rows.last().amount)
    }

    @Test
    fun `a typed amount that is already a round one, or nothing typed, adds no row`() {
        assertEquals(8, rows(typed = BigDecimal("100.00")).size)
        assertTrue(rows(typed = BigDecimal("100.00")).none { it.isTyped })
        assertEquals(8, rows(typed = BigDecimal.ZERO).size)
        assertEquals(8, rows(typed = null).size)
    }

    @Test
    fun `a row reads code, symbol and amount on each side`() {
        val row = rows().first { it.amount.compareTo(BigDecimal.ONE) == 0 }
        assertEquals("USD $ 1", row.amountFromText)
        assertEquals("EUR € 0.80", row.amountToText)
        assertEquals("USD $ 1 = EUR € 0.80", row.copyText)
    }

    @Test
    fun `with a fee the row adds the percent and the cost in the result's currency`() {
        val row = rows(feeStack = BigDecimal("1.01")).first { it.amount.compareTo(BigDecimal.TEN) == 0 }
        // 10 USD = 8 EUR; with +1%, 8 x 1.01 = 8.08 EUR (the converter's true cost).
        assertEquals("+1% = EUR € 8.08", row.costWithFeeText)
        assertEquals("USD $ 10 = EUR € 8.00   +1% = EUR € 8.08", row.copyText)
        assertNull(rows().first().costWithFeeText)
    }

    @Test
    fun `the table's cells are the bare numbers, the fee's rate its column header`() {
        val row = rows(feeStack = BigDecimal("1.01")).first { it.amount.compareTo(BigDecimal("1000")) == 0 }
        assertEquals("1,000", row.fromValue)
        assertEquals("800.00", row.toValue)
        assertEquals("808.00", row.withFeeValue)
        assertEquals("+1%", row.feePercent)
        assertNull(rows().first().withFeeValue)
    }

    @Test
    fun `results round to the user's decimal places, small ones too`() {
        val one = { places: Int ->
            rows(feeStack = BigDecimal("1.01"), decimalPlaces = places).first {
                it.amount.compareTo(BigDecimal.ONE) ==
                    0
            }
        }
        assertEquals("0.8000", one(4).toValue)
        assertEquals("0.8080", one(4).withFeeValue)
        assertEquals("1", one(0).toValue)
        assertEquals("0.8", one(1).toValue)
    }
}
