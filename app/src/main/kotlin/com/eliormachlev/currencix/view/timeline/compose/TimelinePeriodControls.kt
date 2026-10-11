package com.eliormachlev.currencix.view.timeline.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.TIMELINE_MAX_YEARS
import com.eliormachlev.currencix.view.compose.ConnectedToggleGroup
import com.eliormachlev.currencix.view.compose.dialogs.LedgerPromptSheet
import com.eliormachlev.currencix.viewmodel.timeline.TimelineViewModel.Period
import com.eliormachlev.currencix.viewmodel.timeline.TimelineViewModel.Span
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Week, Month, Year, 5 years, then Custom, all in one row.
private val PERIODS = Period.entries

/** The period control's state and what it does. */
@Immutable
data class PeriodControl(
    val selected: Period,
    val customRange: Span?,
    val onPeriod: (Period) -> Unit,
    val onCustomRange: (start: LocalDate, end: LocalDate) -> Unit,
)

/**
 * One row of connected buttons: Week / Month / Year / 5 years / Custom.
 * Custom opens a date-range picker (back to [TIMELINE_MAX_YEARS] ago) and is
 * picked once dates are chosen; tapped again, it reopens the picker to change
 * them. The chosen dates show above, as the first and last rates' dates.
 */
@Composable
internal fun TimelinePeriodControls(
    control: PeriodControl,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    // A period stays picked when tapped again: there's always one shown.
    ConnectedToggleGroup(
        options = PERIODS,
        selected = control.selected,
        label = { stringResource(it.labelRes).replaceFirstChar { c -> c.titlecase() } },
        onToggle = { period, _ -> if (period == Period.CUSTOM) picking = true else control.onPeriod(period) },
        modifier = modifier,
    )
    if (picking) {
        RangePickerSheet(
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
            Period.CUSTOM -> R.string.timeline_custom
        }

@Composable
private fun RangePickerSheet(
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
    // A sheet, like the app's other prompts. The picker scrolls its months
    // itself, so the sheet doesn't, and it gets a bounded height to do it in.
    LedgerPromptSheet(
        title = stringResource(R.string.timeline_custom_range),
        confirmLabel = stringResource(android.R.string.ok),
        onConfirm = { if (start != null && end != null) onPicked(start.toLocalDate(), end.toLocalDate()) },
        onDismiss = onDismiss,
        confirmEnabled = start != null && end != null,
        scrollableBody = false,
    ) {
        DateRangePicker(
            state = state,
            title = null,
            showModeToggle = false,
            modifier = Modifier.fillMaxWidth().height(RANGE_PICKER_HEIGHT),
        )
    }
}

// Tall enough for the selected range and about two months of days.
private val RANGE_PICKER_HEIGHT = 480.dp

// Only days the app can chart: from [earliest] to [latest].
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
