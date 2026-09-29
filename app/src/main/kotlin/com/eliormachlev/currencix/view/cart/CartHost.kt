package com.eliormachlev.currencix.view.cart

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.repository.CartExporter
import com.eliormachlev.currencix.view.cart.compose.CartChoiceRequest
import com.eliormachlev.currencix.view.compose.AppSnackbar
import com.eliormachlev.currencix.view.compose.showOrToast
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The cart screen's non-UI half: the coordinators behind its menu (share,
 * save / load, file import / export), the floating keypad, the pending
 * name edits, and which sheet or dialog is open. One per cart screen,
 * remembered for as long as the screen is composed; [dispose] when it
 * leaves.
 *
 * [mainBase] / [mainDest] are the converter's pair when the cart opened —
 * Clear re-seeds the cart with them.
 */
class CartHost(
    private val activity: ComponentActivity,
    private val snackbar: AppSnackbar?,
    val viewModel: CartViewModel,
    private val mainBase: Currency?,
    private val mainDest: Currency?,
    onClose: () -> Unit,
) {
    // Un-debounced name edits from the rows. Flushed synchronously by
    // [flushPendingCommits] before any save / share / snapshot.
    private val pendingNames = PendingNameBuffer()

    /** Which sheet / dialog is up. Separate fields so they can stack (a delete confirm above the load list). */
    val overlays = CartOverlayState()

    // LiveData bridged into Compose; fields so the list's observeAsState
    // survives cart re-emissions.
    val itemsLive = MediatorLiveData<ImmutableList<CartItem>>().apply { value = persistentListOf() }
    val currencyLive = MediatorLiveData<String>().apply { value = "" }

    private val toast: (String) -> Unit = { message -> snackbar.showOrToast(activity, message) }

    // A destructive change the user can take back from the snackbar. Without
    // a snackbar (outside the app shell) the change simply stands.
    private val toastWithUndo: (String, () -> Unit) -> Unit = { message, undo ->
        snackbar?.showWithUndo(message, activity.getString(R.string.undo), undo) ?: toast(message)
    }

    val fileIo =
        CartFileIo(
            activity = activity,
            viewModel = viewModel,
            exporter = CartExporter(activity),
            flushPendingCommits = ::flushPendingCommits,
            snackbar = toast,
            snackbarWithUndo = toastWithUndo,
        )
    val shareCoordinator =
        CartShareCoordinator(
            context = activity,
            viewModel = viewModel,
            flushPendingCommits = ::flushPendingCommits,
            snackbar = toast,
            showChoice = { request -> overlays.cartChoice = request },
        )
    val saveLoad =
        CartSaveLoadCoordinator(
            context = activity,
            viewModel = viewModel,
            flushPendingCommits = ::flushPendingCommits,
            snackbar = toast,
            showLoadList = { overlays.loadListVisible = true },
            showUnsavedChanges = { request -> overlays.unsavedChanges = request },
            showNameInput = { request -> overlays.nameInput = request },
            showDeleteConfirm = { request -> overlays.deleteConfirm = request },
            onClose = {
                flushPendingCommits()
                onClose()
            },
        )
    val keypad =
        CartKeypadController(
            activity = activity,
            isExpandedKeypad = viewModel.isExpandedKeypadEnabled,
            onExpressionCommit = ::commitExpression,
        )

    private val cartObserver =
        Observer<SavedCart> { cart ->
            currencyLive.value = cart.currency
            itemsLive.value = cart.items.toImmutableList()
            // A cart load can retire the item the keypad was bound to; drop
            // that binding so the keypad doesn't linger over a missing row.
            val currentIds = cart.items.mapTo(mutableSetOf()) { it.id }
            pendingNames.retainAll(currentIds)
            keypad.activeItemId.value?.let { if (it !in currentIds) keypad.closeKeypad() }
        }

    fun observe(owner: LifecycleOwner) = viewModel.getCurrentCart().observe(owner, cartObserver)

    fun dispose() {
        viewModel.getCurrentCart().removeObserver(cartObserver)
        fileIo.unregister()
    }

    /** Empties the cart (re-seeded with the converter's pair); Undo restores it as it was. */
    fun clearCart() {
        flushPendingCommits()
        val previous = viewModel.getCurrentCart().value ?: return
        if (previous.items.isEmpty()) return
        keypad.closeKeypad()
        viewModel.clearCart(mainBase, mainDest)
        toastWithUndo(activity.getString(R.string.cart_cleared)) { viewModel.setCurrent(previous) }
    }

    fun addItem() = viewModel.addItem(name = "", expression = "")

    /** Removes one row (button or swipe); Undo puts it back where it was. */
    fun deleteItem(id: String) {
        if (keypad.activeItemId.value == id) keypad.closeKeypad()
        pendingNames.remove(id)
        val items =
            viewModel
                .getCurrentCart()
                .value
                ?.items
                .orEmpty()
        val index = items.indexOfFirst { it.id == id }
        viewModel.removeItem(id)
        if (index < 0) return
        val removed = items[index]
        toastWithUndo(activity.getString(R.string.cart_item_deleted)) { viewModel.restoreItem(removed, index) }
    }

    fun onNamePending(
        id: String,
        name: String,
    ) = pendingNames.put(id, name)

    // Cancel every row's pending debounce and push its current buffer to the
    // view model synchronously. Must run before any snapshot/save/share so a
    // freshly-typed name or a pending keypad expression doesn't get lost.
    fun flushPendingCommits() {
        keypad.flushActiveExpression()
        pendingNames.drain().forEach { (id, name) -> commitName(id, name) }
    }

    fun commitName(
        id: String,
        name: String,
    ) {
        pendingNames.remove(id)
        val current = itemsLive.value?.firstOrNull { it.id == id } ?: return
        if (current.name == name) return
        viewModel.updateItem(id, name, current.expression)
    }

    private fun commitExpression(
        id: String,
        expression: String,
    ) {
        val current = itemsLive.value?.firstOrNull { it.id == id } ?: return
        val effectiveName = pendingNames[id] ?: current.name
        if (current.name == effectiveName && current.expression == expression) return
        pendingNames.remove(id)
        viewModel.updateItem(id, effectiveName, expression)
    }
}

// Overlay visibility bag — each field toggles one sheet or dialog. Snapshot
// state, so a change recomposes only the overlay host, not the cart list.
class CartOverlayState {
    var cartChoice by mutableStateOf<CartChoiceRequest?>(null)
    var loadListVisible by mutableStateOf(false)
    var unsavedChanges by mutableStateOf<CartUnsavedChangesRequest?>(null)
    var nameInput by mutableStateOf<CartNameInputRequest?>(null)
    var deleteConfirm by mutableStateOf<CartDeleteConfirmRequest?>(null)
}

// Row-id → pending display name for un-debounced text edits. Encapsulated so
// mutations read intentfully (put/remove/drain) instead of raw map operations.
private class PendingNameBuffer {
    private val map = mutableMapOf<String, String>()

    operator fun get(id: String): String? = map[id]

    fun put(
        id: String,
        name: String,
    ) {
        map[id] = name
    }

    fun remove(id: String) {
        map.remove(id)
    }

    fun retainAll(ids: Set<String>) {
        map.keys.retainAll(ids)
    }

    /** Take a snapshot of every pending edit and clear the buffer in one shot. */
    fun drain(): Map<String, String> {
        val snapshot = map.toMap()
        map.clear()
        return snapshot
    }
}
