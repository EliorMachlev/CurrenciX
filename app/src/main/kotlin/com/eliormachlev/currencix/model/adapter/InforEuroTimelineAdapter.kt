package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate

private const val FIELD_CURRENCY = "currencyIso"
private const val FIELD_AMOUNT = "amount"
private const val FIELD_DATE_START = "dateStart"
private const val FIELD_DATE_END = "dateEnd"

/**
 * One currency's InforEuro history: `[{currencyIso, amount, dateStart, dateEnd}, …]`,
 * each rate standing for every day from its start to its end. Periods that
 * start before the month of [startDate] are left out.
 */
internal class InforEuroTimelineAdapter(
    private val startDate: LocalDate,
    private val endDate: LocalDate,
) : ResponseAdapter<Timeline> {
    private val base: String = Currency.EUR.iso4217Alpha()

    override fun decode(json: JsonElement): Timeline =
        json.arrayOrError(onError = ::errorResponse) { entries ->
            val rates = buildMap { entries.forEach { processEntry(it, this) } }
            Timeline(
                success = rates.isNotEmpty(),
                error = null,
                base = base,
                startDate = startDate,
                endDate = endDate,
                rates = rates.toSortedMap(compareBy { it }),
                provider = ApiProvider.INFOR_EURO,
            )
        }

    private fun processEntry(
        entry: JsonElement,
        rates: MutableMap<LocalDate, Rate>,
    ) {
        val fields = entry.jsonObject
        val rate = rateOrNull(fields[FIELD_CURRENCY]?.let { Currency.fromString(it.text()) }, fields[FIELD_AMOUNT]?.decimal())
        val dateStart = fields[FIELD_DATE_START]?.let { LocalDate.parse(it.text(), SLASHED_DATE_FORMATTER) }
        val dateEnd = fields[FIELD_DATE_END]?.let { LocalDate.parse(it.text(), SLASHED_DATE_FORMATTER) }
        if (rate == null || dateStart == null || dateEnd == null) return
        // inclusive: before-or-equal start
        if (startDate.withDayOfMonth(1).isAfter(dateStart)) return

        var date: LocalDate = dateEnd
        while (!date.isBefore(dateStart)) {
            rates[date] = rate
            date = date.minusDays(1)
        }
    }

    private fun errorResponse(message: String?): Timeline =
        Timeline(
            success = false,
            error = message,
            base = base,
            startDate = null,
            endDate = null,
            rates = null,
            provider = ApiProvider.INFOR_EURO,
        )
}
