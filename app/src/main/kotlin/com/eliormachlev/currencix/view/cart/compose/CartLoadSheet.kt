package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.HomeCurrency
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.theme.Spacing
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
    onOpenFees: () -> Unit = {},
) {
    // Seed with the persisted snapshot so the first frame has the real list,
    // otherwise the empty-list default would trip the auto-dismiss before the
    // LiveData observer even fires.
    val saved by viewModel.getSavedCarts().observeAsState(viewModel.getSavedCartsSnapshot())
    val trips by viewModel.trips.getSummaries().observeAsState(emptyList())
    var editing by remember { mutableStateOf<TripEdit?>(null) }
    var checklistFor by remember { mutableStateOf<Trip?>(null) }
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
            TextButton(
                onClick = rememberHapticOnClick { editing = TripEdit(trip = null, addCart = null) },
                modifier = Modifier.padding(horizontal = Spacing.Small),
            ) {
                Icon(painterResource(R.drawable.ic_luggage), contentDescription = null)
                Text(stringResource(R.string.trip_new), modifier = Modifier.padding(start = Spacing.Small))
            }
            SavedCartsList(
                items = saved,
                trips = trips,
                actions =
                    SavedCartActions(
                        onPick = { cart ->
                            onDismiss()
                            onPick(cart)
                        },
                        onRename = onRename,
                        onDelete = onDelete,
                        onMoveToTrip = { cart, tripId -> viewModel.trips.moveCart(cart.id, tripId) },
                        onNewTrip = { cart -> editing = TripEdit(trip = null, addCart = cart) },
                        onEditTrip = { trip -> editing = TripEdit(trip = trip, addCart = null) },
                        onTripChecklist = { trip -> checklistFor = trip },
                    ),
            )
        }
    }
    editing?.let { TripEditor(it, viewModel, saved, onClose = { editing = null }) }
    checklistFor?.let { trip ->
        TripChecklistSheet(
            trip = trip,
            onOpenFees = {
                checklistFor = null
                onDismiss()
                onOpenFees()
            },
            onDismiss = { checklistFor = null },
        )
    }
}

// The trip sheet's subject: a trip to edit, or a new one (with [addCart] put in it).
private class TripEdit(
    val trip: Trip?,
    val addCart: SavedCart?,
)

// The trip sheet over the saved carts; saving a new trip from a cart's menu puts that cart in it.
@Composable
private fun TripEditor(
    edit: TripEdit,
    viewModel: CartViewModel,
    saved: List<SavedCart>,
    onClose: () -> Unit,
) {
    val trip = edit.trip
    // Home first, then the currencies the carts use.
    val currencies =
        remember(saved, trip) {
            (
                listOfNotNull(trip?.currency, HomeCurrency.defaultPair.to) +
                    saved
                        .flatMap { listOfNotNull(it.destinationCurrency, it.currency) }
                        .mapNotNull(Currency::fromString)
            ).distinct()
                .take(MAX_TRIP_CURRENCIES)
        }
    TripSheet(
        initial = trip,
        currencies = currencies,
        onSave = { draft ->
            val id = viewModel.trips.save(trip?.id, draft)
            edit.addCart?.let { viewModel.trips.moveCart(it.id, id) }
            onClose()
        },
        onDismiss = onClose,
        onDelete =
            trip?.let { t ->
                {
                    viewModel.trips.delete(t.id)
                    onClose()
                }
            },
    )
}

// Enough to choose from without the chips wrapping onto many lines.
private const val MAX_TRIP_CURRENCIES = 8
