package com.eliormachlev.currencix.view.preference.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.UpdateCadence
import com.eliormachlev.currencix.util.DISABLED_ROW_ALPHA
import com.eliormachlev.currencix.view.compose.LedgerActiveChip
import com.eliormachlev.currencix.view.compose.LedgerRow
import com.eliormachlev.currencix.view.compose.LedgerSectionHeader
import com.eliormachlev.currencix.view.compose.LedgerTrailing
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet

// Small breather between the last picker row and the system navigation bar so
// the final divider doesn't butt directly against the sheet edge.
private val PROVIDER_SHEET_BOTTOM_SPACE = 12.dp

/**
 * Data-provider picker — a [LedgerBottomSheet] listing every [ApiProvider] in
 * [ApiProvider.pickerOrder]: free providers, then the ones that need an API
 * key, each under its own heading and from the most to the least frequently
 * updated. The [selected] provider trails a [LedgerActiveChip]; tapping a row
 * commits it and dismisses.
 *
 * [unavailable] is shown greyed out and can't be picked — the main provider,
 * when this picks the fallback (a provider can't stand in for itself).
 */
@Composable
fun ProviderPickerSheet(
    selected: ApiProvider?,
    onDismiss: () -> Unit,
    onPicked: (ApiProvider) -> Unit,
    title: String = stringResource(id = R.string.api_title),
    unavailable: ApiProvider? = null,
) {
    LedgerBottomSheet(title = title, onDismiss = onDismiss) {
        ApiProvider.pickerOrder.groupBy { it.needsApiKey }.forEach { (needsKey, providers) ->
            LedgerSectionHeader(
                stringResource(if (needsKey) R.string.provider_group_api_key else R.string.provider_group_free),
            )
            providers.forEachIndexed { index, provider ->
                ProviderRow(
                    provider = provider,
                    chip =
                        when (provider) {
                            selected -> R.string.picker_active_chip
                            unavailable -> R.string.provider_main_chip
                            else -> null
                        },
                    enabled = provider != unavailable,
                    showDivider = index != providers.lastIndex,
                    onClick = {
                        onPicked(provider)
                        onDismiss()
                    },
                )
            }
        }
        Spacer(Modifier.height(PROVIDER_SHEET_BOTTOM_SPACE))
    }
}

// One provider: its label, and a trailing [chip] (Active, or Main when it's
// the provider this pick can't be).
@Composable
private fun ProviderRow(
    provider: ApiProvider,
    @StringRes chip: Int?,
    enabled: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    LedgerRow(
        modifier = if (enabled) Modifier else Modifier.alpha(DISABLED_ROW_ALPHA),
        onClick = onClick,
        enabled = enabled,
        showDivider = showDivider,
        label = { ProviderRowLabel(provider = provider) },
        value = chip?.let { res -> { LedgerTrailing { LedgerActiveChip(text = stringResource(id = res)) } } },
    )
}

/**
 * Label slot for the provider picker row — name (title), short description,
 * how often it updates, and an optional hint (e.g. "requires API key").
 * Extracted so the picker's row lambda stays readable and the label
 * composition can be reused if a future screen (e.g. an About-this-provider
 * surface) wants the same block.
 */
@Composable
private fun RowScope.ProviderRowLabel(provider: ApiProvider) {
    val context = LocalContext.current
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = provider.getName(context).toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = provider.getDescriptionShort(context).toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(provider.cadence.labelRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        provider.getHint(context)?.let { hint ->
            Text(
                text = hint.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** "Updates hourly" / "…every business day" / "…once a month". */
@get:StringRes
private val UpdateCadence.labelRes: Int
    get() =
        when (this) {
            UpdateCadence.HOURLY -> R.string.provider_cadence_hourly
            UpdateCadence.DAILY -> R.string.provider_cadence_daily
            UpdateCadence.MONTHLY -> R.string.provider_cadence_monthly
        }
