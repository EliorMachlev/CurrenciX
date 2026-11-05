package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.provider.api.OpenExchangeratesRates
import java.time.Instant
import java.time.ZoneId

/**
 * Open Exchange Rates' answer as the app's rates, dated and timed (to the
 * minute) in the phone's zone; its message when there are no rates.
 */
internal fun OpenExchangeratesRates.toExchangeRates(): ExchangeRates {
    val zoned = timestamp?.let { Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()) }
    val known =
        buildList {
            rates?.forEach { (name, value) -> Currency.fromString(name)?.let { add(Rate(it, value)) } }
            addFokFromDkkIfMissing()
        }
    return ExchangeRates(
        success = known.isNotEmpty(),
        error = if (known.isEmpty()) message else null,
        base = base?.let(Currency::fromString),
        date = zoned?.toLocalDate(),
        time = zoned?.toLocalTime()?.withSecond(0)?.withNano(0),
        rates = known.takeIf { it.isNotEmpty() },
        provider = ApiProvider.OPEN_EXCHANGERATES,
    )
}
