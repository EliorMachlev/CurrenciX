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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.view.cart.compose.CartChoiceSheet
import com.eliormachlev.currencix.view.cart.compose.CartExtrasSheet
import com.eliormachlev.currencix.view.cart.compose.CartItemActions
import com.eliormachlev.currencix.view.cart.compose.CartItemArrangeActions
import com.eliormachlev.currencix.view.cart.compose.CartListMode
import com.eliormachlev.currencix.view.cart.compose.CartListSources
import com.eliormachlev.currencix.view.cart.compose.CartLoadSheet
import com.eliormachlev.currencix.view.cart.compose.CartNameInputSheet
import com.eliormachlev.currencix.view.cart.compose.CartReorder
import com.eliormachlev.currencix.view.cart.compose.CartScreen
import com.eliormachlev.currencix.view.cart.compose.CartScreenActions
import com.eliormachlev.currencix.view.cart.compose.CartUnsavedChangesSheet
import com.eliormachlev.currencix.view.cart.compose.ReceiptLanguageOfferSheet
import com.eliormachlev.currencix.view.cart.compose.ReceiptReadingSheet
import com.eliormachlev.currencix.view.cart.compose.ReceiptReviewSheet
import com.eliormachlev.currencix.view.cart.compose.ReceiptSetupOfferSheet
import com.eliormachlev.currencix.view.cart.compose.ReceiptSetupProgressSheet
import com.eliormachlev.currencix.view.compose.LocalAppSnackbar
import com.eliormachlev.currencix.view.compose.OverflowAction
import com.eliormachlev.currencix.view.compose.ReadingDirection
import com.eliormachlev.currencix.view.compose.ScreenScaffold
import com.eliormachlev.currencix.view.compose.TopBarAction
import com.eliormachlev.currencix.view.compose.TopBarOverflowMenu
import com.eliormachlev.currencix.view.compose.dialogs.LedgerConfirmSheet
import com.eliormachlev.currencix.view.navigation.Screen
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel

/**
 * The shopping cart: items, totals and the floating keypad, under a top bar
 * whose overflow menu holds share, save / load, file import / export and
 * clear.
 *
 * Back leaves directly, except while the cart has unsaved edits, when it
 * first offers to save (the same prompt the up arrow shows).
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
    val scan = rememberReceiptScan()
    val cart by viewModel.getCurrentCart().observeAsState()
    val needsClosePrompt = remember(cart) { host.saveLoad.needsClosePrompt() }
    BackHandler(enabled = needsClosePrompt, onBack = host.saveLoad::attemptClose)

    // The bar follows the language: in Hebrew the back arrow and title on the
    // right, the overflow menu on the left.
    ReadingDirection {
        ScreenScaffold(
            title = { Text(stringResource(R.string.cart_title)) },
            onBack = host.saveLoad::attemptClose,
            actions = { CartBarActions(onSave = host.saveLoad::saveOrPromptForName, menu = cartMenu(host, scan)) },
        ) { padding ->
            CartScreen(
                viewModel = viewModel,
                keypad = host.keypad,
                sources = remember(host) { host.listSources() },
                actions = remember(host, onOpenFees) { host.screenActions(onOpenFees) },
                // With the window drawn edge to edge, the keyboard no longer
                // resizes it: lift the list and footer above the keyboard here.
                modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding(),
            )
        }
    }
    CartOverlays(host)
    ReceiptOverlays(host, scan)
}

// Reading a receipt photo, then its items to review and add.
@Composable
private fun ReceiptOverlays(
    host: CartHost,
    scan: ReceiptScan,
) {
    val snackbar = LocalAppSnackbar.current
    val resources = LocalResources.current
    val currency by host.viewModel.getBaseCurrency().observeAsState()
    val lines = scan.lines
    val setup = scan.setup
    if (scan.setupFailed) {
        val failed = stringResource(R.string.receipt_setup_failed)
        LaunchedEffect(Unit) {
            snackbar?.show(failed)
            scan.setupFailureShown()
        }
    }
    when {
        setup is ReceiptSetup.Offer -> {
            ReceiptSetupOfferSheet(offer = setup, onDownload = scan::acceptSetup, onDismiss = scan::dismissSetup)
        }

        setup is ReceiptSetup.LanguageOffer -> {
            ReceiptLanguageOfferSheet(language = setup.language, onAnswer = scan::answerLanguage)
        }

        setup is ReceiptSetup.Installing -> {
            ReceiptSetupProgressSheet(fraction = setup.fraction, onDismiss = scan::dismissSetup)
        }

        scan.reading -> {
            // Closing it doesn't stop the reading; the review follows when it's done.
            var hidden by remember { mutableStateOf(false) }
            if (!hidden) ReceiptReadingSheet(onDismiss = { hidden = true })
        }

        lines != null -> {
            ReceiptReviewSheet(
                lines = lines,
                currency = currency,
                languagePending = scan.languagePending,
                languageDeclined = scan.languageDeclined,
                onOfferLanguage = scan::offerLanguageAgain,
                onAdd = { chosen ->
                    chosen.forEach { host.viewModel.rows.add(it.name, it.price.toPlainString()) }
                    scan.clear()
                    snackbar?.show(resources.getQuantityString(R.plurals.receipt_added, chosen.size, chosen.size))
                },
                onDismiss = scan::clear,
            )
        }
    }
}

/** The cart bar's actions: Save, one tap away, then the ⋮ menu with Save as and the rest. */
@Composable
internal fun CartBarActions(
    onSave: () -> Unit,
    menu: List<OverflowAction>,
) {
    TopBarAction(
        icon = painterResource(R.drawable.ic_save),
        contentDescription = stringResource(R.string.cart_menu_save),
        onClick = onSave,
    )
    TopBarOverflowMenu(menu)
}

