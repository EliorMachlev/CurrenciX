package com.eliormachlev.currencix.view.cart

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.KeyboardType
import com.eliormachlev.currencix.util.CALC_TOKEN_REGEX
import com.eliormachlev.currencix.util.OPERATOR_REGEX
import com.eliormachlev.currencix.util.asciiToDisplayGlyphs
import com.eliormachlev.currencix.util.hapticTap
import com.eliormachlev.currencix.util.paintParenCycle
import com.eliormachlev.currencix.viewmodel.main.CalculatorInputState

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

/**
 * Owns the slide-up cart keypad: view refs, active-row bookkeeping, IME
 * coordination, drag-to-dismiss, and the touch-dispatch overrides. The host
 * activity keeps the `android:onClick` reflection targets (LayoutInflater
 * resolves those against the Activity) and forwards each press through
 * [forwardKeypadAction].
 *
 * `activeItemId` and `liveExpression` are exposed for the compose row to
 * observe (active-row highlight + inline display), and [onExpressionCommit] is
 * invoked whenever a keypad session ends so the host can push the buffered
 * value into the view model.
 */
class CartKeypadController(
    private val activity: AppCompatActivity,
    private val itemsView: ComposeView,
    keyboardType: LiveData<KeyboardType>,
    private val hapticEnabled: () -> Boolean,
    private val onExpressionCommit: (id: String, expression: String) -> Unit,
    private val dispatchCancel: (MotionEvent) -> Unit,
) {
    private val keypadContainer: ViewGroup = activity.findViewById(R.id.cart_keypad_container)
    private val keypadRegular: View = activity.findViewById(R.id.cart_keypad_regular)
    private val keypadExtended: View = activity.findViewById(R.id.cart_keypad_extended)
    private val contentColumn: View = activity.findViewById(R.id.cart_content)

    val activeItemId = MutableLiveData<String?>(null)
    val liveExpression = MutableLiveData("")

    private var currentKeyboardType: KeyboardType = KeyboardType.DEFAULT
    private var activeCalculatorState: CalculatorInputState? = null
    private var activeStateObserver: Observer<String?>? = null
    private var activeParenObserver: Observer<Char>? = null
    private val keypadBackCallback: OnBackPressedCallback

    // Rising-edge latch for the IME-visibility guard; see [installImeVisibilityGuard].
    private var systemImeVisible = false

    // Drag-to-dismiss state — only meaningful once ACTION_DOWN lands inside
    // the keypad and the pointer travels far enough downward to cross
    // [touchSlop]. [keypadDragStartY] is null when no candidate gesture is
    // being tracked; [keypadDragActive] flips true once slop is crossed and
    // the child buttons have been sent an ACTION_CANCEL.
    private var keypadDragStartY: Float? = null
    private var keypadDragActive = false
    private val touchSlop: Int by lazy { ViewConfiguration.get(activity).scaledTouchSlop }

    init {
        installImeVisibilityGuard()
        keypadBackCallback =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() = closeKeypad()
            }.also { activity.onBackPressedDispatcher.addCallback(activity, it) }
        keyboardType.observe(activity) { type ->
            currentKeyboardType = type
            val extended = type == KeyboardType.EXPANDED
            keypadRegular.visibility = if (extended) View.GONE else View.VISIBLE
            keypadExtended.visibility = if (extended) View.VISIBLE else View.GONE
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

    /** Hide the keypad and unbind whichever row was being edited. */
    fun closeKeypad() {
        if (activeItemId.value == null && keypadContainer.visibility == View.GONE) return
        detachActiveField()
        hideKeypad()
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
            activity.currentFocus?.clearFocus()
        }
    }

    /** Commit whichever expression is currently buffered on the active row. */
    fun flushActiveExpression() {
        val id = activeItemId.value ?: return
        onExpressionCommit(id, liveExpression.value.orEmpty())
    }

    /**
     * Called from the host activity's `dispatchTouchEvent`. Returns true when
     * the event has been consumed (drag interception) so the caller should
     * skip its `super.dispatchTouchEvent`.
     */
    fun handleTouchEvent(ev: MotionEvent): Boolean {
        if (keypadContainer.visibility == View.VISIBLE && handleKeypadDrag(ev)) return true
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) handleOutsideTap(ev)
        return false
    }

    // Route each keypad button press through the currently-active state, with
    // a haptic tap on the button that fired.
    fun forwardKeypadAction(
        view: View,
        action: (CalculatorInputState) -> Unit,
    ) {
        view.hapticTap(hapticEnabled())
        activeCalculatorState?.let(action)
    }

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
        val root = activity.findViewById<View>(R.id.cart_root)
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

    // The `()` cycle-toggle key on the extended keypad. Absent from the basic
    // layout, so callers must tolerate null.
    private fun parenButton(): AppCompatButton? = keypadExtended.findViewById(R.id.btn_parens)

    private fun showKeypad() {
        keypadBackCallback.isEnabled = true
        if (keypadContainer.visibility == View.VISIBLE) return
        keypadContainer.visibility = View.VISIBLE
        keypadContainer.translationY = keypadContainer.height.toFloat().takeIf { it > 0f }
            ?: activity.resources.displayMetrics.heightPixels
                .toFloat()
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
        keypadBackCallback.isEnabled = false
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
            onExpressionCommit(id, expression)
        }
        activeCalculatorState = null
        activeItemId.value = null
        activeStateObserver = null
        activeParenObserver = null
        liveExpression.value = ""
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
                    dispatchCancel(cancel)
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
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val token = activity.currentFocus?.windowToken ?: activity.window.decorView.windowToken ?: return
        imm.hideSoftInputFromWindow(token, 0)
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
