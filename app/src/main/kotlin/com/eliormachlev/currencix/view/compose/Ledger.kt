package com.eliormachlev.currencix.view.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.view.compose.theme.Brass

// Ledger vocabulary — the shared row/section primitives every list surface
// (Preferences, Fees, Backup, drawer, picker) leans on so the "paper + brass"
// aesthetic reads the same everywhere.
//
// - Rows sit directly on the paper background — no card elevation, no rounded
//   surface behind the row.
// - Label leads, value trails, single baseline.
// - Hairline rule renders inside the row's last pixel column via drawWithContent
//   rather than as a sibling composable; strandable LazyColumn dividers were
//   the visual bug that killed the previous grouped-card look.
// - Section headers use brass small-caps, left-aligned.
// - The "active value" trail uses a brass-outlined chip (see [LedgerActiveChip]).

// Padding + type metrics for the ledger row.
private val LEDGER_ROW_HORIZONTAL: Dp = 20.dp
private val LEDGER_ROW_VERTICAL: Dp = 14.dp
private val LEDGER_ROW_ICON_SIZE: Dp = 24.dp
private val LEDGER_ROW_ICON_GAP: Dp = 20.dp
private val LEDGER_TRAILING_GAP: Dp = 12.dp
private val LEDGER_SECTION_HEADER_HORIZONTAL: Dp = 20.dp
private val LEDGER_SECTION_HEADER_TOP: Dp = 20.dp
private val LEDGER_SECTION_HEADER_BOTTOM: Dp = 6.dp
private val LEDGER_HAIRLINE_HEIGHT: Dp = 1.dp
private const val LEDGER_HAIRLINE_ALPHA = 0.5f
private const val LEDGER_DISABLED_ALPHA = 0.38f

// Small-caps flavour applied to the brass section header. Kept modest so the
// header still reads at a glance without shouting; the wide letter-spacing
// carries the "engraved on paper" cue instead.
private val LEDGER_SECTION_HEADER_LETTER_SPACING = 0.14.em

private val LEDGER_CHIP_HORIZONTAL_PADDING: Dp = 10.dp
private val LEDGER_CHIP_VERTICAL_PADDING: Dp = 3.dp
private val LEDGER_CHIP_CORNER_RADIUS: Dp = 4.dp
private val LEDGER_CHIP_BORDER_WIDTH: Dp = 1.dp
private val LEDGER_CHIP_LETTER_SPACING = 0.06.em
private const val LEDGER_CHIP_BG_ALPHA = 0.12f

/**
 * The ledger row primitive — a single label/value line drawn directly on the
 * paper background. A hairline rule paints inside the row's last pixel via
 * [drawWithContent] so LazyColumn recycling can't strand a sibling divider.
 *
 * Callers supply [label] and [value] as `RowScope` slots so cross-axis
 * alignment is uniform with the ambient row; the row's outer horizontal
 * padding is baked in, but content is otherwise free to render whatever
 * text / chip / switch it needs.
 */
@Composable
fun LedgerRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    label: @Composable RowScope.() -> Unit,
    value: (@Composable RowScope.() -> Unit)? = null,
) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = LEDGER_HAIRLINE_ALPHA)
    val strokePx = with(LocalDensity.current) { LEDGER_HAIRLINE_HEIGHT.toPx() }
    val base =
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.hapticClickable(enabled = enabled, onClick = onClick) else it }
            .drawWithContent {
                drawContent()
                if (showDivider) {
                    drawLedgerHairline(color = outlineColor, strokePx = strokePx)
                }
            }.padding(horizontal = LEDGER_ROW_HORIZONTAL, vertical = LEDGER_ROW_VERTICAL)
    Row(base, verticalAlignment = Alignment.CenterVertically) {
        label()
        if (value != null) {
            Spacer(Modifier.width(LEDGER_TRAILING_GAP))
            value()
        }
    }
}

