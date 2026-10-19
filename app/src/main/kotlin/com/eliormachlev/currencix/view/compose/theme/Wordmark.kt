package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.view.compose.Ltr

// The logo stands in for the × of "Currenci×", larger than the letters:
// this many times the font size across.
private const val MARK_SCALE = 1.5f

// Air between "Currenci" and the logo, as a fraction of the font size.
private const val MARK_GAP = 0.08f

/**
 * The wordmark's reveal, the logo's arrows turning into place, which plays
 * once per cold start ([play]), and its hand-off with the splash screen:
 * [onFirstFrame] tells the host the wordmark is composed, and the splash,
 * which holds until then, fades over the turn's opening frames.
 */
class WordmarkReveal(
    val play: Boolean,
    val onFirstFrame: () -> Unit = {},
) {
    companion object {
        /** No reveal: the logo is at rest from the start. */
        val Shown = WordmarkReveal(play = false)
    }
}

/**
 * "Currenci" and the app's logo ([BrandMark]) as its ×. The logo's arrows
 * turn into place on a cold start ([reveal]), keep turning while [turning]
 * (rates refreshing), and turn when the wordmark is tapped, which calls
 * [onClick] (a refresh) when it's set.
 */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    color: Color = MaterialTheme.colorScheme.onBackground,
    reveal: WordmarkReveal = WordmarkReveal.Shown,
    turning: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val style =
        TextStyle(
            fontFamily = SpaceGrotesk,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.01).em,
        )
    val turns = remember(reveal) { MarkTurns(launch = reveal.play) }
    val isTurning by rememberUpdatedState(turning)
    val firstFrameCallback by rememberUpdatedState(reveal.onFirstFrame)
    // Tell the host we're mounted before any animation starts: the splash
    // hand-off releases its keep-on-screen guard on this, so the platform's
    // exit animation can overlap the turn's opening frames. Run as the
    // composition is applied rather than from a coroutine — the splash holds
    // every draw until it hears this, so it must not wait on a dispatcher.
    DisposableEffect(Unit) {
        firstFrameCallback()
        onDispose {}
    }
    LaunchedEffect(turns) { turns.run { isTurning } }
    val density = LocalDensity.current
    val markSize = with(density) { (fontSize * MARK_SCALE).toDp() }
    val gap = with(density) { (fontSize * MARK_GAP).toDp() }
    val name = stringResource(R.string.app_name)
    val tap =
        onClick?.let { action ->
            Modifier
                .clip(MaterialTheme.shapes.small)
                .hapticClickable(onClickLabel = stringResource(R.string.menu_refresh)) {
                    action()
                    turns.turnOnce()
                }
        } ?: Modifier
    // A logo: "Currenci×" in every language, even in a right-to-left top bar.
    Ltr {
        Row(
            modifier = modifier.then(tap).semantics { contentDescription = name },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Currenci", color = color, style = style, modifier = Modifier.clearAndSetSemantics {})
            BrandMark(
                rotation = { turns.rotation },
                crossColor = color,
                modifier =
                    Modifier
                        .padding(start = gap)
                        .size(markSize),
            )
        }
    }
}
