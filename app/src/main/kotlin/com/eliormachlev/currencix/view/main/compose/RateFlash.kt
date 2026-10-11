package com.eliormachlev.currencix.view.main.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.colorResource
import com.eliormachlev.currencix.R
import java.math.BigDecimal

// Long enough to catch the eye after a refresh, short enough to be gone
// before the next look.
private const val FLASH_MILLIS = 1_400

/**
 * [rest], or, for a moment after an update moves [value] for the same
 * [key] (the pair), the color of the way it went: green up, red down,
 * fading back. A new pair, or the first value, doesn't flash.
 */
@Composable
internal fun flashOnChange(
    key: Any?,
    value: BigDecimal?,
    rest: Color,
): Color {
    val up = colorResource(R.color.rate_diff_positive)
    val down = MaterialTheme.colorScheme.error
    val fade = remember { Animatable(0f) }
    var direction by remember { mutableIntStateOf(0) }
    val last = remember { LastValue() }
    LaunchedEffect(key, value) {
        val before = last.value.takeIf { last.key == key }
        last.key = key
        last.value = value
        if (before == null || value == null || value.compareTo(before) == 0) return@LaunchedEffect
        direction = value.compareTo(before)
        fade.snapTo(1f)
        fade.animateTo(0f, tween(FLASH_MILLIS, easing = LinearEasing))
    }
    return lerp(rest, if (direction > 0) up else down, fade.value)
}

// The value last seen, and for which key.
private class LastValue {
    var key: Any? = null
    var value: BigDecimal? = null
}
