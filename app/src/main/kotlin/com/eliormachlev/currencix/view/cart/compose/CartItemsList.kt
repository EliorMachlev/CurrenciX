package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.compose.LedgerSectionHeader
import com.eliormachlev.currencix.view.compose.onBackgroundTap
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** A finished drag: the rows' new on-screen order and the row that moved. */
typealias CartDragCommit = (displayOrder: List<String>, movedId: String) -> Unit

/** The live values the list renders. */
class CartListSources(
    val items: LiveData<ImmutableList<CartItem>>,
    val currency: LiveData<String>,
    val activeItemId: LiveData<String?>,
    val activeExpression: LiveData<String>,
)

/** What the rows can ask for, each naming the row it's about. */
@Immutable
class CartItemActions(
    val onNameCommit: (id: String, name: String) -> Unit,
    val onNamePending: (id: String, name: String) -> Unit,
    val onExpressionTap: (item: CartItem) -> Unit,
    val onTogglePin: (id: String) -> Unit,
    val onDelete: (id: String) -> Unit,
) {
    /** These actions, bound to [item]'s row. */
    fun forRow(item: CartItem) =
        CartRowActions(
            onNameCommit = { onNameCommit(item.id, it) },
            onNamePending = { onNamePending(item.id, it) },
            onExpressionTap = { onExpressionTap(item) },
            onTogglePin = { onTogglePin(item.id) },
            onDelete = { onDelete(item.id) },
        )
}

/** A drag to reorder: told when it starts, and where the rows ended up. */
@Immutable
class CartReorder(
    val onStart: () -> Unit,
    val onCommit: CartDragCommit,
)

@Composable
fun CartItemsList(
    sources: CartListSources,
    actions: CartItemActions,
    reorder: CartReorder,
    onBackgroundTap: () -> Unit,
) {
    AppTheme {
        val items by sources.items.observeAsState(initial = persistentListOf())
        val currency by sources.currency.observeAsState(initial = "")
        val activeId by sources.activeItemId.observeAsState()
        val liveExpression by sources.activeExpression.observeAsState(initial = "")

        // Local mirror the drag gesture mutates in-flight; ReorderableLazyList
        // needs a stable, mutable data source so the visual swap can settle
        // before we round-trip through the ViewModel (rememberDisplayItems).
        val displayItems = rememberDisplayItems(items)

        val lazyListState = rememberLazyListState()
        val reorderableState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                // By key: the section headings sit between rows, so lazy
                // indices don't line up with the list's.
                displayItems.moveByKey(from.key, to.key)
            }
        val row: @Composable LazyItemScope.(CartItem) -> Unit = { item ->
            // ReorderableItem applies `Modifier.animateItem()` internally
            // via its `animateItemModifier` parameter, so add/remove/re-slot
            // already animates without extra wiring at the call site.
            ReorderableItem(reorderableState, key = item.id) { _ ->
                SwipeableCartItemRow(
                    state = CartRowState(item, currency, liveExpression.takeIf { item.id == activeId }),
                    actions = remember(actions, item) { actions.forRow(item) },
                    dragHandleModifier =
                        Modifier.longPressDraggableHandle(
                            onDragStarted = { reorder.onStart() },
                            onDragStopped = { commitDrag(displayItems, item.id, reorder.onCommit) },
                        ),
                )
            }
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
            cartSections(
                rows = displayItems,
                pinnedTitle = { stringResource(R.string.cart_section_pinned) },
                othersTitle = { stringResource(R.string.cart_section_others) },
                row = row,
            )
        }
    }
}

// Section keys; distinct from item ids (UUIDs), so they never collide.
private const val PINNED_HEADER_KEY = "section:pinned"
private const val OTHERS_HEADER_KEY = "section:others"

/**
 * Pinned rows under a "Pinned" heading, then the rest — headed "Other items"
 * only when there are pinned rows to set them apart from. With nothing
 * pinned it's one plain list. Each section reorders on its own
 * ([moveByKey]); pin or unpin a row to move it across.
 */
private fun LazyListScope.cartSections(
    rows: List<CartItem>,
    pinnedTitle: @Composable () -> String,
    othersTitle: @Composable () -> String,
    row: @Composable LazyItemScope.(CartItem) -> Unit,
) {
    val (pinned, others) = rows.partition { it.pinned }
    if (pinned.isNotEmpty()) {
        item(key = PINNED_HEADER_KEY) { LedgerSectionHeader(pinnedTitle(), Modifier.animateItem()) }
        items(pinned, key = { it.id }, itemContent = row)
        if (others.isNotEmpty()) {
            item(key = OTHERS_HEADER_KEY) { LedgerSectionHeader(othersTitle(), Modifier.animateItem()) }
        }
    }
    items(others, key = { it.id }, itemContent = row)
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

// Moves the dragged row ([fromKey]) to the slot of the row it passed
// ([toKey]) — only within its own section: a pinned row never swaps with an
// unpinned one. Keys that aren't rows (the headings) are ignored.
internal fun SnapshotStateList<CartItem>.moveByKey(
    fromKey: Any,
    toKey: Any,
) {
    val from = indexOfFirst { it.id == fromKey }
    val to = indexOfFirst { it.id == toKey }
    if (from < 0 || to < 0 || from == to) return
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
