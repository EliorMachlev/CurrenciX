package com.eliormachlev.currencix.view.cart.compose

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.view.compose.PairRowActions
import kotlinx.collections.immutable.ImmutableList

// Stand-ins for the cart composables' inputs, for tests that render them
// without a ViewModel behind.

/** A list showing [items] priced in USD, with no row being edited. */
internal fun cartSources(items: LiveData<ImmutableList<CartItem>>) =
    CartListSources(
        items = items,
        currency = MutableLiveData("USD"),
        activeItemId = MutableLiveData(null),
        activeExpression = MutableLiveData(""),
    )

/** Row actions that do nothing, but for [onTogglePin]. */
internal fun cartItemActions(onTogglePin: (id: String) -> Unit = {}) =
    CartItemActions(
        onNameCommit = { _, _ -> },
        onNamePending = { _, _ -> },
        onExpressionTap = {},
        onTogglePin = onTogglePin,
        onDelete = {},
    )

internal val NO_REORDER = CartReorder(onStart = {}, onCommit = { _, _ -> })
internal val NO_ROW_ACTIONS = CartRowActions({}, {}, {}, {}, {})
internal val NO_PAIR_ACTIONS = PairRowActions({}, {}, {}, {})
