package com.eliormachlev.currencix.view.timeline.compose

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.ReadingDirection
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val RATE_DIFF_DECIMALS = 2

// Real minus sign (U+2212): the same width as "+", unlike a hyphen.
private const val MINUS_SIGN = '\u2212'
private const val PLUS_SIGN = '+'
private val CONTENT_PADDING = TIMELINE_CONTENT_PADDING
private val DIVIDER_THICKNESS = 0.75.dp
private val DIFF_FONT_SIZE = 20.sp
private val DIFF_ICON_SIZE = 20.dp
private val DIFF_ICON_GAP = 4.dp
private val TILE_GAP = 8.dp
private val TILE_RADIUS = 16.dp
private val TILE_PADDING_H = 12.dp
private val TILE_PADDING_V = 10.dp
private const val TILE_LABEL_LETTER_SPACING_EM = 0.1f

private data class StatRowData(
    val label: String,
    val value: AnnotatedString?,
    val date: String?,
)

/**
 * The readouts under the chart, as the view model publishes them: the rate
 * at the start of the range (or at the scrub) and now, the change between
 * them, and the range's max / average / median / min. Each carries the
 * number of decimals to show.
 */
@Immutable
data class TimelineStats(
    val ratePast: Pair<Map.Entry<LocalDate, Rate?>?, Int>? = null,
    val rateCurrent: Pair<Map.Entry<LocalDate, Rate?>?, Int>? = null,
    val diffPercent: BigDecimal? = null,
    val ratesMax: Triple<Rate?, LocalDate?, Int>? = null,
    val ratesAvg: Pair<Rate?, Int>? = null,
    val ratesMed: Pair<Rate?, Int>? = null,
    val ratesMin: Triple<Rate?, LocalDate?, Int>? = null,
)

