package com.eliormachlev.currencix.view.compose.dialogs

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.AppTheme
import androidx.compose.foundation.text.KeyboardOptions as ComposeKeyboardOptions

// Ledger dialog chrome — a paper-toned Surface holds the title + body + trailing
// text buttons, so the whole thing reads like the rest of the ledger UI rather
// than the stock M3 AlertDialog's elevated tonal-container feel. Sits on the
// same surface color as the underlying screen; no elevation.
private val DIALOG_CORNER_RADIUS = 4.dp
private val DIALOG_HORIZONTAL_PADDING = 24.dp
private val DIALOG_VERTICAL_PADDING = 20.dp
private val DIALOG_TITLE_TO_BODY_GAP = 12.dp
private val DIALOG_BODY_TO_ACTIONS_GAP = 20.dp
private const val DIALOG_MAX_WIDTH_FRACTION = 0.92f
private val PASSWORD_FIELD_TO_TOGGLE_GAP = 8.dp

/**
 * Ledger-styled confirm dialog — [BasicAlertDialog] wrapping a paper-toned
 * [Surface] with a small-radius shape and two trailing text buttons (Cancel /
 * Confirm). Replaces stock [androidx.compose.material3.AlertDialog] for
 * confirm-shape callers (delete, overwrite, wipe, …) so every confirm across
 * the app reads with the same "ink on paper" aesthetic.
 *
 * Set [destructive] when the confirm action removes or overwrites data — the
 * confirm button tints to `error` to keep the pending-loss cue immediate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val cancel = rememberHapticOnClick(onDismiss)
    val confirm = rememberHapticOnClick(onConfirm)
    LedgerDialogFrame(title = title, onDismiss = onDismiss) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LedgerDialogActions(
            confirmLabel = confirmLabel,
            onConfirm = confirm,
            onCancel = cancel,
            destructive = destructive,
        )
    }
}

/**
 * Ledger-styled password dialog — [BasicAlertDialog] with a title, an
 * [OutlinedTextField] under [PasswordVisualTransformation], a "show password"
 * eye toggle, and Cancel / Continue actions. Reused by both backup export and
 * import password prompts so both surfaces share the same chrome + toggle
 * placement.
 *
 * [errorText] renders as the field's supporting text and flips [OutlinedTextField]
 * into error state so wrong-password retries surface inline without a second
 * dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerPasswordDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    label: String = stringResource(id = R.string.backup_password_hint),
    message: String? = null,
    errorText: String? = null,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    val cancel = rememberHapticOnClick(onDismiss)
    val confirm = rememberHapticOnClick { onConfirm(password) }
    LedgerDialogFrame(title = title, onDismiss = onDismiss) {
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(DIALOG_TITLE_TO_BODY_GAP))
        }
        PasswordFieldWithToggle(
            value = password,
            onValueChange = { password = it },
            label = label,
            errorText = errorText,
            visible = visible,
            onToggleVisibility = { visible = !visible },
        )
        LedgerDialogActions(
            confirmLabel = confirmLabel,
            onConfirm = confirm,
            onCancel = cancel,
        )
    }
}

/**
 * Convenience overload that resolves both title and confirm-button label from
 * string resources — the shape most callers actually want since these strings
 * are always localized.
 */
@Composable
fun LedgerPasswordDialog(
    @StringRes titleRes: Int,
    @StringRes confirmLabelRes: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    @StringRes labelRes: Int = R.string.backup_password_hint,
    message: String? = null,
    errorText: String? = null,
) {
    LedgerPasswordDialog(
        title = stringResource(id = titleRes),
        confirmLabel = stringResource(id = confirmLabelRes),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        label = stringResource(id = labelRes),
        message = message,
        errorText = errorText,
    )
}

/**
 * Shared chrome for every ledger dialog — [BasicAlertDialog] wraps a paper
 * [Surface] with a small-radius shape, hoists the title, and stacks whatever
 * body the caller emits. Kept private so callers never re-implement the
 * padding / typography / theme wrap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LedgerDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AppTheme {
        BasicAlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(DIALOG_MAX_WIDTH_FRACTION),
        ) {
            Surface(
                shape = RoundedCornerShape(DIALOG_CORNER_RADIUS),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = DIALOG_HORIZONTAL_PADDING,
                                vertical = DIALOG_VERTICAL_PADDING,
                            ),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(DIALOG_TITLE_TO_BODY_GAP))
                    content()
                }
            }
        }
    }
}

/**
 * Trailing action row — Cancel + Confirm text buttons, brass by default, error
 * tint for [destructive] confirms. Optional [leadingDestructiveLabel] +
 * [onLeadingDestructive] surface a delete-shape action on the row's leading
 * edge (e.g. the "Delete" button in the fee editor) so callers don't have to
 * re-wire the split-row layout per site. Pass [showConfirm] = false for
 * cancel-only footers (e.g. the fee picker's "close" bar). Extracted so every
 * dialog body drops this shape in as a single call.
 */
@Composable
internal fun LedgerDialogActions(
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    destructive: Boolean = false,
    leadingDestructiveLabel: String? = null,
    onLeadingDestructive: (() -> Unit)? = null,
    showConfirm: Boolean = true,
) {
    Spacer(Modifier.height(DIALOG_BODY_TO_ACTIONS_GAP))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingDestructiveLabel != null && onLeadingDestructive != null) {
            TextButton(onClick = onLeadingDestructive) {
                Text(
                    text = leadingDestructiveLabel,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.weight(1f))
        }
        TextButton(onClick = onCancel) {
            Text(stringResource(id = android.R.string.cancel))
        }
        if (showConfirm) {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        }
    }
}

/**
 * Password field row: [OutlinedTextField] with [PasswordVisualTransformation]
 * (or none when [visible]) plus a trailing eye [IconButton]. Shared by
 * [LedgerPasswordDialog] and any future password-entry surface so the toggle
 * placement + accessibility labels stay in one place.
 */
@Composable
internal fun PasswordFieldWithToggle(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorText: String?,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
) {
    val toggle = rememberHapticOnClick(onToggleVisibility)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation =
                if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = ComposeKeyboardOptions(keyboardType = KeyboardType.Password),
            label = { Text(text = label) },
            isError = errorText != null,
            supportingText = errorText?.let { { Text(text = it) } },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(PASSWORD_FIELD_TO_TOGGLE_GAP))
        val descRes =
            if (visible) R.string.backup_password_hide else R.string.backup_password_show
        IconButton(onClick = toggle) {
            Icon(
                imageVector =
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = stringResource(id = descRes),
            )
        }
    }
}
