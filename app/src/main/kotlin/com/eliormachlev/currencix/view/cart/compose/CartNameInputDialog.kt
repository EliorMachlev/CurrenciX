package com.eliormachlev.currencix.view.cart.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.view.compose.dialogs.LedgerDialogActions
import com.eliormachlev.currencix.view.compose.dialogs.LedgerDialogFrame

/**
 * Ledger-styled one-line text input dialog — Save-as and Rename both use this
 * shape, so the input field, blank-fallback, and action row all live in one
 * place. Blank submissions collapse to [R.string.cart_default_saved_name] so
 * every entry point produces a nameable, findable saved cart.
 */
@Composable
fun CartNameInputDialog(
    @StringRes titleRes: Int,
    initial: String,
    onOk: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(initial) { mutableStateOf(initial) }
    val defaultName = stringResource(id = R.string.cart_default_saved_name)
    LedgerDialogFrame(title = stringResource(id = titleRes), onDismiss = onDismiss) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text(stringResource(id = R.string.cart_save_name_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        LedgerDialogActions(
            confirmLabel = stringResource(id = android.R.string.ok),
            onCancel = onDismiss,
            onConfirm = {
                onOk(name.trim().ifBlank { defaultName })
                onDismiss()
            },
        )
    }
}
