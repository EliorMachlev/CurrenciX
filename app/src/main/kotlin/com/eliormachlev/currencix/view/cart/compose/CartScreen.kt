package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.eliormachlev.currencix.view.cart.CartKeypadController
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.ProseTheme
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel

/**
 * Root of the cart screen. Column-lays the items list (weighted, so it
 * absorbs any spare height; it holds the empty state and the "Add item"
 * row too) and the totals footer. Overlays [CartKeypadOverlay]
 * at the bottom so it slides in over the footer without shifting layout.
 */
@Composable
fun CartScreen(
    viewModel: CartViewModel,
    keypad: CartKeypadController,
    sources: CartListSources,
    actions: CartScreenActions,
    modifier: Modifier = Modifier,
) {
    // Laid out the way the language reads (item rows, labels, the add
    // button); amounts, prices, the currency pair and the keypad keep their
    // left-to-right reading in Ltr islands of their own.
    ProseTheme {
        Box(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    CartItemsList(
                        sources = sources,
                        actions = actions.items,
                        reorder = actions.reorder,
                        onAddItem = actions.onAddItem,
                        onBackgroundTap = keypad::dismissKeyboards,
                    )
                }
                CartFooter(
                    viewModel = viewModel,
                    onOpenFees = actions.onOpenFees,
                    onEditExtras = actions.onEditExtras,
                )
            }
            Ltr {
                CartKeypadOverlay(
                    keypad = keypad,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
