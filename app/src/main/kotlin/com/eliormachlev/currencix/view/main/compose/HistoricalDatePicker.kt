package com.eliormachlev.currencix.view.main.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZoneOffset

private const val HISTORICAL_MIN_YEAR = 2010
private val SheetHorizontalPadding = 24.dp
private val DatePickerVerticalPadding = 16.dp
private val SwitchRowVerticalPadding = 8.dp
private val ActionRowVerticalPadding = 8.dp

/**
 * Compose-native historical-rates picker — a [LedgerBottomSheet] wrapping a
 * switch (use-historical toggle) and the M3 [DatePicker] beneath it. Replaces
 * the AlertDialog-hosted `showHistoricalDatePickerDialog` so callers no
 * longer need a `Context`; the sheet renders OK/Cancel action buttons in its
 * own footer, mirroring the AlertDialog semantics.
 *
 * On OK, [onPick] fires with the selected [LocalDate] (or null when the
 * switch is off), then [onDismiss] closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalDatePickerSheet(
    initial: LocalDate?,
    onPick: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    var enabled by remember { mutableStateOf(initial != null) }
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = (initial ?: LocalDate.now()).toUtcMillis(),
            yearRange = HISTORICAL_MIN_YEAR..Year.now().value,
            initialDisplayMode = DisplayMode.Picker,
            selectableDates = PastOrTodaySelectableDates,
        )
    LedgerBottomSheet(
        title = stringResource(id = R.string.historical_rates_dialog_title),
        onDismiss = onDismiss,
    ) {
        HistoricalToggleRow(enabled = enabled, onEnabledChange = { enabled = it })
        if (enabled) {
            HorizontalDivider()
            DatePicker(
                state = datePickerState,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(),
                modifier = Modifier.padding(vertical = DatePickerVerticalPadding),
            )
        }
        HistoricalActionRow(
            onNow = {
                onPick(null)
                onDismiss()
            },
            onCancel = onDismiss,
            onConfirm = {
                val picked =
                    if (enabled) {
                        datePickerState.selectedDateMillis?.toLocalDate() ?: LocalDate.now()
                    } else {
                        null
                    }
                // Selecting today with historical-mode on is equivalent to
                // turning historical off — the "latest" endpoint returns the
                // same data. Collapse both to null so downstream refreshes
                // hit the cheaper path and the banner clears.
                val normalized = if (picked == LocalDate.now()) null else picked
                onPick(normalized)
                onDismiss()
            },
        )
    }
}

@Composable
private fun HistoricalToggleRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SheetHorizontalPadding, vertical = SwitchRowVerticalPadding),
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
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onEnabledChange(new)
            },
        )
    }
}

@Composable
private fun HistoricalActionRow(
    onNow: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SheetHorizontalPadding, vertical = ActionRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // "Now" is the shortcut for "clear historical selection"; kept on the
        // leading edge so it doesn't compete with the confirm buttons on the
        // trailing edge for the primary tap target.
        TextButton(onClick = onNow) {
            Text(text = stringResource(id = R.string.historical_rates_dialog_now))
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onCancel) {
            Text(text = stringResource(id = android.R.string.cancel))
        }
        TextButton(onClick = onConfirm) {
            Text(text = stringResource(id = android.R.string.ok))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private object PastOrTodaySelectableDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= System.currentTimeMillis()

    override fun isSelectableYear(year: Int): Boolean = year in HISTORICAL_MIN_YEAR..Year.now().value
}

private fun LocalDate.toUtcMillis(): Long = this.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
