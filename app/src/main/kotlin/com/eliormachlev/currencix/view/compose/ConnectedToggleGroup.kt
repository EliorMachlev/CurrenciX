package com.eliormachlev.currencix.view.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliormachlev.currencix.util.rememberHapticOnClick

// The smallest a label shrinks to before it would be cut off, at the
// default font size. Divided by the font scale, so even at 200 % a long
// word ("Custom", "Month") still fits its share of the row whole.
private val MIN_LABEL_SIZE = 10.sp

// Tighter than a lone button's 24 dp sides: the buttons share one row, and a
// label still fits at large font sizes.
private val CONNECTED_BUTTON_PADDING = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

/**
 * A row of connected toggle buttons (Material 3 Expressive), one per
 * [options] entry, sharing the width, with the group's outer corners on its
 * ends; the checked one keeps its shape and takes the accent color.
 * [selected] is the checked option, or none. Tapping an option calls
 * [onToggle] with it and whether it is now checked (false when the checked
 * one is tapped again), so a caller can clear the choice or keep it.
 */
@Composable
internal fun <T> ConnectedToggleGroup(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onToggle: (option: T, checked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val minLabelSize = MIN_LABEL_SIZE / LocalDensity.current.fontScale
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            val toggle = rememberHapticOnClick { onToggle(option, !checked) }
            ToggleButton(
                checked = checked,
                onCheckedChange = { toggle() },
                shapes = connectedShapes(index, options.size),
                contentPadding = CONNECTED_BUTTON_PADDING,
                modifier = Modifier.weight(1f),
            ) {
                // Shrinks to fit rather than cut off ("Mont", "5" for "5 years") at large font sizes.
                Text(
                    label(option),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = minLabelSize, maxFontSize = LocalTextStyle.current.fontSize),
                )
            }
        }
    }
}

// The outer corners on the ends, square-ish joins in between. A checked
// button keeps its shape (Material's default rounds it fully): only its
// color says it's the one picked.
@Composable
private fun connectedShapes(
    index: Int,
    count: Int,
): ToggleButtonShapes {
    val shapes =
        when (index) {
            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
            count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
        }
    return shapes.copy(checkedShape = shapes.shape)
}
