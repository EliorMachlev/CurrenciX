package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Frankfurter's `{base, date, rates: {USD: 1.08, …}}`: the rates as a list,
 * with the [base] added (at 1) when the response leaves it out, and FOK
 * riding on DKK.
 */
internal class FrankfurterAppRatesAdapter(
    private val base: Currency,
) : ResponseAdapter<ExchangeRates> {
    override fun decode(json: JsonElement): ExchangeRates? {
        if (json is JsonNull) return null
        val response = json.jsonObject
        return ExchangeRates(
            success = null,
            error = null,
            base = response.field(FIELD_BASE)?.let { Currency.valueOf(it.text()) },
            date = response.field(FIELD_DATE)?.let { LocalDate.parse(it.text()) },
            rates = response.field(FIELD_RATES)?.let { rates(it.jsonObject) },
        )
    }

    private fun rates(rates: JsonObject): List<Rate> =
        buildList {
            rates.forEach { (name, value) ->
                val rate = value.decimal()
                Currency.fromString(name)?.let { add(Rate(it, rate)) }
            }
            // add base - but only if it's missing in the api response!
            if (none { rate -> rate.currency == base }) {
                add(Rate(base, BigDecimal.ONE))
            }
            addFokFromDkkIfMissing()
        }
}
