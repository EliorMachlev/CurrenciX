package com.eliormachlev.currencix.view.cart

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.view.cart.compose.CartChoiceSheet
import com.eliormachlev.currencix.view.cart.compose.CartLoadSheet
import com.eliormachlev.currencix.view.cart.compose.CartNameInputDialog
import com.eliormachlev.currencix.view.cart.compose.CartScreen
import com.eliormachlev.currencix.view.cart.compose.CartUnsavedChangesSheet
import com.eliormachlev.currencix.view.compose.LocalAppSnackbar
import com.eliormachlev.currencix.view.compose.OverflowAction
import com.eliormachlev.currencix.view.compose.ScreenScaffold
import com.eliormachlev.currencix.view.compose.TopBarOverflowMenu
import com.eliormachlev.currencix.view.compose.dialogs.LedgerConfirmDialog
import com.eliormachlev.currencix.view.navigation.Screen
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel

/**
 * The shopping cart: items, totals and the floating keypad, under a top bar
 * whose overflow menu holds share, save / load, file import / export and
 * clear.
 *
 * Back leaves directly — so the predictive back gesture can preview the
 * converter — except while the cart has unsaved edits, when it first offers
 * to save (the same prompt the up arrow shows).
 */
@Composable
fun CartRoute(
    screen: Screen.Cart,
    onBack: () -> Unit,
    onOpenFees: () -> Unit,
) {
    val activity = LocalActivity.current as ComponentActivity
    val viewModel: CartViewModel = viewModel()
    val snackbar = LocalAppSnackbar.current
    val host = remember(viewModel) { CartHost(activity, snackbar, viewModel, screen.mainBase, screen.mainDest, onBack) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(host, lifecycleOwner) {
        viewModel.seedFromMain(screen.mainBase, screen.mainDest)
        host.observe(lifecycleOwner)
        onDispose {
            host.flushPendingCommits()
            host.dispose()
        }
    }
    val cart by viewModel.getCurrentCart().observeAsState()
    val needsClosePrompt = remember(cart) { host.saveLoad.needsClosePrompt() }
    BackHandler(enabled = needsClosePrompt, onBack = host.saveLoad::attemptClose)

    ScreenScaffold(
        title = { Text(stringResource(R.string.cart_title)) },
        onBack = host.saveLoad::attemptClose,
        actions = { TopBarOverflowMenu(cartMenu(host)) },
    ) { padding ->
        CartScreen(
            viewModel = viewModel,
            keypad = host.keypad,
            itemsSource = host.itemsLive,
            currencySource = host.currencyLive,
            onAddItem = host::addItem,
            onNameCommit = host::commitName,
            onNamePending = host::onNamePending,
            onExpressionTap = { item -> host.keypad.openKeypadFor(item.id, item.expression) },
            onTogglePin = viewModel::togglePinned,
            onDelete = host::deleteItem,
            onReorder = viewModel::reorderItem,
            // A drag doesn't interact well with a floating keypad — the row
            // being edited would slide out from under the caret. Commit the
            // current edit and close before the gesture takes over the list.
            onReorderStart = host.keypad::closeKeypad,
            onOpenFees = onOpenFees,
            // With the window drawn edge to edge, the keyboard no longer
            // resizes it: lift the list and footer above the keyboard here.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding(),
        )
    }
    CartOverlays(host)
}

@Composable
private fun cartMenu(host: CartHost): List<OverflowAction> {
    val share = stringResource(R.string.menu_share)
    val save = stringResource(R.string.cart_menu_save)
    val saveAs = stringResource(R.string.cart_menu_save_as)
    val load = stringResource(R.string.cart_menu_load)
    val export = stringResource(R.string.cart_menu_export)
    val import = stringResource(R.string.cart_menu_import)
    val clear = stringResource(R.string.cart_menu_clear)
    return remember(host, share, save, saveAs, load, export, import, clear) {
        listOf(
            OverflowAction(share, R.drawable.ic_share) { host.shareCoordinator.show() },
            OverflowAction(save, R.drawable.ic_save) { host.saveLoad.saveOrPromptForName() },
            OverflowAction(saveAs, R.drawable.ic_save_as) { host.saveLoad.showSaveAsDialog() },
            OverflowAction(load, R.drawable.ic_folder_open) { host.saveLoad.showLoadDialog() },
            OverflowAction(export, R.drawable.ic_upload) { host.fileIo.launchExport() },
            OverflowAction(import, R.drawable.ic_download) { host.fileIo.launchImport() },
            // No confirmation: Clear is undoable from the snackbar.
            OverflowAction(clear, R.drawable.ic_delete, destructive = true, separated = true) { host.clearCart() },
        )
    }
}

// Every sheet / dialog the cart can open.
@Composable
private fun CartOverlays(host: CartHost) {
    val state = host.overlays
    state.cartChoice?.let { request ->
        CartChoiceSheet(
            titleRes = request.titleRes,
            options = request.options,
            onDismiss = { state.cartChoice = null },
        )
    }
    if (state.loadListVisible) {
        CartLoadSheet(
            viewModel = host.viewModel,
            onPick = host.saveLoad::onLoadPick,
            onRename = host.saveLoad::onLoadRename,
            onDelete = host.saveLoad::onLoadDelete,
            onDismiss = { state.loadListVisible = false },
        )
    }
    state.unsavedChanges?.let { request ->
        CartUnsavedChangesSheet(
            canOverwrite = request.canOverwrite,
            onSave = request.onSave,
            onSaveAs = request.onSaveAs,
            onDiscard = request.onDiscard,
            onContinue = request.onContinue,
            onDismiss = { state.unsavedChanges = null },
        )
    }
    state.nameInput?.let { request ->
        CartNameInputDialog(
            titleRes = request.titleRes,
            initial = request.initial,
            onOk = request.onOk,
            onDismiss = { state.nameInput = null },
        )
    }
    state.deleteConfirm?.let { request ->
        DestructiveConfirmDialog(
            title = request.name,
            message = stringResource(id = R.string.cart_delete_confirm, request.name),
            confirmLabel = stringResource(id = R.string.cart_delete_confirm_button),
            onConfirm = request.onConfirm,
            onClose = { state.deleteConfirm = null },
        )
    }
}

// Confirmation for deleting a saved cart (which, unlike the working cart's
// rows, has no Undo): confirming runs [onConfirm] and then closes the dialog
// via [onClose], which dismissing also calls.
@Composable
private fun DestructiveConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) = LedgerConfirmDialog(
    title = title,
    message = message,
    confirmLabel = confirmLabel,
    destructive = true,
    onConfirm = {
        onConfirm()
        onClose()
    },
    onDismiss = onClose,
)
