package com.eliormachlev.currencix.view.cart

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.compose.runtime.mutableStateListOf
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
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.FeeSide
import com.eliormachlev.currencix.model.KeyboardType
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.repository.CartExporter
import com.eliormachlev.currencix.repository.CartFileResult
import com.eliormachlev.currencix.util.CALC_TOKEN_REGEX
import com.eliormachlev.currencix.util.CART_EXPORT_DISPLAY_SCALE
import com.eliormachlev.currencix.util.OPERATOR_REGEX
import com.eliormachlev.currencix.util.asciiToDisplayGlyphs
import com.eliormachlev.currencix.util.buildCartShareChooser
import com.eliormachlev.currencix.util.choiceExplainerRow
import com.eliormachlev.currencix.util.feePercentDelta
import com.eliormachlev.currencix.util.hapticTap
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.paddedDialogContainer
import com.eliormachlev.currencix.util.paintParenCycle
import com.eliormachlev.currencix.util.rateSpinnerListener
import com.eliormachlev.currencix.util.roundForDisplay
import com.eliormachlev.currencix.util.toCsv
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.util.toPdfBytes
import com.eliormachlev.currencix.view.BaseActivity
import com.eliormachlev.currencix.view.cart.compose.CartEmptyHint
import com.eliormachlev.currencix.view.cart.compose.CartItemsList
import com.eliormachlev.currencix.view.cart.compose.SavedCartsList
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.main.spinner.SearchableSpinner
import com.eliormachlev.currencix.view.preference.PreferenceActivity
import com.eliormachlev.currencix.viewmodel.cart.CartSnapshot
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel
import com.eliormachlev.currencix.viewmodel.main.CalculatorInputState
import com.google.android.material.button.MaterialButton
import java.math.BigDecimal
import java.math.MathContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Decimal places for cart display; matches the main-screen convention where
// "money-facing" values render to two places by default. Aliased onto the
// shared export scale so on-screen numbers and exported artefacts always
// round identically.
private const val CART_DISPLAY_SCALE = CART_EXPORT_DISPLAY_SCALE

// Suffix used when SAF asks for a suggested filename.
private const val EXPORT_FILE_MIME = "application/json"
private const val EXPORT_FILE_EXT = ".json"

// Filename-safe timestamp used both as the JSON-export suffix and as the
// fallback name for share artefacts (CSV/PDF) when a cart has no user name.
// Stateless formatter, no need to instantiate per call.
private val FILENAME_TIMESTAMP: SimpleDateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

private fun filenameTimestampNow(): String = FILENAME_TIMESTAMP.format(Date())

private const val CSV_MIME = "text/csv"
private const val CSV_EXT = ".csv"
private const val PDF_MIME = "application/pdf"
private const val PDF_EXT = ".pdf"

// Chars that can trip up FileProvider / OSes when embedded in a filename.
// Collapsed to a single underscore so a cart named "Café / July 2026" becomes
// "Café_July_2026", not "Café___July_2026".
private val FILENAME_UNSAFE = Regex("""[\\/:*?"<>|\p{Cntrl}]+""")
private val FILENAME_WHITESPACE = Regex("""\s+""")

