package com.eliormachlev.currencix.viewmodel.cart

import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.FeeCalculator
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.ocr.TessLanguage
import com.eliormachlev.currencix.ocr.forCurrency
import java.time.LocalDate

/** How fresh the rates on the phone are for a trip's destination. */
sealed interface RatesCheck {
    /** From [date], today's or yesterday's (a provider publishes once a day). */
    data class Fresh(
        val date: LocalDate,
    ) : RatesCheck

    /** From [date], older than that: worth refreshing before going. */
    data class Old(
        val date: LocalDate,
    ) : RatesCheck

    /** None for the destination's currency (none at all, or not from this provider). */
    data object Missing : RatesCheck
}

/** The receipt language for a trip's destination, and whether it's on the phone. */
data class LanguageCheck(
    val language: TessLanguage,
    val installed: Boolean,
    val downloading: Boolean,
)

/**
 * What to have ready before a trip, each checkable on the phone: fresh rates
 * for where it's to, the receipt language there (when it has one besides
 * English) and the card fees that apply to paying there from home (empty:
 * none set, so totals there show no fee).
 */
data class TripChecklist(
    val rates: RatesCheck,
    val language: LanguageCheck?,
    val fees: List<Fee>,
) {
    /** Whether everything is ready. */
    val ready: Boolean
        get() = rates is RatesCheck.Fresh && (language == null || language.installed) && fees.isNotEmpty()
}

// Rates older than this many days are worth a refresh before going.
private const val FRESH_DAYS = 1L

/** [trip]'s checklist on [today], from what's on the phone; null for a trip with no destination. */
internal fun tripChecklist(
    trip: Trip,
    rates: ExchangeRates?,
    fees: List<Fee>,
    today: LocalDate,
    language: (TessLanguage) -> LanguageCheck,
): TripChecklist? {
    val destination = trip.destination ?: return null
    val date = rates?.date
    val hasDestination = rates?.base == destination || rates?.rates?.any { it.currency == destination } == true
    val ratesCheck =
        when {
            date == null || !hasDestination -> RatesCheck.Missing
            date.isBefore(today.minusDays(FRESH_DAYS)) -> RatesCheck.Old(date)
            else -> RatesCheck.Fresh(date)
        }
    return TripChecklist(
        rates = ratesCheck,
        language = TessLanguage.forCurrency(destination)?.let(language),
        fees = FeeCalculator.applicableFees(fees, destination, trip.currency),
    )
}
