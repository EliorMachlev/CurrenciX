package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.provider.api.ValetObservations
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/**
 * Bank of Canada's latest observation as the app's rates: Canadian dollars
 * per unit inverted to units per dollar, CAD added at 1. Its message when
 * it has none; null when the answer has neither.
 */
internal fun ValetObservations.toExchangeRates(): ExchangeRates? =
    when {
        observations != null -> observationRates(observations.firstOrNull())
        message != null -> bankOfCanadaError(message)
        else -> null
    }

private fun bankOfCanadaError(message: String): ExchangeRates =
    ExchangeRates(
        success = false,
        error = message,
        base = null,
        date = null,
        rates = null,
        provider = ApiProvider.BANK_OF_CANADA,
    )

// The first observation is the one asked for; any others are passed over.
private fun observationRates(observation: JsonObject?): ExchangeRates {
    val rates = mutableListOf<Rate>()
    var date: LocalDate? = null
    observation?.forEach { (name, value) ->
        if (name == BANK_OF_CANADA_DATE) {
            date = LocalDate.parse(value.text())
        } else {
            val (currency, dollars) = value.bankOfCanadaValue(name)
            currency?.let { rates.add(Rate(it, BigDecimal.ONE.divide(dollars, MathContext.DECIMAL128))) }
        }
    }
    if (rates.isNotEmpty()) rates.add(Rate(Currency.CAD, BigDecimal.ONE))
    val errorMessage = if (observation == null) NO_DATA_ERROR else null
    return ExchangeRates(
        success = errorMessage == null && rates.isNotEmpty() && date != null,
        error = errorMessage,
        base = Currency.CAD,
        date = date,
        rates = rates,
        provider = ApiProvider.BANK_OF_CANADA,
    )
}
