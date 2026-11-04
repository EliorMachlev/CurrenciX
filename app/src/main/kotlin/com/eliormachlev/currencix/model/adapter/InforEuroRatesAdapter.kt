package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate

private const val FIELD_ISO_CODE = "isoA3Code"
private const val FIELD_VALUE = "value"

/** InforEuro's month of rates: `[{isoA3Code, value}, …]`, dated [date]. */
internal class InforEuroRatesAdapter(
    private val date: LocalDate,
) : ResponseAdapter<ExchangeRates> {
    override fun decode(json: JsonElement): ExchangeRates =
        json.arrayOrError(onError = ::errorResponse) { entries ->
            val rates = entries.mapNotNull(::parseEntry)
            ExchangeRates(
                success = rates.isNotEmpty(),
                error = null,
                base = Currency.EUR,
                date = date,
                rates = rates,
                provider = ApiProvider.INFOR_EURO,
            )
        }

    private fun parseEntry(entry: JsonElement): Rate? {
        val fields = entry.jsonObject
        return rateOrNull(
            fields[FIELD_ISO_CODE]?.let { Currency.fromString(it.text()) },
            fields[FIELD_VALUE]?.decimal(),
        )
    }

    private fun errorResponse(message: String?): ExchangeRates =
        ExchangeRates(
            success = false,
            error = message,
            base = Currency.EUR,
            date = date,
            rates = null,
            provider = ApiProvider.INFOR_EURO,
        )
}
