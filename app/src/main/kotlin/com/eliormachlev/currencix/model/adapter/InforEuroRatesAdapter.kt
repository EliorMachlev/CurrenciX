package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.squareup.moshi.JsonReader
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate

internal class InforEuroRatesAdapter(
    private val date: LocalDate,
) : ResponseAdapter<ExchangeRates>(ExchangeRates::class.java) {
    @Synchronized
    @Throws(IOException::class)
    override fun fromJson(reader: JsonReader): ExchangeRates =
        reader.readArrayOrError(
            onError = ::errorResponse,
        ) { r ->
            val rates =
                buildList {
                    while (r.hasNext()) {
                        parseEntry(r)?.let { add(it) }
                    }
                }
            ExchangeRates(
                success = rates.isNotEmpty(),
                error = null,
                base = Currency.EUR,
                date = date,
                rates = rates,
                provider = ApiProvider.INFOR_EURO,
            )
        }

    private fun parseEntry(reader: JsonReader): Rate? {
        reader.beginObject()
        var name: Currency? = null
        var value: BigDecimal? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "isoA3Code" -> name = Currency.fromString(reader.nextString())
                "value" -> value = BigDecimal(reader.nextString())
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return rateOrNull(name, value)
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
