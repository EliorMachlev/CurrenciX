package com.eliormachlev.currencix.view.preference.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.view.compose.LedgerRow
import com.eliormachlev.currencix.view.compose.LedgerTrailing
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet

private val SHEET_BOTTOM_SPACE = 12.dp

/**
 * Ledger-styled graph options sheet — replaces the old
 * `GraphOptionsDialog` AlertDialog. Each toggle renders as a [LedgerRow] with
 * a [Switch] in the trailing slot, matching the picker sheet family
 * ([com.eliormachlev.currencix.view.preference.compose.ProviderPickerDialog]).
 *
 * Reads current values via [Database]'s blocking accessors so the sheet
 * lights up with the persisted state on the first frame — the same shape the
 * old AlertDialog used; no LiveData/Flow subscription needed because the
 * chart consumers already observe these keys.
 */
@Composable
fun GraphOptionsSheet(
    db: Database,
    onDismiss: () -> Unit,
) {
    LedgerBottomSheet(
        title = stringResource(id = R.string.category_graph_options),
        onDismiss = onDismiss,
    ) {
        GraphOptionRow(
            labelRes = R.string.graph_grid_title,
            initial = db.isChartGridEnabledBlocking(),
            onChange = db::setChartGridEnabled,
            isLast = false,
        )
        GraphOptionRow(
            labelRes = R.string.graph_x_axis_title,
            initial = db.isChartXAxisLabelEnabledBlocking(),
            onChange = db::setChartXAxisLabelEnabled,
            isLast = false,
        )
        GraphOptionRow(
            labelRes = R.string.graph_y_axis_title,
            initial = db.isChartYAxisLabelEnabledBlocking(),
            onChange = db::setChartYAxisLabelEnabled,
            isLast = false,
        )
        GraphOptionRow(
            labelRes = R.string.graph_highlight_extremes_title,
            initial = db.isChartHighlightExtremesEnabledBlocking(),
            onChange = db::setChartHighlightExtremesEnabled,
            isLast = false,
        )
        GraphOptionRow(
            labelRes = R.string.graph_highlight_period_change_title,
            initial = db.isChartHighlightPeriodChangeEnabledBlocking(),
            onChange = db::setChartHighlightPeriodChangeEnabled,
            isLast = true,
        )
        Spacer(Modifier.height(SHEET_BOTTOM_SPACE))
    }
}

@Composable
private fun GraphOptionRow(
    @StringRes labelRes: Int,
    initial: Boolean,
    onChange: (Boolean) -> Unit,
    isLast: Boolean,
) {
    var checked by remember { mutableStateOf(initial) }
    val haptics = LocalHapticFeedback.current
    val commit: (Boolean) -> Unit = { new ->
        checked = new
        onChange(new)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LedgerRow(
        onClick = { commit(!checked) },
        showDivider = !isLast,
        label = {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        },
        value = {
            LedgerTrailing {
                Switch(checked = checked, onCheckedChange = commit)
            }
        },
    )
}
