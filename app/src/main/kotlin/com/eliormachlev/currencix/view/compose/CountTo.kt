package com.eliormachlev.currencix.view.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.math.BigDecimal
import java.math.MathContext

// Quick enough not to hold up a glance, long enough to see the count.
private const val COUNT_MILLIS = 450

/**
 * [target], counting there from the value shown before whenever it changes
 * (adding an item to the cart counts the total up), and jumping straight to
 * it when [key] changes (another currency: nothing to count from) or there
 * was nothing, or zero, before.
 */
@Composable
fun countTo(
    target: BigDecimal?,
    key: Any? = null,
): BigDecimal? {
    val count = remember { CountState(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target, key) {
        val shown = count.at(progress.value)
        if (target == null || !count.canCountFrom(shown, key)) {
            count.restart(target, target, key)
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        count.restart(shown, target, key)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(COUNT_MILLIS, easing = FastOutSlowInEasing))
    }
    return count.at(progress.value)
}

// Where a count runs from and to, and for which key. Compose state, so a
// jump (restart with the progress already at 1) redraws: as plain fields, the
// total stayed on the value drawn before it (0.00 when the cart reopened).
private class CountState(
    initial: BigDecimal?,
) {
    private var from by mutableStateOf(initial)
    private var to by mutableStateOf(initial)
    var key: Any? by mutableStateOf(null)
        private set

    fun restart(
        from: BigDecimal?,
        to: BigDecimal?,
        key: Any?,
    ) {
        this.from = from
        this.to = to
        this.key = key
    }

    // Whether there's something to count from: a value shown, in the same
    // currency, and not zero (the cart as it opens, before its items load),
    // which would otherwise count up from 0.00 each time the screen appears.
    fun canCountFrom(
        shown: BigDecimal?,
        key: Any?,
    ): Boolean = shown != null && shown.signum() != 0 && key == this.key

    fun at(fraction: Float): BigDecimal? {
        val start = from ?: return to
        val end = to ?: return null
        if (fraction >= 1f) return end
        return start + (end - start).multiply(BigDecimal(fraction.toDouble()), MathContext.DECIMAL64)
    }
}
