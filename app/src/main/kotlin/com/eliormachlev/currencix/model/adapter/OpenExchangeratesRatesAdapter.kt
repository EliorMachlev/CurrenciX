package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.Instant
import java.time.ZoneId

private const val FIELD_TIMESTAMP = "timestamp"

/**
 * Open Exchange Rates' `{timestamp, base, rates: {USD: 1, …}}`, dated and
 * timed (to the minute) in the phone's zone; or `{message}` on failure.
 */
internal class OpenExchangeratesRatesAdapter : ResponseAdapter<ExchangeRates> {
    override fun decode(json: JsonElement): ExchangeRates? {
        val response = json as? JsonObject ?: return null
        val base = response[FIELD_BASE]?.let { Currency.fromString(it.text()) }
        val zoned =
            response[FIELD_TIMESTAMP]?.let { Instant.ofEpochSecond(it.jsonPrimitive.long).atZone(ZoneId.systemDefault()) }
        val date = zoned?.toLocalDate()
        val time = zoned?.toLocalTime()?.withSecond(0)?.withNano(0)
        val rates =
            buildList {
                response[FIELD_RATES]?.let { addAll(parseRates(it)) }
                addFokFromDkkIfMissing()
            }

        return ExchangeRates(
            success = rates.isNotEmpty(),
            error = if (rates.isEmpty()) response[FIELD_MESSAGE]?.text() else null,
            base = base,
            date = date,
            time = time,
            rates = rates.takeIf { it.isNotEmpty() },
            provider = ApiProvider.OPEN_EXCHANGERATES,
        )
    }

    private fun parseRates(rates: JsonElement): List<Rate> =
        rates.jsonObject.mapNotNull { (name, value) ->
            val rate = value.decimal()
            Currency.fromString(name)?.let { Rate(it, rate) }
        }
}
