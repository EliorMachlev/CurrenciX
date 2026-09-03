package com.eliormachlev.currencix.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.PowerManager
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.preference.Preference
import com.eliormachlev.currencix.repository.Database

/**
 * True when the device is in battery-saver mode. Haptic feedback is suppressed
 * in this state regardless of the user's own preference — the system's
 * power-saving contract asks apps to skip non-essential vibrations.
 */
private fun Context.isInPowerSaveMode(): Boolean {
    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isPowerSaveMode
}

/**
 * Resolve whether haptic feedback should currently fire: the user's preference
 * AND the device NOT being in battery-saver mode. Reads SharedPreferences on
 * the calling thread — fine for tap-time use.
 */
private fun Context.shouldHaptic(): Boolean = Database(this).isHapticFeedbackEnabledBlocking() && !isInPowerSaveMode()

/**
 * Perform a "keyboard tap" haptic when [enabled] says the user wants it AND
 * the device isn't in battery-saver mode. Used by activities that already
 * observe the preference as LiveData (the calculator keypad, cart keypad).
 */
fun View.hapticTap(enabled: Boolean) {
    if (!enabled) return
    if (context.isInPowerSaveMode()) return
    // FLAG_IGNORE_VIEW_SETTING: some parent chains disable haptic feedback
    // (e.g. cards, RecyclerView rows), so force the tap regardless of the
    // view-tree setting. The user's global "haptic feedback" system toggle
    // is still respected by the OS.
    performHapticFeedback(
        HapticFeedbackConstants.KEYBOARD_TAP,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
    )
}

/**
 * No-arg variant that reads the preference and power-save state itself. Use
 * this at sites without an observed [Boolean] handy — menu items, dialog
 * buttons, spinner selections, one-off click listeners.
 */
fun View.hapticTap() {
    if (!context.shouldHaptic()) return
    performHapticFeedback(
        HapticFeedbackConstants.KEYBOARD_TAP,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
    )
}

/**
 * Activity-scoped haptic tap for callbacks that don't have a specific source
 * View — menu items, back-press callbacks, dialog buttons routed through the
 * activity. Falls back to the window's decor view for the vibration source.
 */
fun Activity.hapticTap() {
    window?.decorView?.hapticTap()
}

/**
 * Attach haptic taps to the standard positive/negative/neutral buttons of a
 * shown [AlertDialog]. Uses a touch-listener on ACTION_DOWN so the vibration
 * fires immediately with the press — the built-in click behaviour (dismiss +
 * user callback) is preserved because the listener returns false.
 *
 * Deferred to the dialog's onShow callback so it works whether called before
 * or after [AlertDialog.show]. Currently the codebase has no other
 * OnShowListener, so a plain [AlertDialog.setOnShowListener] is safe.
 */
@SuppressLint("ClickableViewAccessibility")
private fun AlertDialog.wireHapticButtons() {
    fun applyNow() {
        listOf(
            AlertDialog.BUTTON_POSITIVE,
            AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_NEUTRAL,
        ).forEach { which ->
            getButton(which)?.setOnTouchListener { v, ev ->
                if (ev.action == MotionEvent.ACTION_DOWN) v.hapticTap()
                false
            }
        }
    }
    if (isShowing) applyNow() else setOnShowListener { applyNow() }
}

/**
 * Build the dialog and wire haptics onto its buttons in one call. Covers both
 * [AlertDialog.Builder] and [com.google.android.material.dialog.MaterialAlertDialogBuilder]
 * (which extends it) so every dialog show-site in the app collapses to a
 * single terminal call instead of repeating `.show().also { it.wireHapticButtons() }`.
 */
fun AlertDialog.Builder.showWithHapticButtons(): AlertDialog = show().also { it.wireHapticButtons() }

/** [showWithHapticButtons] counterpart for sites that want the dialog created but not yet shown. */
fun AlertDialog.Builder.createWithHapticButtons(): AlertDialog = create().apply { wireHapticButtons() }

