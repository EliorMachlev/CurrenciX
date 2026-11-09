package com.eliormachlev.currencix.model

import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.LocalTime

data class ExchangeRates(
    val success: Boolean?,
    val error: String?,
    val base: Currency?,
    val date: LocalDate?,
    val rates: List<Rate>?,
    val time: LocalTime? = null,
    val provider: ApiProvider? = null,
    // Set when these rates came from the fallback provider because this one
    // — the user's main provider — couldn't be reached.
    val fallbackFrom: ApiProvider? = null,
    // Set when these rates are an old cached answer served because the provider
    // couldn't be reached. Not stored; only keeps rate alerts from firing on them.
    val servedStale: Boolean = false,
)

fun ExchangeRates.rateFor(currency: Currency?): Rate? = currency?.let { c -> rates?.firstOrNull { it.currency == c } }

/** [amount] in [from] expressed in [to]; null when either rate is missing. */
fun ExchangeRates.convert(
    amount: BigDecimal,
    from: Currency,
    to: Currency,
): BigDecimal? {
    if (from == to) return amount
    val fromRate = rateFor(from)?.value ?: return null
    val toRate = rateFor(to)?.value ?: return null
    return amount.divide(fromRate, MathContext.DECIMAL128).multiply(toRate)
}
