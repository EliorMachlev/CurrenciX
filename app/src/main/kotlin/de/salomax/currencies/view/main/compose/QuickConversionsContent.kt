package de.salomax.currencies.view.main.compose

import android.content.Context
import android.widget.ImageView
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.FeeSide

private const val FLAG_SIZE_DP = 28
private const val FEE_INFO_ALPHA = 0.7f
private const val EQ_ALPHA = 0.5f
private const val ROW_LABEL_ALPHA = 0.7f
private const val ICON_BUTTON_SIZE_DP = 40

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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .alpha(FEE_INFO_ALPHA),
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

@OptIn(ExperimentalFoundationApi::class)
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
        Box(
            modifier =
                Modifier
                    .size(ICON_BUTTON_SIZE_DP.dp)
                    .padding(horizontal = dimensionResource(id = R.dimen.margin1x))
                    .combinedClickable(
                        onClick = onSwap,
                        onLongClick = onSwapLongPress,
                    ),
            contentAlignment = Alignment.Center,
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
            Box(
                modifier =
                    Modifier
                        .size(ICON_BUTTON_SIZE_DP.dp)
                        .combinedClickable(
                            onClick = onToggleFeeSide,
                            onLongClick = onFeeSideLongPress,
                        ),
                contentAlignment = Alignment.Center,
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

@Composable
private fun CurrencyBadge(currency: Currency?) {
    if (currency == null) {
        Spacer(Modifier.size(FLAG_SIZE_DP.dp))
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AndroidView(
            factory = { ctx: Context ->
                ImageView(ctx).apply {
                    adjustViewBounds = true
                    contentDescription = null
                }
            },
            update = { iv ->
                iv.setImageDrawable(currency.flag(iv.context))
            },
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
                .padding(vertical = dimensionResource(id = R.dimen.margin1x)),
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
                    modifier = Modifier.alpha(ROW_LABEL_ALPHA),
                )
            }
        }
        Box(modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.margin2x))) {
            Text(
                text = "=",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Unspecified,
                modifier = Modifier.alpha(EQ_ALPHA),
            )
        }
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
                    modifier = Modifier.alpha(ROW_LABEL_ALPHA),
                )
            }
        }
    }
}
