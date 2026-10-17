package com.eliormachlev.currencix.view.cart.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.DraggableState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.cart.CartKeypadController
import com.eliormachlev.currencix.view.compose.UiTestTags
import com.eliormachlev.currencix.view.compose.theme.Motion
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.view.main.compose.MainKeypad

@Composable
private fun GripStrip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(GRIP_STRIP_HEIGHT).padding(vertical = Spacing.Small),
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

// The keys keep what they had under the old 20dp grip strip (the old
// activity_cart.xml's 320dp allocation); the header with the grip and the
// Next key sits above them.
private val KEYS_HEIGHT: Dp = 300.dp
private val KEYPAD_HEADER_HEIGHT: Dp = 40.dp
private val KEYPAD_HEIGHT: Dp = KEYS_HEIGHT + KEYPAD_HEADER_HEIGHT

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
 * press, or drag-down-past-threshold.
 *
 * Auto-closes when the system IME becomes visible so we don't stack two keyboards.
 */
@Composable
fun CartKeypadOverlay(
    keypad: CartKeypadController,
    modifier: Modifier = Modifier,
) {
    val visible = keypad.keypadVisible.value
    val isExpandedKeypad by keypad.isExpandedKeypad.observeAsState(false)
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
        // Motion.settle: a quick, bounce-free spring, so re-opening the keypad
        // mid-slide reverses smoothly instead of restarting.
        enter = slideInVertically(animationSpec = Motion.settle(IntOffset.VisibilityThreshold), initialOffsetY = { it }),
        exit = slideOutVertically(animationSpec = Motion.settle(IntOffset.VisibilityThreshold), targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(KEYPAD_HEIGHT)
                .graphicsLayer { translationY = dragOffsetPx }
                .background(MaterialTheme.colorScheme.background),
        ) {
            KeypadHeader(
                dragState = dragState,
                onDragStopped = {
                    if (dragOffsetPx >= thresholdPx) {
                        keypad.closeKeypad()
                    } else {
                        dragOffsetPx = 0f
                    }
                },
                onNext = keypad.onNext,
            )
            MainKeypad(
                isExpandedKeypad = isExpandedKeypad,
                nextParen = nextParen,
                callbacks = keypad.keypadCallbacks,
            )
        }
    }
}

// Above the keys: the grip to drag the keypad away by, and Next at the end.
@Composable
private fun KeypadHeader(
    dragState: DraggableState,
    onDragStopped: () -> Unit,
    onNext: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(KEYPAD_HEADER_HEIGHT)
                .draggable(state = dragState, orientation = Orientation.Vertical, onDragStopped = { onDragStopped() }),
    ) {
        GripStrip(Modifier.align(Alignment.Center))
        NextButton(onClick = onNext, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

// "Next →": on to the next row's price, or a new row after the last.
@Composable
private fun NextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = rememberHapticOnClick(onClick), modifier = modifier.padding(end = Spacing.Small).testTag(UiTestTags.CART_NEXT)) {
        Text(stringResource(R.string.cart_keypad_next))
        Icon(
            painter = painterResource(R.drawable.ic_arrow_forward),
            contentDescription = null,
            modifier = Modifier.padding(start = Spacing.Tiny).size(NEXT_ICON_SIZE),
        )
    }
}

private val NEXT_ICON_SIZE: Dp = 18.dp
