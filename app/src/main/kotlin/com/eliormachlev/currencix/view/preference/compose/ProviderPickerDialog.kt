package com.eliormachlev.currencix.view.preference.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.view.compose.LedgerActiveChip
import com.eliormachlev.currencix.view.compose.LedgerRow
import com.eliormachlev.currencix.view.compose.LedgerTrailing
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet

/**
 * Compose data-provider picker — a [LedgerBottomSheet] with one [LedgerRow]
 * per [ApiProvider]. The currently-selected provider trails a
 * [LedgerActiveChip] rather than a radio button, so the picker reads with the
 * same "ink on paper" affordance the rest of the ledger uses for active-value
 * indication. Tapping a row commits the selection and dismisses.
 *
 * Replaces the previous [androidx.compose.material3.AlertDialog]-hosted radio
 * list per task #160.
 */
@Composable
fun ProviderPickerDialog(
    selected: ApiProvider?,
    onDismiss: () -> Unit,
    onPicked: (ApiProvider) -> Unit,
) {
    val context = LocalContext.current
    LedgerBottomSheet(
        title = stringResource(id = R.string.api_title),
        onDismiss = onDismiss,
    ) {
        ApiProvider.entries.forEachIndexed { index, provider ->
            val isLast = index == ApiProvider.entries.lastIndex
            LedgerRow(
                onClick = {
                    onPicked(provider)
                    onDismiss()
                },
                showDivider = !isLast,
                label = {
                    ProviderRowLabel(provider = provider)
                },
                value =
                    if (provider == selected) {
                        {
                            LedgerTrailing {
                                LedgerActiveChip(text = stringResource(id = R.string.provider_active_chip))
                            }
                        }
                    } else {
                        null
                    },
            )
        }
        Spacer(Modifier.height(PROVIDER_SHEET_BOTTOM_SPACE))
    }
}

// Small breather between the last picker row and the system navigation bar so
// the final divider doesn't butt directly against the sheet edge.
private val PROVIDER_SHEET_BOTTOM_SPACE = 12.dp

/**
 * Label slot for the provider picker row — name (title), short description,
 * and optional hint (e.g. "requires API key"). Extracted so the picker's row
 * lambda stays readable and the label composition can be reused if a future
 * screen (e.g. an About-this-provider surface) wants the same three-line block.
 */
@Composable
private fun RowScope.ProviderRowLabel(provider: ApiProvider) {
    val context = LocalContext.current
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = provider.getName(context).toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = provider.getDescriptionShort(context).toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        provider.getHint(context)?.let { hint ->
            Text(
                text = hint.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
