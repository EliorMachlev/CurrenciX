package com.eliormachlev.currencix.view.main.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.hapticCombinedClickable
import com.eliormachlev.currencix.view.compose.CurrencyFlagImage
import com.eliormachlev.currencix.view.compose.LayerCapture
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.captureInto
import com.eliormachlev.currencix.view.compose.theme.Spacing
import java.math.BigDecimal

private const val FLAG_SIZE_DP = 28
private const val EQ_ALPHA = 0.5f

// Between a result and its cost with the fee, in copied and shared text.
private const val FEE_GAP = "   "

private const val NO_BREAK_SPACE = '\u00A0'

private fun String.unbroken(): String = replace(' ', NO_BREAK_SPACE)

// Bumped from 40dp to hit WCAG 2.1 AA "target size" (48×48). Applies to the
// header swap button — the only icon-only action in this dialog.
private const val ICON_BUTTON_SIZE_DP = 48

data class QuickConversionsRow(
    val amountFromText: String,
    val amountToText: String,
    // "+ 1% = USD $ 0.3281", after the result; null without a fee.
    val costWithFeeText: String?,
    // The amount the row converts, for a tap to put into the converter.
    val amount: BigDecimal = BigDecimal.ZERO,
    // The converter's own amount, set apart from the round ones.
    val isTyped: Boolean = false,
) {
    /** "ILS ₪ 1 = USD $ 0.3249  + 1% = USD $ 0.3281", what a long-press copies. */
    val copyText: String
        get() = listOfNotNull("$amountFromText = $amountToText", costWithFeeText).joinToString(FEE_GAP)
}

/** The pair's conversions at a spread of round amounts. */
@Immutable
data class QuickConversionsTable(
    val from: Currency?,
    val to: Currency?,
    val rows: List<QuickConversionsRow>,
    // "Fees applied: +2.5%"; null when no fee is in force.
    val feeInfoText: String? = null,
    // "Based on Frankfurter.app, 12/10/26 16:00": where the rates come from.
    val sourceText: String? = null,
    // Why they may not be today's: offline, the fallback, a past date.
    val warningText: String? = null,
)

/** What the sheet's taps do. */
@Immutable
class QuickConversionsActions(
    val onSwap: () -> Unit,
    val onSwapLongPress: () -> Unit,
    // A row's tap: put its amount into the converter.
    val onUseAmount: (BigDecimal) -> Unit = {},
    // A row's long-press: copy "100 USD = 92.00 EUR".
    val onCopy: (QuickConversionsRow) -> Unit = {},
    // Share the table; null hides the button.
    val onShare: (() -> Unit)? = null,
)

@Composable
fun QuickConversionsContent(
    table: QuickConversionsTable,
    emptyText: String,
    actions: QuickConversionsActions,
    // Above the pair: the recent pairs, to switch without closing.
    top: @Composable () -> Unit = {},
    // What Share snapshots: the rows and where their rates come from.
    capture: LayerCapture? = null,
) {
    val padH = Spacing.Large
    val padT = Spacing.Large
    Column(modifier = Modifier.fillMaxWidth()) {
        top()
        // Math reads left-to-right in every locale. Force LTR for the table
        // so "from | swap | to" and "amount = amount" rows don't mirror
        // under RTL layout.
        Ltr {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = padH, end = padH, top = padT),
            ) {
                QuickConversionsHeader(
                    from = table.from,
                    to = table.to,
                    onSwap = actions.onSwap,
                    onSwapLongPress = actions.onSwapLongPress,
                )
                table.feeInfoText?.let { feeInfoText ->
                    Spacer(Modifier.height(Spacing.Small))
                    Text(
                        text = feeInfoText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(Spacing.Large))
                HorizontalDivider()
                Column(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                ) {
                    QuickConversionsTableBody(table, emptyText, actions, capture)
                }
            }
        }
        // In the reading direction, unlike the table above it.
        actions.onShare?.takeIf { table.rows.isNotEmpty() }?.let { share -> ShareTableButton(share) }
    }
}

// The rows and, under them, where their rates come from: all of it on the
// sheet's own background, so a shared snapshot reads like the sheet.
@Composable
private fun QuickConversionsTableBody(
    table: QuickConversionsTable,
    emptyText: String,
    actions: QuickConversionsActions,
    capture: LayerCapture?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .captureInto(capture)
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = Spacing.Small),
    ) {
        if (table.rows.isEmpty()) {
            Text(
                text = emptyText,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.Large),
            )
        } else {
            val hasFees = table.rows.any { it.costWithFeeText != null }
            val rowStyle = if (hasFees) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
            val columns = rememberFeeColumns(table.rows, rowStyle, hasFees)
            table.rows.forEach { row -> QuickConversionsRowUi(row, actions, rowStyle, columns) }
        }
        table.sourceText?.let { SourceLine(it, MaterialTheme.colorScheme.onSurfaceVariant) }
        table.warningText?.let { SourceLine(it, MaterialTheme.colorScheme.error) }
    }
}

