package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.eliormachlev.currencix.view.compose.Ltr

// U+00D7 MULTIPLICATION SIGN, not the letter X — a semantic pun on
// conversion. Split into two Text nodes so the × can be larger and
// center-aligned against the cap-height of "Currenci" (SpanStyle can't
// change the box height an AnnotatedString glyph is measured in, so it
// couldn't optically center on its own).
private const val WORDMARK_X_SCALE = 1.35f

// TransformOrigin vertical pivot: 0.5 = center of the glyph's box. Used so
// the × grows outward from its optical middle rather than from the top.
private const val TRANSFORM_ORIGIN_CENTER = 0.5f

/**
 * The wordmark's reveal, the × growing in, which plays once per cold start
 * ([play]), and its hand-off with the splash screen: [onFirstFrame] tells
 * the host the wordmark is composed (the splash holds until then), and the
 * reveal starts once [awaitStart] returns, when the splash starts to fade.
 */
class WordmarkReveal(
    val play: Boolean,
    val onFirstFrame: () -> Unit = {},
    val awaitStart: suspend () -> Unit = {},
) {
    companion object {
        /** No reveal: the × is drawn from the start. */
        val Shown = WordmarkReveal(play = false)
    }
}

@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    color: Color = MaterialTheme.colorScheme.onBackground,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    // The cold-start reveal and its hand-off with the splash screen; by
    // default the × is simply shown.
    reveal: WordmarkReveal = WordmarkReveal.Shown,
) {
    val base =
        TextStyle(
            fontFamily = SpaceGrotesk,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.01).em,
        )
    // Initial value is the final scale when the reveal doesn't play, so the
    // × is fully drawn immediately on config change / navigation returns.
    val xScale = remember(reveal) { Animatable(if (reveal.play) 0f else 1f) }
    val firstFrameCallback by rememberUpdatedState(reveal.onFirstFrame)
    // Tell the host we're mounted before any animation starts: the splash
    // hand-off releases its keep-on-screen guard on this. Run as the
    // composition is applied rather than from a coroutine — the splash holds
    // every draw until it hears this, so it must not wait on a dispatcher.
    DisposableEffect(Unit) {
        firstFrameCallback()
        onDispose {}
    }
    LaunchedEffect(reveal) {
        if (reveal.play) {
            // Under the splash the reveal would play unseen: start it as the
            // splash fades.
            reveal.awaitStart()
            xScale.animateTo(
                targetValue = 1f,
                animationSpec = Motion.flourish(),
            )
        }
    }
    val tintTarget by rememberUpdatedState(if (xScale.value >= 1f) accentColor else color)
    val xColor by animateColorAsState(
        targetValue = tintTarget,
        animationSpec = tween(durationMillis = Motion.LONG_MILLIS),
        label = "wordmarkXTint",
    )
    // A logo: "Currenci×" in every language, even in a right-to-left top bar.
    Ltr {
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
}
