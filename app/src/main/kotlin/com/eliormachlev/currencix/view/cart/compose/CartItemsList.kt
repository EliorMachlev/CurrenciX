package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.view.compose.AppTheme

@Composable
@Suppress("LongParameterList")
fun CartItemsList(
    itemsSource: LiveData<List<CartItem>>,
    currencySource: LiveData<String>,
    activeItemIdSource: LiveData<String?>,
    activeExpressionSource: LiveData<String>,
    onNameCommit: (id: String, name: String) -> Unit,
    onNamePending: (id: String, name: String) -> Unit,
    onExpressionTap: (item: CartItem) -> Unit,
    onTogglePin: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    AppTheme {
        val items by itemsSource.observeAsState(initial = emptyList())
        val currency by currencySource.observeAsState(initial = "")
        val activeId by activeItemIdSource.observeAsState()
        val liveExpression by activeExpressionSource.observeAsState(initial = "")
        // Sort pinned-first at render time so the underlying storage order is
        // preserved when the user toggles pins on and off. `sortedByDescending`
        // is stable, so items within each partition keep their relative order.
        val displayItems = remember(items) { items.sortedByDescending { it.pinned } }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = dimensionResource(id = R.dimen.margin1x),
                    vertical = dimensionResource(id = R.dimen.margin1x),
                ),
        ) {
            items(items = displayItems, key = { it.id }) { item ->
                val isActive = item.id == activeId
                CartItemRow(
                    item = item,
                    currency = currency,
                    isActive = isActive,
                    liveExpression = if (isActive) liveExpression else null,
                    onNameCommit = { onNameCommit(item.id, it) },
                    onNamePending = { onNamePending(item.id, it) },
                    onExpressionTap = { onExpressionTap(item) },
                    onTogglePin = { onTogglePin(item.id) },
                    onDelete = { onDelete(item.id) },
                )
            }
        }
    }
}
