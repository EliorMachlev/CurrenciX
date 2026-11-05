package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.model.provider.api.ValetObservations
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/**
 * Bank of Canada's series for two currencies as the app's timeline: the
 * [symbol] priced in the [base] each day both are quoted. Its message when
 * it has none; null when the answer has neither.
 */
internal fun ValetObservations.toTimeline(
    base: Currency,
    symbol: Currency,
): Timeline? =
    when {
        observations != null -> {
            seriesTimeline(observations, base, symbol)
        }

        message != null -> {
            Timeline(
                success = false,
                error = message,
                base = null,
                startDate = null,
                endDate = null,
                rates = null,
                provider = ApiProvider.BANK_OF_CANADA,
            )
        }

        else -> {
            null
        }
    }

private fun seriesTimeline(
    observations: List<JsonObject>,
    base: Currency,
    symbol: Currency,
): Timeline {
    val rates = sortedMapOf<LocalDate, Rate>()
    observations.forEach { observation -> dayRate(observation, base, symbol)?.let { (date, rate) -> rates[date] = rate } }
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
private fun dayRate(
    observation: JsonObject,
    base: Currency,
    symbol: Currency,
): Pair<LocalDate, Rate>? {
    var date: LocalDate? = null
    var baseDollars: BigDecimal? = null
    var symbolDollars: BigDecimal? = null
    observation.forEach { (name, value) ->
        if (name == BANK_OF_CANADA_DATE) {
            date = LocalDate.parse(value.text())
        } else {
            val (currency, dollars) = value.bankOfCanadaValue(name)
            if (currency == base) baseDollars = dollars
            if (currency == symbol) symbolDollars = dollars
        }
    }
    val day = date
    val baseValue = baseDollars
    val symbolValue = symbolDollars
    return if (day != null && baseValue != null && symbolValue != null) {
        day to Rate(symbol, baseValue.divide(symbolValue, MathContext.DECIMAL128))
    } else {
        null
    }
}
