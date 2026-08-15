package com.eliormachlev.currencix.view.main.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.FeeSide
import com.eliormachlev.currencix.view.compose.CurrencyFlagImage
import com.eliormachlev.currencix.view.compose.Ltr

private const val FLAG_SIZE_DP = 28
private const val EQ_ALPHA = 0.5f

// Bumped from 40dp to hit WCAG 2.1 AA "target size" (48×48). Applies to the
// header swap and fee-side toggle — the two icon-only actions in this dialog.
private const val ICON_BUTTON_SIZE_DP = 48

data class QuickConversionsRow(
    val amountFromText: String,
    val amountToText: String,
    val trueCostText: String?,
    val originalValueText: String?,
)

@Composable
fun QuickConversionsContent(
    from: Currency?,
    to: Currency?,
    feeSide: FeeSide,
    showFeeSideButton: Boolean,
    feeInfoText: String?,
    rows: List<QuickConversionsRow>,
    emptyText: String,
    onSwap: () -> Unit,
    onSwapLongPress: () -> Unit,
    onToggleFeeSide: () -> Unit,
    onFeeSideLongPress: () -> Unit,
) {
    val padH = dimensionResource(id = R.dimen.margin2x)
    val padT = dimensionResource(id = R.dimen.margin2x)
    // Math reads left-to-right in every locale. Force LTR for the whole
    // dialog so "from | swap | to" and "amount = amount" rows don't mirror
    // under RTL layout.
    Ltr {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = padH, end = padH, top = padT),
        ) {
            QuickConversionsHeader(
                from = from,
                to = to,
                feeSide = feeSide,
                showFeeSideButton = showFeeSideButton,
                onSwap = onSwap,
                onSwapLongPress = onSwapLongPress,
                onToggleFeeSide = onToggleFeeSide,
                onFeeSideLongPress = onFeeSideLongPress,
            )
            if (feeInfoText != null) {
                Spacer(Modifier.height(dimensionResource(id = R.dimen.margin1x)))
                Text(
                    text = feeInfoText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(dimensionResource(id = R.dimen.margin2x)))
            HorizontalDivider()
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = dimensionResource(id = R.dimen.margin1x)),
            ) {
                if (rows.isEmpty()) {
                    Text(
                        text = emptyText,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = dimensionResource(id = R.dimen.margin2x)),
                    )
                } else {
                    rows.forEach { row ->
                        QuickConversionsRowUi(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickConversionsHeader(
    from: Currency?,
    to: Currency?,
    feeSide: FeeSide,
    showFeeSideButton: Boolean,
    onSwap: () -> Unit,
    onSwapLongPress: () -> Unit,
    onToggleFeeSide: () -> Unit,
    onFeeSideLongPress: () -> Unit,
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
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = stringResource(id = R.string.desc_toggle_currencies),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (showFeeSideButton) {
            val iconRes =
                if (feeSide == FeeSide.CONVERTED) {
                    R.drawable.ic_fee_side_converted_horizontal
                } else {
                    R.drawable.ic_fee_side_original_horizontal
                }
            LongPressIconButton(
                onClick = onToggleFeeSide,
                onLongClick = onFeeSideLongPress,
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = stringResource(id = R.string.fee_side_label),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
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

// Shared shape for the header's swap and fee-side icon buttons: a 48dp
// clickable region (WCAG 2.1 AA target size) with long-press support. Keeps
// the .size and .combinedClickable chained together so no intermediate
// padding can shrink the click target below 48dp.
@OptIn(ExperimentalFoundationApi::class)
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
                .combinedClickable(
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
        Spacer(Modifier.size(dimensionResource(id = R.dimen.margin1x)))
        Text(
            text = currency.iso4217Alpha(),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun QuickConversionsRowUi(row: QuickConversionsRow) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = dimensionResource(id = R.dimen.margin1x))
                // Merge so TalkBack reads "$FROM equals $TO" as one item rather
                // than swiping through five separate text nodes per row.
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = row.amountFromText,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (row.trueCostText != null) {
                Text(
                    text = row.trueCostText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(
            text = "=",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Unspecified,
            modifier =
                Modifier
                    .padding(horizontal = dimensionResource(id = R.dimen.margin2x))
                    .alpha(EQ_ALPHA),
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = row.amountToText,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (row.originalValueText != null) {
                Text(
                    text = row.originalValueText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
