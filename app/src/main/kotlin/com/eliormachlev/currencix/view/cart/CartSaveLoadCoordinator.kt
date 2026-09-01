package com.eliormachlev.currencix.view.cart

import android.widget.EditText
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.util.wireHapticButtons
import com.eliormachlev.currencix.view.cart.compose.SavedCartsList
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel

/**
 * Owns the Save / Save-as / Load / Rename / Delete flows plus the
 * unsaved-changes prompt that gates destructive transitions (loading another
 * cart, closing the screen). Persistence is delegated to [CartViewModel];
 * this class only mediates the dialog UX.
 */
class CartSaveLoadCoordinator(
    private val activity: AppCompatActivity,
    private val viewModel: CartViewModel,
    private val flushPendingCommits: () -> Unit,
    private val snackbar: (String) -> Unit,
) {
    fun attemptClose() = confirmUnsavedThen { activity.finish() }

    /**
     * "Save" menu action — overwrites the current saved cart in-place. Falls
     * back to Save-as when there's nothing to overwrite (never saved yet).
     */
    fun saveOrPromptForName(onSaved: () -> Unit = {}) {
        if (guardEmptyForSave()) return
        flushPendingCommits()
        if (viewModel.saveCurrent()) {
            val name =
                viewModel
                    .getCurrentCart()
                    .value
                    ?.name
                    .orEmpty()
            snackbar(activity.getString(R.string.cart_saved_toast, name))
            onSaved()
        } else {
            showSaveAsDialog(onSaved = onSaved)
        }
    }

    fun showSaveAsDialog(onSaved: () -> Unit = {}) {
        if (guardEmptyForSave()) return
        showNameInputDialog(
            titleRes = R.string.cart_menu_save_as,
            initial =
                viewModel
                    .getCurrentCart()
                    .value
                    ?.name
                    .orEmpty(),
        ) { name ->
            // "Save as" always creates a fresh entry so users can keep
            // multiple snapshots of the same cart under different names.
            flushPendingCommits()
            viewModel.saveCurrentAs(name)
            snackbar(activity.getString(R.string.cart_saved_toast, name))
            onSaved()
        }
    }

    fun showLoadDialog() {
        val initial = viewModel.getSavedCartsSnapshot()
        if (initial.isEmpty()) {
            snackbar(activity.getString(R.string.cart_no_saved))
            return
        }
        val saved = mutableStateListOf<SavedCart>().apply { addAll(initial) }
        lateinit var dialog: AlertDialog
        val composeView =
            ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    AppTheme {
                        SavedCartsList(
                            items = saved,
                            onPick = { cart ->
                                dialog.dismiss()
                                confirmUnsavedThen { viewModel.loadSaved(cart.id) }
                            },
                            onRename = { cart ->
                                showNameInputDialog(
                                    titleRes = R.string.cart_rename_title,
                                    initial = cart.name,
                                ) { name ->
                                    viewModel.renameSaved(cart.id, name)
                                    val idx = saved.indexOfFirst { it.id == cart.id }
                                    if (idx >= 0) saved[idx] = cart.copy(name = name)
                                }
                            },
                            onDelete = { cart ->
                                AlertDialog
                                    .Builder(activity)
                                    .setTitle(cart.name.ifBlank { cart.id.take(8) })
                                    .setMessage(activity.getString(R.string.cart_delete_confirm, cart.name))
                                    .setPositiveButton(R.string.cart_delete_confirm_button) { _, _ ->
                                        viewModel.deleteSaved(cart.id)
                                        saved.removeAll { it.id == cart.id }
                                        if (saved.isEmpty()) dialog.dismiss()
                                    }.setNegativeButton(android.R.string.cancel, null)
                                    .show()
                                    .also { it.wireHapticButtons() }
                            },
                        )
                    }
                }
            }
        dialog =
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.cart_menu_load)
                .setView(composeView)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .apply { wireHapticButtons() }
        dialog.show()
    }

    // Refuse to persist an empty cart — matches Share's "nothing to share"
    // guard so both write paths behave consistently. Returns true when the
    // caller should abort.
    private fun guardEmptyForSave(): Boolean {
        val items = viewModel.getCurrentCart().value?.items
        if (items.isNullOrEmpty()) {
            snackbar(activity.getString(R.string.cart_save_empty))
            return true
        }
        return false
    }

    /**
     * Gate a destructive action (switching carts, closing the screen) behind
     * an unsaved-changes prompt. Presents Save (overwrite — only when the
     * cart has a persisted counterpart), Save as (new entry), Continue
     * (discard), and Cancel (abort). Each save path invokes [action] after
     * the write completes.
     */
    private fun confirmUnsavedThen(action: () -> Unit) {
        // An empty cart has nothing worth saving, so skip the prompt entirely
        // even if a persisted counterpart differs — the destructive action
        // would just replace an empty working set with something else.
        val items = viewModel.getCurrentCart().value?.items
        if (items.isNullOrEmpty() || !viewModel.hasUnsavedChanges()) {
            action()
            return
        }
        val canOverwrite =
            viewModel
                .getCurrentCart()
                .value
                ?.id
                ?.isNotEmpty() == true
        val options = mutableListOf<Pair<String, () -> Unit>>()
        if (canOverwrite) {
            options += activity.getString(R.string.cart_unsaved_save) to { saveOrPromptForName(action) }
        }
        options += activity.getString(R.string.cart_unsaved_save_as) to { showSaveAsDialog(onSaved = action) }
        options += activity.getString(R.string.cart_unsaved_discard) to {
            viewModel.discardChanges()
            action()
        }
        options += activity.getString(R.string.cart_unsaved_continue) to action
        AlertDialog
            .Builder(activity)
            .setTitle(R.string.cart_unsaved_title)
            .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                options[which].second.invoke()
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { it.wireHapticButtons() }
    }

    // Shared "one-line text input" dialog for cart name prompts (Save-as,
    // Rename). Blank input collapses to the localised default cart name so
    // every entry point produces a nameable, findable saved cart.
    private fun showNameInputDialog(
        @StringRes titleRes: Int,
        initial: String,
        onOk: (String) -> Unit,
    ) {
        val input =
            EditText(activity).apply {
                hint = activity.getString(R.string.cart_save_name_hint)
                setText(initial)
            }
        AlertDialog
            .Builder(activity)
            .setTitle(titleRes)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onOk(
                    input.text
                        .toString()
                        .trim()
                        .ifBlank { activity.getString(R.string.cart_default_saved_name) },
                )
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { it.wireHapticButtons() }
    }
}
