package com.eliormachlev.currencix.view.cart

import android.content.Context
import androidx.annotation.StringRes
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel

/**
 * Payload for the "give this cart a name" dialog. Save-as and Rename both use
 * this shape so the same [com.eliormachlev.currencix.view.cart.compose.CartNameInputDialog]
 * handles both entry points.
 */
data class CartNameInputRequest(
    @StringRes val titleRes: Int,
    val initial: String,
    val onOk: (String) -> Unit,
)

/**
 * Payload for the confirm-delete dialog spawned from inside the load-list
 * sheet. [name] is the pre-formatted display title (falls back to a short-id
 * prefix when the cart was never named).
 */
data class CartDeleteConfirmRequest(
    val name: String,
    val onConfirm: () -> Unit,
)

/**
 * Payload for the unsaved-changes prompt. [canOverwrite] hides the "Save"
 * branch when the current cart has no persisted counterpart to overwrite —
 * without it the branch would misleadingly claim to overwrite nothing.
 */
data class CartUnsavedChangesRequest(
    val canOverwrite: Boolean,
    val onSave: () -> Unit,
    val onSaveAs: () -> Unit,
    val onDiscard: () -> Unit,
    val onContinue: () -> Unit,
)

/** What [CartSaveLoadCoordinator] asks the screen to show: a message, or one of its dialogs. */
class CartSaveLoadPrompts(
    val snackbar: (String) -> Unit,
    val showLoadList: () -> Unit,
    val showUnsavedChanges: (CartUnsavedChangesRequest) -> Unit,
    val showNameInput: (CartNameInputRequest) -> Unit,
    val showDeleteConfirm: (CartDeleteConfirmRequest) -> Unit,
)

/**
 * Owns the Save / Save-as / Load / Rename / Delete flows plus the
 * unsaved-changes prompt that gates destructive transitions (loading another
 * cart, closing the screen). Persistence is delegated to [CartViewModel];
 * this class only decides *which* dialog to show and hands the payload to the
 * compose overlays via injected callbacks. All UI is compose-native ledger
 * chrome — no AppCompat AlertDialog instances anywhere.
 */
class CartSaveLoadCoordinator(
    private val context: Context,
    private val viewModel: CartViewModel,
    private val flushPendingCommits: () -> Unit,
    private val prompts: CartSaveLoadPrompts,
    private val onClose: () -> Unit,
) {
    /**
     * Leaves the cart, first offering to save when it has unsaved edits.
     * Back only routes here while [needsClosePrompt] — otherwise the back
     * gesture pops the screen directly.
     */
    fun attemptClose() = confirmUnsavedThen(onClose)

    /** Whether leaving now would ask about unsaved changes (see [confirmUnsavedThen]). */
    fun needsClosePrompt(): Boolean = viewModel.currentCartItems().isNotEmpty() && viewModel.hasUnsavedChanges()

    /**
     * "Save" menu action — overwrites the current saved cart in-place. Falls
     * back to Save-as when there's nothing to overwrite (never saved yet).
     */
    fun saveOrPromptForName(onSaved: () -> Unit = {}) {
        if (guardEmptyForSave()) return
        flushPendingCommits()
        if (viewModel.saveCurrent()) {
            prompts.snackbar(context.getString(R.string.cart_saved_toast, viewModel.currentCartName()))
            onSaved()
        } else {
            showSaveAsDialog(onSaved = onSaved)
        }
    }

    fun showSaveAsDialog(onSaved: () -> Unit = {}) {
        if (guardEmptyForSave()) return
        prompts.showNameInput(
            CartNameInputRequest(
                titleRes = R.string.cart_menu_save_as,
                initial = viewModel.currentCartName(),
            ) { name ->
                // "Save as" always creates a fresh entry so users can keep
                // multiple snapshots of the same cart under different names.
                flushPendingCommits()
                viewModel.saveCurrentAs(name)
                prompts.snackbar(context.getString(R.string.cart_saved_toast, name))
                onSaved()
            },
        )
    }

    fun showLoadDialog() {
        if (viewModel.getSavedCartsSnapshot().isEmpty()) {
            prompts.snackbar(context.getString(R.string.cart_no_saved))
            return
        }
        prompts.showLoadList()
    }

    fun onLoadPick(cart: SavedCart) = confirmUnsavedThen { viewModel.loadSaved(cart.id) }

    fun onLoadRename(cart: SavedCart) {
        prompts.showNameInput(
            CartNameInputRequest(
                titleRes = R.string.cart_rename_title,
                initial = cart.name,
            ) { name -> viewModel.renameSaved(cart.id, name) },
        )
    }

    fun onLoadDelete(cart: SavedCart) {
        prompts.showDeleteConfirm(
            CartDeleteConfirmRequest(
                name = cart.name.ifBlank { cart.id.take(SHORT_ID_LENGTH) },
            ) { viewModel.deleteSaved(cart.id) },
        )
    }

    // Refuse to persist an empty cart — matches Share's "nothing to share"
    // guard so both write paths behave consistently. Returns true when the
    // caller should abort.
    private fun guardEmptyForSave(): Boolean {
        if (viewModel.currentCartItems().isEmpty()) {
            prompts.snackbar(context.getString(R.string.cart_save_empty))
            return true
        }
        return false
    }

    /**
     * Gate a destructive action (switching carts, closing the screen) behind
     * an unsaved-changes prompt. Presents Save (overwrite — only when the
     * cart has a persisted counterpart), Save as (new entry), Discard, and
     * Continue. Each save path invokes [action] after the write completes.
     */
    private fun confirmUnsavedThen(action: () -> Unit) {
        // An empty cart has nothing worth saving, so skip the prompt entirely
        // even if a persisted counterpart differs — the destructive action
        // would just replace an empty working set with something else.
        if (!needsClosePrompt()) {
            action()
            return
        }
        prompts.showUnsavedChanges(
            CartUnsavedChangesRequest(
                canOverwrite = viewModel.currentCartHasId(),
                onSave = { saveOrPromptForName(action) },
                onSaveAs = { showSaveAsDialog(onSaved = action) },
                onDiscard = {
                    viewModel.discardChanges()
                    action()
                },
                onContinue = action,
            ),
        )
    }

    private companion object {
        // Fall-back display prefix for saved carts with no user-given name;
        // matches the load-list row's own truncation.
        const val SHORT_ID_LENGTH = 8
    }
}
