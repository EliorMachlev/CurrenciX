package com.eliormachlev.currencix.view.cart

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModelProvider
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.repository.CartExporter
import com.eliormachlev.currencix.util.hapticTap
import com.eliormachlev.currencix.view.BaseActivity
import com.eliormachlev.currencix.view.cart.compose.CartChoiceRequest
import com.eliormachlev.currencix.view.cart.compose.CartChoiceSheet
import com.eliormachlev.currencix.view.cart.compose.CartLoadSheet
import com.eliormachlev.currencix.view.cart.compose.CartNameInputDialog
import com.eliormachlev.currencix.view.cart.compose.CartScreen
import com.eliormachlev.currencix.view.cart.compose.CartUnsavedChangesSheet
import com.eliormachlev.currencix.view.compose.dialogs.LedgerConfirmDialog
import com.eliormachlev.currencix.view.preference.PreferenceActivity
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

class CartActivity : BaseActivity() {
    private lateinit var viewModel: CartViewModel
    private lateinit var exporter: CartExporter
    private lateinit var fileIo: CartFileIo
    private lateinit var shareCoordinator: CartShareCoordinator
    private lateinit var saveLoadCoordinator: CartSaveLoadCoordinator
    private lateinit var keypad: CartKeypadController

    // Main's currently-visible currency pair, delivered via intent extras when
    // Cart is opened from main. Used to seed a fresh cart and to re-seed on
    // Clear, so the cart always mirrors what the user just saw on main
    // (bypasses stored-prefs collisions).
    private var mainBase: Currency? = null
    private var mainDest: Currency? = null

    // Pending, un-debounced name edits from the composable rows. Flushed
    // synchronously by [flushPendingCommits] before any save/share/snapshot.
    private val pendingNames = PendingNameBuffer()

    // Bridges for imperative callers (menu handlers, coordinators) to open
    // each overlay. Wired inside CartRoot's DisposableEffect; null when the
    // compose tree isn't attached (initial construction, teardown). Kept as
    // separate signals so the sheets/dialogs can stack (e.g. a delete-confirm
    // dialog above the load-list sheet) without one closing the other.
    private var openCartChoice: ((CartChoiceRequest) -> Unit)? = null
    private var openLoadList: (() -> Unit)? = null
    private var openUnsavedChanges: ((CartUnsavedChangesRequest) -> Unit)? = null
    private var openNameInput: ((CartNameInputRequest) -> Unit)? = null
    private var openDeleteConfirm: ((CartDeleteConfirmRequest) -> Unit)? = null
    private var openClearConfirm: (() -> Unit)? = null

    // LiveData sources bridged into Compose. Kept as fields so observeAsState
    // in the list survives cart re-emissions.
    private val itemsLive = MediatorLiveData<ImmutableList<CartItem>>().apply { value = persistentListOf() }
    private val currencyLive = MediatorLiveData<String>().apply { value = "" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.apply {
            title = getString(R.string.cart_title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        mainBase = intent.getStringExtra(EXTRA_MAIN_BASE)?.let(Currency::fromString)
        mainDest = intent.getStringExtra(EXTRA_MAIN_DEST)?.let(Currency::fromString)

        this.viewModel = ViewModelProvider(this)[CartViewModel::class.java]
        viewModel.seedFromMain(mainBase, mainDest)
        this.exporter = CartExporter(this)
        this.fileIo =
            CartFileIo(
                activity = this,
                viewModel = viewModel,
                exporter = exporter,
                flushPendingCommits = ::flushPendingCommits,
                snackbar = ::showSnackbar,
            )
        this.shareCoordinator =
            CartShareCoordinator(
                activity = this,
                viewModel = viewModel,
                flushPendingCommits = ::flushPendingCommits,
                snackbar = ::showSnackbar,
                showChoice = ::showCartChoice,
            )
        this.saveLoadCoordinator =
            CartSaveLoadCoordinator(
                activity = this,
                viewModel = viewModel,
                flushPendingCommits = ::flushPendingCommits,
                snackbar = ::showSnackbar,
                showLoadList = { openLoadList?.invoke() },
                showUnsavedChanges = { request -> openUnsavedChanges?.invoke(request) },
                showNameInput = { request -> openNameInput?.invoke(request) },
                showDeleteConfirm = { request -> openDeleteConfirm?.invoke(request) },
            )
        this.keypad =
            CartKeypadController(
                activity = this,
                isExpandedKeypad = viewModel.isExpandedKeypadEnabled,
                onExpressionCommit = ::commitExpression,
            )

        // Compose owns the entire screen tree — no XML layout involved.
        setContentView(
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { CartRoot() }
            },
        )

        // Registered so back-press first tries the save-prompt flow. The
        // keypad's own BackHandler wins when it's up (BackHandler is
        // registered later in composition than this activity-level callback).
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = saveLoadCoordinator.attemptClose()
            },
        )

