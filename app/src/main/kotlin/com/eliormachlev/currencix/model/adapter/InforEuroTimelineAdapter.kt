package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.model.provider.api.InforEuroFailure
import com.eliormachlev.currencix.model.provider.api.InforEuroHistory
import com.eliormachlev.currencix.model.provider.api.InforEuroPeriod
import com.eliormachlev.currencix.model.provider.api.InforEuroPeriods
import java.time.LocalDate

/**
 * One currency's InforEuro history as the app's timeline, each rate standing
 * for every day of its period. Periods that start before the month of
 * [startDate] are left out. Its message when it has none.
 */
internal fun InforEuroHistory.toTimeline(
    startDate: LocalDate,
    endDate: LocalDate,
): Timeline {
    val base = Currency.EUR.iso4217Alpha()
    return when (this) {
        is InforEuroPeriods -> {
            val rates = sortedMapOf<LocalDate, Rate>()
            periods.forEach { it.spreadInto(rates, startDate.withDayOfMonth(1)) }
            Timeline(
                success = rates.isNotEmpty(),
                error = null,
                base = base,
                startDate = startDate,
                endDate = endDate,
                rates = rates,
                provider = ApiProvider.INFOR_EURO,
            )
        }

        is InforEuroFailure -> {
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
    }
}

// The period's rate on each of its days, when it starts no earlier than [from].
private fun InforEuroPeriod.spreadInto(
    rates: MutableMap<LocalDate, Rate>,
    from: LocalDate,
) {
    val rate = rateOrNull(currencyIso?.let(Currency::fromString), amount) ?: return
    val start = dateStart?.let { LocalDate.parse(it, SLASHED_DATE_FORMATTER) } ?: return
    val end = dateEnd?.let { LocalDate.parse(it, SLASHED_DATE_FORMATTER) } ?: return
    if (from.isAfter(start) || end.isBefore(start)) return
    dateSequence(start, end).forEach { rates[it] = rate }
}
