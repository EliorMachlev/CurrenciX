package com.eliormachlev.currencix.view.main

import android.app.Application
import android.content.ClipData
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.util.ParsedPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

// What the paste chip offers, and the pair a pasted price lands on.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ClipboardAmountTest {
    private val usdToIls = CurrencyPair(Currency.USD, Currency.ILS)

    private fun copied(
        label: String?,
        at: Long,
    ) = ClipInfo(isText = true, label = label, copiedAt = at)

    @Test
    fun `a plain-text clip counts as text`() {
        val info = ClipData.newPlainText("note", "€24.90").description.toClipInfo()
        assertTrue(info.isText)
        assertEquals("note", info.label)
    }

    @Test
    fun `a recent copy from another app is offered`() {
        assertTrue(isPasteCandidate(copied("note", at = 1_000), now = 1_000 + RECENT_COPY_MILLIS))
    }

    @Test
    fun `an old copy, the app's own, or nothing is not`() {
        assertFalse(isPasteCandidate(copied("note", at = 1_000), now = 1_001 + RECENT_COPY_MILLIS))
        assertFalse(isPasteCandidate(copied("CurrenciX", at = 1_000), now = 1_000))
        assertFalse(isPasteCandidate(null, now = 1_000))
        assertFalse(isPasteCandidate(copied("note", at = 1_000).copy(isText = false), now = 1_000))
    }

    @Test
    fun `a pasted price's currency goes on the from side`() {
        val amount = BigDecimal("24.90")
        assertEquals(CurrencyPair(Currency.EUR, Currency.ILS), pairForPaste(ParsedPrice(amount, Currency.EUR), usdToIls))
        assertEquals(CurrencyPair(Currency.ILS, Currency.USD), pairForPaste(ParsedPrice(amount, Currency.ILS), usdToIls))
        assertEquals(usdToIls, pairForPaste(ParsedPrice(amount, Currency.USD), usdToIls))
        assertEquals(usdToIls, pairForPaste(ParsedPrice(amount, null), usdToIls))
    }
}
