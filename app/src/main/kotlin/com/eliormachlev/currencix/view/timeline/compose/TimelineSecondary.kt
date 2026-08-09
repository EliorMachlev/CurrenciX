package com.eliormachlev.currencix.view.timeline.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.viewmodel.timeline.TimelineViewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TEXT_WIDTH_PADDING_FACTOR = 1.25f
private const val RATE_DIFF_DECIMALS = 2
private val CONTENT_PADDING = TIMELINE_CONTENT_PADDING
private val DIVIDER_THICKNESS = 0.75.dp
private val DIFF_FONT_SIZE = 20.sp

private data class StatRowData(
    val label: String,
    val value: AnnotatedString?,
    val date: String?,
    val visible: Boolean,
)

@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun TimelineSecondary(
    ratePast: Pair<Map.Entry<LocalDate, Rate?>?, Int>?,
    rateCurrent: Pair<Map.Entry<LocalDate, Rate?>?, Int>?,
    diffPercent: BigDecimal?,
    ratesMax: Triple<Rate?, LocalDate?, Int>?,
    ratesAvg: Pair<Rate?, Int>?,
    ratesMin: Triple<Rate?, LocalDate?, Int>?,
    formatter: DateTimeFormatter,
    selectedPeriod: TimelineViewModel.Period,
    onPeriodSelected: (TimelineViewModel.Period) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    val pastRate = ratePast?.first?.value
    val currentRate = rateCurrent?.first?.value

    val maxLabel = stringResource(R.string.rate_max)
    val avgLabel = stringResource(R.string.rate_average)
    val minLabel = stringResource(R.string.rate_min)

    val rows =
        listOf(
            statRow(context, maxLabel, ratesMax?.first, ratesMax?.second, ratesMax?.third, formatter),
            statRow(context, avgLabel, ratesAvg?.first, null, ratesAvg?.second, formatter),
            statRow(context, minLabel, ratesMin?.first, ratesMin?.second, ratesMin?.third, formatter),
        )

    val labelWidth = maxLabelWidth(listOf(maxLabel, avgLabel, minLabel))

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(CONTENT_PADDING),
    ) {
        // Past / diff-% / current row
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(
                    text =
                        ratePast
                            ?.first
                            ?.key
                            ?.format(formatter)
                            .orEmpty(),
                    fontSize = TIMELINE_DATE_FONT_SIZE,
                    letterSpacing = TIMELINE_DATE_LETTER_SPACING_EM.sp,
                    color = secondaryColor,
                )
                if (pastRate != null) {
                    Text(
                        text =
                            combineValueAndSymbol(
                                context,
                                pastRate.value,
                                pastRate.currency.symbol(),
                                ratePast.second,
                            ),
                        fontSize = TIMELINE_RATE_VALUE_FONT_SIZE,
                    )
                }
            }
            Column(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text =
                        rateCurrent
                            ?.first
                            ?.key
                            ?.format(formatter)
                            .orEmpty(),
                    fontSize = TIMELINE_DATE_FONT_SIZE,
                    letterSpacing = TIMELINE_DATE_LETTER_SPACING_EM.sp,
                    color = secondaryColor,
                )
                if (currentRate != null) {
                    Text(
                        text =
                            combineValueAndSymbol(
                                context,
                                currentRate.value,
                                currentRate.currency.symbol(),
                                rateCurrent.second,
                            ),
                        fontSize = TIMELINE_RATE_VALUE_FONT_SIZE,
                    )
                }
            }
            if (diffPercent != null) {
                val positiveColor = colorResource(R.color.dollarBill)
                val negativeColor = MaterialTheme.colorScheme.error
                Text(
                    text = formatRateDiff(context, diffPercent, RATE_DIFF_DECIMALS),
                    fontSize = DIFF_FONT_SIZE,
                    color = if (diffPercent < BigDecimal.ZERO) negativeColor else positiveColor,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
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

        // Distribute max/avg/min evenly in the remaining vertical space instead of
        // stacking them at the top with a single flex spacer above the segmented
        // button — the row otherwise reads as top-aligned with a large gap below.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            rows.forEach { row ->
                if (row.visible) {
                    TimelineStatsRow(
                        label = row.label,
                        labelWidth = labelWidth,
                        value = row.value,
                        date = row.date,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        PeriodSegmentedButtons(
            selected = selectedPeriod,
            onSelected = onPeriodSelected,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PeriodSegmentedButtons(
    selected: TimelineViewModel.Period,
    onSelected: (TimelineViewModel.Period) -> Unit,
    modifier: Modifier = Modifier,
) {
    val periods = TimelineViewModel.Period.entries
    val labels =
        listOf(
            TimelineViewModel.Period.WEEK to stringResource(R.string.week),
            TimelineViewModel.Period.MONTH to stringResource(R.string.month),
            TimelineViewModel.Period.YEAR to stringResource(R.string.year),
        )
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        labels.forEachIndexed { index, (period, label) ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelected(period) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
            ) {
                Text(label.replaceFirstChar { it.titlecase() })
            }
        }
    }
}

private fun statRow(
    context: android.content.Context,
    label: String,
    rate: Rate?,
    date: LocalDate?,
    decimals: Int?,
    formatter: DateTimeFormatter,
): StatRowData {
    val visible = rate != null
    val value =
        if (rate != null && decimals != null) {
            combineValueAndSymbol(context, rate.value, rate.currency.symbol(), decimals)
        } else {
            null
        }
    return StatRowData(
        label = label,
        value = value,
        date = date?.format(formatter),
        visible = visible,
    )
}

// Equalise the label column across max/avg/min the way the old code did
// (paint.measureText * 1.25). All three labels share a single style so one
// TextMeasurer call per label is enough.
@Composable
private fun maxLabelWidth(labels: List<String>): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style =
        remember {
            TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
        }
    return remember(labels, style, measurer, density) {
        val maxPx =
            labels.maxOf { label ->
                measurer.measure(text = AnnotatedString(label.uppercase()), style = style).size.width
            }
        with(density) { (maxPx * TEXT_WIDTH_PADDING_FACTOR).toInt().toDp() }
    }
}
