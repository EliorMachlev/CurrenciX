package com.eliormachlev.currencix.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.LocalTime

@JsonClass(generateAdapter = true)
data class ExchangeRates(
    @field:Json(name = "success") val success: Boolean?,
    @field:Json(name = "error") val error: String?,
    @field:Json(name = "base") val base: Currency?,
    @field:Json(name = "date") val date: LocalDate?,
    @field:Json(name = "rates") val rates: List<Rate>?,
    @Transient val time: LocalTime? = null,
    @Transient val provider: ApiProvider? = null,
    // Set when these rates came from the fallback provider because this one
    // — the user's main provider — couldn't be reached.
    @Transient val fallbackFrom: ApiProvider? = null,
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
