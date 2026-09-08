package com.eliormachlev.currencix.view.preference

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.createWithHapticButtons
import com.eliormachlev.currencix.view.compose.AppTheme

// Padding mirrors the deleted dialog_graph_options.xml (margin3x horizontal,
// margin1x top, margin2x bottom) so the visual rhythm inside the AlertDialog
// content area is unchanged.
private val DialogHorizontalPadding = 24.dp
private val DialogTopPadding = 8.dp
private val DialogBottomPadding = 16.dp
private val RowVerticalPadding = 8.dp

class GraphOptionsDialog : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val db = Database(requireContext())
        val content =
            ComposeView(requireContext()).apply {
                setContent {
                    AppTheme {
                        GraphOptionsContent(db)
                    }
                }
            }
        return AlertDialog
            .Builder(requireContext())
            .setTitle(R.string.category_graph_options)
            .setView(content)
            .setPositiveButton(android.R.string.ok, null)
            .createWithHapticButtons()
    }
}

@Composable
private fun GraphOptionsContent(db: Database) {
    Column(
        modifier =
            Modifier.padding(
                start = DialogHorizontalPadding,
                end = DialogHorizontalPadding,
                top = DialogTopPadding,
                bottom = DialogBottomPadding,
            ),
    ) {
        SwitchRow(
            labelRes = R.string.graph_grid_title,
            initial = db.isChartGridEnabledBlocking(),
            onChange = db::setChartGridEnabled,
        )
        SwitchRow(
            labelRes = R.string.graph_x_axis_title,
            initial = db.isChartXAxisLabelEnabledBlocking(),
            onChange = db::setChartXAxisLabelEnabled,
        )
        SwitchRow(
            labelRes = R.string.graph_y_axis_title,
            initial = db.isChartYAxisLabelEnabledBlocking(),
            onChange = db::setChartYAxisLabelEnabled,
        )
        SwitchRow(
            labelRes = R.string.graph_highlight_extremes_title,
            initial = db.isChartHighlightExtremesEnabledBlocking(),
            onChange = db::setChartHighlightExtremesEnabled,
        )
        SwitchRow(
            labelRes = R.string.graph_highlight_period_change_title,
            initial = db.isChartHighlightPeriodChangeEnabledBlocking(),
            onChange = db::setChartHighlightPeriodChangeEnabled,
        )
    }
}

@Composable
private fun SwitchRow(
    labelRes: Int,
    initial: Boolean,
    onChange: (Boolean) -> Unit,
) {
    var checked by remember { mutableStateOf(initial) }
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.padding(vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = { new ->
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                checked = new
                onChange(new)
            },
        )
    }
}
