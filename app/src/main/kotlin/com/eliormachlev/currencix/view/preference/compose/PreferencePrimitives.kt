package com.eliormachlev.currencix.view.preference.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.util.rememberHapticOnClick

// Grouped-card style, M3-Expressive settings look. Sections are rounded
// surface-container blocks with a small primary-tinted header above; rows
// inside are edge-to-edge without dividers so the shared background does the
// grouping visually.
private val SECTION_RADIUS: Dp = 20.dp
private val SECTION_HEADER_HORIZONTAL: Dp = 24.dp
private val SECTION_HEADER_TOP: Dp = 20.dp
private val SECTION_HEADER_BOTTOM: Dp = 8.dp
private val ROW_HORIZONTAL: Dp = 20.dp
private val ROW_VERTICAL: Dp = 14.dp
private val ROW_ICON_SIZE: Dp = 24.dp
private val ROW_ICON_GAP: Dp = 20.dp
private val TRAILING_GAP: Dp = 12.dp

/**
 * Section grouping used by the Compose preferences screen. Wraps [content]
 * (typically a stack of [PreferenceRow] / [SwitchRow]) in a single rounded
 * surface-container-high card, and prefixes it with a tinted [text] header
 * marked as a TalkBack heading so users can jump between sections.
 */
@Composable
fun PreferenceSection(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .padding(
                        start = SECTION_HEADER_HORIZONTAL,
                        end = SECTION_HEADER_HORIZONTAL,
                        top = SECTION_HEADER_TOP,
                        bottom = SECTION_HEADER_BOTTOM,
                    ).semantics { heading() },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SECTION_RADIUS))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) { content() }
    }
}

/**
 * Basic preference row — leading icon (optional), [title] + optional [summary]
 * stacked, optional [trailing] slot for chevrons, values, switches. Whole row
 * is haptic-clickable when [onClick] is non-null; falls back to a static
 * (non-focusable in click semantics) row when null so info-only entries don't
 * pretend to be actionable.
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
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    val base =
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.hapticClickable(enabled = enabled, onClick = onClick) else it }
            .padding(horizontal = ROW_HORIZONTAL, vertical = ROW_VERTICAL)
    Row(base, verticalAlignment = Alignment.CenterVertically) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier.size(ROW_ICON_SIZE),
            )
            Spacer(Modifier.width(ROW_ICON_GAP))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(TRAILING_GAP))
            Box(contentAlignment = Alignment.Center) { trailing() }
        }
    }
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
 * Thin divider between adjacent rows inside a [PreferenceSection]. Sits at
 * the row's start-icon indent so the leading rule aligns with the title
 * column and the icon strip stays visually clean.
 */
@Composable
fun PreferenceDivider(hasIcon: Boolean = true) {
    val startInset =
        if (hasIcon) ROW_HORIZONTAL + ROW_ICON_SIZE + ROW_ICON_GAP else ROW_HORIZONTAL
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startInset, end = ROW_HORIZONTAL)
            .height(DIVIDER_HEIGHT)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA)),
    )
}

private const val DISABLED_ALPHA = 0.38f
private const val DIVIDER_ALPHA = 0.5f
private val DIVIDER_HEIGHT: Dp = 1.dp