private fun String.sanitizeForFilename(): String =
    replace(FILENAME_UNSAFE, "_")
        .replace(FILENAME_WHITESPACE, "_")
        .trim('_', '.')
        .ifBlank { "cart" }

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

    private lateinit var itemsView: ComposeView
    private lateinit var subtotalLabel: TextView
    private lateinit var subtotalExtra: View
    private lateinit var subtotalExtraLabel: TextView
    private lateinit var subtotalExtraValue: TextView
    private lateinit var feeLine: TextView
    private lateinit var totalLabel: TextView
    private lateinit var totalExtra: View
    private lateinit var totalExtraLabel: TextView
    private lateinit var totalExtraValue: TextView
    private lateinit var spinnerFrom: SearchableSpinner
    private lateinit var spinnerTo: SearchableSpinner
    private lateinit var swapButton: ImageButton
    private lateinit var feeSideButton: AppCompatImageButton
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

    // Pre-mapped boolean the composable list consumes — keeps the KeyboardType
    // enum out of the view layer. Either system-IME variant (numpad or
    // full-text) counts, since both host the EditText inline on the row.
    private val isSystemKeyboardModeLive: LiveData<Boolean> by lazy {
        viewModel.keyboardType.map { it.isSystem }
    }

    // Mirrors the sub-variant so the composable row can pick the right
    // CalculatorKeyListener (numpad vs full-text IME) when it inflates the
    // inline EditText.
    private val useFullTextImeLive: LiveData<Boolean> by lazy {
        viewModel.keyboardType.map { it == KeyboardType.SYSTEM_FULL }
    }

    private lateinit var exportLauncher: ActivityResultLauncher<String>
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>

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

        this.itemsView = findViewById(R.id.cart_items)
        this.subtotalLabel = findViewById(R.id.cart_subtotal_value)
        this.subtotalExtra = findViewById(R.id.cart_subtotal_extra)
        this.subtotalExtraLabel = findViewById(R.id.cart_subtotal_extra_label)
        this.subtotalExtraValue = findViewById(R.id.cart_subtotal_extra_value)
        this.feeLine = findViewById(R.id.cart_fee_line)
        this.totalLabel = findViewById(R.id.cart_total_value)
        this.totalExtra = findViewById(R.id.cart_total_extra)
        this.totalExtraLabel = findViewById(R.id.cart_total_extra_label)
        this.totalExtraValue = findViewById(R.id.cart_total_extra_value)
        this.spinnerFrom = findViewById(R.id.cart_spinner_from)
        this.spinnerTo = findViewById(R.id.cart_spinner_to)
        this.swapButton = findViewById(R.id.cart_swap)
        this.feeSideButton = findViewById(R.id.cart_btn_fee_side)
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
                override fun handleOnBackPressed() = attemptClose()
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
                isSystemKeyboardModeSource = isSystemKeyboardModeLive,
                useFullTextImeSource = useFullTextImeLive,
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
        feeSideButton.setOnClickListener {
            it.hapticTap(hapticEnabled)
            val current = viewModel.getFeeSide().value ?: FeeSide.ORIGINAL
            viewModel.setFeeSide(current.toggled())
        }
        feeSideButton.setOnLongClickListener {
            it.hapticTap(hapticEnabled)
            startActivity(PreferenceActivity.feesIntent(this))
            true
        }

        exportLauncher =
            registerForActivityResult(
                ActivityResultContracts.CreateDocument(EXPORT_FILE_MIME),
            ) { uri -> uri?.let(::doExport) }
        importLauncher =
            registerForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let(::doImport) }

        observe()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.cart, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                attemptClose()
                true
            }
            R.id.cart_share -> {
                showShareDialog()
                true
            }
            R.id.cart_save -> {
                saveOrPromptForName()
                true
            }
            R.id.cart_save_as -> {
                showSaveAsDialog()
                true
            }
            R.id.cart_load -> {
                showLoadDialog()
                true
            }
            R.id.cart_export -> {
                launchExport()
                true
            }
            R.id.cart_import -> {
                launchImport()
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
            updateFeeVisuals()
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
        viewModel.getSubtotal().observe(this) { value ->
            subtotalLabel.text = formatAmount(value, viewModel.getBaseCurrency().value)
            updateFeeExtras()
        }
        viewModel.getTotal().observe(this) { value ->
            totalLabel.text = formatAmount(value, viewModel.getDestinationCurrency().value)
            updateFeeExtras()
        }
        viewModel.getFees().observe(this) { updateFeeVisuals() }
        viewModel.getFeeSide().observe(this) { side ->
            val effective = side ?: FeeSide.ORIGINAL
            feeSideButton.setImageResource(
                if (effective == FeeSide.CONVERTED) {
                    R.drawable.ic_fee_side_converted_horizontal
                } else {
                    R.drawable.ic_fee_side_original_horizontal
                },
            )
            updateFeeExtras()
        }
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
            updateFeeLine()
        }
    }

    // Both the arrow/percentage line and the "other side" total need to re-run
    // whenever fees or the cart's currencies change — bundle so callers don't
    // have to know which slot depends on what.
    private fun updateFeeVisuals() {
        updateFeeLine()
        updateFeeExtras()
    }

    private fun updateFeeLine() {
        // Compute the stack directly from currencies + fees so the arrow /
        // percentage show even when the cart is empty (matches main screen).
        val stack = viewModel.currentFeeStack()
        if (stack.isNeutralFeeStack()) {
            feeLine.visibility = View.GONE
            feeSideButton.visibility = View.GONE
            return
        }
        feeLine.text = getString(R.string.cart_fee_line, stack.toFeePercentDisplay())
        feeLine.visibility = View.VISIBLE
        feeSideButton.visibility = View.VISIBLE
    }

    /**
     * When fees apply, expose the "other side" of the fee so users can see both:
     * fee-side ORIGINAL keeps subtotal at mid-market → show subtotal-after-fee in
     * the base currency ("True cost"); fee-side CONVERTED bakes fees into total
     * → show total-before-fee in the destination currency ("Original value").
     */
    private fun updateFeeExtras() {
        val stack = viewModel.currentFeeStack()
        if (stack.isNeutralFeeStack()) {
            subtotalExtra.visibility = View.GONE
            totalExtra.visibility = View.GONE
            return
        }
        val side = viewModel.getFeeSide().value ?: FeeSide.ORIGINAL
        when (side) {
            FeeSide.ORIGINAL -> {
                val subtotal = viewModel.getSubtotal().value ?: BigDecimal.ZERO
                val withFee = subtotal.multiply(stack, MathContext.DECIMAL128)
                subtotalExtraLabel.text = stripLabelSeparator(getString(R.string.fee_true_cost_prefix))
                subtotalExtraValue.text = formatAmount(withFee, viewModel.getBaseCurrency().value)
                subtotalExtra.visibility = View.VISIBLE
                totalExtra.visibility = View.GONE
            }
            FeeSide.CONVERTED -> {
                val total = viewModel.getTotal().value ?: BigDecimal.ZERO
                val fair = total.multiply(stack, MathContext.DECIMAL128)
                totalExtraLabel.text = stripLabelSeparator(getString(R.string.fee_original_value_prefix))
                totalExtraValue.text = formatAmount(fair, viewModel.getDestinationCurrency().value)
                totalExtra.visibility = View.VISIBLE
                subtotalExtra.visibility = View.GONE
            }
        }
    }

    // Existing prefix strings end with a locale-specific ": " / " : " / "：" for
    // inline use. When we're showing them as a standalone left-aligned label,
    // strip the trailing separator so it doesn't dangle before the right column.
    private fun stripLabelSeparator(text: String): String = text.trimEnd(' ', '\u00A0', ':', '：')

    private fun formatAmount(
        value: BigDecimal?,
        currency: Currency?,
    ): String {
        val amount =
            (value ?: BigDecimal.ZERO)
                .cartScale()
                .toHumanReadableNumber(this, decimalPlaces = CART_DISPLAY_SCALE)
        val iso = currency?.iso4217Alpha()
        return if (iso.isNullOrEmpty()) amount else "$amount $iso"
    }

    // Round to the two-decimal "money" scale used across the cart UI. Extracted
    // so display, share text, and fee-percent all pin to the same rounding.
    private fun BigDecimal.cartScale(): BigDecimal = roundForDisplay(CART_DISPLAY_SCALE)

    // Rounded, plain string in the cart's display scale — the form used
    // wherever we drop a number into shared text (share sheet).
    private fun BigDecimal.toCartDisplayString(): String = cartScale().toPlainString()

    // Percentage delta of the fee stack ("2.50" for a 1.025 stack), pinned to
    // the cart's display scale. Shared by the on-screen fee line and the
    // "Fees:" row in shared text.
    private fun BigDecimal.toFeePercentDisplay(): String = feePercentDelta(CART_DISPLAY_SCALE).toPlainString()

    // Fallback to the localised "My cart" name when the user hasn't given
    // the cart one. Shared by Save-as, Rename, and Export.
    private fun String.orDefaultCartName(): String = ifBlank { getString(R.string.cart_default_saved_name) }

    private fun showSaveAsDialog(onSaved: () -> Unit = {}) {
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
            showSnackbar(getString(R.string.cart_saved_toast, name))
            onSaved()
        }
    }

    /**
     * "Save" menu action — overwrites the current saved cart in-place. Falls
     * back to Save-as when there's nothing to overwrite (never saved yet).
     */
    private fun saveOrPromptForName(onSaved: () -> Unit = {}) {
        if (guardEmptyForSave()) return
        flushPendingCommits()
        if (viewModel.saveCurrent()) {
            val name =
                viewModel
                    .getCurrentCart()
                    .value
                    ?.name
                    .orEmpty()
            showSnackbar(getString(R.string.cart_saved_toast, name))
            onSaved()
        } else {
            showSaveAsDialog(onSaved = onSaved)
        }
    }

    // Shared "one-line text input" dialog for cart name prompts (Save-as,
    // Rename). Blank input collapses to the localised default cart name so
    // every entry point produces a nameable, findable saved cart.
    private fun showNameInputDialog(
        titleRes: Int,
        initial: String,
        onOk: (String) -> Unit,
    ) {
        val input =
            EditText(this).apply {
                hint = getString(R.string.cart_save_name_hint)
                setText(initial)
            }
        AlertDialog
            .Builder(this)
            .setTitle(titleRes)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onOk(
                    input.text
                        .toString()
                        .trim()
                        .orDefaultCartName(),
                )
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // Refuse to persist an empty cart — matches Share's "nothing to share"
    // guard so both write paths behave consistently. Returns true when the
    // caller should abort.
    private fun guardEmptyForSave(): Boolean {
        val items = viewModel.getCurrentCart().value?.items
        if (items.isNullOrEmpty()) {
            showSnackbar(getString(R.string.cart_save_empty))
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
            options += getString(R.string.cart_unsaved_save) to { saveOrPromptForName(action) }
        }
        options += getString(R.string.cart_unsaved_save_as) to { showSaveAsDialog(onSaved = action) }
        options += getString(R.string.cart_unsaved_discard) to {
            viewModel.discardChanges()
            action()
        }
        options += getString(R.string.cart_unsaved_continue) to action
        AlertDialog
            .Builder(this)
            .setTitle(R.string.cart_unsaved_title)
            .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                options[which].second.invoke()
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun attemptClose() = confirmUnsavedThen { finish() }

    private fun showLoadDialog() {
        val initial = viewModel.getSavedCartsSnapshot()
        if (initial.isEmpty()) {
            showSnackbar(getString(R.string.cart_no_saved))
            return
        }
        val saved = mutableStateListOf<SavedCart>().apply { addAll(initial) }
        lateinit var dialog: AlertDialog
        val composeView =
            ComposeView(this).apply {
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
                                    .Builder(this@CartActivity)
                                    .setTitle(cart.name.ifBlank { cart.id.take(8) })
                                    .setMessage(getString(R.string.cart_delete_confirm, cart.name))
                                    .setPositiveButton(R.string.cart_delete_confirm_button) { _, _ ->
                                        viewModel.deleteSaved(cart.id)
                                        saved.removeAll { it.id == cart.id }
                                        if (saved.isEmpty()) dialog.dismiss()
                                    }.setNegativeButton(android.R.string.cancel, null)
                                    .show()
                            },
                        )
                    }
                }
            }
        dialog =
            AlertDialog
                .Builder(this)
                .setTitle(R.string.cart_menu_load)
                .setView(composeView)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        dialog.show()
    }

    private fun launchExport() {
        flushPendingCommits()
        val name =
            viewModel
                .getCurrentCart()
                .value
                ?.name
                ?.ifBlank { null } ?: "cart"
        exportLauncher.launch("$name-${filenameTimestampNow()}$EXPORT_FILE_EXT")
    }

    private fun launchImport() {
        importLauncher.launch(arrayOf(EXPORT_FILE_MIME))
    }

    private fun doExport(uri: Uri) {
        val cart = viewModel.getCurrentCart().value ?: return
        // Copy so the exported file always has a real name, even if the
        // user hasn't gone through Save-as yet.
        val toExport =
            cart.copy(
                name = cart.name.orDefaultCartName(),
                createdAt = System.currentTimeMillis(),
            )
        when (val res = exporter.export(uri, toExport)) {
            is CartFileResult.Success -> showSnackbar(getString(R.string.cart_export_ok))
            is CartFileResult.Failure ->
                showSnackbar(
                    getString(R.string.cart_export_error, res.message),
                )
            is CartFileResult.Loaded -> Unit
        }
    }

    private fun doImport(uri: Uri) {
        when (val res = exporter.import(uri)) {
            is CartFileResult.Loaded -> {
                viewModel.setCurrent(res.cart)
                showSnackbar(getString(R.string.cart_import_ok))
            }
            is CartFileResult.Failure ->
                showSnackbar(
                    getString(R.string.cart_import_error, res.message),
                )
            is CartFileResult.Success -> Unit
        }
    }

    private fun confirmClear() {
        showChoiceExplainerDialog(
            titleRes = R.string.cart_menu_clear,
            choices =
                listOf(
                    ChoiceRow(
                        R.string.cart_clear_items_only,
                        R.string.cart_clear_items_only_desc,
                    ) { viewModel.clearItems() },
                    ChoiceRow(
                        R.string.cart_clear_reset_all,
                        R.string.cart_clear_reset_all_desc,
                    ) { viewModel.resetToMainDefaults() },
                ),
        )
    }

    // Shared "title + one-line explainer per option" picker. Mirrors the
    // preference-screen fee-side dialog so users get the same shape of
    // guidance in the cart's destructive flows.
    private data class ChoiceRow(
        val title: Int,
        val description: Int,
        val onPick: () -> Unit,
    )

    private fun showChoiceExplainerDialog(
        titleRes: Int,
        choices: List<ChoiceRow>,
    ) {
        val padV = resources.getDimensionPixelSize(R.dimen.margin2x)
        val container = paddedDialogContainer(this, topPadding = padV)
        val dialogHolder = arrayOfNulls<AlertDialog>(1)
        choices.forEach { choice ->
            container.addView(
                choiceExplainerRow(
                    ctx = this,
                    title = getString(choice.title),
                    description = getString(choice.description),
                ) {
                    choice.onPick()
                    dialogHolder[0]?.dismiss()
                },
            )
        }
        dialogHolder[0] =
            AlertDialog
                .Builder(this)
                .setTitle(titleRes)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    // Single "Share" entry point — presents plain-text / CSV / PDF as picker
    // rows so the top-level overflow menu stays short. Flush + empty-guard run
    // once up-front so an empty cart never surfaces a picker it can't act on.
    private fun showShareDialog() {
        flushPendingCommits()
        val snapshot = viewModel.snapshotForShare()
        if (snapshot == null) {
            showSnackbar(getString(R.string.cart_share_empty))
            return
        }
        showChoiceExplainerDialog(
            titleRes = R.string.menu_share,
            choices =
                listOf(
                    ChoiceRow(
                        R.string.cart_share_option_text,
                        R.string.cart_share_option_text_desc,
                    ) { shareCartAsText(snapshot) },
                    ChoiceRow(
                        R.string.cart_share_option_csv,
                        R.string.cart_share_option_csv_desc,
                    ) { shareCartAsCsv(snapshot) },
                    ChoiceRow(
                        R.string.cart_share_option_pdf,
                        R.string.cart_share_option_pdf_desc,
                    ) { shareCartAsPdf(snapshot) },
                ),
        )
    }

    private fun shareCartAsText(snapshot: CartSnapshot) {
        val text = buildShareText(snapshot)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        startActivity(Intent.createChooser(intent, null))
    }

    private fun shareCartAsCsv(snapshot: CartSnapshot) {
        val title = shareTitle(snapshot)
        val chooser =
            buildCartShareChooser(
                context = this,
                filename = shareFilename(title, CSV_EXT),
                mimeType = CSV_MIME,
                bytes = snapshot.toCsv(title = title).toByteArray(Charsets.UTF_8),
            )
        startActivity(chooser)
    }

    private fun shareCartAsPdf(snapshot: CartSnapshot) {
        val title = shareTitle(snapshot)
        val chooser =
            buildCartShareChooser(
                context = this,
                filename = shareFilename(title, PDF_EXT),
                mimeType = PDF_MIME,
                bytes = snapshot.toPdfBytes(title = title),
            )
        startActivity(chooser)
    }

    // Cart name if the user has one (from Save-as), otherwise a phone-local
    // timestamp so the artefact still has an identifying handle.
    private fun shareTitle(snapshot: CartSnapshot): String = snapshot.cart.name.ifBlank { filenameTimestampNow() }

    private fun shareFilename(
        title: String,
        extension: String,
    ): String = title.sanitizeForFilename() + extension

    private fun buildShareText(snapshot: CartSnapshot): String =
        buildString {
            val baseIso = snapshot.baseCurrency.iso4217Alpha()
            val destIso = snapshot.destinationCurrency.iso4217Alpha()
            val name = snapshot.cart.name.ifBlank { getString(R.string.cart_share_default_title) }
            appendLine(getString(R.string.cart_share_header, name, baseIso))
            snapshot.evaluatedItems.forEach { (item, value) ->
                val label = item.name.ifBlank { item.expression }
                appendLine("• $label: ${value.toCartDisplayString()}")
            }
            appendLine("—")
            appendLine(getString(R.string.cart_share_subtotal, snapshot.subtotal.toCartDisplayString(), baseIso))
            if (snapshot.isConverting) {
                appendLine(
                    getString(R.string.cart_share_converted, snapshot.convertedSubtotal.toCartDisplayString(), destIso),
                )
            }
            if (!snapshot.feeStack.isNeutralFeeStack()) {
                appendLine(getString(R.string.cart_share_fees, snapshot.feeStack.toFeePercentDisplay()))
            }
            append(getString(R.string.cart_share_total, snapshot.total.toCartDisplayString(), destIso))
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