/**
 * [View.setOnClickListener] plus a haptic tap on the source view before the
 * click callback runs. Use at every click-listener site in the app so the
 * "vibrate then dispatch" pattern lives in one place.
 */
fun View.setOnHapticClickListener(onClick: (View) -> Unit) {
    setOnClickListener { v ->
        v.hapticTap()
        onClick(v)
    }
}

/**
 * [Preference.setOnPreferenceClickListener] that first fires a haptic tap on
 * the hosting Activity, then runs [onClick]. Returns `true` from the listener
 * — the preference framework treats that as "handled, don't open the default
 * dialog", which is what every current call site wants (they open their own
 * dialog / navigate / push a fragment).
 *
 * For built-in [androidx.preference.DialogPreference] rows whose default
 * dialog SHOULD open, override
 * [androidx.preference.PreferenceFragmentCompat.onDisplayPreferenceDialog]
 * instead — that path fires haptic without short-circuiting the framework.
 */
fun Preference.setOnHapticClickListener(onClick: () -> Unit) {
    setOnPreferenceClickListener {
        (context as? Activity)?.hapticTap()
        onClick()
        true
    }
}

/**
 * [Preference.setOnPreferenceChangeListener] that first fires a haptic tap on
 * the hosting Activity, then runs [onChange]. Always returns `true` so the
 * new value is persisted — the callers we have here uniformly want that; if
 * a future site needs conditional persistence, wire the raw listener directly.
 */
fun Preference.setOnHapticChangeListener(onChange: (newValue: Any?) -> Unit) {
    setOnPreferenceChangeListener { _, newValue ->
        (context as? Activity)?.hapticTap()
        onChange(newValue)
        true
    }
}

/**
 * Compose analogue of [Modifier.clickable] that also fires a keyboard-tap
 * haptic on click, honouring the user's preference and battery-saver state.
 * [onClickLabel] is surfaced to TalkBack as the "double-tap to X" verb; pass
 * a translated action string ("Select", "Load", …) at row-tap sites so the
 * screen reader announces what activating the row does.
 */
fun Modifier.hapticClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier =
    composed {
        val ctx = LocalContext.current
        val haptic = LocalHapticFeedback.current
        clickable(enabled = enabled, onClickLabel = onClickLabel) {
            if (ctx.shouldHaptic()) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            onClick()
        }
    }

/**
 * Wraps a Compose [onClick] callback so the wrapped lambda fires the same
 * battery-saver-aware haptic before delegating to the original. Use at sites
 * that already take an `onClick: () -> Unit` (e.g. Material3 [IconButton])
 * where a full [Modifier.hapticClickable] would be awkward. The returned
 * lambda is remembered across recompositions so IconButton's `onClick`
 * identity stays stable.
 */
@Composable
fun rememberHapticOnClick(onClick: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(onClick)
    return remember {
        {
            if (ctx.shouldHaptic()) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            current()
        }
    }
}

/**
 * Fires a keyboard-tap haptic the moment the modified node gains focus.
 * Use on text fields (BasicTextField, etc.) so tap-to-edit feels the same
 * as tap-to-click, honouring the user's preference and battery-saver state.
 */
fun Modifier.hapticOnFocus(): Modifier =
    composed {
        val ctx = LocalContext.current
        val haptic = LocalHapticFeedback.current
        onFocusChanged { state ->
            if (state.isFocused && ctx.shouldHaptic()) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

/**
 * Compose analogue of [Modifier.combinedClickable] with the same
 * battery-saver-aware haptic behaviour on click and long-click.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.hapticCombinedClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier =
    composed {
        val ctx = LocalContext.current
        val haptic = LocalHapticFeedback.current

        fun tap() {
            if (ctx.shouldHaptic()) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        combinedClickable(
            onClick = {
                tap()
                onClick()
            },
            onLongClick =
                onLongClick?.let {
                    {
                        tap()
                        it()
                    }
                },
        )
    }
