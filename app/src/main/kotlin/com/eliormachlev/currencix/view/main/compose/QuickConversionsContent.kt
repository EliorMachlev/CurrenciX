package com.eliormachlev.currencix.view.main.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.eliormachlev.currencix.view.compose.CurrencyPairRow
import com.eliormachlev.currencix.view.compose.FlagCode
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
    // "+ 1% = USD $ 0.3281", after the result; null without a fee.
    val costWithFeeText: String?,
    // The amount the row converts, for a tap to put into the converter.
    val amount: BigDecimal = BigDecimal.ZERO,
    // The converter's own amount, set apart from the round ones.
    val isTyped: Boolean = false,
    // The table's cells: the bare numbers, "1" / "0.32" / "0.33", under
    // headers naming the currencies; the texts above are for copy and share.
    val fromValue: String = "",
    val toValue: String = "",
    val withFeeValue: String? = null,
    // "+ 1%", the fee column's header; null without a fee.
    val feePercent: String? = null,
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
                // The converter's pills and swap button: the same pair, the same look.
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
            val feePercent = table.rows.firstNotNullOfOrNull { it.feePercent }
            TableHeader(table.from, table.to, feePercent)
            HorizontalDivider(Modifier.padding(horizontal = Spacing.Small), color = MaterialTheme.colorScheme.outlineVariant)
            table.rows.forEach { row -> QuickConversionsRowUi(row, actions, hasFees = feePercent != null) }
        }
        table.sourceText?.let { SourceLine(it, MaterialTheme.colorScheme.onSurfaceVariant) }
        table.warningText?.let { SourceLine(it, MaterialTheme.colorScheme.error) }
    }
}

// The table's numbers: same-width digits, so the decimals line up down a column.
@Composable
private fun tableNumberStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TABULAR_FIGURES)

// "🇨🇦 CAD | 🇮🇱 ILS | + 1%": what each column is in, so its cells are just numbers.
@Composable
private fun TableHeader(
    from: Currency?,
    to: Currency?,
    feePercent: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell(Modifier.weight(1f)) { from?.let { FlagCode(it) } }
        HeaderCell(Modifier.weight(1f)) { to?.let { FlagCode(it) } }
        if (feePercent != null) {
            HeaderCell(Modifier.weight(1f)) {
                Text(feePercent, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// A header cell, its content at the end, over the right-aligned numbers.
@Composable
private fun HeaderCell(
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.XSmall, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
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

// One "amount = result" row: a tap puts the amount into the converter, a
// long-press copies the line. The typed amount's row is tinted and bold.
@Composable
private fun QuickConversionsRowUi(
    row: QuickConversionsRow,
    actions: QuickConversionsActions,
    hasFees: Boolean,
) {
    val style = tableNumberStyle().copy(fontWeight = if (row.isTyped) FontWeight.Bold else null)
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
                // TalkBack hears the whole line, "CAD $ 1 = ILS ₪ 2.18 …", as
                // one item, not three bare numbers.
                .clearAndSetSemantics { contentDescription = row.copyText },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.fromValue, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(row.toValue, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        if (hasFees) {
            Text(
                text = row.withFeeValue.orEmpty(),
                style = style,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
