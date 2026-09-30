package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel

/**
 * Ledger-styled saved-cart picker sheet — a [LedgerBottomSheet] hosting the
 * [SavedCartsList]. Observes the persisted saved-cart list live so a delete
 * spawned from inside the sheet flows through into the same recomposition; the
 * sheet auto-dismisses when the last cart is removed since there's nothing
 * left to pick.
 *
 * [onPick] fires *after* the sheet dismisses so any follow-up prompt
 * (unsaved-changes) stacks above a settled UI rather than sliding in over the
 * sheet on its way out.
 */
@Composable
fun CartLoadSheet(
    viewModel: CartViewModel,
    onPick: (SavedCart) -> Unit,
    onRename: (SavedCart) -> Unit,
    onDelete: (SavedCart) -> Unit,
    onDismiss: () -> Unit,
) {
    // Seed with the persisted snapshot so the first frame has the real list,
    // otherwise the empty-list default would trip the auto-dismiss before the
    // LiveData observer even fires.
    val saved by viewModel.getSavedCarts().observeAsState(viewModel.getSavedCartsSnapshot())
    LaunchedEffect(saved.isEmpty()) {
        if (saved.isEmpty()) onDismiss()
    }
    LedgerBottomSheet(
        title = stringResource(id = R.string.cart_menu_load),
        onDismiss = onDismiss,
        scrollableBody = false,
        // Start at the half-height anchor; upward drag / list scroll expands
        // to full. Matches the currency picker sheet so both list-shaped
        // pickers feel the same to open.
        skipPartiallyExpanded = false,
    ) {
        // Bound the inner LazyColumn — without fillMaxHeight the sheet gives
        // the body an unbounded height constraint and the LazyColumn crashes.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
        ) {
            SavedCartsList(
                items = saved,
                onPick = { cart ->
                    onDismiss()
                    onPick(cart)
                },
                onRename = onRename,
                onDelete = onDelete,
            )
        }
    }
}
