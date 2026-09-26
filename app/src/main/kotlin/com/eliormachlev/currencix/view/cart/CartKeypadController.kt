package com.eliormachlev.currencix.view.cart

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.eliormachlev.currencix.model.KeyboardType
import com.eliormachlev.currencix.util.CALC_TOKEN_REGEX
import com.eliormachlev.currencix.util.OPERATOR_REGEX
import com.eliormachlev.currencix.util.asciiToDisplayGlyphs
import com.eliormachlev.currencix.viewmodel.main.CalculatorInputState
import com.eliormachlev.currencix.viewmodel.main.Operator

/**
 * State holder for the cart's floating calculator keypad. All animation,
 * drag-to-dismiss, back-press, and outside-tap handling now live in the
 * Compose layer ([CartScreen] / [CartKeypadOverlay]); this class owns the
 * per-row bookkeeping (which item is active, what expression buffer is being
 * edited, when to commit) and the [MainKeypad] callback adapter that routes
 * keypad presses into the active [CalculatorInputState].
 *
 * Split of concerns:
 * - [activeItemId] / [liveExpression] — observed by the compose row for the
 *   active-row highlight + inline display.
 * - [keypadVisible] — observed by [CartKeypadOverlay] to slide the app keypad
 *   in/out. Only true for in-app-keypad variants; system-IME variants leave
 *   it false and the row hosts an EditText instead.
 * - [keypadKeyboardType] / [keypadNextParen] — piped into the [MainKeypad]
 *   composable so it renders the correct layout and paren glyph.
 */
class CartKeypadController(
    activity: AppCompatActivity,
    private val keyboardType: LiveData<KeyboardType>,
    private val onExpressionCommit: (id: String, expression: String) -> Unit,
) {
    private val ctx: Context = activity

    val activeItemId = MutableLiveData<String?>(null)
    val liveExpression = MutableLiveData("")

    // Consumed by CartKeypadOverlay's AnimatedVisibility for the slide.
    val keypadVisible = mutableStateOf(false)

    // Piped into MainKeypad composable for layout + paren glyph.
    val keypadKeyboardType: LiveData<KeyboardType> get() = keyboardType
    private val nextParenLive = MutableLiveData('(')
    val keypadNextParen: LiveData<Char> get() = nextParenLive

    private var currentKeyboardType: KeyboardType = KeyboardType.DEFAULT
    private var activeCalculatorState: CalculatorInputState? = null
    private var activeStateObserver: Observer<String?>? = null
    private var activeParenObserver: Observer<Char>? = null

    init {
        keyboardType.observe(activity) { type -> currentKeyboardType = type }
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
        val parenObserver = Observer<Char> { next -> nextParenLive.value = next }
        state.nextParen.observeForever(parenObserver)
        activeCalculatorState = state
        activeItemId.value = itemId
        activeStateObserver = observer
        activeParenObserver = parenObserver
        keypadVisible.value = true
    }

    /** Hide the keypad and unbind whichever row was being edited. */
    fun closeKeypad() {
        if (activeItemId.value == null && !keypadVisible.value) return
        detachActiveField()
        keypadVisible.value = false
    }

    // Bridge each keystroke from the row's inline EditText (ASCII) back into
    // [liveExpression] (display glyphs) so the row's preview + eventual commit
    // see the same round-tripped form the in-app keypad produces.
    fun onInlineExpressionChanged(
        id: String,
        ascii: String,
    ) {
        if (activeItemId.value != id) return
        val glyphs = ascii.asciiToDisplayGlyphs()
        if (liveExpression.value == glyphs) return
        liveExpression.value = glyphs
    }

    fun dismissKeyboards() {
        if (keypadVisible.value) {
            closeKeypad()
        } else if (activeItemId.value != null) {
            detachActiveField()
        }
        hideSystemIme()
    }

    /** Commit whichever expression is currently buffered on the active row. */
    fun flushActiveExpression() {
        val id = activeItemId.value ?: return
        onExpressionCommit(id, liveExpression.value.orEmpty())
    }

    /**
     * Callback bundle bound to the currently-active [CalculatorInputState].
     * Each button press forwards through the active state (or no-ops when
     * nothing is active). Passed straight into the [MainKeypad] composable.
     */
    val keypadCallbacks =
        com.eliormachlev.currencix.view.main.compose.MainKeypadCallbacks(
            onDigit = { d -> activeCalculatorState?.addNumber(d) },
            onDecimal = { activeCalculatorState?.addDecimal() },
            onOperator = { op -> activeCalculatorState?.addOperator(op.display) },
            onPercent = { activeCalculatorState?.addPercent() },
            onParens = { activeCalculatorState?.applyNextParen() },
            onDelete = { activeCalculatorState?.delete() },
            onDeleteLong = { activeCalculatorState?.clear() },
        )

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
        nextParenLive.value = '('
        // Commit the current keypad expression to the VM so the row's
        // persisted value matches what the user just typed.
        val id = activeItemId.value
        if (id != null) {
            val expression = liveExpression.value.orEmpty()
            onExpressionCommit(id, expression)
        }
        activeCalculatorState = null
        activeItemId.value = null
        activeStateObserver = null
        activeParenObserver = null
        liveExpression.value = ""
    }

    // Suppress unused-parameter lint — Operator import kept in scope for the
    // [keypadCallbacks] adapter without a static reference here.
    @Suppress("unused")
    private fun operatorTypeAnchor(): Operator = Operator.PLUS

    private fun hideSystemIme() {
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val activity = ctx as? AppCompatActivity ?: return
        val token = activity.currentFocus?.windowToken ?: activity.window.decorView.windowToken ?: return
        imm.hideSoftInputFromWindow(token, 0)
    }

    // Retained so BaseActivity's own view lookup (unused after the migration)
    // continues to compile; safe to delete once the cart has been QA'd.
    @Suppress("unused")
    private fun findRoot(activity: AppCompatActivity): View = activity.window.decorView
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
