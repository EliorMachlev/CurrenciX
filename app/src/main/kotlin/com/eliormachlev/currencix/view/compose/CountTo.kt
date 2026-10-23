package com.eliormachlev.currencix.view.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import java.math.BigDecimal
import java.math.MathContext

// Quick enough not to hold up a glance, long enough to see the count.
private const val COUNT_MILLIS = 450

/**
 * [target], counting there from the value shown before whenever it changes
 * (adding an item to the cart counts the total up), and jumping straight to
 * it when [key] changes (another currency: nothing to count from) or there
 * was nothing before.
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
        if (target == null || shown == null || key != count.key) {
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

// Where a count runs from and to, and for which key.
private class CountState(
    initial: BigDecimal?,
) {
    private var from: BigDecimal? = initial
    private var to: BigDecimal? = initial
    var key: Any? = null
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

    fun at(fraction: Float): BigDecimal? {
        val start = from ?: return to
        val end = to ?: return null
        if (fraction >= 1f) return end
        return start + (end - start).multiply(BigDecimal(fraction.toDouble()), MathContext.DECIMAL64)
    }
}
