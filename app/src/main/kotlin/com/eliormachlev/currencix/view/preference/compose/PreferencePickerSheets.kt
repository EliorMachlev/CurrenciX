package com.eliormachlev.currencix.view.preference.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.view.compose.LedgerActiveChip
import com.eliormachlev.currencix.view.compose.LedgerRow
import com.eliormachlev.currencix.view.compose.LedgerTrailing
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.dialogs.LedgerPromptSheet
import com.eliormachlev.currencix.view.compose.dialogs.PromptMessage

// Small breather between the last picker row and the sheet edge so the final
// LedgerRow (which has no divider) doesn't butt against the system nav.
private val SHEET_BOTTOM_SPACE = 12.dp

/** What a single-choice picker offers: the [options], the one [selected] now, and how each reads. */
class Choices<T>(
    val options: List<T>,
    val selected: T?,
    val label: (T) -> String,
)

/**
 * Compose single-choice picker — one [LedgerRow] per option under a
 * [LedgerBottomSheet]. The currently-selected option trails a [LedgerActiveChip]
 * so the picker reads with the same "ink on paper" affordance as the rest of
 * the ledger surfaces (see [ProviderPickerSheet]). Selecting an option fires
 * [onPicked] and dismisses.
 */
@Composable
fun <T> SingleChoicePickerSheet(
    title: String,
    choices: Choices<T>,
    onDismiss: () -> Unit,
    onPicked: (T) -> Unit,
) {
    PickerSheet(title = title, onDismiss = onDismiss) {
        choices.options.forEachIndexed { index, option ->
            PickerRow(
                title = choices.label(option),
                description = null,
                isSelected = option == choices.selected,
                isLast = index == choices.options.lastIndex,
                onClick = {
                    onPicked(option)
                    onDismiss()
                },
            )
        }
    }
}

/**
 * Text-entry prompt — a sheet with an [OutlinedTextField], and [message]
 * above it when non-null. Shares its chrome with the cart's name prompt and
 * the fee editor, so every form-shaped prompt looks the same.
 */
@Composable
fun TextEntrySheet(
    title: String,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    message: String? = null,
    singleLine: Boolean = true,
) {
    val text = rememberTextFieldState(initialText)
    LedgerPromptSheet(
        title = title,
        confirmLabel = stringResource(id = android.R.string.ok),
        onConfirm = { onConfirm(text.text.toString()) },
        onDismiss = onDismiss,
    ) {
        if (!message.isNullOrBlank()) {
            PromptMessage(message)
            Spacer(Modifier.height(TEXT_ENTRY_MESSAGE_GAP))
        }
        OutlinedTextField(
            state = text,
            lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.Default,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val TEXT_ENTRY_MESSAGE_GAP = 12.dp

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
