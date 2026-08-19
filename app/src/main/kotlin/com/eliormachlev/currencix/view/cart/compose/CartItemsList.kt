package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.compose.DRAG_REORDER_ACTIVE_ALPHA
import com.eliormachlev.currencix.view.compose.dragReorderHandle
import com.eliormachlev.currencix.view.compose.rememberDragReorderState
import com.eliormachlev.currencix.view.compose.translationYFor

// Nominal row height used to translate finger travel into "how many rows have
// I dragged past". Cart rows aren't uniform (name + expression + preview), but
// this gives a good-enough delta for a snap-to-slot reorder feel; the actual
// list re-lays out via LazyColumn animateItem() once the drop commits.
private val REORDER_ROW_HEIGHT = 96.dp

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
    onReorder: (fromId: String, toId: String) -> Unit,
    onReorderStart: () -> Unit,
) {
    AppTheme {
        val items by itemsSource.observeAsState(initial = emptyList())
        val currency by currencySource.observeAsState(initial = "")
        val activeId by activeItemIdSource.observeAsState()
        val liveExpression by activeExpressionSource.observeAsState(initial = "")
        // Sort pinned-first at render time so the underlying storage order is
        // preserved when the user toggles pins on and off. `sortedByDescending`
        // is stable, so items within each partition keep their relative order.
        // Dragging operates on this display list; drops that cross the pin
        // boundary re-sort into the pinned block on next composition, so a
        // pinned row visually snaps back after a downward drag — the user
        // has to unpin first to move it out of the pinned section.
        val displayItems = remember(items) { items.sortedByDescending { it.pinned } }

        val rowHeightPx = with(LocalDensity.current) { REORDER_ROW_HEIGHT.toPx() }
        val drag = rememberDragReorderState()

        // Drop the drag if the dragged row disappears mid-gesture (delete,
        // cart reload) — the pointer input can't cancel itself when its item
        // is removed from the LazyColumn.
        LaunchedEffect(items) {
            val src = drag.draggingIndex ?: return@LaunchedEffect
            if (src !in items.indices) drag.reset()
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = dimensionResource(id = R.dimen.margin1x),
                    vertical = dimensionResource(id = R.dimen.margin1x),
                ),
        ) {
            itemsIndexed(items = displayItems, key = { _, it -> it.id }) { index, item ->
                val isActive = item.id == activeId
                SwipeableCartItemRow(
                    item = item,
                    currency = currency,
                    isActive = isActive,
                    liveExpression = if (isActive) liveExpression else null,
                    onNameCommit = { onNameCommit(item.id, it) },
                    onNamePending = { onNamePending(item.id, it) },
                    onExpressionTap = { onExpressionTap(item) },
                    onTogglePin = { onTogglePin(item.id) },
                    onDelete = { onDelete(item.id) },
                    modifier =
                        Modifier
                            .animateItem()
                            .graphicsLayer {
                                translationY = drag.translationYFor(index, rowHeightPx)
                                if (drag.draggingIndex == index) alpha = DRAG_REORDER_ACTIVE_ALPHA
                            },
                    dragHandleModifier =
                        Modifier.dragReorderHandle(
                            state = drag,
                            index = index,
                            key = item.id,
                            rowHeightPx = rowHeightPx,
                            itemCount = { displayItems.size },
                            onStart = onReorderStart,
                            onCommit = { from, to -> onReorder(displayItems[from].id, displayItems[to].id) },
                        ),
                )
            }
        }
    }
}
