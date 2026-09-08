package com.eliormachlev.currencix.view.main.compose

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.showWithHapticButtons
import com.eliormachlev.currencix.view.compose.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZoneOffset

private const val HISTORICAL_MIN_YEAR = 2010
private val DialogHorizontalPadding = 24.dp
private val DialogVerticalPadding = 16.dp
private val SwitchRowVerticalPadding = 8.dp

// Compose port of the historical-rates AlertDialog (formerly
// main_dialog_historical_rates.xml). Switch controls whether a historical date
// is used; the M3 DatePicker below it fades in/out with the switch. On OK,
// [onPick] fires with the selected LocalDate (or null if the switch is off).
fun showHistoricalDatePickerDialog(
    context: Context,
    initial: LocalDate?,
    onPick: (LocalDate?) -> Unit,
) {
    val state = HistoricalPickResult(initial)
    val content =
        ComposeView(context).apply {
            setContent {
                AppTheme {
                    HistoricalDatePickerContent(
                        initial = initial,
                        onStateChange = state::update,
                    )
                }
            }
        }
    AlertDialog
        .Builder(context)
        .setTitle(R.string.historical_rates_dialog_title)
        .setView(content)
        .setPositiveButton(android.R.string.ok) { _, _ -> onPick(state.snapshot()) }
        .setNegativeButton(android.R.string.cancel, null)
        .showWithHapticButtons()
}

private class HistoricalPickResult(
    initial: LocalDate?,
) {
    private var enabled: Boolean = initial != null
    private var date: LocalDate = initial ?: LocalDate.now()

    fun update(
        newEnabled: Boolean,
        newDate: LocalDate,
    ) {
        enabled = newEnabled
        date = newDate
    }

    fun snapshot(): LocalDate? = if (enabled) date else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoricalDatePickerContent(
    initial: LocalDate?,
    onStateChange: (enabled: Boolean, date: LocalDate) -> Unit,
) {
    var enabled by remember { mutableStateOf(initial != null) }
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = (initial ?: LocalDate.now()).toUtcMillis(),
            yearRange = HISTORICAL_MIN_YEAR..Year.now().value,
            initialDisplayMode = DisplayMode.Picker,
            selectableDates = PastOrTodaySelectableDates,
        )
    val haptics = LocalHapticFeedback.current
    val selectedDate = datePickerState.selectedDateMillis?.toLocalDate() ?: LocalDate.now()

    onStateChange(enabled, selectedDate)

    Column {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = DialogHorizontalPadding,
                    vertical = SwitchRowVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.historical_rates_dialog_toggle),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled,
                onCheckedChange = { new ->
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    enabled = new
                },
            )
        }
        AnimatedVisibility(visible = enabled) {
            Column {
                HorizontalDivider()
                DatePicker(
                    state = datePickerState,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(),
                    modifier = Modifier.padding(vertical = DialogVerticalPadding),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private object PastOrTodaySelectableDates : androidx.compose.material3.SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= System.currentTimeMillis()

    override fun isSelectableYear(year: Int): Boolean = year in HISTORICAL_MIN_YEAR..Year.now().value
}

private fun LocalDate.toUtcMillis(): Long = this.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
