package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.provider.api.FrankfurterRates
import java.math.BigDecimal

/**
 * Frankfurter's rates as the app's: a list, with the [base] added (at 1)
 * when the response leaves it out, FOK riding on DKK, and currencies the
 * app doesn't know left out.
 */
internal fun FrankfurterRates.toExchangeRates(base: Currency): ExchangeRates =
    ExchangeRates(
        success = null,
        error = null,
        base = this.base?.let(Currency::valueOf),
        date = date,
        rates =
            rates?.let { quoted ->
                buildList {
                    quoted.forEach { (name, value) -> Currency.fromString(name)?.let { add(Rate(it, value)) } }
                    if (none { rate -> rate.currency == base }) add(Rate(base, BigDecimal.ONE))
                    addFokFromDkkIfMissing()
                }
            },
    )
