package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.model.provider.api.AllRatesTodayAnswer
import com.eliormachlev.currencix.model.provider.api.AllRatesTodayFailure
import com.eliormachlev.currencix.model.provider.api.AllRatesTodayQuote
import com.eliormachlev.currencix.model.provider.api.AllRatesTodayQuotes
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// Its quotes' "time": ISO-8601 with an offset written "Z", "+00:00" or (the
// Wise style its API mirrors) "+0000", optionally with fractional seconds.
private val QUOTE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS][XXX][XX]")

// A plain date stands for itself (a day-grouped series may be dated so).
private const val PLAIN_DATE_LENGTH = 10

/** One `{rate, source, target, time}` entry of AllRatesToday's answer. */
private data class Quote(
    val target: Currency,
    val rate: BigDecimal,
    val time: OffsetDateTime?,
    val date: LocalDate?,
)

/**
 * The quotes in AllRatesToday's answer; [onError] gets the message of an
 * error object instead. Entries for a currency the app doesn't know are
 * skipped.
 */
private inline fun <T> AllRatesTodayAnswer.readQuotes(
    onError: (String?) -> T,
    onQuotes: (List<Quote>) -> T,
): T =
    when (this) {
        is AllRatesTodayQuotes -> onQuotes(quotes.mapNotNull { it.toQuote() })
        is AllRatesTodayFailure -> onError(text)
    }

private fun AllRatesTodayQuote.toQuote(): Quote? {
    val currency = target?.let(Currency::fromString) ?: return null
    val value = rate ?: return null
    val quoted = time?.let(::parseQuoteTime)
    return Quote(currency, value, quoted, quoted?.toLocalDate() ?: time?.let(::parsePlainDate))
}

// A quote's time; null for a plain date or anything unreadable.
private fun parseQuoteTime(text: String): OffsetDateTime? =
    if (text.length == PLAIN_DATE_LENGTH) null else runCatchingParse { OffsetDateTime.parse(text, QUOTE_TIME) }

// A quote dated by day alone ("2026-03-04").
private fun parsePlainDate(text: String): LocalDate? =
    if (text.length == PLAIN_DATE_LENGTH) runCatchingParse { LocalDate.parse(text) } else null

private inline fun <T> runCatchingParse(parse: () -> T): T? =
    try {
        parse()
    } catch (_: DateTimeParseException) {
        null
    }

/**
 * Every rate from [base] (`/api/v1/rates?source=…` with no target), dated by
 * the newest quote's time in the phone's zone, as the other hourly provider is.
 */
internal fun AllRatesTodayAnswer.toExchangeRates(base: Currency): ExchangeRates =
    readQuotes(
        onError = { message ->
            ExchangeRates(
                success = false,
                error = message,
                base = base,
                date = null,
                rates = null,
                provider = ApiProvider.ALL_RATES_TODAY,
            )
        },
        onQuotes = { quotes ->
            val newest = quotes.mapNotNull { it.time }.maxOrNull()?.atZoneSameInstant(ZoneId.systemDefault())
            val rates =
                buildList {
                    // The base itself, at 1, so it can be converted to and from like any other.
                    add(Rate(base, BigDecimal.ONE))
                    quotes.filter { it.target != base }.forEach { add(Rate(it.target, it.rate)) }
                    addFokFromDkkIfMissing()
                }
            ExchangeRates(
                success = quotes.isNotEmpty(),
                error = null,
                base = base,
                date = newest?.toLocalDate() ?: quotes.firstNotNullOfOrNull { it.date },
                time = newest?.toLocalTime()?.let { LocalTime.of(it.hour, it.minute) },
                rates = rates.takeIf { quotes.isNotEmpty() },
                provider = ApiProvider.ALL_RATES_TODAY,
            )
        },
    )

/**
 * One pair's daily series (`group=day`): one rate per date, the last quote
 * of a day winning. Asked for as DKK when the [symbol] is FOK (pegged 1:1,
 * and not listed upstream), and labelled FOK again here.
 */
internal fun AllRatesTodayAnswer.toTimeline(
    base: Currency,
    symbol: Currency,
): Timeline =
    readQuotes(
        onError = { message -> timeline(base, success = false, error = message, rates = null) },
        onQuotes = { quotes ->
            val asked = if (symbol == Currency.FOK) Currency.DKK else symbol
            val rates =
                quotes
                    .filter { it.target == asked && it.date != null }
                    .sortedWith(compareBy({ it.date }, { it.time }))
                    .associate { requireNotNull(it.date) to Rate(symbol, it.rate) }
            timeline(base, success = rates.isNotEmpty(), error = null, rates = rates.takeIf { it.isNotEmpty() })
        },
    )

private fun timeline(
    base: Currency,
    success: Boolean,
    error: String?,
    rates: Map<LocalDate, Rate>?,
) = Timeline(
    success = success,
    error = error,
    base = base.iso4217Alpha(),
    startDate = rates?.keys?.minOrNull(),
    endDate = rates?.keys?.maxOrNull(),
    rates = rates,
    provider = ApiProvider.ALL_RATES_TODAY,
)
