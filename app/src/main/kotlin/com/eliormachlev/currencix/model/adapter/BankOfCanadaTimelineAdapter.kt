package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/**
 * Bank of Canada's series for two currencies, `{observations: [{d, FXUSDCAD: {v}, …}]}`,
 * as the [symbol] priced in the [base] each day both are quoted; or
 * `{message}` when it has none to give.
 */
internal class BankOfCanadaTimelineAdapter(
    private val base: Currency,
    private val symbol: Currency,
) : ResponseAdapter<Timeline> {
    override fun decode(json: JsonElement): Timeline? =
        json.firstOfFields { name, value ->
            when (name) {
                FIELD_MESSAGE -> errorResponse(value.text())
                BANK_OF_CANADA_OBSERVATIONS -> convertObservations(value)
                else -> null
            }
        }

    private fun errorResponse(message: String?): Timeline =
        Timeline(
            success = false,
            error = message,
            base = null,
            startDate = null,
            endDate = null,
            rates = null,
            provider = ApiProvider.BANK_OF_CANADA,
        )

    private fun convertObservations(observations: JsonElement): Timeline {
        val rates = sortedMapOf<LocalDate, Rate>()
        observations.jsonArray.forEach { observation ->
            convertObservation(observation)?.let { (date, rate) -> rates[date] = rate }
        }
        return Timeline(
            success = rates.isNotEmpty(),
            error = if (rates.isEmpty()) NO_DATA_ERROR else null,
            base = base.iso4217Alpha(),
            startDate = rates.keys.firstOrNull(),
            endDate = rates.keys.lastOrNull(),
            rates = rates,
            provider = ApiProvider.BANK_OF_CANADA,
        )
    }

    // One day's observation: the symbol priced in the base, when the day has both.
    private fun convertObservation(observation: JsonElement): Pair<LocalDate, Rate>? {
        var date: LocalDate? = null
        var baseValue: BigDecimal? = null
        var symbolValue: BigDecimal? = null
        observation.jsonObject.forEach { (name, value) ->
            if (name == BANK_OF_CANADA_DATE) {
                date = LocalDate.parse(value.text())
            } else {
                val (currency, dollars) = value.bankOfCanadaValue(name)
                if (currency == base) baseValue = dollars
                if (currency == symbol) symbolValue = dollars
            }
        }
        val day = date
        val baseDollars = baseValue
        val symbolDollars = symbolValue
        return if (day != null && baseDollars != null && symbolDollars != null) {
            day to Rate(symbol, baseDollars.divide(symbolDollars, MathContext.DECIMAL128))
        } else {
            null
        }
    }
}
