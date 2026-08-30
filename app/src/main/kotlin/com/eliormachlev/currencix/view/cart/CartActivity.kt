package com.eliormachlev.currencix.view.cart

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatButton
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.KeyboardType
import com.eliormachlev.currencix.repository.CartExporter
import com.eliormachlev.currencix.util.CALC_TOKEN_REGEX
import com.eliormachlev.currencix.util.CalculatorKeyListener
import com.eliormachlev.currencix.util.OPERATOR_REGEX
import com.eliormachlev.currencix.util.asciiToDisplayGlyphs
import com.eliormachlev.currencix.util.hapticTap
import com.eliormachlev.currencix.util.paintParenCycle
import com.eliormachlev.currencix.util.rateSpinnerListener
import com.eliormachlev.currencix.view.BaseActivity
import com.eliormachlev.currencix.view.cart.compose.CartEmptyHint
import com.eliormachlev.currencix.view.cart.compose.CartItemsList
import com.eliormachlev.currencix.view.main.spinner.SearchableSpinner
import com.eliormachlev.currencix.view.preference.PreferenceActivity
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel
import com.eliormachlev.currencix.viewmodel.main.CalculatorInputState
import com.google.android.material.button.MaterialButton

// Duration of the slide-in / slide-out animation for the cart keypad.
private const val KEYPAD_ANIM_MS = 180L

// Grace window on outside taps before closing the keypad: a tap that lands on
// another row's expression must run through Compose's click handler first so
// it can swap the active id — otherwise we'd close, then re-open with an
// unwanted flicker.
private const val OUTSIDE_TAP_DEBOUNCE_MS = 40L

// Fraction of the keypad container's height a drag-down must clear before
// release commits the dismissal. Below this we snap back — matches the
// behaviour of Material bottom sheets.
private const val KEYPAD_DRAG_DISMISS_FRACTION = 0.35f

class CartActivity : BaseActivity() {
    private lateinit var viewModel: CartViewModel
    private lateinit var exporter: CartExporter
    private lateinit var fileIo: CartFileIo
    private lateinit var shareCoordinator: CartShareCoordinator
    private lateinit var saveLoadCoordinator: CartSaveLoadCoordinator
    private lateinit var footerBinding: CartFooterBinding

    private lateinit var itemsView: ComposeView
    private lateinit var spinnerFrom: SearchableSpinner
    private lateinit var spinnerTo: SearchableSpinner
    private lateinit var swapButton: ImageButton
    private lateinit var emptyHint: ComposeView
    private lateinit var addButton: MaterialButton

    // Cached haptic setting so per-tap handlers don't need to touch prefs.
    private var hapticEnabled = false

    // Mirrors the keyboard-type preference so row-tap handlers decide
    // between the in-app keypad and a system-IME dialog without a pref read.
    private var currentKeyboardType: KeyboardType = KeyboardType.DEFAULT

    // Slide-up keypad state — behaves like a soft IME. The keypad edits the
    // row identified by [activeItemId]; taps route through
    // [activeCalculatorState] and every state change is mirrored into
    // [liveExpression], which the composable row observes for its display.
    private lateinit var keypadContainer: ViewGroup
    private lateinit var keypadRegular: View
    private lateinit var keypadExtended: View
    private lateinit var contentColumn: View
    private var activeCalculatorState: CalculatorInputState? = null
    private val activeItemId = MutableLiveData<String?>(null)
    private val liveExpression = MutableLiveData("")
    private var activeStateObserver: Observer<String?>? = null
    private var activeParenObserver: Observer<Char>? = null
    private var keypadBackCallback: OnBackPressedCallback? = null

    // Rising-edge latch for the IME-visibility guard; see [installImeVisibilityGuard].
    private var systemImeVisible = false

    // Drag-to-dismiss state — only meaningful once ACTION_DOWN lands inside
    // the keypad and the pointer travels far enough downward to cross
    // [touchSlop]. [keypadDragStartY] is null when no candidate gesture is
    // being tracked; [keypadDragActive] flips true once slop is crossed and
    // the child buttons have been sent an ACTION_CANCEL.
    private var keypadDragStartY: Float? = null
    private var keypadDragActive = false
    private val touchSlop: Int by lazy { ViewConfiguration.get(this).scaledTouchSlop }

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
        this.keypadContainer = findViewById(R.id.cart_keypad_container)
        this.keypadRegular = findViewById(R.id.cart_keypad_regular)
        this.keypadExtended = findViewById(R.id.cart_keypad_extended)
        this.contentColumn = findViewById(R.id.cart_content)
        installImeVisibilityGuard()

