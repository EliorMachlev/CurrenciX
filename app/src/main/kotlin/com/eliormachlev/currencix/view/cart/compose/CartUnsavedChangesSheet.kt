package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.runtime.Composable
import com.eliormachlev.currencix.R

/**
 * Multi-branch prompt shown before a destructive cart transition (switching
 * carts, closing the screen) when there are unsaved changes. Shape:
 *  - "Save" (overwrite) — only when the cart has a persisted counterpart.
 *  - "Save as new" — always available.
 *  - "Discard changes" — always available.
 *  - "Continue" — always available (proceed with destructive action, keep buffer).
 * Cancel is implicit via sheet drag-down / scrim tap.
 *
 * Thin wrapper over [CartChoiceSheet] so all cart branch-picker surfaces share
 * the same row shape (title + one-line explainer) and the coordinator only
 * has to describe *what* the branches do, not *how* to lay them out.
 */
@Composable
fun CartUnsavedChangesSheet(
    canOverwrite: Boolean,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onDiscard: () -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val options =
        buildList {
            if (canOverwrite) {
                add(
                    CartChoiceOption(
                        R.string.cart_unsaved_save,
                        R.string.cart_unsaved_save_desc,
                        onSave,
                    ),
                )
            }
            add(
                CartChoiceOption(
                    R.string.cart_unsaved_save_as,
                    R.string.cart_unsaved_save_as_desc,
                    onSaveAs,
                ),
            )
            add(
                CartChoiceOption(
                    R.string.cart_unsaved_discard,
                    R.string.cart_unsaved_discard_desc,
                    onDiscard,
                ),
            )
            add(
                CartChoiceOption(
                    R.string.cart_unsaved_continue,
                    R.string.cart_unsaved_continue_desc,
                    onContinue,
                ),
            )
        }
    CartChoiceSheet(
        titleRes = R.string.cart_unsaved_title,
        options = options,
        onDismiss = onDismiss,
    )
}
