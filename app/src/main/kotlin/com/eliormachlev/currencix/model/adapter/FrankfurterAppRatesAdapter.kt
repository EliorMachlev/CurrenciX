package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.squareup.moshi.JsonReader
import java.io.IOException
import java.math.BigDecimal

/*
 * Converts currency object to array of currencies.
 * Also removes some unwanted values and adds some wanted ones.
 */
internal class FrankfurterAppRatesAdapter(
    private val base: Currency,
) : ResponseAdapter<List<Rate>>(RATE_LIST_TYPE) {
    @Synchronized
    @Throws(IOException::class)
    override fun fromJson(reader: JsonReader): List<Rate> =
        buildList {
            reader.beginObject()
            // convert
            while (reader.hasNext()) {
                val name: String = reader.nextName()
                val value: BigDecimal = BigDecimal(reader.nextString())
                Currency.fromString(name)?.let { add(Rate(it, value)) }
            }
            reader.endObject()
            // add base - but only if it's missing in the api response!
            if (none { rate -> rate.currency == base }) {
                add(Rate(base, BigDecimal.ONE))
            }
            addFokFromDkkIfMissing()
        }
}