        // Registered before the keypad callback so the keypad's (which is
        // added second) wins when it's enabled. When the keypad is closed
        // and there are unsaved edits, we prompt instead of finishing.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = saveLoadCoordinator.attemptClose()
            },
        )
        keypadBackCallback =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() = closeKeypad()
            }.also { onBackPressedDispatcher.addCallback(this, it) }
        itemsView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        itemsView.setContent {
            CartItemsList(
                itemsSource = itemsLive,
                currencySource = currencyLive,
                activeItemIdSource = activeItemId,
                activeExpressionSource = liveExpression,
                keyListenerSource = keyListenerLive,
                onNameCommit = ::commitName,
                onNamePending = { id, name -> pendingNames[id] = name },
                onExpressionTap = { item ->
                    itemsView.hapticTap(hapticEnabled)
                    openKeypadFor(item.id, item.expression)
                },
                onExpressionChange = ::onInlineExpressionChanged,
                onTogglePin = { id ->
                    itemsView.hapticTap(hapticEnabled)
                    viewModel.togglePinned(id)
                },
                onDelete = { id ->
                    itemsView.hapticTap(hapticEnabled)
                    if (activeItemId.value == id) closeKeypad()
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
                    closeKeypad()
                },
                onBackgroundTap = ::dismissKeyboards,
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
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

    private fun observe() {
        viewModel.isHapticFeedbackEnabled.observe(this) {
            hapticEnabled = it
        }
        viewModel.keyboardType.observe(this) { type ->
            currentKeyboardType = type
            val extended = type == KeyboardType.EXPANDED
            keypadRegular.visibility = if (extended) View.GONE else View.VISIBLE
            keypadExtended.visibility = if (extended) View.VISIBLE else View.GONE
        }
        viewModel.getCurrentCart().observe(this) { cart ->
            currencyLive.value = cart.currency
            itemsLive.value = cart.items.toList()
            emptyHint.visibility = if (cart.items.isEmpty()) View.VISIBLE else View.GONE
            // A cart load can retire the item the keypad was bound to; drop
            // that binding so the keypad doesn't linger over a missing row.
            val currentIds = cart.items.map { it.id }.toSet()
            pendingNames.keys.retainAll(currentIds)
            activeItemId.value?.let { if (it !in currentIds) closeKeypad() }
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

    // ------------------------------------------------------------------
    // Slide-up keypad — behaves like a soft IME. A value field taps calls
    // `openKeypadFor`; taps on non-value regions (or a back-press) call
    // `closeKeypad`; every keypad button routes through the reflection
    // targets below.
    // ------------------------------------------------------------------

    // Only one keyboard should be visible at a time. `openKeypadFor` calls
    // `hideSystemIme` when opening the app keypad; this listener handles the
    // reverse — when the system IME rises (e.g. a row's name field takes
    // focus while the app keypad is open), dismiss the app keypad. Rising-edge
    // tracking via [systemImeVisible] avoids retriggering `closeKeypad` on
    // redundant inset dispatches while the IME stays visible.
    //
    // Installing our own listener on cart_root disables its `fitsSystemWindows`
    // auto-padding (that's how the view API works — a custom listener takes
    // over), so we re-apply the system-bar insets as padding ourselves. Without
    // this the toolbar slides under the status bar.
    private fun installImeVisibilityGuard() {
        val root = findViewById<View>(R.id.cart_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeVisible && !systemImeVisible && keypadContainer.visibility == View.VISIBLE) {
                closeKeypad()
            }
            systemImeVisible = imeVisible
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    /**
     * Show the keypad for the row identified by [itemId], seeding a fresh
     * [CalculatorInputState] with [seedExpression] and mirroring every state
     * change into [liveExpression] — the composable row observes that
     * LiveData for its inline display.
     *
     * In either system-IME mode there is no in-app keypad to raise; the
     * row itself hosts an EditText once it becomes active, so we just seed
     * [liveExpression] and flip [activeItemId] — the composable does the rest
     * (focus request + IME show).
     */
    fun openKeypadFor(
        itemId: String,
        seedExpression: String,
    ) {
        if (currentKeyboardType.isSystem) {
            if (activeItemId.value == itemId) return
            detachActiveField()
            liveExpression.value = seedExpression
            activeItemId.value = itemId
            return
        }
        hideSystemIme()
        detachActiveField()
        val state = CalculatorInputState().apply { seedExpression(seedExpression) }
        liveExpression.value = state.toExpressionString().ifEmpty { seedExpression }
        val observer = Observer<String?> { liveExpression.value = state.toExpressionString() }
        state.baseValueText.observeForever(observer)
        state.calculationValueText.observeForever(observer)
        val parenObserver = Observer<Char> { next -> parenButton()?.paintParenCycle(next) }
        state.nextParen.observeForever(parenObserver)
        activeCalculatorState = state
        activeItemId.value = itemId
        activeStateObserver = observer
        activeParenObserver = parenObserver
        showKeypad()
    }

    // Bridge each keystroke from the row's inline EditText (ASCII) back into
    // [liveExpression] (display glyphs) so the row's preview + eventual commit
    // see the same round-tripped form the in-app keypad produces.
    private fun onInlineExpressionChanged(
        id: String,
        ascii: String,
    ) {
        if (activeItemId.value != id) return
        val glyphs = ascii.asciiToDisplayGlyphs()
        if (liveExpression.value == glyphs) return
        liveExpression.value = glyphs
    }

    // The `()` cycle-toggle key on the extended keypad. Absent from the basic
    // layout, so callers must tolerate null.
    private fun parenButton(): AppCompatButton? = keypadExtended.findViewById(R.id.btn_parens)

    /** Hide the keypad and unbind whichever row was being edited. */
    fun closeKeypad() {
        if (activeItemId.value == null && keypadContainer.visibility == View.GONE) return
        detachActiveField()
        hideKeypad()
    }

    private fun showKeypad() {
        keypadBackCallback?.isEnabled = true
        if (keypadContainer.visibility == View.VISIBLE) return
        keypadContainer.visibility = View.VISIBLE
        keypadContainer.translationY = keypadContainer.height.toFloat().takeIf { it > 0f }
            ?: resources.displayMetrics.heightPixels.toFloat()
        keypadContainer
            .animate()
            .translationY(0f)
            .setDuration(KEYPAD_ANIM_MS)
            .start()
        // Leave the totals card / add-item button pinned to the bottom of the
        // screen (behind the keypad) and only inset the items list so its
        // Compose content stops at the keypad's top edge instead of rendering
        // underneath. Keeps the summary from being pushed above the keypad.
        setItemsBottomInsetForKeypad()
    }

    private fun hideKeypad() {
        keypadBackCallback?.isEnabled = false
        if (keypadContainer.visibility != View.VISIBLE) return
        keypadContainer
            .animate()
            .translationY(keypadContainer.height.toFloat())
            .setDuration(KEYPAD_ANIM_MS)
            .withEndAction { keypadContainer.visibility = View.GONE }
            .start()
        setItemsBottomInset(0)
    }

    private fun setItemsBottomInsetForKeypad() {
        val apply = {
            val keypadH =
                keypadContainer.height.takeIf { it > 0 }
                    ?: keypadContainer.layoutParams.height
            // keypad is anchored to the bottom of cart_root; cart_content fills
            // the same area, so its own height is our reference. The overlap
            // is the amount by which the items view extends behind the keypad
            // once the fixed footer (add button + totals card) has taken its
            // own space at the bottom.
            val overlap = itemsView.bottom - (contentColumn.height - keypadH)
            setItemsBottomInset(overlap.coerceAtLeast(0))
        }
        if (keypadContainer.height > 0 && itemsView.height > 0) {
            apply()
        } else {
            keypadContainer.post(apply)
        }
    }

    private fun setItemsBottomInset(bottom: Int) {
        itemsView.setPadding(
            itemsView.paddingLeft,
            itemsView.paddingTop,
            itemsView.paddingRight,
            bottom,
        )
    }

    private fun detachActiveField() {
        val state = activeCalculatorState
        val observer = activeStateObserver
        if (state != null && observer != null) {
            state.baseValueText.removeObserver(observer)
            state.calculationValueText.removeObserver(observer)
        }
        val parenObserver = activeParenObserver
        if (state != null && parenObserver != null) {
            state.nextParen.removeObserver(parenObserver)
        }
        // Reset the paren button to its rest state — `(` is the only sensible
        // next glyph when no field is being edited.
        parenButton()?.paintParenCycle('(')
        // Commit the current keypad expression to the VM so the row's
        // persisted value matches what the user just typed.
        val id = activeItemId.value
        if (id != null) {
            val expression = liveExpression.value.orEmpty()
            commitExpression(id, expression)
        }
        activeCalculatorState = null
        activeItemId.value = null
        activeStateObserver = null
        activeParenObserver = null
        liveExpression.value = ""
    }

    /**
     * Route outside-taps to close whichever keyboard is up (app keypad or
     * system IME) and, when a drag starts inside the keypad, convert it into
     * a slide-down dismissal. Taps *inside* the keypad (button presses) pass
     * through unchanged; a tap on another row's expression falls through to
     * its Compose click handler, which re-opens [openKeypadFor] and swaps the
     * active state without a visible close/open flicker.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (keypadContainer.visibility == View.VISIBLE && handleKeypadDrag(ev)) return true
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) handleOutsideTap(ev)
        return super.dispatchTouchEvent(ev)
    }

    // Only owns taps outside the items ComposeView (toolbar, totals card,
    // add-item button when the IME is up). Taps inside the items view are
    // resolved by Compose in [CartItemsList] via onBackgroundTap.
    private fun handleOutsideTap(ev: MotionEvent) {
        val x = ev.rawX.toInt()
        val y = ev.rawY.toInt()
        val itemsRect = Rect().also(itemsView::getGlobalVisibleRect)
        if (itemsRect.contains(x, y)) return
        if (keypadContainer.visibility == View.VISIBLE) {
            val keypadRect = Rect().also(keypadContainer::getGlobalVisibleRect)
            if (keypadRect.contains(x, y)) return
            val stillOn = activeItemId.value
            keypadContainer.postDelayed({
                if (activeItemId.value == stillOn && stillOn != null) closeKeypad()
            }, OUTSIDE_TAP_DEBOUNCE_MS)
        } else if (systemImeVisible) {
            dismissKeyboards()
        }
    }

    /**
     * Vertical drag-to-dismiss: once a downward gesture starting inside the
     * keypad crosses touch slop, steal it from the buttons (by dispatching
     * ACTION_CANCEL), track the finger via [ViewGroup.setTranslationY], and
     * either commit the dismiss ([KEYPAD_DRAG_DISMISS_FRACTION]) or snap back
     * on release. Returns true once the gesture has been intercepted so the
     * activity's super-dispatch is skipped for the rest of the stream.
     */
    private fun handleKeypadDrag(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val rect = Rect().also(keypadContainer::getGlobalVisibleRect)
                if (rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    keypadDragStartY = ev.rawY
                    keypadDragActive = false
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val start = keypadDragStartY ?: return false
                val delta = ev.rawY - start
                if (!keypadDragActive && delta > touchSlop) {
                    keypadDragActive = true
                    // Cancel the child press so no button fires when the
                    // finger lifts — from here on the gesture is a drag.
                    val cancel = MotionEvent.obtain(ev).also { it.action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                }
                if (keypadDragActive) {
                    keypadContainer.translationY = delta.coerceAtLeast(0f)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!keypadDragActive) {
                    keypadDragStartY = null
                    return false
                }
                val dragged = keypadContainer.translationY
                val threshold = keypadContainer.height * KEYPAD_DRAG_DISMISS_FRACTION
                if (dragged >= threshold) {
                    // closeKeypad → hideKeypad animates from the current
                    // translationY (which we just set) back down to full
                    // height, so the release picks up exactly where the
                    // finger left off.
                    closeKeypad()
                } else {
                    keypadContainer.animate().cancel()
                    keypadContainer
                        .animate()
                        .translationY(0f)
                        .setDuration(KEYPAD_ANIM_MS)
                        .start()
                }
                keypadDragActive = false
                keypadDragStartY = null
                return true
            }
            else -> return false
        }
    }

    private fun hideSystemIme() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val token = currentFocus?.windowToken ?: window.decorView.windowToken ?: return
        imm.hideSoftInputFromWindow(token, 0)
    }

    private fun dismissKeyboards() {
        // closeKeypad already detaches for the in-app keypad; the else branch
        // covers system-IME mode (no keypad) so the active row's typed value
        // commits and its EditText tears down.
        if (keypadContainer.visibility == View.VISIBLE) {
            closeKeypad()
        } else if (activeItemId.value != null) {
            detachActiveField()
        }
        if (systemImeVisible) {
            hideSystemIme()
            currentFocus?.clearFocus()
        }
    }

    // Cancel every row's pending debounce and push its current buffer to the
    // view model synchronously. Must run before any snapshot/save/share so a
    // freshly-typed name or a pending keypad expression doesn't get lost.
    private fun flushPendingCommits() {
        activeItemId.value?.let { id ->
            commitExpression(id, liveExpression.value.orEmpty())
        }
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
    // signatures here and forward to the currently-active state.
    // ------------------------------------------------------------------

    fun numberEvent(view: View) = keypadEvent(view) { it.addNumber((view as AppCompatButton).text.toString()) }

    fun decimalEvent(view: View) = keypadEvent(view) { it.addDecimal() }

    fun deleteEvent(view: View) = keypadEvent(view) { it.delete() }

    fun percentEvent(view: View) = keypadEvent(view) { it.addPercent() }

    fun calculationEvent(view: View) = keypadEvent(view) { it.addOperator((view as AppCompatButton).text.toString()) }

    fun parensEvent(view: View) = keypadEvent(view) { it.applyNextParen() }

    // Every keypad button does the same two-step: haptic tap on the button,
    // then forward the action to whichever row's calculator state is active.
    private inline fun keypadEvent(
        view: View,
        action: (CalculatorInputState) -> Unit,
    ) {
        view.hapticTap(hapticEnabled)
        activeCalculatorState?.let(action)
    }

    private fun showSnackbar(message: String) {
        snackbar(message).show()
    }
}

/**
 * Rebuild the input state from a previously-saved cart-row expression so the
 * keypad opens where the user left off. Empty input leaves the state at its
 * default "0" seed.
 */
private fun CalculatorInputState.seedExpression(expression: String) {
    val trimmed = expression.trim()
    if (trimmed.isEmpty()) return
    if (!trimmed.contains(CALC_TOKEN_REGEX)) {
        replayDigits(trimmed)
        return
    }
    // Split on operator/paren boundaries while keeping each structural token
    // as its own entry — mirrors how the state serialises them back out.
    val tokens = mutableListOf<String>()
    val buf = StringBuilder()
    trimmed.forEach { ch ->
        if (ch.toString().matches(CALC_TOKEN_REGEX)) {
            if (buf.isNotBlank()) tokens += buf.toString().trim()
            tokens += ch.toString()
            buf.clear()
        } else {
            buf.append(ch)
        }
    }
    if (buf.isNotBlank()) tokens += buf.toString().trim()
    // The digit-replay API is state-aware — it targets the base row before
    // the first operator and the calculation row afterwards — so every
    // operand goes through the same path.
    tokens.forEach { token ->
        when {
            token == "(" -> addOpenParen()
            token == ")" -> addCloseParen()
            token.matches(OPERATOR_REGEX) -> addOperator(token)
            else -> replayDigits(token)
        }
    }
}

private fun CalculatorInputState.replayDigits(number: String) {
    number.forEach { ch ->
        when {
            ch.isDigit() -> addNumber(ch.toString())
            ch == '.' -> addDecimal()
        }
    }
}

/**
 * Serialise the current input state back to a string the cart layer can
 * store and later evaluate (matches the expression format cart rows use).
 */
private fun CalculatorInputState.toExpressionString(): String {
    val calc = calculationValueText.value
    return if (calc.isNullOrBlank()) baseValueText.value.orEmpty() else calc.trim()
}
