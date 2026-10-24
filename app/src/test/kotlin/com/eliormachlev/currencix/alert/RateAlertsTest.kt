package com.eliormachlev.currencix.alert

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.text.Annotation
import android.text.Spanned
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.model.AlertDirection
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.RateAlert
import com.eliormachlev.currencix.proto.RateAlertProto
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.throughJson
import com.eliormachlev.currencix.repository.throughProto
import com.eliormachlev.currencix.repository.toModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.math.BigDecimal

// Rate alerts: which way they point, how they're kept, and that a reached one fires once.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RateAlertsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val db = Database(context)
    private val usdIls = CurrencyPair(Currency.USD, Currency.ILS)

    // 1 USD = 3.6 ILS (EUR-based: 1.1 USD, 3.96 ILS per euro).
    private fun ratesAt(ilsPerEuro: String) =
        ExchangeRates(
            success = true,
            error = null,
            base = Currency.EUR,
            date = null,
            rates = listOf(Rate(Currency.USD, BigDecimal("1.1")), Rate(Currency.ILS, BigDecimal(ilsPerEuro))),
        )

    @After
    fun clear() = db.alerts.alerts.set(emptyList())

    @Test
    fun `an alert points away from the rate it was set at`() {
        val up = RateAlert.towards(usdIls, BigDecimal("3.8"), current = BigDecimal("3.6"))
        val down = RateAlert.towards(usdIls, BigDecimal("3.5"), current = BigDecimal("3.6"))
        assertEquals(AlertDirection.ABOVE, up.direction)
        assertEquals(AlertDirection.BELOW, down.direction)
        assertTrue(up.isReachedBy(BigDecimal("3.8")))
        assertFalse(up.isReachedBy(BigDecimal("3.79")))
        assertTrue(down.isReachedBy(BigDecimal("3.4")))
        assertFalse(down.isReachedBy(BigDecimal("3.51")))
    }

    @Test
    fun `alerts survive a save, and broken entries are skipped`() {
        val alerts = listOf(RateAlert.towards(usdIls, BigDecimal("3.5"), BigDecimal("3.6")))
        assertEquals(alerts, alerts.mapNotNull { it.throughProto() })
        assertEquals(alerts, alerts.mapNotNull { it.throughJson() })
        val broken =
            listOf(
                RateAlertProto(id = "x", from = "USD", to = "???", threshold = "3.5", direction = RateAlertProto.Direction.BELOW),
                RateAlertProto(id = "y"),
                RateAlertProto(from = "USD", to = "ILS", threshold = "0", direction = RateAlertProto.Direction.ABOVE),
            )
        assertEquals(emptyList<RateAlert>(), broken.mapNotNull { it.toModel() })
    }

    @Test
    fun `a reached alert notifies once and is gone`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val reached = RateAlert.towards(usdIls, BigDecimal("3.5"), current = BigDecimal("3.6"))
        val waiting = RateAlert.towards(usdIls, BigDecimal("4.0"), current = BigDecimal("3.6"))
        db.alerts.add(reached)
        db.alerts.add(waiting)
        // 3.795 / 1.1 = 3.45 ILS per USD: below 3.5.
        checkRateAlerts(context, db, ratesAt("3.795"))
        checkRateAlerts(context, db, ratesAt("3.795"))
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        assertEquals(1, notifications.size)
        assertEquals(listOf(waiting), db.alerts.alerts.get())
    }

    @Test
    fun `without notification permission alerts wait`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val reached = RateAlert.towards(usdIls, BigDecimal("3.5"), current = BigDecimal("3.6"))
        db.alerts.add(reached)
        checkRateAlerts(context, db, ratesAt("3.795"))
        assertEquals(listOf(reached), db.alerts.alerts.get())
    }

    @Test
    fun `the color goes over the rate in the title, the last time it appears`() {
        val span = Annotation("style", "safe")
        val colored = "USD 3 → ILS is at 3".withSpanOnLast("3", span) as Spanned
        val spans = colored.getSpans(0, colored.length, Any::class.java)
        assertSame(span, spans.single())
        assertEquals(colored.length - 1, colored.getSpanStart(span))
        assertEquals(colored.length, colored.getSpanEnd(span))
    }

    @Test
    fun `a title without the rate stays as it is`() {
        val title = "USD → ILS"
        assertSame(title, title.withSpanOnLast("3.6000", Annotation("style", "safe")))
    }
}
