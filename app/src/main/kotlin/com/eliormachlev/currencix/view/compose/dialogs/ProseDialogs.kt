package com.eliormachlev.currencix.view.compose.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.eliormachlev.currencix.view.compose.ReadingDirection

// Material's AlertDialog and DatePickerDialog — same slots, shape, colours
// and spacing — laid out the way the app's language reads.
//
// The app declares supportsRtl="false", so a dialog's window always resolves
// left to right, whatever direction the composition around it asked for. And
// those dialogs' own layout (title, text, the buttons' order and side) can't
// be reached from their slots. So these set the direction inside the window,
// around the whole content.

/** [androidx.compose.material3.AlertDialog], laid out the way the language reads. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProseAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier, properties = properties) {
        ReadingDirection {
            Surface(
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(Modifier.padding(ALERT_PADDING)) {
                    Slot(AlertDialogDefaults.titleContentColor, MaterialTheme.typography.headlineSmall) {
                        Box(Modifier.padding(bottom = ALERT_TITLE_GAP).align(Alignment.Start)) { title() }
                    }
                    Slot(AlertDialogDefaults.textContentColor, MaterialTheme.typography.bodyMedium) {
                        Box(Modifier.weight(1f, fill = false).padding(bottom = ALERT_TEXT_GAP).align(Alignment.Start)) { text() }
                    }
                    Buttons(Modifier.align(Alignment.End), dismissButton, confirmButton)
                }
            }
        }
    }
}

/** [androidx.compose.material3.DatePickerDialog], laid out the way the language reads. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProseDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.wrapContentHeight(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ReadingDirection {
            Surface(
                modifier = Modifier.requiredWidth(DATE_PICKER_WIDTH).heightIn(max = DATE_PICKER_MAX_HEIGHT),
                shape = DatePickerDefaults.shape,
                color = DatePickerDefaults.colors().containerColor,
                tonalElevation = DatePickerDefaults.TonalElevation,
            ) {
                Column(verticalArrangement = Arrangement.SpaceBetween) {
                    // Weighted, so the buttons stay in view on a short screen.
                    Box(Modifier.weight(1f, fill = false)) { this@Column.content() }
                    Buttons(
                        Modifier.align(Alignment.End).padding(DATE_PICKER_BUTTONS_PADDING),
                        dismissButton,
                        confirmButton,
                    )
                }
            }
        }
    }
}

// Dismiss, then confirm, at the end edge — the right in English, the left in Hebrew.
@Composable
private fun Buttons(
    modifier: Modifier,
    dismissButton: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
) {
    Slot(MaterialTheme.colorScheme.primary, MaterialTheme.typography.labelLarge) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
            dismissButton?.invoke()
            confirmButton()
        }
    }
}

@Composable
private fun Slot(
    color: Color,
    style: TextStyle,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides color, LocalTextStyle provides style, content = content)
}

// Material's dialog measurements.
private val ALERT_PADDING = 24.dp
private val ALERT_TITLE_GAP = 16.dp
private val ALERT_TEXT_GAP = 24.dp
private val BUTTON_GAP = 8.dp
private val DATE_PICKER_WIDTH = 360.dp
private val DATE_PICKER_MAX_HEIGHT = 568.dp
private val DATE_PICKER_BUTTONS_PADDING = PaddingValues(bottom = 8.dp, end = 6.dp)
