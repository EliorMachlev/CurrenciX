package com.eliormachlev.currencix.view.compose.dialogs

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.rememberHapticOnClick
import androidx.compose.foundation.text.KeyboardOptions as ComposeKeyboardOptions

private val MESSAGE_TO_CONTENT_GAP = 12.dp
private val CONTENT_TO_ACTIONS_GAP = 20.dp
private val PASSWORD_FIELD_TO_TOGGLE_GAP = 8.dp

/**
 * Every prompt in the app: a [LedgerBottomSheet] with the prompt's
 * [content] (a message, a field, a form), then the way out ([cancelLabel],
 * Cancel by default) and [confirmLabel]. Prompts that ask for a choice and
 * prompts that take typed input open, swipe away and go back the same way;
 * the sheet makes room for the keyboard itself.
 *
 * Set [destructive] when confirming removes or overwrites data: the confirm
 * button turns the error color, and [confirmEnabled] = false greys it out
 * until the prompt is filled in. [leadingAction] goes on the far side of the
 * row from Cancel and confirm (the fee editor's Delete). Confirming doesn't
 * close the sheet by itself: [onConfirm] decides (a password that's too
 * short keeps it open). Set [scrollableBody] = false when [content] scrolls
 * by itself (a date range picker), as for [LedgerBottomSheet].
 */
@Composable
fun LedgerPromptSheet(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    cancelLabel: String = stringResource(id = android.R.string.cancel),
    leadingAction: (@Composable RowScope.() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    scrollableBody: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    LedgerBottomSheet(title = title, onDismiss = onDismiss, scrollableBody = scrollableBody) {
        Column(Modifier.padding(horizontal = SHEET_CONTENT_PADDING_H)) {
            content()
            Spacer(Modifier.height(CONTENT_TO_ACTIONS_GAP))
            LedgerPromptActions(
                confirmLabel = confirmLabel,
                onConfirm = rememberHapticOnClick(onConfirm),
                onCancel = rememberHapticOnClick(onDismiss),
                destructive = destructive,
                cancelLabel = cancelLabel,
                leading = leadingAction,
                confirmEnabled = confirmEnabled,
            )
        }
    }
}

/**
 * An "are you sure?" or "here's what happened" prompt: an optional
 * [header] (what it's about, such as the chip being removed) over the
 * [message]. See [LedgerPromptSheet] for the rest.
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
    LedgerPromptSheet(
        title = title,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = destructive,
        cancelLabel = cancelLabel,
    ) {
        if (header != null) {
            header()
            Spacer(Modifier.height(MESSAGE_TO_CONTENT_GAP))
        }
        PromptMessage(message)
    }
}

/**
 * A password prompt: the password field with its show / hide toggle, an
 * optional [message] above it and [errorText] under it (a wrong password,
 * so a retry says what went wrong without a second prompt). Used by the
 * backup import's password.
 */
@Composable
fun LedgerPasswordSheet(
    @StringRes titleRes: Int,
    @StringRes confirmLabelRes: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    @StringRes labelRes: Int = R.string.backup_password_hint,
    message: String? = null,
    errorText: String? = null,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    LedgerPromptSheet(
        title = stringResource(id = titleRes),
        confirmLabel = stringResource(id = confirmLabelRes),
        onConfirm = { onConfirm(password) },
        onDismiss = onDismiss,
    ) {
        if (!message.isNullOrBlank()) {
            PromptMessage(message)
            Spacer(Modifier.height(MESSAGE_TO_CONTENT_GAP))
        }
        PasswordFieldWithToggle(
            input = PasswordInput(password, visible, errorText),
            label = stringResource(id = labelRes),
            onValueChange = { password = it },
            onToggleVisibility = { visible = !visible },
        )
    }
}

/** A prompt's explanatory text, above its field or on its own. */
@Composable
fun PromptMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A prompt's action row: [leading] (if any) on one side; Cancel and
 * Confirm on the other, brass by default, error-tinted for [destructive]
 * confirms. [cancelLabel] names the way out when there is nothing to
 * cancel ("OK").
 */
@Composable
internal fun LedgerPromptActions(
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    destructive: Boolean = false,
    cancelLabel: String = stringResource(id = android.R.string.cancel),
    leading: (@Composable RowScope.() -> Unit)? = null,
    confirmEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (leading != null) Arrangement.SpaceBetween else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        Row {
            TextButton(onClick = onCancel) {
                Text(cancelLabel)
            }
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(
                    text = confirmLabel,
                    color = if (destructive && confirmEnabled) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        }
    }
}

/** A password field's state: what is typed, whether it is shown, and the error under it. */
@Immutable
data class PasswordInput(
    val value: String,
    val visible: Boolean,
    val errorText: String? = null,
)

/**
 * Password field row: [OutlinedTextField] with [PasswordVisualTransformation]
 * (or none when [PasswordInput.visible]) plus a trailing eye [IconButton].
 * Shared by [LedgerPasswordSheet] and the backup export's prompt so the
 * toggle placement + accessibility labels stay in one place.
 */
@Composable
internal fun PasswordFieldWithToggle(
    input: PasswordInput,
    label: String,
    onValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
) {
    val toggle = rememberHapticOnClick(onToggleVisibility)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input.value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation =
                if (input.visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = ComposeKeyboardOptions(keyboardType = KeyboardType.Password),
            label = { Text(text = label) },
            isError = input.errorText != null,
            supportingText = input.errorText?.let { { Text(text = it) } },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(PASSWORD_FIELD_TO_TOGGLE_GAP))
        val descRes =
            if (input.visible) R.string.backup_password_hide else R.string.backup_password_show
        IconButton(onClick = toggle) {
            Icon(
                painter =
                    painterResource(if (input.visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                contentDescription = stringResource(id = descRes),
            )
        }
    }
}
