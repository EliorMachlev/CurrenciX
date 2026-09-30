package com.eliormachlev.currencix.view.timeline.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.TIMELINE_MAX_YEARS
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.viewmodel.timeline.TimelineViewModel.Period
import com.eliormachlev.currencix.viewmodel.timeline.TimelineViewModel.Span
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val CONTROLS_GAP = 8.dp
private val CHIP_ICON_SIZE = 18.dp

// The presets in the segmented row; CUSTOM is the chip under it.
private val PRESETS = listOf(Period.WEEK, Period.MONTH, Period.YEAR, Period.FIVE_YEARS)

/** The period control's state and what it does. */
@Immutable
data class PeriodControl(
    val selected: Period,
    val customRange: Span?,
    val onPeriod: (Period) -> Unit,
    val onCustomRange: (start: LocalDate, end: LocalDate) -> Unit,
)

/**
 * Week / Month / Year / 5 years, and under them a "Custom range" chip that
 * opens a date-range picker (back to [TIMELINE_MAX_YEARS] ago); once chosen,
 * the chip shows the range.
 */
@Composable
internal fun TimelinePeriodControls(
    control: PeriodControl,
    formatter: DateTimeFormatter,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CONTROLS_GAP)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PRESETS.forEachIndexed { index, period ->
                SegmentedButton(
                    selected = period == control.selected,
                    onClick = rememberHapticOnClick { control.onPeriod(period) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = PRESETS.size),
                ) { Text(stringResource(period.labelRes).replaceFirstChar { it.titlecase() }, maxLines = 1) }
            }
        }
        val custom = control.selected == Period.CUSTOM
        FilterChip(
            selected = custom,
            onClick = { picking = true },
            label = {
                val range = control.customRange
                Text(
                    if (custom && range != null) {
                        stringResource(R.string.timeline_range, range.start.format(formatter), range.end.format(formatter))
                    } else {
                        stringResource(R.string.timeline_custom_range)
                    },
                )
            },
            leadingIcon = { Icon(painterResource(R.drawable.ic_event), contentDescription = null, Modifier.size(CHIP_ICON_SIZE)) },
        )
    }
    if (picking) {
        RangePickerDialog(
            initial = control.customRange,
            onPicked = { start, end ->
                control.onCustomRange(start, end)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

private val Period.labelRes: Int
    get() =
        when (this) {
            Period.WEEK -> R.string.week
            Period.MONTH -> R.string.month
            Period.YEAR -> R.string.year
            Period.FIVE_YEARS -> R.string.timeline_five_years
            Period.CUSTOM -> R.string.timeline_custom_range
        }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(
    initial: Span?,
    onPicked: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val earliest = remember(today) { today.minusYears(TIMELINE_MAX_YEARS) }
    val state =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = initial?.start?.toUtcMillis(),
            initialSelectedEndDateMillis = initial?.end?.toUtcMillis(),
            yearRange = earliest.year..today.year,
            selectableDates = PastDates(earliest, today),
        )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (start != null && end != null) onPicked(start.toLocalDate(), end.toLocalDate()) },
                enabled = start != null && end != null,
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.fillMaxWidth())
    }
}

// Only days the app can chart: from [earliest] to [latest].
@OptIn(ExperimentalMaterial3Api::class)
private class PastDates(
    private val earliest: LocalDate,
    private val latest: LocalDate,
) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val day = utcTimeMillis.toLocalDate()
        return !day.isBefore(earliest) && !day.isAfter(latest)
    }

    override fun isSelectableYear(year: Int): Boolean = year in earliest.year..latest.year
}

// The picker works in UTC midnights.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
