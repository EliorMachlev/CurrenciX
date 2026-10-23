package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.squareup.moshi.JsonReader
import java.math.BigDecimal
import java.time.LocalDate

/*
 * Converts a timeline rates object to a Map<LocalDate, Rate?>>
 * The API actually returns Map<LocalDate, List<Rate>>>, however, we only want one Rate per day.
 * This converter reduces the list.
 */
internal class FrankfurterAppTimelineAdapter(
    private val symbol: Currency,
) : ResponseAdapter<Map<LocalDate, Rate>>(DATED_RATES_TYPE) {
    @Synchronized
    override fun fromJson(reader: JsonReader): Map<LocalDate, Rate> =
        buildMap {
            reader.beginObject()
            // convert
            while (reader.hasNext()) {
                val date: LocalDate = LocalDate.parse(reader.nextName())
                var rate: Rate? = null
                reader.beginObject()
                // sometimes there's no rate yet, but an empty body or more than one rate, so check first
                while (reader.hasNext() && reader.peek() == JsonReader.Token.NAME) {
                    val name = Currency.fromString(reader.nextName())
                    val value: BigDecimal = BigDecimal(reader.nextString())
                    // Rate resolution order:
                    //   - change dkk to fok, when needed
                    //   - otherwise, require the symbol to match the one we requested
                    //   - anything else is discarded
                    rate =
                        if (name == Currency.DKK && symbol == Currency.FOK) {
                            Rate(Currency.FOK, value)
                        } else if (name == symbol) {
                            Rate(name, value)
                        } else {
                            null
                        }
                }
                if (rate != null) {
                    put(date, rate)
                }
                reader.endObject()
            }
            reader.endObject()
        }
}