        observe()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.cart, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        hapticTap()
        return when (item.itemId) {
            android.R.id.home -> {
                saveLoadCoordinator.attemptClose()
                true
            }
            R.id.cart_share -> {
                shareCoordinator.show()
                true
            }
            R.id.cart_save -> {
                saveLoadCoordinator.saveOrPromptForName()
                true
            }
            R.id.cart_save_as -> {
                saveLoadCoordinator.showSaveAsDialog()
                true
            }
            R.id.cart_load -> {
                saveLoadCoordinator.showLoadDialog()
                true
            }
            R.id.cart_export -> {
                fileIo.launchExport()
                true
            }
            R.id.cart_import -> {
                fileIo.launchImport()
                true
            }
            R.id.cart_clear -> {
                confirmClear()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun observe() {
        viewModel.getCurrentCart().observe(this) { cart ->
            currencyLive.value = cart.currency
            itemsLive.value = cart.items.toImmutableList()
            // A cart load can retire the item the keypad was bound to; drop
            // that binding so the keypad doesn't linger over a missing row.
            val currentIds = cart.items.mapTo(mutableSetOf()) { it.id }
            pendingNames.retainAll(currentIds)
            keypad.activeItemId.value?.let { if (it !in currentIds) keypad.closeKeypad() }
        }
    }

    private fun openFeesSettings() {
        startActivity(PreferenceActivity.feesIntent(this))
    }

    // Compose root — creates the overlay state that imperative callers push
    // into (via the openXxx fields) and renders CartScreen with a CartOverlays
    // host on top.
    @Composable
    private fun CartRoot() {
        val overlays = remember { CartOverlayState() }
        DisposableEffect(Unit) {
            openCartChoice = { overlays.cartChoice = it }
            openLoadList = { overlays.loadListVisible = true }
            openUnsavedChanges = { overlays.unsavedChanges = it }
            openNameInput = { overlays.nameInput = it }
            openDeleteConfirm = { overlays.deleteConfirm = it }
            openClearConfirm = { overlays.clearConfirmVisible = true }
            onDispose {
                openCartChoice = null
                openLoadList = null
                openUnsavedChanges = null
                openNameInput = null
                openDeleteConfirm = null
                openClearConfirm = null
            }
        }
        CartScreen(
            viewModel = viewModel,
            keypad = keypad,
            itemsSource = itemsLive,
            currencySource = currencyLive,
            onAddItem = { viewModel.addItem(name = "", expression = "") },
            onNameCommit = ::commitName,
            onNamePending = pendingNames::put,
            onExpressionTap = { item -> keypad.openKeypadFor(item.id, item.expression) },
            onTogglePin = viewModel::togglePinned,
            onDelete = { id ->
                if (keypad.activeItemId.value == id) keypad.closeKeypad()
                pendingNames.remove(id)
                viewModel.removeItem(id)
            },
            onReorder = viewModel::reorderItem,
            // A drag doesn't interact well with a floating keypad — the
            // row being edited would slide out from under the caret.
            // Commit the current edit and close before the gesture takes
            // over the visible list.
            onReorderStart = keypad::closeKeypad,
            onOpenFees = ::openFeesSettings,
        )
        CartOverlays(overlays)
    }

    // Compose overlay host — every sheet/dialog CartRoot can open sits here.
    @Composable
    private fun CartOverlays(state: CartOverlayState) {
        state.cartChoice?.let { request ->
            CartChoiceSheet(
                titleRes = request.titleRes,
                options = request.options,
                onDismiss = { state.cartChoice = null },
            )
        }
        if (state.loadListVisible) {
            CartLoadSheet(
                viewModel = viewModel,
                onPick = saveLoadCoordinator::onLoadPick,
                onRename = saveLoadCoordinator::onLoadRename,
                onDelete = saveLoadCoordinator::onLoadDelete,
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
        if (state.clearConfirmVisible) {
            DestructiveConfirmDialog(
                title = stringResource(id = R.string.cart_menu_clear),
                message = stringResource(id = R.string.cart_clear_confirm),
                confirmLabel = stringResource(id = R.string.cart_clear_confirm_button),
                onConfirm = { viewModel.clearCart(mainBase, mainDest) },
                onClose = { state.clearConfirmVisible = false },
            )
        }
    }

    private fun showCartChoice(request: CartChoiceRequest) {
        openCartChoice?.invoke(request)
    }

    private fun confirmClear() {
        openClearConfirm?.invoke()
    }

    // Cancel every row's pending debounce and push its current buffer to the
    // view model synchronously. Must run before any snapshot/save/share so a
    // freshly-typed name or a pending keypad expression doesn't get lost.
    private fun flushPendingCommits() {
        keypad.flushActiveExpression()
        pendingNames.drain().forEach { (id, name) -> commitName(id, name) }
    }

    private fun commitName(
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

    private fun showSnackbar(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val EXTRA_MAIN_BASE = "com.eliormachlev.currencix.cart.MAIN_BASE"
        private const val EXTRA_MAIN_DEST = "com.eliormachlev.currencix.cart.MAIN_DEST"

        fun intent(
            context: Context,
            mainBase: Currency?,
            mainDest: Currency?,
        ): Intent =
            Intent(context, CartActivity::class.java).apply {
                mainBase?.let { putExtra(EXTRA_MAIN_BASE, it.iso4217Alpha()) }
                mainDest?.let { putExtra(EXTRA_MAIN_DEST, it.iso4217Alpha()) }
            }
    }
}

// Row-id → pending display name for un-debounced text edits. Encapsulated so
// mutations read intentfully (put/remove/drain) instead of the raw MutableMap
// operations that used to be scattered across the activity.
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

// The cart's destructive confirmations (delete a saved cart, clear the current
// one) share one shape: confirming runs [onConfirm] and then closes the dialog
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

// Overlay visibility bag for CartActivity — each field toggles one sheet or
// dialog. Held in `remember` so mutations recompose CartOverlays without
// forcing recomposition of CartScreen's list content.
private class CartOverlayState {
    var cartChoice by mutableStateOf<CartChoiceRequest?>(null)
    var loadListVisible by mutableStateOf(false)
    var unsavedChanges by mutableStateOf<CartUnsavedChangesRequest?>(null)
    var nameInput by mutableStateOf<CartNameInputRequest?>(null)
    var deleteConfirm by mutableStateOf<CartDeleteConfirmRequest?>(null)
    var clearConfirmVisible by mutableStateOf(false)
}
