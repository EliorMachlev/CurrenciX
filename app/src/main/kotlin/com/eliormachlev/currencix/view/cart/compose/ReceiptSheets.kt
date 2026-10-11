package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.ReceiptLine
import com.eliormachlev.currencix.util.formatCartAmount
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.SupportingText
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.dialogs.LedgerPromptSheet
import com.eliormachlev.currencix.view.compose.dialogs.PromptMessage
import com.eliormachlev.currencix.view.compose.theme.Spacing

/** "Reading the receipt…" while the photo is read; it takes a few seconds. */
@Composable
fun ReceiptReadingSheet(onDismiss: () -> Unit) {
    LedgerBottomSheet(title = stringResource(R.string.receipt_scan), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.XXLarge, vertical = Spacing.Large)) {
            PromptMessage(stringResource(R.string.receipt_reading))
            Spacer(Modifier.height(Spacing.Medium))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

/** The first scan where the reader isn't on the device yet: download it? */
@Composable
fun ReceiptSetupOfferSheet(
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    LedgerPromptSheet(
        title = stringResource(R.string.receipt_scan),
        confirmLabel = stringResource(R.string.receipt_setup_download),
        onConfirm = onDownload,
        onDismiss = onDismiss,
    ) {
        PromptMessage(stringResource(R.string.receipt_setup_message))
    }
}

/** The reader downloading, [fraction] of the way when known; closing it lets it carry on unseen. */
@Composable
fun ReceiptSetupProgressSheet(
    fraction: Float?,
    onDismiss: () -> Unit,
) {
    LedgerBottomSheet(title = stringResource(R.string.receipt_scan), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.XXLarge, vertical = Spacing.Large)) {
            PromptMessage(stringResource(R.string.receipt_setup_progress))
            Spacer(Modifier.height(Spacing.Medium))
            if (fraction == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The items found on a receipt, each ticked, prices in the cart's
 * [currency]: untick what isn't wanted, then add the rest ([onAdd]). With
 * none found, says so. [languagePending]: the app language's OCR data is
 * still downloading, so names were read as English.
 */
@Composable
fun ReceiptReviewSheet(
    lines: List<ReceiptLine>,
    currency: Currency?,
    languagePending: Boolean,
    onAdd: (List<ReceiptLine>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val chosen = remember(lines) { lines.indices.toList().toMutableStateList() }
    LedgerPromptSheet(
        title = stringResource(R.string.receipt_scan),
        confirmLabel = pluralStringResource(R.plurals.receipt_add, chosen.size, chosen.size),
        onConfirm = { onAdd(chosen.sorted().map(lines::get)) },
        onDismiss = onDismiss,
        confirmEnabled = chosen.isNotEmpty(),
    ) {
        if (lines.isEmpty()) PromptMessage(stringResource(R.string.receipt_none_found))
        lines.forEachIndexed { index, line ->
            val ticked = index in chosen
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = ticked, role = Role.Checkbox) { on -> if (on) chosen += index else chosen -= index }
                        .padding(vertical = Spacing.Tiny),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Checkbox(checked = ticked, onCheckedChange = null)
                Text(line.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Ltr { Text(context.formatCartAmount(line.price, currency), style = MaterialTheme.typography.bodyLarge) }
            }
        }
        Spacer(Modifier.height(Spacing.Small))
        SupportingText(stringResource(if (languagePending) R.string.receipt_language_pending else R.string.receipt_check_hint))
    }
}
