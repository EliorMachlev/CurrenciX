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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.compose.dragReorderGraphics
import com.eliormachlev.currencix.view.compose.dragReorderHandle
import com.eliormachlev.currencix.view.compose.onBackgroundTap
import com.eliormachlev.currencix.view.compose.rememberDragReorderState

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
    isSystemKeyboardModeSource: LiveData<Boolean>,
    useFullTextImeSource: LiveData<Boolean>,
    onNameCommit: (id: String, name: String) -> Unit,
    onNamePending: (id: String, name: String) -> Unit,
    onExpressionTap: (item: CartItem) -> Unit,
    onExpressionChange: (id: String, expression: String) -> Unit,
    onTogglePin: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onReorder: (fromId: String, toId: String) -> Unit,
    onReorderStart: () -> Unit,
    onBackgroundTap: () -> Unit,
) {
    AppTheme {
        val items by itemsSource.observeAsState(initial = emptyList())
        val currency by currencySource.observeAsState(initial = "")
        val activeId by activeItemIdSource.observeAsState()
        val liveExpression by activeExpressionSource.observeAsState(initial = "")
        val isSystemKeyboardMode by isSystemKeyboardModeSource.observeAsState(initial = false)
        val useFullTextIme by useFullTextImeSource.observeAsState(initial = false)
        // Display pinned-first while preserving storage order within each
        // partition. Drag operates on this list; dropping a pinned row into
        // the unpinned section snaps back on the next composition — user must
        // unpin first to move it out.
        val displayItems = remember(items) { items.filter { it.pinned } + items.filterNot { it.pinned } }

        val rowHeightPx = with(LocalDensity.current) { REORDER_ROW_HEIGHT.toPx() }
        val drag = rememberDragReorderState()

        // Drop the drag if the list shrinks past the dragged index (delete,
        // cart reload) — the pointer input can't cancel itself.
        LaunchedEffect(displayItems.size) {
            val src = drag.draggingIndex ?: return@LaunchedEffect
            if (src !in displayItems.indices) drag.reset()
        }

        // Rows consume taps on the expression, pin toggle, and name field;
        // anything left over (blank space below the last row, blank card
        // padding on a row) dismisses whatever keyboard is up.
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onBackgroundTap(onBackgroundTap),
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
                    isSystemKeyboardMode = isSystemKeyboardMode,
                    useFullTextIme = useFullTextIme,
                    liveExpression = if (isActive) liveExpression else null,
                    onNameCommit = { onNameCommit(item.id, it) },
                    onNamePending = { onNamePending(item.id, it) },
                    onExpressionTap = { onExpressionTap(item) },
                    onExpressionChange = { onExpressionChange(item.id, it) },
                    onTogglePin = { onTogglePin(item.id) },
                    onDelete = { onDelete(item.id) },
                    modifier =
                        Modifier
                            .animateItem()
                            .dragReorderGraphics(drag, index, rowHeightPx),
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
