package com.eliormachlev.currencix.view.preference.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.viewmodel.preference.BACKUP_MIN_PASSWORD_LENGTH
import com.eliormachlev.currencix.viewmodel.preference.BackupDialog
import com.eliormachlev.currencix.viewmodel.preference.BackupViewModel
import androidx.compose.foundation.text.KeyboardOptions as ComposeKeyboardOptions
import com.eliormachlev.currencix.view.compose.AppTheme as AppComposeTheme

private val DIALOG_FIELD_GAP = 12.dp
private val CHECKBOX_LABEL_GAP = 8.dp

// Two categories match the old PreferenceFragmentCompat-backed BackupFragment:
// Local backup (export) on top, Restore (import) below.
private enum class BackupSection {
    LOCAL,
    RESTORE,
}

/**
 * Full backup-and-restore screen — Compose replacement for the old
 * PreferenceFragmentCompat-backed BackupFragment. Renders two preference cards
 * and dispatches the active dialog off the [viewModel]'s dialog state. SAF
 * launcher fires and toast handling stay in the hosting Fragment because
 * ActivityResult contracts must be registered on a lifecycle owner and
 * [android.widget.Toast] wants a real Context anyway; the screen calls back
 * through [onLaunchExport] / [onLaunchImport] / [onImportConfirmed] once its
 * own dialog state has settled.
 */
@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    onLaunchExport: () -> Unit,
    onLaunchImport: () -> Unit,
    onImportConfirmed: (uri: android.net.Uri, password: CharArray?) -> Unit,
) {
    AppComposeTheme {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = dimensionResource(id = R.dimen.margin2x),
                    vertical = dimensionResource(id = R.dimen.margin1x),
                ),
        ) {
            item(key = BackupSection.LOCAL) {
                PreferenceSection(text = stringResource(id = R.string.backup_section_local)) {
                    PreferenceRow(
                        title = stringResource(id = R.string.backup_export_title),
                        summary = stringResource(id = R.string.backup_export_summary),
                        onClick = viewModel::openExportPasswordPrompt,
                    )
                }
            }
            item(key = BackupSection.RESTORE) {
                PreferenceSection(text = stringResource(id = R.string.backup_section_restore)) {
                    PreferenceRow(
                        title = stringResource(id = R.string.backup_import_title),
                        summary = stringResource(id = R.string.backup_import_summary),
                        onClick = onLaunchImport,
                    )
                }
            }
        }
    }

    when (val dialog = viewModel.dialog) {
        BackupDialog.ExportPassword ->
            ExportPasswordDialog(
                onCancel = viewModel::dismissDialog,
                onConfirm = { password ->
                    viewModel.stashExportPasswordAndDismiss(password)
                    onLaunchExport()
                },
            )
        is BackupDialog.ImportPassword ->
            ImportPasswordDialog(
                isRetry = dialog.isRetry,
                onCancel = viewModel::dismissDialog,
                onConfirm = { password ->
                    viewModel.confirmImportPassword(dialog.uri, password)
                },
            )
        is BackupDialog.ImportConfirm ->
            ImportConfirmDialog(
                onCancel = viewModel::dismissDialog,
                onConfirm = {
                    val password = dialog.password
                    viewModel.dismissDialog()
                    onImportConfirmed(dialog.uri, password)
                },
            )
        null -> Unit
    }
}

/**
 * Pre-export dialog: optional encrypt checkbox + a password field that reveals
 * when the checkbox is on. Empty-password + short-password reject stays here so
 * the confirm handler in the fragment only ever sees a valid (or null) password.
 */
@Composable
private fun ExportPasswordDialog(
    onCancel: () -> Unit,
    onConfirm: (CharArray?) -> Unit,
) {
    var encrypt by rememberSaveable { mutableStateOf(false) }
    var passwordText by rememberSaveable { mutableStateOf("") }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    val cancel = rememberHapticOnClick(onCancel)
    val confirm =
        rememberHapticOnClick {
            if (!encrypt) {
                onConfirm(null)
                return@rememberHapticOnClick
            }
            if (passwordText.length < BACKUP_MIN_PASSWORD_LENGTH) {
                errorRes = R.string.backup_password_too_short
                return@rememberHapticOnClick
            }
            onConfirm(passwordText.toCharArrayOffHeap())
        }
    AppComposeTheme {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(text = stringResource(id = R.string.backup_export_title)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = encrypt,
                            onCheckedChange = { encrypt = it },
                        )
                        Spacer(Modifier.width(CHECKBOX_LABEL_GAP))
                        Text(
                            text = stringResource(id = R.string.backup_encrypt_option),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    if (encrypt) {
                        Spacer(Modifier.height(DIALOG_FIELD_GAP))
                        PasswordField(
                            value = passwordText,
                            onValueChange = {
                                passwordText = it
                                errorRes = null
                            },
                            errorText = errorRes?.let { stringResource(id = it, BACKUP_MIN_PASSWORD_LENGTH) },
                        )
                    }
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
}

/**
 * Password prompt for decrypting an encrypted import. In [isRetry] state the
 * previous attempt failed decryption — surface the reason inline as a supporting
 * error on the field so the user knows what to do differently.
 */
@Composable
private fun ImportPasswordDialog(
    isRetry: Boolean,
    onCancel: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var passwordText by rememberSaveable { mutableStateOf("") }
    val cancel = rememberHapticOnClick(onCancel)
    val confirm = rememberHapticOnClick { onConfirm(passwordText.toCharArrayOffHeap()) }
    AppComposeTheme {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(text = stringResource(id = R.string.backup_password_prompt_title)) },
            text = {
                PasswordField(
                    value = passwordText,
                    onValueChange = { passwordText = it },
                    errorText = if (isRetry) stringResource(id = R.string.backup_password_wrong) else null,
                )
            },
            confirmButton = {
                TextButton(onClick = confirm) { Text(stringResource(id = android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = cancel) { Text(stringResource(id = android.R.string.cancel)) }
            },
        )
    }
}

/**
 * "Are you sure?" confirm before actually overwriting current settings. Kept
 * as its own dialog (rather than a second SAF-driven prompt) so users see the
 * destructive-action language on the same screen that will do the destruction.
 */
@Composable
private fun ImportConfirmDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cancel = rememberHapticOnClick(onCancel)
    val confirm = rememberHapticOnClick(onConfirm)
    AppComposeTheme {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(text = stringResource(id = R.string.backup_import_confirm_title)) },
            text = { Text(text = stringResource(id = R.string.backup_import_confirm_message)) },
            confirmButton = {
                TextButton(onClick = confirm) {
                    Text(stringResource(id = R.string.backup_import_confirm_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = cancel) { Text(stringResource(id = android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    errorText: String?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = ComposeKeyboardOptions(keyboardType = KeyboardType.Password),
        label = { Text(text = stringResource(id = R.string.backup_password_hint)) },
        isError = errorText != null,
        supportingText = errorText?.let { { Text(text = it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Char-by-char copy that avoids [String.toCharArray]'s intermediate allocation
 * chain landing in the JVM string pool. Compose's [OutlinedTextField] backs
 * the field with a plain [String] regardless, so the defense is weaker than
 * the old XML EditText flow, but the CharArray we hand off can still be
 * zero-filled after use — which is the part that matters for [BackupViewModel]'s
 * pending-password stash and for [com.eliormachlev.currencix.repository.BackupManager].
 */
private fun String.toCharArrayOffHeap(): CharArray {
    val out = CharArray(length)
    for (i in 0 until length) out[i] = this[i]
    return out
}
