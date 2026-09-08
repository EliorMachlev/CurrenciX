package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.util.CalculatorKeyListener
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.cart.CartKeypadController
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel

/**
 * Root of the cart screen. Column-lays the items list (weighted, so it
 * absorbs any spare height), the empty hint (only when there are no rows),
 * the "Add item" button, and the totals footer. Overlays [CartKeypadOverlay]
 * at the bottom so it slides in over the footer without shifting layout.
 */
@Composable
@Suppress("LongParameterList")
fun CartScreen(
    viewModel: CartViewModel,
    fragmentManager: FragmentManager,
    keypad: CartKeypadController,
    itemsSource: LiveData<List<CartItem>>,
    currencySource: LiveData<String>,
    keyListenerSource: LiveData<CalculatorKeyListener?>,
    onAddItem: () -> Unit,
    onNameCommit: (id: String, name: String) -> Unit,
    onNamePending: (id: String, name: String) -> Unit,
    onExpressionTap: (item: CartItem) -> Unit,
    onExpressionChange: (id: String, expression: String) -> Unit,
    onTogglePin: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onReorder: (fromId: String, toId: String) -> Unit,
    onReorderStart: () -> Unit,
    onOpenFees: () -> Unit,
) {
    AppTheme {
        val items by itemsSource.observeAsState(initial = emptyList())
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    CartItemsList(
                        itemsSource = itemsSource,
                        currencySource = currencySource,
                        activeItemIdSource = keypad.activeItemId,
                        activeExpressionSource = keypad.liveExpression,
                        keyListenerSource = keyListenerSource,
                        onNameCommit = onNameCommit,
                        onNamePending = onNamePending,
                        onExpressionTap = onExpressionTap,
                        onExpressionChange = onExpressionChange,
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
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                        Text(text = stringResource(id = R.string.cart_add_item))
                    }
                }
                CartFooter(
                    viewModel = viewModel,
                    fragmentManager = fragmentManager,
                    onOpenFees = onOpenFees,
                )
            }
            CartKeypadOverlay(
                keypad = keypad,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
