package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.compose.onBackgroundTap
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** A finished drag: the rows' new on-screen order and the row that moved. */
typealias CartDragCommit = (displayOrder: List<String>, movedId: String) -> Unit

@Composable
@Suppress("LongParameterList")
fun CartItemsList(
    itemsSource: LiveData<ImmutableList<CartItem>>,
    currencySource: LiveData<String>,
    activeItemIdSource: LiveData<String?>,
    activeExpressionSource: LiveData<String>,
    onNameCommit: (id: String, name: String) -> Unit,
    onNamePending: (id: String, name: String) -> Unit,
    onExpressionTap: (item: CartItem) -> Unit,
    onTogglePin: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onReorder: CartDragCommit,
    onReorderStart: () -> Unit,
    onBackgroundTap: () -> Unit,
) {
    AppTheme {
        val items by itemsSource.observeAsState(initial = persistentListOf())
        val currency by currencySource.observeAsState(initial = "")
        val activeId by activeItemIdSource.observeAsState()
        val liveExpression by activeExpressionSource.observeAsState(initial = "")

        // Local mirror the drag gesture mutates in-flight; ReorderableLazyList
        // needs a stable, mutable data source so the visual swap can settle
        // before we round-trip through the ViewModel. The mirror re-syncs from
        // storage whenever the source list identity changes.
        val displayItems = rememberDisplayItems(items)

        val lazyListState = rememberLazyListState()
        val reorderableState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                // Delegate to a helper so the swap logic stays testable and
                // out of the state factory's callback.
                displayItems.moveByLazyIndex(from.index, to.index)
            }

        // Rows consume taps on the expression, pin toggle, and name field;
        // anything left over (blank space below the last row, blank card
        // padding on a row) dismisses whatever keyboard is up.
        LazyColumn(
            state = lazyListState,
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
            items(items = displayItems, key = { it.id }) { item ->
                // ReorderableItem applies `Modifier.animateItem()` internally
                // via its `animateItemModifier` parameter, so add/remove/re-slot
                // already animates without extra wiring at the call site.
                ReorderableItem(reorderableState, key = item.id) { _ ->
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
                        dragHandleModifier =
                            Modifier.longPressDraggableHandle(
                                onDragStarted = { onReorderStart() },
                                onDragStopped = { commitDrag(displayItems, item.id, onReorder) },
                            ),
                    )
                }
            }
        }
    }
}

/**
 * SnapshotStateList mirror of the storage list, ordered pinned-first while
 * preserving storage order within each partition. Drag operates on this list.
 *
 * When the id set or the pinned set changes (add/remove/load, a pin toggle),
 * take the source order wholesale, so a newly pinned row jumps to the top
 * right away. When only content changed (name/expression edit mid-drag),
 * refresh per-id in place so the display's current order isn't wiped.
 */
@Composable
private fun rememberDisplayItems(items: ImmutableList<CartItem>): SnapshotStateList<CartItem> {
    val list = remember { mutableStateListOf<CartItem>() }
    LaunchedEffect(items) {
        val ordered = items.sortedByDescending { it.pinned }
        if (list.pinState() != ordered.pinState()) {
            list.clear()
            list.addAll(ordered)
        } else {
            val byId = ordered.associateBy { it.id }
            list.indices.forEach { i ->
                val fresh = byId.getValue(list[i].id)
                if (fresh != list[i]) list[i] = fresh
            }
        }
    }
    return list
}

// Each id with whether it's pinned: equal before and after a change that
// neither added, removed, pinned nor unpinned anything.
private fun List<CartItem>.pinState(): Map<String, Boolean> = associate { it.id to it.pinned }

// Moves the dragged row one slot as it passes a neighbour — only within its
// own group: pinned rows stay above the last pinned one and unpinned rows
// below it, so a row never swaps with one from the other group.
internal fun SnapshotStateList<CartItem>.moveByLazyIndex(
    from: Int,
    to: Int,
) {
    if (from !in indices || to !in indices || from == to) return
    if (this[from].pinned != this[to].pinned) return
    add(to, removeAt(from))
}

// Hands the order the drag left to the ViewModel; it skips no-ops (a release
// without movement).
private fun commitDrag(
    displayItems: SnapshotStateList<CartItem>,
    movedId: String,
    onReorder: CartDragCommit,
) = onReorder(displayItems.map { it.id }, movedId)
