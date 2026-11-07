package com.eliormachlev.currencix.view.cart.compose

import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.view.compose.PairRowActions
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Stand-ins for the cart composables' inputs, for tests that render them
// without a ViewModel behind.

/** A list showing [items] priced in USD, with no row being edited. */
internal fun cartSources(
    items: StateFlow<ImmutableList<CartItem>>,
    shoppingMode: Boolean = false,
) = CartListSources(
    items = items,
    currency = MutableStateFlow("USD"),
    activeItemId = MutableStateFlow(null),
    activeExpression = MutableStateFlow(""),
    conversion = MutableStateFlow(null),
    mode = CartListMode(shoppingMode = MutableStateFlow(shoppingMode), people = MutableStateFlow(emptyList())),
)

/** Row actions that do nothing, but for [onTogglePin]. */
internal fun cartItemActions(onTogglePin: (id: String) -> Unit = {}) =
    CartItemActions(
        onNameCommit = { _, _ -> },
        onNamePending = { _, _ -> },
        onExpressionTap = {},
        onQuantityChange = { _, _ -> },
        onToggleChecked = {},
        arrange = CartItemArrangeActions(onTogglePin = onTogglePin, onDuplicate = {}, onMoveToTop = {}, onDelete = {}),
    )

internal val NO_REORDER = CartReorder(onStart = {}, onCommit = { _, _ -> })
internal val NO_ROW_ACTIONS = CartRowActions({}, {}, {}, {}, {})
internal val NO_PAIR_ACTIONS = PairRowActions({}, {}, {}, {})
