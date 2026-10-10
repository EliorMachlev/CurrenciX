package com.eliormachlev.currencix.view.compose.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.util.rememberHapticOnClick

private val HEADER_TO_MESSAGE_GAP = 12.dp

/**
 * The app's "are you sure?" and "here's what happened" prompts: a
 * [LedgerBottomSheet] with an optional [header] (what the prompt is about,
 * such as the chip being removed), the [message], then the way out
 * ([cancelLabel], Cancel by default) and [confirmLabel].
 *
 * A sheet, like the app's pickers, so every prompt that only asks for a
 * choice opens the same way and is swiped away the same way. Dialogs are
 * kept for prompts that take typed input (a name, a password, a fee).
 *
 * Set [destructive] when confirming removes or overwrites data: the confirm
 * button turns the error color.
 */
@Composable
fun LedgerConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    cancelLabel: String = stringResource(id = android.R.string.cancel),
    header: (@Composable () -> Unit)? = null,
) {
    LedgerBottomSheet(title = title, onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = SHEET_CONTENT_PADDING_H)) {
            if (header != null) {
                header()
                Spacer(Modifier.height(HEADER_TO_MESSAGE_GAP))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LedgerDialogActions(
                confirmLabel = confirmLabel,
                onConfirm = rememberHapticOnClick(onConfirm),
                onCancel = rememberHapticOnClick(onDismiss),
                destructive = destructive,
                cancelLabel = cancelLabel,
            )
        }
    }
}
