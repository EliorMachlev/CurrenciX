package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.model.provider.api.FrankfurterTimeline
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Frankfurter's timeline as the app's: one rate per day, of the [symbol]
 * asked for (DKK read as FOK, when FOK was asked for). A day without it is
 * left out.
 */
internal fun FrankfurterTimeline.toTimeline(symbol: Currency): Timeline =
    Timeline(
        success = null,
        error = null,
        base = base,
        startDate = startDate,
        endDate = endDate,
        rates =
            rates?.let { days ->
                buildMap {
                    days.forEach { (day, quotes) ->
                        quotes.firstNotNullOfOrNull { (name, value) -> rateOf(symbol, name, value) }?.let { put(LocalDate.parse(day), it) }
                    }
                }
            },
    )

// Rate resolution order: DKK becomes FOK when FOK was asked for; otherwise
// only the symbol asked for counts.
private fun rateOf(
    symbol: Currency,
    name: String,
    value: BigDecimal,
): Rate? {
    val currency = Currency.fromString(name)
    return when {
        currency == Currency.DKK && symbol == Currency.FOK -> Rate(Currency.FOK, value)
        currency == symbol -> Rate(symbol, value)
        else -> null
    }
}
