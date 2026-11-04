package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.provider.api.BankOfIsraelRate
import com.eliormachlev.currencix.model.provider.api.BankOfIsraelRates
import java.math.BigDecimal
import java.math.MathContext
import java.time.OffsetDateTime

/**
 * Bank of Israel's PublicApi rates as the app's: shekels per unit inverted
 * to units per shekel, ILS added at 1, dated by the newest update.
 */
internal fun BankOfIsraelRates.toExchangeRates(): ExchangeRates {
    val entries = exchangeRates.orEmpty()
    val rates = entries.mapNotNullTo(mutableListOf(), BankOfIsraelRate::toRate)
    if (rates.isNotEmpty()) {
        rates.add(Rate(Currency.ILS, BigDecimal.ONE))
        rates.addFokFromDkkIfMissing()
    }
    return ExchangeRates(
        success = rates.isNotEmpty(),
        error = if (rates.isEmpty()) NO_DATA_ERROR else null,
        base = Currency.ILS,
        date = entries.mapNotNull { entry -> entry.lastUpdate?.let { OffsetDateTime.parse(it).toLocalDate() } }.maxOrNull(),
        rates = rates,
        provider = ApiProvider.BANK_OF_ISRAEL,
    )
}

private fun BankOfIsraelRate.toRate(): Rate? {
    val currency = key?.let(Currency::fromString)
    val shekels = currentExchangeRate
    return if (currency != null && shekels != null && shekels.signum() > 0) {
        Rate(currency, (unit ?: BigDecimal.ONE).divide(shekels, MathContext.DECIMAL128))
    } else {
        null
    }
}
