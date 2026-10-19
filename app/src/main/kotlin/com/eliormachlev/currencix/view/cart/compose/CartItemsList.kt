package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Person
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.compose.LedgerSectionHeader
import com.eliormachlev.currencix.view.compose.UiTestTags
import com.eliormachlev.currencix.view.compose.onBackgroundTap
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.viewmodel.cart.RowConversion
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** A finished drag: the rows' new on-screen order and the row that moved. */
typealias CartDragCommit = (displayOrder: List<String>, movedId: String) -> Unit

/**
 * The live values the list renders. Stable: its fields never change, and
 * the values inside them reach the list by being observed.
 */
@Stable
class CartListSources(
    val items: LiveData<ImmutableList<CartItem>>,
    val currency: LiveData<String>,
    val activeItemId: LiveData<String?>,
    val activeExpression: LiveData<String>,
    // Each row's price in the destination currency.
    val conversion: LiveData<RowConversion?>,
    val mode: CartListMode,
)

/** How the cart is being used, which changes what each row offers. Stable, like [CartListSources]. */
@Stable
class CartListMode(
    // Shopping-list mode: each row has a tick for "in the basket".
    val shoppingMode: LiveData<Boolean>,
    // The cart's people, when it splits by person; empty otherwise.
    val people: LiveData<List<Person>>,
)

/** What a row's menu can ask for: pin, duplicate, move to top, delete, who it's for. */
@Immutable
class CartItemArrangeActions(
    val onTogglePin: (id: String) -> Unit,
    val onDuplicate: (id: String) -> Unit,
    val onMoveToTop: (id: String) -> Unit,
    val onDelete: (id: String) -> Unit,
    val onAssign: (id: String, personId: String?) -> Unit = { _, _ -> },
)

/** What the rows can ask for, each naming the row it's about. */
@Immutable
class CartItemActions(
    val onNameCommit: (id: String, name: String) -> Unit,
    val onNamePending: (id: String, name: String) -> Unit,
    val onExpressionTap: (item: CartItem) -> Unit,
    val onQuantityChange: (id: String, quantity: Int) -> Unit,
    val onToggleChecked: (id: String) -> Unit,
    val arrange: CartItemArrangeActions,
) {
    /** These actions, bound to [item]'s row. */
    fun forRow(item: CartItem) =
        CartRowActions(
            onNameCommit = { onNameCommit(item.id, it) },
            onNamePending = { onNamePending(item.id, it) },
            onExpressionTap = { onExpressionTap(item) },
            onTogglePin = { arrange.onTogglePin(item.id) },
            onDelete = { arrange.onDelete(item.id) },
            onQuantityChange = { onQuantityChange(item.id, it) },
            onToggleChecked = { onToggleChecked(item.id) },
            onDuplicate = { arrange.onDuplicate(item.id) },
            onMoveToTop = { arrange.onMoveToTop(item.id) },
            onAssign = { arrange.onAssign(item.id, it) },
        )
}

/** A drag to reorder: told when it starts, and where the rows ended up. */
@Immutable
class CartReorder(
    val onStart: () -> Unit,
    val onCommit: CartDragCommit,
)

/**
 * The cart's items, pinned ones first, each section one card of receipt
 * lines, then an "Add item" row; with no items, the empty state instead.
 */
