package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
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
    onDelete: (id: String) -> Unit,
) {
    AppTheme {
        val items by itemsSource.observeAsState(initial = emptyList())
        val currency by currencySource.observeAsState(initial = "")
        val activeId by activeItemIdSource.observeAsState()
        val liveExpression by activeExpressionSource.observeAsState(initial = "")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = dimensionResource(id = R.dimen.margin1x),
                    vertical = dimensionResource(id = R.dimen.margin1x),
                ),
        ) {
            items(items = items, key = { it.id }) { item ->
                val isActive = item.id == activeId
                CartItemRow(
                    item = item,
                    currency = currency,
                    isActive = isActive,
                    liveExpression = if (isActive) liveExpression else null,
                    onNameCommit = { onNameCommit(item.id, it) },
                    onNamePending = { onNamePending(item.id, it) },
                    onExpressionTap = { onExpressionTap(item) },
                    onDelete = { onDelete(item.id) },
                )
            }
        }
    }
}
