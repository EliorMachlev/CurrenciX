package com.eliormachlev.currencix.view.cart

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatButton
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
import com.eliormachlev.currencix.util.rateSpinnerListener
import com.eliormachlev.currencix.view.BaseActivity
import com.eliormachlev.currencix.view.cart.compose.CartEmptyHint
import com.eliormachlev.currencix.view.cart.compose.CartItemsList
import com.eliormachlev.currencix.view.main.spinner.SearchableSpinner
import com.eliormachlev.currencix.view.preference.PreferenceActivity
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel
import com.google.android.material.button.MaterialButton

class CartActivity : BaseActivity() {
    private lateinit var viewModel: CartViewModel
    private lateinit var exporter: CartExporter
    private lateinit var fileIo: CartFileIo
    private lateinit var shareCoordinator: CartShareCoordinator
    private lateinit var saveLoadCoordinator: CartSaveLoadCoordinator
    private lateinit var footerBinding: CartFooterBinding
    private lateinit var keypad: CartKeypadController

    private lateinit var itemsView: ComposeView
    private lateinit var spinnerFrom: SearchableSpinner
    private lateinit var spinnerTo: SearchableSpinner
    private lateinit var swapButton: ImageButton
    private lateinit var emptyHint: ComposeView
    private lateinit var addButton: MaterialButton

    // Cached haptic setting so per-tap handlers don't need to touch prefs.
    private var hapticEnabled = false

    // Pending, un-debounced name edits from the composable rows. Flushed
    // synchronously by [flushPendingCommits] before any save/share/snapshot.
    private val pendingNames = mutableMapOf<String, String>()

    // LiveData sources bridged into the Compose list. Kept as fields so
    // observeAsState in the list survives cart re-emissions.
    private val itemsLive = MediatorLiveData<List<CartItem>>().apply { value = emptyList() }
    private val currencyLive = MediatorLiveData<String>().apply { value = "" }

