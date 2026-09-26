package com.eliormachlev.currencix.view.preference.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.LedgerActiveChip
import com.eliormachlev.currencix.view.compose.LedgerRow
import com.eliormachlev.currencix.view.compose.LedgerTrailing
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.dialogs.LedgerDialogActions
import com.eliormachlev.currencix.view.compose.dialogs.LedgerDialogFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Small breather between the last picker row and the sheet edge so the final
// LedgerRow (which has no divider) doesn't butt against the system nav.
private val SHEET_BOTTOM_SPACE = 12.dp

/**
 * Compose single-choice picker — one [LedgerRow] per option under a
 * [LedgerBottomSheet]. The currently-selected option trails a [LedgerActiveChip]
 * so the picker reads with the same "ink on paper" affordance as the rest of
 * the ledger surfaces (see [ProviderPickerDialog]). Selecting an option fires
 * [onPicked] and dismisses.
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
    PickerSheet(title = title, onDismiss = onDismiss) {
        options.forEachIndexed { index, option ->
            PickerRow(
                title = label(option),
                description = null,
                isSelected = option == selected,
                isLast = index == options.lastIndex,
                onClick = {
                    onPicked(option)
                    onDismiss()
                },
            )
        }
    }
}

/**
 * Compose single-choice picker with a descriptive second line under each
 * option — the "explainer" variant. Same [LedgerBottomSheet] chrome as
 * [SingleChoicePickerDialog], but each row stacks title + description.
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
    PickerSheet(title = title, onDismiss = onDismiss) {
        options.forEachIndexed { index, option ->
            PickerRow(
                title = label(option),
                description = description(option),
                isSelected = option == selected,
                isLast = index == options.lastIndex,
                onClick = {
                    onPicked(option)
                    onDismiss()
                },
            )
        }
    }
}

/**
 * Compose text-entry dialog — an OutlinedTextField wrapped in the shared
 * [LedgerDialogFrame] with OK/Cancel actions. [message] shows above the
 * field when non-null. Focus + soft-keyboard is requested on show so the user
 * can start typing immediately; the whole initial value is left in place so
 * an existing key can be edited without a full retype.
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
    LedgerDialogFrame(title = title, onDismiss = onDismiss) {
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        LedgerDialogActions(
            confirmLabel = stringResource(id = android.R.string.ok),
            onConfirm = confirm,
            onCancel = cancel,
        )
    }
    // Dialog content composes the frame after opening, so the FocusRequester
    // isn't attached synchronously — delay one frame before asking for focus,
    // otherwise it silently no-ops on the first show.
    LaunchedEffect(Unit) {
        scope.launch {
            delay(FOCUS_DELAY_MS)
            runCatching { focusRequester.requestFocus() }
        }
    }
}

private val TEXT_ENTRY_MESSAGE_GAP = 12.dp
private const val FOCUS_DELAY_MS = 50L

// Shared shell + row shape used by both picker variants (simple / explainer)
// and by the language picker. Callers just describe rows; the sheet chrome,
// active-chip, and terminal spacer are hoisted here so the three surfaces
// stay in visual lockstep and any future picker (e.g. currency) can drop in.

@Composable
internal fun PickerSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    LedgerBottomSheet(title = title, onDismiss = onDismiss) {
        content()
        Spacer(Modifier.height(SHEET_BOTTOM_SPACE))
    }
}

@Composable
internal fun PickerRow(
    title: String,
    description: String?,
    isSelected: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    LedgerRow(
        onClick = onClick,
        showDivider = !isLast,
        label = {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        value =
            if (isSelected) {
                {
                    LedgerTrailing {
                        LedgerActiveChip(text = stringResource(id = R.string.picker_active_chip))
                    }
                }
            } else {
                null
            },
    )
}
