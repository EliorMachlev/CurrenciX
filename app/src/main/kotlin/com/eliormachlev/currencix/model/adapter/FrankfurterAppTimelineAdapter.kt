package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate

private const val FIELD_START_DATE = "start_date"
private const val FIELD_END_DATE = "end_date"

/**
 * Frankfurter's timeline, `{base, start_date, end_date, rates: {date: {USD: 1.09}}}`,
 * with one rate per day: the [symbol] asked for (DKK read as FOK, when FOK
 * was asked for). A day without it is left out.
 */
internal class FrankfurterAppTimelineAdapter(
    private val symbol: Currency,
) : ResponseAdapter<Timeline> {
    override fun decode(json: JsonElement): Timeline? {
        if (json is JsonNull) return null
        val response = json.jsonObject
        return Timeline(
            success = null,
            error = null,
            base = response.field(FIELD_BASE)?.text(),
            startDate = response.field(FIELD_START_DATE)?.let { LocalDate.parse(it.text()) },
            endDate = response.field(FIELD_END_DATE)?.let { LocalDate.parse(it.text()) },
            rates = response.field(FIELD_RATES)?.let { rates(it.jsonObject) },
        )
    }

    private fun rates(days: JsonObject): Map<LocalDate, Rate> =
        buildMap {
            days.forEach { (day, quotes) ->
                // A day can have no rate yet, or more than the one asked for.
                quotes.jsonObject.firstNotNullOfOrNull { (name, value) -> rate(name, value) }?.let { put(LocalDate.parse(day), it) }
            }
        }

    // Rate resolution order: DKK becomes FOK when FOK was asked for;
    // otherwise only the symbol asked for counts.
    private fun rate(
        name: String,
        value: JsonElement,
    ): Rate? {
        val currency = Currency.fromString(name)
        return when {
            currency == Currency.DKK && symbol == Currency.FOK -> Rate(Currency.FOK, value.decimal())
            currency == symbol -> Rate(symbol, value.decimal())
            else -> null
        }
    }
}
