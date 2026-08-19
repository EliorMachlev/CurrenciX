package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.view.compose.AppTheme

// Nominal row height used to translate finger travel into "how many rows have
// I dragged past". Cart rows aren't uniform (name + expression + preview), but
// this gives a good-enough delta for a snap-to-slot reorder feel; the actual
// list re-lays out via LazyColumn animateItem() once the drop commits.
private val REORDER_ROW_HEIGHT = 96.dp
private const val REORDER_DRAG_ALPHA = 0.85f

// State bag for an in-progress drag. Kept as a single class (rather than four
// loose `mutableStateOf`s) so per-frame `dragOffsetY` writes stay scoped to
// the draw-phase `graphicsLayer` lambda instead of invalidating every row's
// composition. Reads inside a `graphicsLayer { ... }` block subscribe at the
// draw layer, not the composition layer — the classic Compose "read state
// late" perf trick.
private class DragState {
    var draggingId by mutableStateOf<String?>(null)
    var draggingIndex by mutableStateOf<Int?>(null)
    var targetIndex by mutableStateOf<Int?>(null)
    var offsetY by mutableStateOf(0f)

    fun reset() {
        draggingId = null
        draggingIndex = null
        targetIndex = null
        offsetY = 0f
    }
}

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
    onReorder: (fromId: String, toId: String) -> Unit,
    onReorderStart: () -> Unit,
) {
    AppTheme {
        val items by itemsSource.observeAsState(initial = emptyList())
        val currency by currencySource.observeAsState(initial = "")
        val activeId by activeItemIdSource.observeAsState()
        val liveExpression by activeExpressionSource.observeAsState(initial = "")

        val rowHeightPx = with(LocalDensity.current) { REORDER_ROW_HEIGHT.toPx() }
        val drag = remember { DragState() }

        // Drop the drag if the item disappears (delete, cart reload) mid-gesture.
        LaunchedEffect(items) {
            val id = drag.draggingId ?: return@LaunchedEffect
            if (items.none { it.id == id }) drag.reset()
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = dimensionResource(id = R.dimen.margin1x),
                    vertical = dimensionResource(id = R.dimen.margin1x),
                ),
        ) {
            itemsIndexed(items = items, key = { _, it -> it.id }) { index, item ->
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
                    modifier =
                        Modifier
                            .animateItem()
                            // Reading DragState fields inside this lambda keeps
                            // per-frame drag updates in the draw phase — rows
                            // don't recompose, they just re-draw at a new Y.
                            .graphicsLayer {
                                val isDragging = drag.draggingId == item.id
                                translationY = dragTranslationY(drag, index, rowHeightPx, isDragging)
                                if (isDragging) alpha = REORDER_DRAG_ALPHA
                            },
                    dragHandleModifier =
                        Modifier.pointerInput(item.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    onReorderStart()
                                    drag.draggingId = item.id
                                    drag.draggingIndex = index
                                    drag.targetIndex = index
                                    drag.offsetY = 0f
                                },
                                onDragEnd = {
                                    val movedId = drag.draggingId
                                    val src = drag.draggingIndex
                                    val dst = drag.targetIndex
                                    if (movedId != null &&
                                        src != null &&
                                        dst != null &&
                                        src != dst &&
                                        dst in items.indices
                                    ) {
                                        onReorder(movedId, items[dst].id)
                                    }
                                    drag.reset()
                                },
                                onDragCancel = { drag.reset() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    drag.offsetY += dragAmount.y
                                    val src = drag.draggingIndex ?: return@detectDragGesturesAfterLongPress
                                    val delta = kotlin.math.round(drag.offsetY / rowHeightPx).toInt()
                                    drag.targetIndex = (src + delta).coerceIn(0, items.lastIndex)
                                },
                            )
                        },
                )
            }
        }
    }
}

// How far a row at [index] should be shifted while another row is being
// dragged. The dragged row itself follows the finger (dragOffsetY); every row
// between old and new slots slides one row-height in the opposite direction
// to open the gap.
private fun dragTranslationY(
    drag: DragState,
    index: Int,
    rowHeightPx: Float,
    isDragging: Boolean,
): Float {
    if (isDragging) return drag.offsetY
    val src = drag.draggingIndex ?: return 0f
    val dst = drag.targetIndex ?: return 0f
    return when {
        src < dst && index in (src + 1)..dst -> -rowHeightPx
        src > dst && index in dst until src -> rowHeightPx
        else -> 0f
    }
}
