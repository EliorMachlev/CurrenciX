package com.eliormachlev.currencix.view.cart.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.eliormachlev.currencix.model.KeyboardType
import com.eliormachlev.currencix.view.cart.CartKeypadController
import com.eliormachlev.currencix.view.main.compose.MainKeypad

@Composable
private fun GripStrip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(GRIP_STRIP_HEIGHT).padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = GRIP_WIDTH, height = GRIP_HEIGHT)
                .clip(RoundedCornerShape(GRIP_HEIGHT / 2))
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

// Matches the old activity_cart.xml FrameLayout's 320dp allocation.
private val KEYPAD_HEIGHT: Dp = 320.dp

// Distance the user must drag the keypad downward before releasing to commit
// the dismiss. Anything less snaps back to fully open.
private val DRAG_DISMISS_THRESHOLD: Dp = 96.dp

// Visible grip strip sizes — Material's bottom-sheet drag handle at the top
// of the keypad so users have a clear surface to grab (the digit buttons
// underneath consume touches, so a whole-surface draggable modifier wouldn't
// fire).
private val GRIP_STRIP_HEIGHT: Dp = 20.dp
private val GRIP_WIDTH: Dp = 40.dp
private val GRIP_HEIGHT: Dp = 4.dp

/**
 * Bottom-aligned floating calculator that slides up when a cart row's
 * expression field is tapped, and slides back down on: outside-tap, back
 * press, or drag-down-past-threshold. Only the in-app-keypad variants
 * surface this overlay — system-IME variants leave [CartKeypadController.keypadVisible]
 * false and the row hosts a real EditText instead.
 *
 * Auto-closes when the system IME becomes visible so we don't stack two keyboards.
 */
@Composable
fun CartKeypadOverlay(
    keypad: CartKeypadController,
    modifier: Modifier = Modifier,
) {
    val visible = keypad.keypadVisible.value
    val keyboardType by keypad.keypadKeyboardType.observeAsState(KeyboardType.DEFAULT)
    val nextParen by keypad.keypadNextParen.observeAsState('(')

    val density = LocalDensity.current
    val thresholdPx = with(density) { DRAG_DISMISS_THRESHOLD.toPx() }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val dragState =
        rememberDraggableState { delta ->
            dragOffsetPx = (dragOffsetPx + delta).coerceAtLeast(0f)
        }

    BackHandler(enabled = visible) { keypad.closeKeypad() }

    val hostView = LocalView.current
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        dragOffsetPx = 0f
        val insets = ViewCompat.getRootWindowInsets(hostView)
        if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) keypad.closeKeypad()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(KEYPAD_HEIGHT)
                .graphicsLayer { translationY = dragOffsetPx }
                .background(MaterialTheme.colorScheme.background),
        ) {
            GripStrip(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = {
                                if (dragOffsetPx >= thresholdPx) {
                                    keypad.closeKeypad()
                                } else {
                                    dragOffsetPx = 0f
                                }
                            },
                        ),
            )
            MainKeypad(
                keyboardType = if (keyboardType.isSystem) KeyboardType.BASIC else keyboardType,
                nextParen = nextParen,
                callbacks = keypad.keypadCallbacks,
            )
        }
    }
}
