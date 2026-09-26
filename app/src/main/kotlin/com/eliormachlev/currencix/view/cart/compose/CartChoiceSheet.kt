package com.eliormachlev.currencix.view.cart.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet

private val RowHorizontalPadding = 24.dp
private val RowVerticalPadding = 12.dp
private const val ROW_DESCRIPTION_ALPHA = 0.7f

/**
 * One entry in a [CartChoiceSheet] — title plus one-line explainer of what
 * picking it does. Sheet closes itself after [onPick] fires so callers only
 * do their branch-specific work.
 */
data class CartChoiceOption(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val onPick: () -> Unit,
)

/**
 * Immutable payload for a "pick one branch" cart flow (Clear, Share). Held as
 * `mutableStateOf<CartChoiceRequest?>` on [com.eliormachlev.currencix.view.cart.CartActivity]
 * so imperative callers (menu handlers, coordinators) can trigger the sheet
 * without wiring their own compose state.
 */
data class CartChoiceRequest(
    @StringRes val titleRes: Int,
    val options: List<CartChoiceOption>,
)

/**
 * Ledger-styled "one branch per row" picker sheet — replaces the AppCompat
 * AlertDialog cart-choice dialog with a [LedgerBottomSheet] wrapping title +
 * clickable option rows.
 */
@Composable
fun CartChoiceSheet(
    @StringRes titleRes: Int,
    options: List<CartChoiceOption>,
    onDismiss: () -> Unit,
) {
    LedgerBottomSheet(
        title = stringResource(id = titleRes),
        onDismiss = onDismiss,
    ) {
        options.forEach { option ->
            CartChoiceRow(
                title = stringResource(id = option.title),
                description = stringResource(id = option.description),
                onClick = {
                    option.onPick()
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun CartChoiceRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .hapticClickable(onClick = onClick)
                .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = ROW_DESCRIPTION_ALPHA),
        )
    }
}
