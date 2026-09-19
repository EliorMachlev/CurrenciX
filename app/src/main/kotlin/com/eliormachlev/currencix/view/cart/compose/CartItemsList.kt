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
import com.eliormachlev.currencix.util.CalculatorKeyListener
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.compose.onBackgroundTap
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
@Suppress("LongParameterList")
fun CartItemsList(
    itemsSource: LiveData<ImmutableList<CartItem>>,
    currencySource: LiveData<String>,
    activeItemIdSource: LiveData<String?>,
    activeExpressionSource: LiveData<String>,
    keyListenerSource: LiveData<CalculatorKeyListener?>,
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
        val items by itemsSource.observeAsState(initial = persistentListOf())
        val currency by currencySource.observeAsState(initial = "")
        val activeId by activeItemIdSource.observeAsState()
        val liveExpression by activeExpressionSource.observeAsState(initial = "")
        val keyListener by keyListenerSource.observeAsState()

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
                        keyListener = keyListener,
                        liveExpression = if (isActive) liveExpression else null,
                        onNameCommit = { onNameCommit(item.id, it) },
                        onNamePending = { onNamePending(item.id, it) },
                        onExpressionTap = { onExpressionTap(item) },
                        onExpressionChange = { onExpressionChange(item.id, it) },
                        onTogglePin = { onTogglePin(item.id) },
                        onDelete = { onDelete(item.id) },
                        dragHandleModifier =
                            Modifier.longPressDraggableHandle(
                                onDragStarted = { onReorderStart() },
                                onDragStopped = {
                                    commitReorder(displayItems, items, onReorder)
                                },
                            ),
                    )
                }
            }
        }
    }
}

/**
 * SnapshotStateList mirror of the storage list, ordered pinned-first while
 * preserving storage order within each partition. Drag operates on this list;
 * dropping a pinned row into the unpinned section snaps back on the next
 * source emit — user must unpin first to move it out.
 */
@Composable
private fun rememberDisplayItems(items: ImmutableList<CartItem>): SnapshotStateList<CartItem> {
    val list = remember { mutableStateListOf<CartItem>() }
    LaunchedEffect(items) {
        val ordered = items.filter { it.pinned } + items.filterNot { it.pinned }
        // Skip the churn on identity-preserving re-emits (our own ViewModel
        // updates round-trip through here) so a mid-drag list emit doesn't
        // wipe out the visual swap.
        if (list.size != ordered.size || list.zip(ordered).any { (a, b) -> a.id != b.id }) {
            list.clear()
            list.addAll(ordered)
        }
    }
    return list
}

private fun SnapshotStateList<CartItem>.moveByLazyIndex(
    from: Int,
    to: Int,
) {
    if (from !in indices || to !in indices || from == to) return
    add(to, removeAt(from))
}

/**
 * Compare the post-drag display order to the pre-drag source snapshot and
 * emit a single (from, to) move to the ViewModel. `toId` is the id of the
 * item that occupied the drop slot *before* the drag — the VM's
 * `reorderItem` then removes `fromId` from its old slot and inserts it at
 * the anchor's storage position, which reproduces the visual outcome for
 * both forward and backward drags. Skips the round-trip when the release
 * lands on the pickup slot (drag with no net movement).
 */
private fun commitReorder(
    displayItems: SnapshotStateList<CartItem>,
    sourceItems: ImmutableList<CartItem>,
    onReorder: (fromId: String, toId: String) -> Unit,
) {
    val before = (sourceItems.filter { it.pinned } + sourceItems.filterNot { it.pinned }).map { it.id }
    val after = displayItems.map { it.id }
    if (before.size != after.size || before == after) return
    val newIdx = after.indices.firstOrNull { after[it] != before[it] } ?: return
    val movedId = after[newIdx]
    val anchorId = before[newIdx]
    if (movedId == anchorId) return
    onReorder(movedId, anchorId)
}
