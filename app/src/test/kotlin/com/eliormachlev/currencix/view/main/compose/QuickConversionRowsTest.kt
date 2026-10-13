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
    ) = buildQuickConversionRows(
        QuickConversionRowInputs(RuntimeEnvironment.getApplication(), Currency.USD, Currency.EUR, RATES, feeStack, "With fee: ", typed),
    )

    @Test
    fun `the typed amount gets its own row, in order, marked`() {
        val rows = rows(typed = BigDecimal("37.5"))
        val typed = rows.single { it.isTyped }
        assertEquals(BigDecimal("37.5"), typed.amount)
        assertEquals(rows.sortedBy { it.amount }, rows)
        assertEquals(9, rows.size)
    }

    @Test
    fun `a typed amount that is already a round one, or nothing typed, adds no row`() {
        assertEquals(8, rows(typed = BigDecimal("100.00")).size)
        assertTrue(rows(typed = BigDecimal("100.00")).none { it.isTyped })
        assertEquals(8, rows(typed = BigDecimal.ZERO).size)
        assertEquals(8, rows(typed = null).size)
    }

    @Test
    fun `a row copies as one line, from amount to result`() {
        val row = rows().first { it.amount.compareTo(BigDecimal.TEN) == 0 }
        assertEquals("10 USD = 8.00 EUR", row.copyText)
    }

    @Test
    fun `with a fee the cost is in the result's currency, the converter's true cost`() {
        val row = rows(feeStack = BigDecimal("1.1")).first { it.amount.compareTo(BigDecimal.TEN) == 0 }
        // 10 USD = 8 EUR; 8 x 1.1 = 8.80 EUR.
        assertTrue(row.costWithFeeText, row.costWithFeeText!!.contains("8.80 EUR"))
        assertNull(rows().first().costWithFeeText)
    }
}
