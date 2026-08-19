package com.eliormachlev.currencix.view.compose

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

// Shared alpha for a row that's actively being dragged; keeps the "picked-up"
// affordance consistent across the two long-press reorder sites (cart items,
// starred currencies).
const val DRAG_REORDER_ACTIVE_ALPHA = 0.85f

/**
 * State bag for a long-press-to-drag reorder gesture. Kept as a class (rather
 * than four loose `mutableStateOf`s at call sites) so field reads inside a
 * `graphicsLayer { ... }` lambda stay in the draw phase — rows don't
 * recompose while the finger moves at 60 Hz, they just re-draw at a new Y.
 */
class DragReorderState {
    var draggingIndex by mutableStateOf<Int?>(null)
    var targetIndex by mutableStateOf<Int?>(null)
    var offsetY by mutableStateOf(0f)

    val isActive: Boolean get() = draggingIndex != null

    fun reset() {
        draggingIndex = null
        targetIndex = null
        offsetY = 0f
    }
}

@Composable
fun rememberDragReorderState(): DragReorderState = remember { DragReorderState() }

/**
 * Vertical translation for the row at [index], given the current drag state.
 * The dragged row itself follows the finger (offsetY); every row between the
 * source and target slots slides one row-height in the opposite direction to
 * open the gap. Callers should invoke this from inside a `graphicsLayer` lambda
 * so state reads land in the draw phase.
 */
fun DragReorderState.translationYFor(
    index: Int,
    rowHeightPx: Float,
): Float {
    if (draggingIndex == index) return offsetY
    val src = draggingIndex ?: return 0f
    val dst = targetIndex ?: return 0f
    return when {
        src < dst && index in (src + 1)..dst -> -rowHeightPx
        src > dst && index in dst until src -> rowHeightPx
        else -> 0f
    }
}

/**
 * Wire long-press drag-to-reorder onto a row (typically its drag-handle
 * icon). [key] scopes the pointer input so re-composition doesn't re-install
 * the gesture unnecessarily — pass the row's stable id. [itemCount] is read
 * lazily so a list change mid-gesture (delete, reload) is reflected in the
 * clamp without needing to re-key.
 *
 * [onCommit] fires only when the user releases on a slot different from the
 * one they picked up from, and both indices are still valid.
 */
@Suppress("LongParameterList")
fun Modifier.dragReorderHandle(
    state: DragReorderState,
    index: Int,
    key: Any?,
    rowHeightPx: Float,
    itemCount: () -> Int,
    onStart: () -> Unit = {},
    onCommit: (fromIndex: Int, toIndex: Int) -> Unit,
): Modifier =
    pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                onStart()
                state.draggingIndex = index
                state.targetIndex = index
                state.offsetY = 0f
            },
            onDragEnd = {
                val src = state.draggingIndex
                val dst = state.targetIndex
                val count = itemCount()
                if (src != null && dst != null && src != dst && src in 0 until count && dst in 0 until count) {
                    onCommit(src, dst)
                }
                state.reset()
            },
            onDragCancel = { state.reset() },
            onDrag = { change, dragAmount ->
                change.consume()
                state.offsetY += dragAmount.y
                val src = state.draggingIndex ?: return@detectDragGesturesAfterLongPress
                val max = itemCount() - 1
                if (max < 0) return@detectDragGesturesAfterLongPress
                val delta = kotlin.math.round(state.offsetY / rowHeightPx).toInt()
                state.targetIndex = (src + delta).coerceIn(0, max)
            },
        )
    }
