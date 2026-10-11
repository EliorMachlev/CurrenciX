package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.squareup.moshi.JsonReader
import java.io.IOException
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

internal class BankOfCanadaRatesAdapter : ResponseAdapter<ExchangeRates>(ExchangeRates::class.java) {
    @Synchronized
    @Throws(IOException::class)
    override fun fromJson(reader: JsonReader): ExchangeRates? =
        reader.readObjectFirstOf { name ->
            when (name) {
                FIELD_MESSAGE -> errorResponse(reader.nextString())
                BANK_OF_CANADA_OBSERVATIONS -> readObservations(reader)
                else -> null.also { reader.skipValue() }
            }
        }

    private fun errorResponse(message: String?): ExchangeRates =
        ExchangeRates(
            success = false,
            error = message,
            base = null,
            date = null,
            rates = null,
            provider = ApiProvider.BANK_OF_CANADA,
        )

    // The first observation is the one asked for; any others are read past.
    private fun readObservations(reader: JsonReader): ExchangeRates {
        reader.beginArray()
        val result = convertObservation(reader)
        while (reader.hasNext()) reader.skipValue()
        reader.endArray()
        return result
    }

    private fun convertObservation(reader: JsonReader): ExchangeRates {
        var errorMessage: String? = null
        var date: LocalDate? = null
        val rates = mutableListOf<Rate>()

        if (reader.peek() == JsonReader.Token.END_ARRAY) {
            errorMessage = NO_DATA_ERROR
        } else {
            date = readObservationRates(reader, rates)
        }
        if (rates.isNotEmpty()) {
            rates.add(Rate(Currency.CAD, BigDecimal.ONE))
        }

        return ExchangeRates(
            success = errorMessage == null && rates.isNotEmpty() && date != null,
            error = errorMessage,
            base = Currency.CAD,
            date = date,
            rates = rates,
            provider = ApiProvider.BANK_OF_CANADA,
        )
    }

    private fun readObservationRates(
        reader: JsonReader,
        rates: MutableList<Rate>,
    ): LocalDate? {
        var date: LocalDate? = null
        reader.beginObject()
        while (reader.hasNext()) {
            val nextName = reader.nextName()
            if (nextName == BANK_OF_CANADA_DATE) {
                date = LocalDate.parse(reader.nextString())
            } else {
                readRate(reader, nextName)?.let(rates::add)
            }
        }
        reader.endObject()
        return date
    }

    // The value is Canadian dollars per unit; the rate is units per dollar.
    private fun readRate(
        reader: JsonReader,
        name: String,
    ): Rate? {
        val (currency, value) = reader.readBankOfCanadaValue(name)
        return currency?.let { Rate(it, BigDecimal.ONE.divide(value, MathContext.DECIMAL128)) }
    }
}
