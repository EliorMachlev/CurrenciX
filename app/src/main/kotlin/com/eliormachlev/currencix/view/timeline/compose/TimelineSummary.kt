package com.eliormachlev.currencix.view.timeline.compose

import android.content.Context
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.util.toHumanReadableNumber
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// The percent change is read to two places, as the screen shows it.
private const val PERCENT_DECIMALS = 2

// Between the summary's parts: TalkBack pauses at a comma.
private const val PART_SEPARATOR = ", "

/**
 * What the chart shows, for TalkBack, which can't follow a line: "Up 2.37%,
 * from 0.9153 euro on 13/09/25 to 0.9370 euro on 13/09/26, high 0.9521 euro
 * on 04/03/26, low 0.8998 euro on 22/11/25". Null until there's a first and
 * last rate.
 */
internal fun timelineSummary(
    context: Context,
    stats: TimelineStats,
    dates: DateTimeFormatter,
): String? {
    val past = stats.ratePast?.toSpoken(context) ?: return null
    val now = stats.rateCurrent?.toSpoken(context) ?: return null
    val parts =
        listOfNotNull(
            stats.diffPercent?.let { change(context, it) },
            context.getString(R.string.timeline_a11y_from_to, past.amount, past.date.format(dates), now.amount, now.date.format(dates)),
            stats.ratesMax?.toSpoken(context)?.let { context.getString(R.string.timeline_a11y_high, it.amount, it.date.format(dates)) },
            stats.ratesMin?.toSpoken(context)?.let { context.getString(R.string.timeline_a11y_low, it.amount, it.date.format(dates)) },
        )
    return parts.joinToString(PART_SEPARATOR)
}

// "Up 2.37%" / "Down 0.40%" / "Unchanged".
private fun change(
    context: Context,
    percent: BigDecimal,
): String {
    val rounded = percent.setScale(PERCENT_DECIMALS, RoundingMode.HALF_EVEN)
    if (rounded.signum() == 0) return context.getString(R.string.timeline_a11y_unchanged)
    val amount = rounded.abs().toHumanReadableNumber(context, decimalPlaces = PERCENT_DECIMALS)
    return context.getString(if (rounded.signum() > 0) R.string.timeline_a11y_up else R.string.timeline_a11y_down, amount)
}

// A rate as it's read out: "0.9370 euro", and its day.
private class SpokenRate(
    val amount: String,
    val date: LocalDate,
)

private fun spoken(
    context: Context,
    rate: Rate,
    date: LocalDate,
    decimals: Int,
) = SpokenRate("${rate.value.toHumanReadableNumber(context, decimalPlaces = decimals)} ${rate.currency.fullName(context)}", date)

// The first / last rate (date, rate, decimals).
private fun Pair<Map.Entry<LocalDate, Rate?>?, Int>.toSpoken(context: Context): SpokenRate? {
    val entry = first ?: return null
    val rate = entry.value ?: return null
    return spoken(context, rate, entry.key, second)
}

// The high / low (rate, date, decimals).
private fun Triple<Rate?, LocalDate?, Int>.toSpoken(context: Context): SpokenRate? {
    val rate = first ?: return null
    val date = second ?: return null
    return spoken(context, rate, date, third)
}
