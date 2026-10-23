package com.eliormachlev.currencix.view.main

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.util.ParsedPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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

    // A ClipboardManager keeps the context it came from, and the system keeps
    // the manager: taken from an activity, it leaked every destroyed one. (The
    // test environment shares one clipboard object between contexts, so this
    // checks which context is asked, not which object comes back.)
    @Test
    fun `the clipboard comes from the application, not the activity`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val activity = ScreenContext(app)
        assertSame(app.getSystemService(ClipboardManager::class.java), clipboardOf(activity))
    }

    // Stands in for an activity: asking it for a system service fails.
    private class ScreenContext(
        private val app: Application,
    ) : ContextWrapper(app) {
        override fun getApplicationContext(): Context = app

        override fun getSystemService(name: String): Any = error("asked the screen for $name")
    }

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