private fun CartHost.listSources() =
    CartListSources(
        items = itemsLive,
        currency = currencyLive,
        activeItemId = keypad.activeItemId,
        activeExpression = keypad.liveExpression,
        conversion = viewModel.getRowConversion(),
        mode = CartListMode(shoppingMode = viewModel.isShoppingMode(), people = viewModel.getPeople()),
    )

private fun CartHost.screenActions(onOpenFees: () -> Unit) =
    CartScreenActions(
        onAddItem = ::addItem,
        onOpenFees = onOpenFees,
        onEditExtras = { overlays.extrasVisible = true },
        items =
            CartItemActions(
                onNameCommit = ::commitName,
                onNamePending = ::onNamePending,
                onExpressionTap = { item -> keypad.openKeypadFor(item.id, item.expression) },
                onQuantityChange = viewModel.rows::setQuantity,
                onToggleChecked = viewModel.rows::toggleChecked,
                arrange =
                    CartItemArrangeActions(
                        onTogglePin = viewModel.rows::togglePinned,
                        onDuplicate = viewModel.rows::duplicate,
                        onMoveToTop = viewModel.rows::moveToTop,
                        onDelete = ::deleteItem,
                        onAssign = viewModel.rows::assignTo,
                    ),
            ),
        // A drag doesn't interact well with a floating keypad — the row
        // being edited would slide out from under the caret. Commit the
        // current edit and close before the gesture takes over the list.
        reorder = CartReorder(onStart = keypad::closeKeypad, onCommit = viewModel.rows::commitDrag),
    )

@Composable
private fun cartMenu(
    host: CartHost,
    scan: ReceiptScan,
): List<OverflowAction> {
    val scanLabel = stringResource(R.string.receipt_scan)
    val share = stringResource(R.string.menu_share)
    val saveAs = stringResource(R.string.cart_menu_save_as)
    val load = stringResource(R.string.cart_menu_load)
    val export = stringResource(R.string.cart_menu_export)
    val import = stringResource(R.string.cart_menu_import)
    val clear = stringResource(R.string.cart_menu_clear)
    val extras = stringResource(R.string.cart_menu_extras)
    val shoppingMode by host.viewModel.isShoppingMode().observeAsState(initial = false)
    val shoppingList = stringResource(if (shoppingMode) R.string.cart_menu_shopping_list_off else R.string.cart_menu_shopping_list)
    return remember(host, scan, scanLabel, share, extras, shoppingList, shoppingMode, saveAs, load, export, import, clear) {
        listOf(
            OverflowAction(scanLabel, R.drawable.ic_receipt) { scan.begin { host.overlays.cartChoice = scan.sourceChoice() } },
            OverflowAction(share, R.drawable.ic_share) { host.shareCoordinator.show() },
            OverflowAction(extras, R.drawable.ic_split) { host.overlays.extrasVisible = true },
            OverflowAction(shoppingList, R.drawable.ic_checklist) { host.viewModel.setShoppingMode(!shoppingMode) },
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
    if (state.extrasVisible) {
        val extras by host.viewModel.getExtras().observeAsState()
        val destination by host.viewModel.getDestinationCurrency().observeAsState()
        CartExtrasSheet(
            initial = extras ?: CartExtras(),
            destination = destination,
            onDone = { edited ->
                host.viewModel.setExtras(edited)
                state.extrasVisible = false
            },
            onDismiss = { state.extrasVisible = false },
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
        CartUnsavedChangesSheet(request = request, onDismiss = { state.unsavedChanges = null })
    }
    state.nameInput?.let { request ->
        CartNameInputSheet(
            titleRes = request.titleRes,
            initial = request.initial,
            onOk = request.onOk,
            onDismiss = { state.nameInput = null },
        )
    }
    state.deleteConfirm?.let { request ->
        DestructiveConfirmSheet(
            title = request.name,
            message = stringResource(id = R.string.cart_delete_confirm, request.name),
            confirmLabel = stringResource(id = R.string.cart_delete_confirm_button),
            onConfirm = request.onConfirm,
            onClose = { state.deleteConfirm = null },
        )
    }
}

// Confirmation for deleting a saved cart (which, unlike the working cart's
// rows, has no Undo): confirming runs [onConfirm] and then closes the sheet
// via [onClose], which dismissing also calls.
@Composable
private fun DestructiveConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) = LedgerConfirmSheet(
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