@Composable
internal fun TimelineSecondary(
    stats: TimelineStats,
    formatter: DateTimeFormatter,
    period: PeriodControl,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pastRate = stats.ratePast?.first?.value

    val maxLabel = stringResource(R.string.rate_max)
    val avgLabel = stringResource(R.string.rate_average)
    val medLabel = stringResource(R.string.rate_median)
    val minLabel = stringResource(R.string.rate_min)

    val stat = StatFormat(context, formatter)
    val rows =
        listOf(
            stat.row(maxLabel, stats.ratesMax?.first, stats.ratesMax?.second, stats.ratesMax?.third),
            stat.row(avgLabel, stats.ratesAvg?.first, null, stats.ratesAvg?.second),
            stat.row(medLabel, stats.ratesMed?.first, null, stats.ratesMed?.second),
            stat.row(minLabel, stats.ratesMin?.first, stats.ratesMin?.second, stats.ratesMin?.third),
        )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(CONTENT_PADDING),
    ) {
        // Past / diff-% / current row
        Box(modifier = Modifier.fillMaxWidth()) {
            DatedRateColumn(
                dated = stats.ratePast,
                horizontalAlignment = Alignment.Start,
                formatter = formatter,
                secondaryColor = secondaryColor,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            DatedRateColumn(
                dated = stats.rateCurrent,
                horizontalAlignment = Alignment.End,
                formatter = formatter,
                secondaryColor = secondaryColor,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
            stats.diffPercent?.let { PercentChange(it, Modifier.align(Alignment.Center)) }
        }

        // Divider — hidden until there's a past rate, mirroring the pre-Compose behaviour.
        if (pastRate != null) {
            Box(
                modifier =
                    Modifier
                        .padding(vertical = CONTENT_PADDING)
                        .fillMaxWidth()
                        .height(DIVIDER_THICKNESS)
                        .background(MaterialTheme.colorScheme.outlineVariant),
            )
        } else {
            Box(modifier = Modifier.height(CONTENT_PADDING * 2))
        }

        TilesAndPeriod(rows, period, formatter)
    }
}

// 2×2 tiles: the extremes (with their dates) on top, the centre (average,
// median) below — compact, so the chart above gets the height — then the
// period controls. Both follow the language (in Hebrew the maximum sits on
// the right, labels right-aligned); the past / current row above them stays
// left to right, in step with the chart's time axis.
@Composable
private fun TilesAndPeriod(
    rows: List<StatRowData>,
    period: PeriodControl,
    formatter: DateTimeFormatter,
) {
    ReadingDirection {
        Column {
            StatGrid(max = rows[0], min = rows[3], avg = rows[1], med = rows[2])
            Spacer(Modifier.height(CONTENT_PADDING))
            TimelinePeriodControls(control = period, formatter = formatter, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DatedRateColumn(
    dated: Pair<Map.Entry<LocalDate, Rate?>?, Int>?,
    horizontalAlignment: Alignment.Horizontal,
    formatter: DateTimeFormatter,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rate = dated?.first?.value
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
    ) {
        Text(
            text =
                dated
                    ?.first
                    ?.key
                    ?.format(formatter)
                    .orEmpty(),
            fontSize = TIMELINE_DATE_FONT_SIZE,
            letterSpacing = TIMELINE_DATE_LETTER_SPACING_EM.sp,
            color = secondaryColor,
        )
        if (rate != null) {
            Text(
                text = combineValueAndSymbol(context, rate.value, rate.currency.symbol(), dated.second),
                fontSize = TIMELINE_RATE_VALUE_FONT_SIZE,
            )
        }
    }
}

// Formats a statistic tile: the rate with its symbol at the chart's
// decimals, and the day it was reached ([dates]), when there is one.
private class StatFormat(
    private val context: Context,
    private val dates: DateTimeFormatter,
) {
    fun row(
        label: String,
        rate: Rate?,
        date: LocalDate?,
        decimals: Int?,
    ): StatRowData {
        val value =
            if (rate != null && decimals != null) {
                combineValueAndSymbol(context, rate.value, rate.currency.symbol(), decimals)
            } else {
                null
            }
        return StatRowData(label = label, value = value, date = date?.format(dates))
    }
}

// "↗ −7.55%": trend arrow + signed percent, green up / red down. The arrow
// carries the direction for anyone who can't tell the two colors apart.
@Composable
private fun PercentChange(
    diff: BigDecimal,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val falling = diff < BigDecimal.ZERO
    // rate_diff_positive is a darker green (#1B7343) on the cream light theme
    // and dollarBill (#85BB65) on dark — dollarBill alone fails AA on cream.
    val color = if (falling) MaterialTheme.colorScheme.error else colorResource(R.color.rate_diff_positive)
    val text = remember(diff) { formatPercentChange(context, diff) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(if (falling) R.drawable.ic_trending_down else R.drawable.ic_trending_up),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(DIFF_ICON_SIZE),
        )
        Spacer(Modifier.width(DIFF_ICON_GAP))
        Text(text = text, fontSize = DIFF_FONT_SIZE, color = color)
    }
}

/** "+2.10%" / "−7.55%": sign, magnitude, percent — no inner spaces. */
internal fun formatPercentChange(
    context: Context,
    diff: BigDecimal,
): String {
    val sign = if (diff < BigDecimal.ZERO) MINUS_SIGN else PLUS_SIGN
    return "$sign${diff.abs().toHumanReadableNumber(context, RATE_DIFF_DECIMALS)}%"
}

@Composable
private fun StatGrid(
    max: StatRowData,
    min: StatRowData,
    avg: StatRowData,
    med: StatRowData,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        StatTileRow(max, min)
        StatTileRow(avg, med)
    }
}

@Composable
private fun StatTileRow(
    start: StatRowData,
    end: StatRowData,
) {
    // Equal heights, so a tile without a date lines up with its neighbour.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        StatTile(start, Modifier.weight(1f).fillMaxHeight())
        StatTile(end, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun StatTile(
    stat: StatRowData,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(TILE_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = TILE_PADDING_H, vertical = TILE_PADDING_V),
    ) {
        Text(
            text = stat.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = TILE_LABEL_LETTER_SPACING_EM.em,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Figures keep their left-to-right reading ("0.356 $", "29/05/26"),
        // sitting at the tile's start edge either way.
        Ltr {
            Text(
                text = stat.value ?: AnnotatedString(PLACEHOLDER_VALUE),
                fontSize = TIMELINE_RATE_VALUE_FONT_SIZE,
                maxLines = 1,
            )
        }
        if (stat.date != null) {
            Ltr {
                Text(
                    text = stat.date,
                    fontSize = TIMELINE_DATE_FONT_SIZE,
                    letterSpacing = TIMELINE_DATE_LETTER_SPACING_EM.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val PLACEHOLDER_VALUE = "—"