    // Single signal for the compose row: non-null iff a system-IME variant is
    // selected, and *which* CalculatorKeyListener to attach to the inline
    // EditText. Collapses the "should host inline editor?" + "which IME class?"
    // decisions into one.
    private val keyListenerLive: LiveData<CalculatorKeyListener?> by lazy {
        viewModel.keyboardType.map { CalculatorKeyListener.forKeyboardType(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)
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

        this.itemsView = findViewById(R.id.cart_items)
        this.footerBinding =
            CartFooterBinding(
                root = findViewById(R.id.cart_root),
                ctx = this,
                viewModel = viewModel,
            )
        this.spinnerFrom = findViewById(R.id.cart_spinner_from)
        this.spinnerTo = findViewById(R.id.cart_spinner_to)
        this.swapButton = findViewById(R.id.cart_swap)
        this.emptyHint =
            findViewById<ComposeView>(R.id.cart_empty_hint).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { CartEmptyHint() }
            }
        this.addButton = findViewById(R.id.cart_add_item)

        // Registered before the keypad controller's callback so the keypad's
        // (added second) wins when enabled. When the keypad is closed and
        // there are unsaved edits, we prompt instead of finishing.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = saveLoadCoordinator.attemptClose()
            },
        )
        this.keypad =
            CartKeypadController(
                activity = this,
                itemsView = itemsView,
                keyboardType = viewModel.keyboardType,
                hapticEnabled = { hapticEnabled },
                onExpressionCommit = ::commitExpression,
                dispatchCancel = ::superDispatchTouchEvent,
            )
        itemsView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        itemsView.setContent {
            CartItemsList(
                itemsSource = itemsLive,
                currencySource = currencyLive,
                activeItemIdSource = keypad.activeItemId,
                activeExpressionSource = keypad.liveExpression,
                keyListenerSource = keyListenerLive,
                onNameCommit = ::commitName,
                onNamePending = { id, name -> pendingNames[id] = name },
                onExpressionTap = { item ->
                    itemsView.hapticTap(hapticEnabled)
                    keypad.openKeypadFor(item.id, item.expression)
                },
                onExpressionChange = keypad::onInlineExpressionChanged,
                onTogglePin = { id ->
                    itemsView.hapticTap(hapticEnabled)
                    viewModel.togglePinned(id)
                },
                onDelete = { id ->
                    itemsView.hapticTap(hapticEnabled)
                    if (keypad.activeItemId.value == id) keypad.closeKeypad()
                    pendingNames.remove(id)
                    viewModel.removeItem(id)
                },
                onReorder = { fromId, toId ->
                    itemsView.hapticTap(hapticEnabled)
                    viewModel.reorderItem(fromId, toId)
                },
                onReorderStart = {
                    // A drag doesn't interact well with a floating keypad — the
                    // row being edited would slide out from under the caret.
                    // Commit the current edit and close before the gesture takes
                    // over the visible list.
                    itemsView.hapticTap(hapticEnabled)
                    keypad.closeKeypad()
                },
                onBackgroundTap = keypad::dismissKeyboards,
            )
        }

        addButton.setOnClickListener {
            it.hapticTap(hapticEnabled)
            viewModel.addItem(name = "", expression = "")
        }
        spinnerFrom.onItemSelectedListener = rateSpinnerListener(viewModel::setBaseCurrency)
        spinnerTo.onItemSelectedListener = rateSpinnerListener(viewModel::setDestinationCurrency)
        swapButton.setOnClickListener {
            it.hapticTap(hapticEnabled)
            viewModel.swapCurrencies()
        }
        swapButton.setOnLongClickListener {
            it.hapticTap(hapticEnabled)
            startActivity(PreferenceActivity.feesIntent(this))
            true
        }

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
        viewModel.isHapticFeedbackEnabled.observe(this) {
            hapticEnabled = it
        }
        viewModel.getCurrentCart().observe(this) { cart ->
            currencyLive.value = cart.currency
            itemsLive.value = cart.items.toList()
            emptyHint.visibility = if (cart.items.isEmpty()) View.VISIBLE else View.GONE
            // A cart load can retire the item the keypad was bound to; drop
            // that binding so the keypad doesn't linger over a missing row.
            val currentIds = cart.items.map { it.id }.toSet()
            pendingNames.keys.retainAll(currentIds)
            keypad.activeItemId.value?.let { if (it !in currentIds) keypad.closeKeypad() }
            footerBinding.refreshFeeAnnotations()
        }
        viewModel.getBaseCurrency().observe(this) {
            spinnerFrom.setSelection(it)
            // Keep the two sides distinct — grey out whatever's picked on
            // the base side inside the destination picker.
            spinnerTo.setDisabledCurrency(it)
        }
        viewModel.getDestinationCurrency().observe(this) {
            spinnerTo.setSelection(it)
            spinnerFrom.setDisabledCurrency(it)
        }
        viewModel.getSubtotal().observe(this) { footerBinding.onSubtotalChanged(it) }
        viewModel.getTotal().observe(this) { footerBinding.onTotalChanged(it) }
        viewModel.getFees().observe(this) { footerBinding.refreshFeeAnnotations() }
        viewModel.getExchangeRates().observe(this) { rates ->
            // Feed the same rate list the spinner shows on the main screen so
            // its picker shows flags, ISO codes, and (when enabled) preview
            // conversions.
            spinnerFrom.setRates(rates?.rates)
            spinnerTo.setRates(rates?.rates)
            // Re-apply the saved base/dest selections: setRates rebuilds the
            // adapter, which drops the previous selection unless we re-set it.
            spinnerFrom.setSelection(viewModel.getBaseCurrency().value)
            spinnerTo.setSelection(viewModel.getDestinationCurrency().value)
            footerBinding.refreshFeeAnnotations()
        }
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

    // Route the activity's raw touch stream through the keypad controller so
    // it can handle drag-to-dismiss (which needs to steal from the child
    // buttons via ACTION_CANCEL) and outside-tap dismissal.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (keypad.handleTouchEvent(ev)) return true
        return super.dispatchTouchEvent(ev)
    }

    // Bridge for the keypad controller's drag-to-dismiss: it needs to send an
    // ACTION_CANCEL through the activity's *super* dispatch chain (not our
    // override, or we'd infinitely re-enter the drag handler). Kotlin doesn't
    // allow `super.foo(...)` from inside a lambda, so we expose this helper.
    private fun superDispatchTouchEvent(ev: MotionEvent) {
        super.dispatchTouchEvent(ev)
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

    // ------------------------------------------------------------------
    // Keypad reflection targets. main_keypad.xml wires each button's
    // android:onClick to these names — Android's LayoutInflater resolves
    // them against the hosting Activity, so we mirror MainActivity's
    // signatures here and forward to the controller's active state.
    // ------------------------------------------------------------------

    fun numberEvent(view: View) = keypad.forwardKeypadAction(view) { it.addNumber((view as AppCompatButton).text.toString()) }

    fun decimalEvent(view: View) = keypad.forwardKeypadAction(view) { it.addDecimal() }

    fun deleteEvent(view: View) = keypad.forwardKeypadAction(view) { it.delete() }

    fun percentEvent(view: View) = keypad.forwardKeypadAction(view) { it.addPercent() }

    fun calculationEvent(view: View) = keypad.forwardKeypadAction(view) { it.addOperator((view as AppCompatButton).text.toString()) }

    fun parensEvent(view: View) = keypad.forwardKeypadAction(view) { it.applyNextParen() }

    private fun showSnackbar(message: String) {
        snackbar(message).show()
    }
}
