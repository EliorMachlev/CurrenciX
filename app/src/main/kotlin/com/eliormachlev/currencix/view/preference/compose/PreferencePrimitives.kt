package com.eliormachlev.currencix.view.preference.compose

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.LedgerLabel
import com.eliormachlev.currencix.view.compose.LedgerRow
import com.eliormachlev.currencix.view.compose.LedgerSection
import com.eliormachlev.currencix.view.compose.LedgerTrailing
import kotlinx.coroutines.delay

// Thin preference-flavoured wrapper around the shared ledger vocabulary in
// `view/compose/Ledger.kt`. All list surfaces (Preferences, Fees, Backup,
// picker, drawer) route through the same primitives so the "paper + brass"
// look stays consistent — this file exists to keep the existing
// PreferenceRow/PreferenceSection/SwitchRow call sites (and the screenshot
// tests pinned to those names) working after the redesign.

/**
 * Section grouping used by the Compose preferences screen. Thin alias over
 * [LedgerSection] so the brass small-caps header + hairline-separated rows
 * apply automatically. The section header is marked as a TalkBack heading
 * so users can jump between sections.
 */
@Composable
fun PreferenceSection(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    LedgerSection(title = text, modifier = modifier) { content() }
}

/**
 * Preference row — leading icon (optional), [title] + optional [summary]
 * stacked, optional [trailing] slot for chevrons, values, switches. Whole
 * row is haptic-clickable when [onClick] is non-null; falls back to a
 * static row when null so info-only entries don't pretend to be actionable.
 * The paper hairline under the row is drawn by [LedgerRow] itself.
 */
@Composable
fun PreferenceRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconRes: Int? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    LedgerRow(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        label = {
            LedgerLabel(
                title = title,
                summary = summary,
                iconRes = iconRes,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        },
        value = trailing?.let { slot -> { LedgerTrailing { slot() } } },
    )
}

/**
 * Boolean preference — [PreferenceRow] with a trailing Material Switch. The
 * row-level click and the switch stay in sync: tapping anywhere on the row
 * toggles the value (with haptic), and the switch itself is presentational.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconRes: Int? = null,
    enabled: Boolean = true,
) {
    val toggle = rememberHapticOnClick { onCheckedChange(!checked) }
    PreferenceRow(
        title = title,
        modifier = modifier,
        summary = summary,
        iconRes = iconRes,
        enabled = enabled,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = { toggle() },
                enabled = enabled,
            )
        },
    )
}

/**
 * No-op divider kept for source compatibility with earlier callers. Ledger
 * rows draw their own hairline internally; adjacent rows already inherit
 * the paper rule, so an explicit divider is redundant. Left as a stub so a
 * follow-up cleanup can remove all remaining call sites without churn here.
 */
@Suppress("UnusedParameter")
@Composable
fun PreferenceDivider(hasIcon: Boolean = true) {
    // Intentionally empty — ledger rows own their own hairline.
}

/**
 * Wraps a preference section (or any grouped card) in a first-appearance
 * fade + slide-up, staggered by [index] so a screen full of sections lands
 * as a soft cascade rather than a jarring flash. Runs once per composition
 * (keyed on the composable being entered), so scrolling in / out of view in
 * a LazyColumn does not replay the animation.
 */
@Composable
fun SectionEnter(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * SECTION_ENTER_STAGGER_MILLIS)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = SECTION_ENTER_MILLIS, easing = FastOutSlowInEasing),
        )
    }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = SECTION_ENTER_BOTTOM_GAP)
                .graphicsLayer {
                    alpha = progress.value
                    translationY = (1f - progress.value) * SECTION_ENTER_TRANSLATION_PX
                },
    ) { content() }
}

// Extra breathing room between adjacent sections. With the grouped-card
// backdrop gone the header brass is the primary separator; a small bottom
// gap keeps consecutive sections from crowding each other's brass.
private val SECTION_ENTER_BOTTOM_GAP: Dp = 4.dp

// ~180ms between sections keeps the cascade legible on a six-section screen
// without pushing the last card past the user's attention window. Total dwell
// for the last section = index*180 + 360 = ~1260ms, still comfortable.
private const val SECTION_ENTER_STAGGER_MILLIS: Long = 60
private const val SECTION_ENTER_MILLIS: Int = 360
private const val SECTION_ENTER_TRANSLATION_PX: Float = 32f
