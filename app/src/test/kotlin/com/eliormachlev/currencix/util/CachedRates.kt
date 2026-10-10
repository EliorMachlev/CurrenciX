package com.eliormachlev.currencix.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.repository.Database
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Puts a small set of rates in the cache, as a finished refresh would, so a
 * test that launches the app has a currency pair from its first frame. Without
 * them the pair only appears once the live request comes back — and a test
 * that taps before then (a slow or unreachable provider) finds the timeline
 * and friends not opening.
 */
fun seedCachedRates(app: Application = ApplicationProvider.getApplicationContext()) {
    val db = Database(app)
    db.rates.insertExchangeRates(
        ExchangeRates(
            success = true,
            error = null,
            base = Currency.EUR,
            date = LocalDate.now(),
            rates =
                listOf(
                    Rate(Currency.EUR, BigDecimal.ONE),
                    Rate(Currency.USD, BigDecimal("1.12")),
                    Rate(Currency.GBP, BigDecimal("0.86")),
                    Rate(Currency.ILS, BigDecimal("4.10")),
                ),
            time = LocalTime.NOON,
            provider = db.providers.getApiProvider(),
        ),
    )
}
