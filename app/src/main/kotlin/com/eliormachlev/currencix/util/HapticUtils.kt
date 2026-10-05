package com.eliormachlev.currencix.util

import android.content.Context
import android.os.PowerManager
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
 * AND the device NOT being in battery-saver mode. Reads the persisted flag
 * on the calling thread — fine for tap-time use.
 */
private fun Context.shouldHaptic(): Boolean = Database(this).isHapticFeedbackEnabledBlocking() && !isInPowerSaveMode()

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
    onLongClickLabel: String? = null,
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
            onLongClickLabel = onLongClickLabel,
            onLongClick =
                onLongClick?.let {
                    {
                        tap()
                        it()
                    }
                },
        )
    }