// Extracted so the drawWithContent lambda inside [LedgerRow] stays a single
// expression — keeps the row builder easy to read at a glance.
private fun ContentDrawScope.drawLedgerHairline(
    color: Color,
    strokePx: Float,
) {
    val y = size.height - strokePx
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = strokePx,
    )
}

/**
 * Section header + child rows. The header renders in brass small-caps,
 * left-aligned above the ledger rows; children stack on the shared paper
 * background with no surrounding surface — the hairline under each row is
 * what separates entries.
 */
@Composable
fun LedgerSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Brass,
            letterSpacing = LEDGER_SECTION_HEADER_LETTER_SPACING,
            modifier =
                Modifier
                    .padding(
                        start = LEDGER_SECTION_HEADER_HORIZONTAL,
                        end = LEDGER_SECTION_HEADER_HORIZONTAL,
                        top = LEDGER_SECTION_HEADER_TOP,
                        bottom = LEDGER_SECTION_HEADER_BOTTOM,
                    ).semantics { heading() },
        )
        content()
    }
}

/**
 * Convenience "label with leading icon + stacked title/summary" — the shape
 * most preference rows want. Kept as a companion to [LedgerRow] so callers
 * can compose the primitive directly when they need something bespoke (e.g.
 * a pair row with flags), while the common case stays one call.
 */
@Composable
fun LedgerLabel(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconRes: Int? = null,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else LEDGER_DISABLED_ALPHA
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier.size(LEDGER_ROW_ICON_SIZE),
            )
            Spacer(Modifier.width(LEDGER_ROW_ICON_GAP))
        }
        Column {
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
                    maxLines = MAX_SUMMARY_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val MAX_SUMMARY_LINES = 3

/**
 * Small brass-outlined chip — mirrors the hero card's stamp accent so the
 * currently-active value on a row (e.g. selected theme, selected provider)
 * reads as the same "ink on paper" affordance. Text renders in brass
 * monospace uppercase.
 */
@Composable
fun LedgerActiveChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    val bg = Brass.copy(alpha = LEDGER_CHIP_BG_ALPHA)
    Row(
        modifier =
            modifier
                .border(
                    width = LEDGER_CHIP_BORDER_WIDTH,
                    color = Brass,
                    shape = RoundedCornerShape(LEDGER_CHIP_CORNER_RADIUS),
                ).background(bg, shape = RoundedCornerShape(LEDGER_CHIP_CORNER_RADIUS))
                .padding(
                    horizontal = LEDGER_CHIP_HORIZONTAL_PADDING,
                    vertical = LEDGER_CHIP_VERTICAL_PADDING,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            letterSpacing = LEDGER_CHIP_LETTER_SPACING,
            color = Brass,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Slot wrapper — takes the value slot of a [LedgerRow] and centers the
 * caller's content in a [Box]. Kept as a companion so a row that wraps a
 * `Switch` or a [LedgerActiveChip] doesn't have to re-implement the same
 * `Box(contentAlignment = Center)` shim.
 */
@Composable
fun LedgerTrailing(content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center) { content() }
}

/**
 * Paints the ledger's paper hairline along the bottom edge of the modified
 * node — the same visual [LedgerRow] uses internally, offered as a Modifier
 * for bespoke rows (currency picker, drawer variants) that can't route
 * through [LedgerRow] because they need custom layout inside the row body.
 * Rendering inside the node (rather than emitting a sibling composable)
 * avoids the LazyColumn recycle-strand bug that killed the previous grouped-
 * card look.
 */
@Composable
fun Modifier.ledgerHairline(): Modifier {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = LEDGER_HAIRLINE_ALPHA)
    val strokePx = with(LocalDensity.current) { LEDGER_HAIRLINE_HEIGHT.toPx() }
    return this.drawWithContent {
        drawContent()
        drawLedgerHairline(color = outlineColor, strokePx = strokePx)
    }
}
