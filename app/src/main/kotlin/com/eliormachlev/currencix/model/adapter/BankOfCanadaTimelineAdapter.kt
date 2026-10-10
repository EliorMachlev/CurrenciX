package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.squareup.moshi.JsonReader
import java.io.IOException
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

internal class BankOfCanadaTimelineAdapter(
    private val base: Currency,
    private val symbol: Currency,
) : ResponseAdapter<Timeline>(Timeline::class.java) {
    @Synchronized
    @Throws(IOException::class)
    override fun fromJson(reader: JsonReader): Timeline? {
        // The whole document is read, whichever field answers first: Retrofit
        // rejects a response its adapter left partly unread.
        var result: Timeline? = null
        reader.beginObject()
        while (reader.hasNext()) {
            val found =
                when (reader.nextName()) {
                    "message" -> errorResponse(reader.nextString())
                    "observations" -> convertObservations(reader)
                    else -> null.also { reader.skipValue() }
                }
            result = result ?: found
        }
        reader.endObject()
        return result
    }

    private fun errorResponse(message: String?): Timeline =
        Timeline(
            success = false,
            error = message,
            base = null,
            startDate = null,
            endDate = null,
            rates = null,
            provider = ApiProvider.BANK_OF_CANADA,
        )

    private fun convertObservations(reader: JsonReader): Timeline {
        val rates = sortedMapOf<LocalDate, Rate>()
        reader.beginArray()
        while (reader.hasNext()) {
            convertObservation(reader)?.let { (date, rate) -> rates[date] = rate }
        }
        reader.endArray()
        return Timeline(
            success = rates.isNotEmpty(),
            error = if (rates.isEmpty()) NO_DATA_ERROR else null,
            base = base.iso4217Alpha(),
            startDate = rates.keys.firstOrNull(),
            endDate = rates.keys.lastOrNull(),
            rates = rates,
            provider = ApiProvider.BANK_OF_CANADA,
        )
    }

    // One day's observation: the symbol priced in the base, when the day has both.
    private fun convertObservation(reader: JsonReader): Pair<LocalDate, Rate>? {
        var date: LocalDate? = null
        var baseValue: BigDecimal? = null
        var symbolValue: BigDecimal? = null

        reader.beginObject()
        while (reader.hasNext()) {
            val nextName = reader.nextName()
            if (nextName == "d") {
                date = LocalDate.parse(reader.nextString())
            } else {
                val (currency, value) = readCurrencyValue(reader, nextName)
                if (currency == base) baseValue = value
                if (currency == symbol) symbolValue = value
            }
        }
        reader.endObject()
        return if (date != null && baseValue != null && symbolValue != null) {
            date to Rate(symbol, baseValue.divide(symbolValue, MathContext.DECIMAL128))
        } else {
            null
        }
    }

    private fun readCurrencyValue(
        reader: JsonReader,
        name: String,
    ): Pair<Currency?, BigDecimal> {
        val currency = Currency.fromString(name.bankOfCanadaIso())
        reader.beginObject()
        reader.skipName() // always "v"
        val value = BigDecimal(reader.nextString())
        reader.endObject()
        return currency to value
    }
}
