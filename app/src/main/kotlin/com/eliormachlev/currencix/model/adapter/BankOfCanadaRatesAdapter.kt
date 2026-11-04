package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/**
 * Bank of Canada's latest observation: `{observations: [{d, FXUSDCAD: {v}, …}]}`,
 * or `{message}` when it has none to give.
 */
internal class BankOfCanadaRatesAdapter : ResponseAdapter<ExchangeRates> {
    override fun decode(json: JsonElement): ExchangeRates? =
        json.firstOfFields { name, value ->
            when (name) {
                FIELD_MESSAGE -> errorResponse(value.text())
                BANK_OF_CANADA_OBSERVATIONS -> convertObservation(value.jsonArray.firstOrNull())
                else -> null
            }
        }

    private fun errorResponse(message: String?): ExchangeRates =
        ExchangeRates(
            success = false,
            error = message,
            base = null,
            date = null,
            rates = null,
            provider = ApiProvider.BANK_OF_CANADA,
        )

    // The first observation is the one asked for; any others are passed over.
    private fun convertObservation(observation: JsonElement?): ExchangeRates {
        val rates = mutableListOf<Rate>()
        val date = observation?.let { readObservationRates(it, rates) }
        if (rates.isNotEmpty()) {
            rates.add(Rate(Currency.CAD, BigDecimal.ONE))
        }
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

    private fun readObservationRates(
        observation: JsonElement,
        rates: MutableList<Rate>,
    ): LocalDate? {
        var date: LocalDate? = null
        observation.jsonObject.forEach { (name, value) ->
            if (name == BANK_OF_CANADA_DATE) {
                date = LocalDate.parse(value.text())
            } else {
                readRate(value, name)?.let(rates::add)
            }
        }
        return date
    }

    // The value is Canadian dollars per unit; the rate is units per dollar.
    private fun readRate(
        value: JsonElement,
        name: String,
    ): Rate? {
        val (currency, dollars) = value.bankOfCanadaValue(name)
        return currency?.let { Rate(it, BigDecimal.ONE.divide(dollars, MathContext.DECIMAL128)) }
    }
}
