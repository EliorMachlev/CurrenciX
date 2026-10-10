package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.runtime.Immutable

/** Everything the cart screen can ask its host for. */
@Immutable
class CartScreenActions(
    val onAddItem: () -> Unit,
    val onOpenFees: () -> Unit,
    val onEditExtras: () -> Unit,
    val items: CartItemActions,
    val reorder: CartReorder,
)
