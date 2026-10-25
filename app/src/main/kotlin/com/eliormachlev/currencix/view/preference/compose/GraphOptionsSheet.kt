package com.eliormachlev.currencix.view.preference.compose

import androidx.annotation.StringRes
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
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.ChartSettings
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.view.compose.LedgerRow
import com.eliormachlev.currencix.view.compose.LedgerTrailing
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.dialogs.SheetListEnd
import com.eliormachlev.currencix.view.compose.switchState

/**
 * Ledger-styled graph options sheet — replaces the old
 * `GraphOptionsDialog` AlertDialog. Each toggle renders as a [LedgerRow] with
 * a [Switch] in the trailing slot, matching the picker sheet family
 * ([com.eliormachlev.currencix.view.preference.compose.ProviderPickerSheet]).
 *
 * Reads current values with [Pref.get] so the sheet lights up with the
 * persisted state on the first frame; no LiveData/Flow subscription needed
 * because the chart consumers already observe these keys.
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
        val options = graphOptions(db.chart)
        options.forEachIndexed { index, (labelRes, pref) ->
            GraphOptionRow(labelRes = labelRes, initial = pref.get(), onChange = pref::set, isLast = index == options.lastIndex)
        }
        SheetListEnd()
    }
}

// Each switch in the sheet (and on Settings' chart screen) and the setting it flips, top to bottom.
internal fun graphOptions(chart: ChartSettings): List<Pair<Int, Pref<Boolean>>> =
    listOf(
        R.string.graph_grid_title to chart.grid,
        R.string.graph_x_axis_title to chart.xAxisLabel,
        R.string.graph_y_axis_title to chart.yAxisLabel,
        R.string.graph_highlight_extremes_title to chart.highlightExtremes,
        R.string.graph_highlight_period_change_title to chart.highlightPeriodChange,
    )

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
        modifier = Modifier.switchState(checked),
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
                Switch(checked = checked, onCheckedChange = null)
            }
        },
    )
}