@Composable
fun CartItemsList(
    sources: CartListSources,
    actions: CartItemActions,
    reorder: CartReorder,
    onAddItem: () -> Unit,
    onBackgroundTap: () -> Unit,
) {
    AppTheme {
        val items by sources.items.observeAsState(initial = persistentListOf())
        val currency by sources.currency.observeAsState(initial = "")
        val activeId by sources.activeItemId.observeAsState()
        val liveExpression by sources.activeExpression.observeAsState(initial = "")
        val conversion by sources.conversion.observeAsState()
        val shoppingMode by sources.mode.shoppingMode.observeAsState(initial = false)
        val people by sources.mode.people.observeAsState(initial = emptyList())

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
        val row: @Composable LazyItemScope.(CartItem, RowPosition) -> Unit = { item, position ->
            // ReorderableItem applies `Modifier.animateItem()` internally
            // via its `animateItemModifier` parameter, so add/remove/re-slot
            // already animates without extra wiring at the call site.
            ReorderableItem(reorderableState, key = item.id) { _ ->
                SwipeableCartItemRow(
                    state = CartRowState(item, currency, liveExpression.takeIf { item.id == activeId }, conversion, shoppingMode, people),
                    actions = remember(actions, item) { actions.forRow(item) },
                    position = position,
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
                    .testTag(UiTestTags.CART_LIST)
                    .onBackgroundTap(onBackgroundTap),
            contentPadding = LIST_PADDING,
        ) {
            if (displayItems.isEmpty()) {
                item(key = EMPTY_KEY) { CartEmptyState(onAddItem = onAddItem, modifier = Modifier.animateItem()) }
            } else {
                cartSections(
                    rows = displayItems,
                    pinnedTitle = { stringResource(R.string.cart_section_pinned) },
                    othersTitle = { stringResource(R.string.cart_section_others) },
                    row = row,
                )
                item(key = ADD_ROW_KEY) { AddItemRow(onAddItem = onAddItem, modifier = Modifier.animateItem()) }
            }
        }
    }
}

// Non-row keys; distinct from item ids (UUIDs), so they never collide.
private const val PINNED_HEADER_KEY = "section:pinned"
private const val OTHERS_HEADER_KEY = "section:others"
private const val ADD_ROW_KEY = "action:add"
private const val EMPTY_KEY = "state:empty"

private val LIST_PADDING = PaddingValues(horizontal = Spacing.Large, vertical = Spacing.Small)

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
    row: @Composable LazyItemScope.(CartItem, RowPosition) -> Unit,
) {
    val (pinned, others) = rows.partition { it.pinned }
    if (pinned.isNotEmpty()) {
        item(key = PINNED_HEADER_KEY) { LedgerSectionHeader(pinnedTitle(), Modifier.animateItem()) }
        sectionRows(pinned, row)
        if (others.isNotEmpty()) {
            item(key = OTHERS_HEADER_KEY) { LedgerSectionHeader(othersTitle(), Modifier.animateItem()) }
        }
    }
    sectionRows(others, row)
}

// One section's rows, each told where it sits in the section's card.
private fun LazyListScope.sectionRows(
    rows: List<CartItem>,
    row: @Composable LazyItemScope.(CartItem, RowPosition) -> Unit,
) {
    itemsIndexed(rows, key = { _, item -> item.id }) { index, item -> row(item, RowPosition.of(index, rows.size)) }
}

/**
 * SnapshotStateList mirror of the storage list, ordered pinned-first while
 * preserving storage order within each partition. Drag operates on this list.
 *
 * When the source order, the id set or the pinned set changes
 * (add/remove/load, move to top, a pin toggle), take the source order
 * wholesale, so a newly pinned row jumps to the top right away. When only content changed (name/expression edit mid-drag),
 * refresh per-id in place so the display's current order isn't wiped.
 */
@Composable
private fun rememberDisplayItems(items: ImmutableList<CartItem>): SnapshotStateList<CartItem> {
    val list = remember { mutableStateListOf<CartItem>() }
    // The source's order last time: a drag reorders [list] but not the
    // source, so only a change to this one replaces the list's order.
    var sourceOrder by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(items) {
        val ordered = items.sortedByDescending { it.pinned }
        val order = ordered.map { it.id }
        if (order != sourceOrder || list.pinState() != ordered.pinState()) {
            list.clear()
            list.addAll(ordered)
        } else {
            val byId = ordered.associateBy { it.id }
            list.indices.forEach { i ->
                val fresh = byId.getValue(list[i].id)
                if (fresh != list[i]) list[i] = fresh
            }
        }
        sourceOrder = order
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
