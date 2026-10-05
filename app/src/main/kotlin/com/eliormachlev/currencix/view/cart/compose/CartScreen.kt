package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.cart.CartKeypadController
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Root of the cart screen. Column-lays the items list (weighted, so it
 * absorbs any spare height), the empty hint (only when there are no rows),
 * the "Add item" button, and the totals footer. Overlays [CartKeypadOverlay]
 * at the bottom so it slides in over the footer without shifting layout.
 */
@Composable
fun CartScreen(
    viewModel: CartViewModel,
    keypad: CartKeypadController,
    itemsSource: LiveData<ImmutableList<CartItem>>,
    currencySource: LiveData<String>,
    onAddItem: () -> Unit,
    onNameCommit: (id: String, name: String) -> Unit,
    onNamePending: (id: String, name: String) -> Unit,
    onExpressionTap: (item: CartItem) -> Unit,
    onTogglePin: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onReorder: CartDragCommit,
    onReorderStart: () -> Unit,
    onOpenFees: () -> Unit,
    onEditExtras: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppTheme {
        val items by itemsSource.observeAsState(initial = persistentListOf())
        Box(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    CartItemsList(
                        itemsSource = itemsSource,
                        currencySource = currencySource,
                        activeItemIdSource = keypad.activeItemId,
                        activeExpressionSource = keypad.liveExpression,
                        onNameCommit = onNameCommit,
                        onNamePending = onNamePending,
                        onExpressionTap = onExpressionTap,
                        onTogglePin = onTogglePin,
                        onDelete = onDelete,
                        onReorder = onReorder,
                        onReorderStart = onReorderStart,
                        onBackgroundTap = keypad::dismissKeyboards,
                    )
                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter,
                        ) { CartEmptyHint() }
                    }
                }
                AddItemButton(onAddItem = onAddItem)
                CartFooter(
                    viewModel = viewModel,
                    onOpenFees = onOpenFees,
                    onEditExtras = onEditExtras,
                )
            }
            CartKeypadOverlay(
                keypad = keypad,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

// "Add item" outlined button — extracted from CartScreen so the screen body
// stays under the LongMethod threshold. Wraps the icon+label row and the
// haptic-click adapter so the caller only supplies the raw action.
@Composable
private fun AddItemButton(onAddItem: () -> Unit) {
    OutlinedButton(
        onClick = rememberHapticOnClick(onAddItem),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = dimensionResource(id = R.dimen.margin2x),
                    vertical = dimensionResource(id = R.dimen.margin1x),
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.margin1x)),
        ) {
            Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
            Text(text = stringResource(id = R.string.cart_add_item))
        }
    }
}
