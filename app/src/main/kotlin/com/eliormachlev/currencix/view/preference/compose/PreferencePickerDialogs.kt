package com.eliormachlev.currencix.view.preference.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Row visuals shared by both single-choice pickers so the two variants
// (simple / explainer) stay in visual lockstep with the row layout used by
// the compose choice-picker dialogs elsewhere in the app.
private val ROW_HORIZONTAL_PADDING = 8.dp
private val ROW_VERTICAL_PADDING = 12.dp
private val RADIO_TEXT_GAP = 12.dp
private const val DESCRIPTION_ALPHA = 0.7f

/**
 * Compose single-choice picker — one radio row per option, title only.
 * Selecting an option fires [onPicked] with its index and dismisses.
 */
@Composable
fun <T> SingleChoicePickerDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onPicked: (T) -> Unit,
) {
    ChoiceDialogFrame(title = title, onDismiss = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth()) {
            items(options) { option ->
                ChoiceRow(
                    checked = option == selected,
                    onClick = {
                        onPicked(option)
                        onDismiss()
                    },
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

/**
 * Compose single-choice picker with a descriptive second line under each
 * option — the "explainer" variant. Matches the old `showChoiceExplainerDialog`
 * shape used for the keyboard-type picker.
 */
@Composable
fun <T> SingleChoiceExplainerPickerDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    description: (T) -> String,
    onDismiss: () -> Unit,
    onPicked: (T) -> Unit,
) {
    ChoiceDialogFrame(title = title, onDismiss = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth()) {
            items(options) { option ->
                ChoiceRow(
                    checked = option == selected,
                    onClick = {
                        onPicked(option)
                        onDismiss()
                    },
                ) {
                    Column {
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = description(option),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DESCRIPTION_ALPHA),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compose text-entry dialog — an OutlinedTextField wrapped in an AlertDialog
 * with OK/Cancel actions. [message] shows above the field when non-null.
 * Focus + soft-keyboard is requested on show so the user can start typing
 * immediately; the whole initial value is left in place so an existing key
 * can be edited without a full retype.
 */
@Composable
fun TextEntryDialog(
    title: String,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    message: String? = null,
    singleLine: Boolean = true,
) {
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val confirm = rememberHapticOnClick { onConfirm(text) }
    val cancel = rememberHapticOnClick(onDismiss)
    AppTheme {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = title) },
            text = {
                Column {
                    if (!message.isNullOrBlank()) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(TEXT_ENTRY_MESSAGE_GAP))
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = singleLine,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = confirm) { Text(stringResource(id = android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = cancel) { Text(stringResource(id = android.R.string.cancel)) }
            },
        )
    }
    // AlertDialog composes its content the frame after opening, so the
    // FocusRequester isn't attached synchronously — delay one frame before
    // asking for focus, otherwise it silently no-ops on the first show.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        scope.launch {
            delay(FOCUS_DELAY_MS)
            runCatching { focusRequester.requestFocus() }
        }
    }
}

private val TEXT_ENTRY_MESSAGE_GAP = 12.dp
private const val FOCUS_DELAY_MS = 50L

/**
 * Frame shared by both single-choice picker dialogs: title + cancel button +
 * theme wrap. Keeps AlertDialog wiring in one spot so both simple/explainer
 * variants stay identical in chrome.
 */
@Composable
internal fun ChoiceDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val cancel = rememberHapticOnClick(onDismiss)
    AppTheme {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = title) },
            text = content,
            confirmButton = {
                TextButton(onClick = cancel) { Text(stringResource(id = android.R.string.cancel)) }
            },
        )
    }
}

/**
 * One radio + text row used inside a [ChoiceDialogFrame] body. Whole row is
 * haptic-clickable; the radio is presentational (isClickable=false) so tap
 * targets stay row-sized instead of the tiny circle.
 */
@Composable
internal fun ChoiceRow(
    checked: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hapticClickable(onClick = onClick)
                .padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = ROW_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RADIO_TEXT_GAP),
    ) {
        RadioButton(selected = checked, onClick = null)
        Column(Modifier.weight(1f)) { content() }
    }
}
