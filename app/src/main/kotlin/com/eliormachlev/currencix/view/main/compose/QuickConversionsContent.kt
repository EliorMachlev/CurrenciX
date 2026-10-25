package com.eliormachlev.currencix.view.main.compose

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.eliormachlev.currencix.view.compose.ReadingDirection
import com.eliormachlev.currencix.view.compose.captureInto
import com.eliormachlev.currencix.view.compose.theme.AmountFamily
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.view.compose.theme.TABULAR_FIGURES
import com.eliormachlev.currencix.view.compose.theme.tile
import java.math.BigDecimal

// Between a result and its cost with the fee, in copied and shared text.
private const val FEE_GAP = "   "

// OpenType tabular figures: every digit the same width.

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
    // The tiles' amounts in [from] (the typed one and the filler aside), and
    // whether they're the user's own rather than the automatic ones.
    val amounts: List<BigDecimal> = emptyList(),
    val customAmounts: Boolean = false,
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
    // From a row's long-press menu: copy "100 USD = 92.00 EUR".
    val onCopy: (QuickConversionsRow) -> Unit = {},
    // From the menu: change a tile's amount, or keep the typed one as a tile;
    // null leaves them out.
    val onChangeAmount: ((QuickConversionsRow) -> Unit)? = null,
    val onKeepAmount: ((QuickConversionsRow) -> Unit)? = null,
    // From the menu, with the user's own amounts in force: back to the automatic ones.
    val onResetAmounts: (() -> Unit)? = null,
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
                line.forEach { row ->
                    Box(Modifier.weight(1f)) {
                        var menuOpen by remember { mutableStateOf(false) }
                        QuickConversionTile(row, table.from, table.to, actions, onMenu = { menuOpen = true })
                        // Words: the menu follows the language again.
                        ReadingDirection {
                            QuickConversionMenu(
                                expanded = menuOpen,
                                row = row,
                                actions = actions,
                                canReset = table.customAmounts,
                                onDismiss = { menuOpen = false },
                            )
                        }
                    }
                }
                repeat(TILES_PER_LINE - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// One conversion: "$ 1", then "3.09 ₪ +1%" and the big "3.12 ₪" (each
// symbol on the side the language puts it), as the
// converter's receipt has it (without a fee, just the big result). A tap puts
// the amount into the converter, a long-press opens its menu ([onMenu]);
// the typed amount's tile is tinted and bold.
@Composable
private fun QuickConversionTile(
    row: QuickConversionsRow,
    from: Currency?,
    to: Currency?,
    actions: QuickConversionsActions,
    onMenu: () -> Unit,
) {
    val context = LocalContext.current
    val numbers = tableNumberStyle()
    val weight = if (row.isTyped) FontWeight.Bold else null
    val colors = MaterialTheme.colorScheme
    val container = if (row.isTyped) colors.secondaryContainer else colors.tile
    // On the tinted tile, its own text color for the secondary lines: the
    // muted gray and the fee's red fall under 4.5:1 on brass in dark themes.
    val muted = if (row.isTyped) colors.onSecondaryContainer else colors.onSurfaceVariant
    val feeColor = if (row.isTyped) colors.onSecondaryContainer else colors.error
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(container)
                .hapticCombinedClickable(
                    onClick = { actions.onUseAmount(row.amount) },
                    onClickLabel = stringResource(R.string.quick_conversions_use_amount),
                    onLongClick = onMenu,
                    onLongClickLabel = stringResource(R.string.quick_conversions_options),
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
            style = total.copy(fontFeatureSettings = TABULAR_FIGURES, fontWeight = weight, fontFamily = AmountFamily),
            maxLines = 1,
            // A big amount in a long currency shrinks to fit the tile.
            autoSize = TextAutoSize.StepBased(minFontSize = MaterialTheme.typography.bodyLarge.fontSize, maxFontSize = total.fontSize),
        )
    }
}

// A tile's long-press: copy it; change its amount (or keep the typed one as
// a tile); and, with the user's own amounts in force, go back to the
// automatic ones.
@Composable
private fun QuickConversionMenu(
    expanded: Boolean,
    row: QuickConversionsRow,
    actions: QuickConversionsActions,
    canReset: Boolean,
    onDismiss: () -> Unit,
) {
    val items =
        listOfNotNull(
            QuickMenuItem(R.string.quick_conversions_copy, R.drawable.ic_content_copy) { actions.onCopy(row) },
            if (row.isTyped) {
                actions.onKeepAmount?.let { keep -> QuickMenuItem(R.string.quick_amount_keep, R.drawable.ic_add) { keep(row) } }
            } else {
                actions.onChangeAmount?.let { change -> QuickMenuItem(R.string.quick_amount_change, R.drawable.ic_edit) { change(row) } }
            },
            actions.onResetAmounts?.takeIf { canReset }?.let { reset ->
                QuickMenuItem(R.string.quick_amount_reset, R.drawable.ic_refresh, reset)
            },
        )
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        items.forEach { item ->
            DropdownMenuItem(
                text = { Text(stringResource(item.label)) },
                leadingIcon = { Icon(painterResource(item.icon), contentDescription = null) },
                onClick = {
                    onDismiss()
                    item.action()
                },
            )
        }
    }
}

private class QuickMenuItem(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val action: () -> Unit,
)
