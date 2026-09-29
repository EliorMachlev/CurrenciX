package com.eliormachlev.currencix.view.main

import android.app.Application
import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ConverterLaunchTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `a convert intent round-trips its pair and amount`() {
        val intent = ConverterLaunch.convert(context, Currency.EUR, Currency.ILS, BigDecimal("49.99"))
        val request = ConverterLaunch.parse(intent) as ConverterLaunch.Request.Convert
        assertEquals(CurrencyPair(Currency.EUR, Currency.ILS), request.pair)
        assertEquals(BigDecimal("49.99"), request.amount)
        assertEquals(ConverterLaunch.Request.OpenCart, ConverterLaunch.parse(ConverterLaunch.openCart(context)))
    }

    @Test
    fun `anything unreadable from outside is dropped, not guessed`() {
        fun convert(vararg extras: Pair<String, String>) =
            ConverterLaunch.parse(
                Intent(ConverterLaunch.ACTION_CONVERT).apply { extras.forEach { (k, v) -> putExtra(k, v) } },
            )
        assertNull(convert("from" to "XXX", "to" to "ILS"))
        assertNull(convert("from" to "EUR", "to" to "EUR"))
        val junkAmount = convert("from" to "EUR", "to" to "ILS", "amount" to "-5") as ConverterLaunch.Request.Convert
        assertNull(junkAmount.amount)
        val longAmount = convert("from" to "EUR", "to" to "ILS", "amount" to "9".repeat(100)) as ConverterLaunch.Request.Convert
        assertNull(longAmount.amount)
        assertNull(ConverterLaunch.parse(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun `shortcuts are the top recent pairs plus the cart`() {
        val recents =
            listOf(Currency.USD to Currency.ILS, Currency.EUR to Currency.USD, Currency.GBP to Currency.JPY, Currency.CHF to Currency.EUR)
                .map { (a, b) -> CurrencyPair(a, b) }
        AppShortcuts.update(context, recents)
        val ids = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }.map { it.id }
        assertEquals(listOf("pair:USD:ILS", "pair:EUR:USD", "pair:GBP:JPY", "cart"), ids)
    }
}