/** With fees, the amounts' and results' column widths, so every fee starts at the same place. */
private class FeeColumns(
    val from: Dp,
    val to: Dp,
)

// With fees, each column is as wide as its longest entry (in bold, as the
// typed row is), leaving the rest of the row to the fees; null without fees,
// when both sides share the row evenly.
@Composable
private fun rememberFeeColumns(
    rows: List<QuickConversionsRow>,
    style: TextStyle,
    hasFees: Boolean,
): FeeColumns? {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    if (!hasFees) return null
    return remember(rows, style, density) {
        val bold = style.copy(fontWeight = FontWeight.Bold)

        fun widest(text: (QuickConversionsRow) -> String) =
            with(density) { rows.maxOf { measurer.measure(text(it).unbroken(), bold).size.width }.toDp() }
        FeeColumns(from = widest { it.amountFromText }, to = widest { it.amountToText })
    }
}

@Composable
private fun SourceLine(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.Small),
    )
}

@Composable
private fun ShareTableButton(onShare: () -> Unit) {
    TextButton(
        onClick = onShare,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Large, vertical = Spacing.Small),
    ) {
        Icon(painter = painterResource(R.drawable.ic_share), contentDescription = null)
        Spacer(Modifier.size(Spacing.Small))
        Text(stringResource(R.string.menu_share))
    }
}

@Composable
private fun QuickConversionsHeader(
    from: Currency?,
    to: Currency?,
    onSwap: () -> Unit,
    onSwapLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CurrencyBadge(currency = from)
        }
        LongPressIconButton(
            onClick = onSwap,
            onLongClick = onSwapLongPress,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_swap_horiz),
                contentDescription = stringResource(id = R.string.desc_toggle_currencies),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CurrencyBadge(currency = to)
        }
    }
}

// Shared shape for the header's swap icon button: a 48dp clickable region
// (WCAG 2.1 AA target size) with long-press support. Keeps the .size and
// .combinedClickable chained together so no intermediate padding can shrink
// the click target below 48dp.
@Composable
private fun LongPressIconButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(ICON_BUTTON_SIZE_DP.dp)
                .hapticCombinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun CurrencyBadge(currency: Currency?) {
    if (currency == null) {
        Spacer(Modifier.size(FLAG_SIZE_DP.dp))
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        CurrencyFlagImage(
            currency = currency,
            modifier = Modifier.size(FLAG_SIZE_DP.dp),
        )
        Spacer(Modifier.size(Spacing.Small))
        Text(
            text = currency.iso4217Alpha(),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

// One "amount = result" row: a tap puts the amount into the converter, a
// long-press copies the line. The typed amount's row is tinted and bold.
@Composable
private fun QuickConversionsRowUi(
    row: QuickConversionsRow,
    actions: QuickConversionsActions,
    // A notch smaller when the rows carry fees, so a whole row fits a line.
    rowStyle: TextStyle,
    // The columns' widths with fees; null shares the row evenly.
    columns: FeeColumns?,
) {
    val weight = if (row.isTyped) FontWeight.Bold else null
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .then(if (row.isTyped) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                .hapticCombinedClickable(
                    onClick = { actions.onUseAmount(row.amount) },
                    onClickLabel = stringResource(R.string.quick_conversions_use_amount),
                    onLongClick = { actions.onCopy(row) },
                    onLongClickLabel = stringResource(R.string.quick_conversions_copy),
                ).padding(horizontal = Spacing.Small, vertical = Spacing.Small)
                // Merge so TalkBack reads "$FROM equals $TO" as one item rather
                // than swiping through several separate text nodes per row.
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.amountFromText.unbroken(),
            style = rowStyle,
            fontWeight = weight,
            textAlign = TextAlign.End,
            modifier = if (columns != null) Modifier.width(columns.from) else Modifier.weight(1f),
        )
        Text(
            text = "=",
            style = rowStyle,
            color = Color.Unspecified,
            modifier =
                Modifier
                    .padding(horizontal = if (columns != null) Spacing.Small else Spacing.Large)
                    .alpha(EQ_ALPHA),
        )
        // "USD $ 0.3249   + 1% = USD $ 0.3281": the result, then its fee in
        // a column of its own; each keeps its words together.
        Text(
            text = row.amountToText.unbroken(),
            style = rowStyle,
            fontWeight = weight,
            modifier = if (columns != null) Modifier.width(columns.to) else Modifier.weight(1f),
        )
        if (columns != null) {
            Text(
                text = row.costWithFeeText?.unbroken().orEmpty(),
                style = rowStyle,
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .padding(start = Spacing.Medium)
                        .weight(1f),
            )
        }
    }
}
