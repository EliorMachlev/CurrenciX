package com.eliormachlev.currencix.view.cart

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.repository.CartExporter
import com.eliormachlev.currencix.util.CalculatorKeyListener
import com.eliormachlev.currencix.util.hapticTap
import com.eliormachlev.currencix.view.BaseActivity
import com.eliormachlev.currencix.view.cart.compose.CartScreen
import com.eliormachlev.currencix.view.preference.PreferenceActivity
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel

class CartActivity : BaseActivity() {
    private lateinit var viewModel: CartViewModel
    private lateinit var exporter: CartExporter
    private lateinit var fileIo: CartFileIo
    private lateinit var shareCoordinator: CartShareCoordinator
    private lateinit var saveLoadCoordinator: CartSaveLoadCoordinator
    private lateinit var keypad: CartKeypadController

    // Pending, un-debounced name edits from the composable rows. Flushed
    // synchronously by [flushPendingCommits] before any save/share/snapshot.
    private val pendingNames = mutableMapOf<String, String>()

    // LiveData sources bridged into Compose. Kept as fields so observeAsState
    // in the list survives cart re-emissions.
    private val itemsLive = MediatorLiveData<List<CartItem>>().apply { value = emptyList() }
    private val currencyLive = MediatorLiveData<String>().apply { value = "" }

    // Single signal for the compose row: non-null iff a system-IME variant is
    // selected. Collapses the "should host inline editor?" + "which IME class?"
    // decisions into one.
    private val keyListenerLive: LiveData<CalculatorKeyListener?> by lazy {
        viewModel.keyboardType.map { CalculatorKeyListener.forKeyboardType(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.apply {
            title = getString(R.string.cart_title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        this.viewModel = ViewModelProvider(this)[CartViewModel::class.java]
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
            )
        this.saveLoadCoordinator =
            CartSaveLoadCoordinator(
                activity = this,
                viewModel = viewModel,
                flushPendingCommits = ::flushPendingCommits,
                snackbar = ::showSnackbar,
            )
        this.keypad =
            CartKeypadController(
                activity = this,
                keyboardType = viewModel.keyboardType,
                onExpressionCommit = ::commitExpression,
            )

        // Compose owns the entire screen tree — no XML layout involved.
        setContentView(
            ComposeView(this).apply {
                fitsSystemWindows = true
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    CartScreen(
                        viewModel = viewModel,
                        fragmentManager = supportFragmentManager,
                        keypad = keypad,
                        itemsSource = itemsLive,
                        currencySource = currencyLive,
                        keyListenerSource = keyListenerLive,
                        onAddItem = { viewModel.addItem(name = "", expression = "") },
                        onNameCommit = ::commitName,
                        onNamePending = { id, name -> pendingNames[id] = name },
                        onExpressionTap = { item -> keypad.openKeypadFor(item.id, item.expression) },
                        onExpressionChange = keypad::onInlineExpressionChanged,
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
                }
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
            itemsLive.value = cart.items.toList()
            // A cart load can retire the item the keypad was bound to; drop
            // that binding so the keypad doesn't linger over a missing row.
            val currentIds = cart.items.map { it.id }.toSet()
            pendingNames.keys.retainAll(currentIds)
            keypad.activeItemId.value?.let { if (it !in currentIds) keypad.closeKeypad() }
        }
    }

    private fun openFeesSettings() {
        startActivity(PreferenceActivity.feesIntent(this))
    }

    private fun confirmClear() {
        showCartChoiceExplainerDialog(
            titleRes = R.string.cart_menu_clear,
            choices =
                listOf(
                    CartChoice(
                        R.string.cart_clear_items_only,
                        R.string.cart_clear_items_only_desc,
                    ) { viewModel.clearItems() },
                    CartChoice(
                        R.string.cart_clear_reset_all,
                        R.string.cart_clear_reset_all_desc,
                    ) { viewModel.resetToMainDefaults() },
                ),
        )
    }

    // Cancel every row's pending debounce and push its current buffer to the
    // view model synchronously. Must run before any snapshot/save/share so a
    // freshly-typed name or a pending keypad expression doesn't get lost.
    private fun flushPendingCommits() {
        keypad.flushActiveExpression()
        val snapshot = pendingNames.toMap()
        pendingNames.clear()
        snapshot.forEach { (id, name) -> commitName(id, name) }
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
        snackbar(message).show()
    }
}
