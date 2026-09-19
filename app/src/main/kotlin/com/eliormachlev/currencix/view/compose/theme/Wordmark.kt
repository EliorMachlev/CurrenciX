package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// U+00D7 MULTIPLICATION SIGN, not the letter X — a semantic pun on
// conversion. Split into two Text nodes so the × can be larger and
// center-aligned against the cap-height of "Currenci" (SpanStyle can't
// change the box height an AnnotatedString glyph is measured in, so it
// couldn't optically center on its own).
private const val WORDMARK_X_SCALE = 1.35f
private const val WORDMARK_REVEAL_MILLIS = 520
private const val WORDMARK_TINT_MILLIS = 640

// TransformOrigin vertical pivot: 0.5 = center of the glyph's box. Used so
// the × grows outward from its optical middle rather than from the top.
private const val TRANSFORM_ORIGIN_CENTER = 0.5f

@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    color: Color = MaterialTheme.colorScheme.onBackground,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    // Gates the × reveal animation. When false the × is shown at its final
    // scale immediately (no animation). This lets callers restart the
    // reveal on cold start only, and avoids re-running it on config
    // change / navigation returns. Also allows MainActivity to overlap the
    // splash-screen exit animation with the in-Compose reveal (#155).
    startReveal: Boolean = true,
    // Invoked once the Wordmark has laid out its first frame. Used by the
    // splash-screen hand-off in MainActivity to release
    // setKeepOnScreenCondition — the splash holds until we're pixel-ready
    // to run the reveal, then fades out over the reveal's opening frames.
    onFirstFrame: () -> Unit = {},
) {
    val base =
        TextStyle(
            fontFamily = SpaceGrotesk,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.01).em,
        )
    // Initial value is the final scale when [startReveal] is false so the
    // × is fully drawn immediately on config change / navigation returns.
    val xScale = remember(startReveal) { Animatable(if (startReveal) 0f else 1f) }
    val firstFrameCallback by rememberUpdatedState(onFirstFrame)
    LaunchedEffect(startReveal) {
        // Notify hosts we're on-screen before kicking off any animation —
        // splash hand-off wants to release the keep-on-screen guard the
        // moment the composable is mounted, so the platform's splash exit
        // animation can overlap the reveal's opening frames.
        firstFrameCallback()
        if (startReveal) {
            xScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = WORDMARK_REVEAL_MILLIS, easing = LinearOutSlowInEasing),
            )
        }
    }
    val tintTarget by rememberUpdatedState(if (xScale.value >= 1f) accentColor else color)
    val xColor by animateColorAsState(
        targetValue = tintTarget,
        animationSpec = tween(durationMillis = WORDMARK_TINT_MILLIS),
        label = "wordmarkXTint",
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Currenci", color = color, style = base)
        Text(
            text = "\u00D7",
            color = xColor,
            style = base.copy(fontSize = fontSize * WORDMARK_X_SCALE),
            modifier =
                Modifier.graphicsLayer {
                    val s = xScale.value
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, TRANSFORM_ORIGIN_CENTER)
                },
        )
    }
}
