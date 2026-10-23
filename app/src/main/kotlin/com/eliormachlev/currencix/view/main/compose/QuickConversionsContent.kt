package com.eliormachlev.currencix.view.main.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.hapticCombinedClickable
import com.eliormachlev.currencix.util.money
import com.eliormachlev.currencix.view.compose.CurrencyPairRow
import com.eliormachlev.currencix.view.compose.LayerCapture
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.PairRowActions
import com.eliormachlev.currencix.view.compose.captureInto
import com.eliormachlev.currencix.view.compose.theme.Spacing
import java.math.BigDecimal

// Between a result and its cost with the fee, in copied and shared text.
private const val FEE_GAP = "   "

// OpenType tabular figures: every digit the same width.
private const val TABULAR_FIGURES = "tnum"

data class QuickConversionsRow(
    val amountFromText: String,
    val amountToText: String,
    // "+1% = USD $ 0.3281", after the result; null without a fee.
    val costWithFeeText: String?,
    // The amount the row converts, for a tap to put into the converter.
    val amount: BigDecimal = BigDecimal.ZERO,
    // The converter's own amount, set apart from the round ones.
    val isTyped: Boolean = false,
    // The tile's numbers, "1" / "0.32" / "0.33", which it shows with the
    // currencies' symbols; the texts above are for copy and share.
    val fromValue: String = "",
    val toValue: String = "",
    val withFeeValue: String? = null,
    // "+1%", the fee's rate, beside the result; null without a fee.
    val feePercent: String? = null,
) {
    /** "ILS ₪ 1 = USD $ 0.32   +1% = USD $ 0.33", what a long-press copies. */
    val copyText: String
        get() = listOfNotNull("$amountFromText = $amountToText", costWithFeeText).joinToString(FEE_GAP)
}

/** The pair's conversions at a spread of round amounts. */
@Immutable
data class QuickConversionsTable(
    val from: Currency?,
    val to: Currency?,
    val rows: List<QuickConversionsRow>,
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
    // A currency pill's tap: pick that side's currency.
    val onFromClick: () -> Unit = {},
    val onToClick: () -> Unit = {},
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
                Column(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                ) {
                    QuickConversionsTableBody(table, emptyText, actions, capture) {
                        // The converter's pills and swap button: the same pair, the
                        // same look, and the table's column headers.
                        CurrencyPairRow(
                            from = table.from,
                            to = table.to,
                            actions =
                                remember(
                                    actions,
                                ) { PairRowActions(actions.onFromClick, actions.onToClick, actions.onSwap, actions.onSwapLongPress) },
                            // Over the converter, whose own pills hold the shared-element keys.
                            sharedElement = false,
                        )
                    }
                }
            }
        }
        // In the reading direction, unlike the table above it.
        actions.onShare?.takeIf { table.rows.isNotEmpty() }?.let { share -> ShareTableButton(share) }
    }
}

// The pair (the columns' headers), the rows under it and where their rates
// come from: all of it on the sheet's own background, so a shared snapshot
// reads like the sheet, pills included.
@Composable
private fun QuickConversionsTableBody(
    table: QuickConversionsTable,
    emptyText: String,
    actions: QuickConversionsActions,
    capture: LayerCapture?,
    pair: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .captureInto(capture)
                .background(MaterialTheme.colorScheme.surface)
                .padding(bottom = Spacing.Small),
    ) {
        pair()
        Spacer(Modifier.height(Spacing.Large))
        HorizontalDivider()
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
            QuickConversionsTiles(table, actions)
        }
        table.sourceText?.let { SourceLine(it, MaterialTheme.colorScheme.onSurfaceVariant) }
        table.warningText?.let { SourceLine(it, MaterialTheme.colorScheme.error) }
    }
}

// The tiles' amounts: same-width digits, so they don't jitter as they change.
@Composable
private fun tableNumberStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TABULAR_FIGURES)

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

// The conversions as tiles, two to a line, in the pills' order: the amount,
// then the result as the converter shows it, the total with the fee (when
// one is in force) as the big number, with the result and the fee above it.
@Composable
private fun QuickConversionsTiles(
    table: QuickConversionsTable,
    actions: QuickConversionsActions,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        table.rows.chunked(TILES_PER_LINE).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                line.forEach { row -> QuickConversionTile(row, table.from, table.to, actions, Modifier.weight(1f)) }
                repeat(TILES_PER_LINE - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// One conversion: "$ 1", then "3.09 ₪ +1%" and the big "3.12 ₪" (each
// symbol on the side the language puts it), as the
// converter's receipt has it (without a fee, just the big result). A tap puts
// the amount into the converter, a long-press copies the line; the typed
// amount's tile is tinted and bold.
@Composable
private fun QuickConversionTile(
    row: QuickConversionsRow,
    from: Currency?,
    to: Currency?,
    actions: QuickConversionsActions,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val numbers = tableNumberStyle()
    val weight = if (row.isTyped) FontWeight.Bold else null
    val colors = MaterialTheme.colorScheme
    val container = if (row.isTyped) colors.secondaryContainer else colors.surfaceContainerHigh
    // On the tinted tile, its own text color for the secondary lines: the
    // muted gray and the fee's red fall under 4.5:1 on brass in dark themes.
    val muted = if (row.isTyped) colors.onSecondaryContainer else colors.onSurfaceVariant
    val feeColor = if (row.isTyped) colors.onSecondaryContainer else colors.error
    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.large)
                .background(container)
                .hapticCombinedClickable(
                    onClick = { actions.onUseAmount(row.amount) },
                    onClickLabel = stringResource(R.string.quick_conversions_use_amount),
                    onLongClick = { actions.onCopy(row) },
                    onLongClickLabel = stringResource(R.string.quick_conversions_copy),
                ).padding(Spacing.Medium)
                // TalkBack hears the whole line, "CAD $ 1 = ILS ₪ 2.18 …", as one item.
                .clearAndSetSemantics { contentDescription = row.copyText },
    ) {
        Text(context.money(row.fromValue, from), style = numbers.copy(fontWeight = weight), color = muted, maxLines = 1)
        row.withFeeValue?.let {
            Row {
                Text(context.money(row.toValue, to), style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 1)
                row.feePercent?.let { fee ->
                    Text(" $fee", style = MaterialTheme.typography.bodyMedium, color = feeColor, maxLines = 1)
                }
            }
        }
        val total = MaterialTheme.typography.headlineSmall
        Text(
            context.money(row.withFeeValue ?: row.toValue, to),
            style = total.copy(fontFeatureSettings = TABULAR_FIGURES, fontWeight = weight),
            maxLines = 1,
            // A big amount in a long currency shrinks to fit the tile.
            autoSize = TextAutoSize.StepBased(minFontSize = MaterialTheme.typography.bodyLarge.fontSize, maxFontSize = total.fontSize),
        )
    }
}
