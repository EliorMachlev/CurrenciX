package com.eliormachlev.currencix.view.preference.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.ProviderReliability
import com.eliormachlev.currencix.model.UpdateCadence
import com.eliormachlev.currencix.util.DISABLED_ROW_ALPHA
import com.eliormachlev.currencix.view.compose.LedgerActiveChip
import com.eliormachlev.currencix.view.compose.LedgerRow
import com.eliormachlev.currencix.view.compose.LedgerSectionHeader
import com.eliormachlev.currencix.view.compose.SupportingText
import com.eliormachlev.currencix.view.compose.activeChipSlot
import com.eliormachlev.currencix.view.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.dialogs.SheetListEnd
import com.eliormachlev.currencix.view.compose.rememberDatabase

/**
 * Data-provider picker — a [LedgerBottomSheet] listing every [ApiProvider] in
 * [ApiProvider.pickerOrder]: free providers, then the ones that need an API
 * key, each under its own heading and from the most to the least frequently
 * updated. The [selected] provider trails a [LedgerActiveChip]; tapping a row
 * commits it and dismisses.
 *
 * [unavailable] is shown greyed out and can't be picked — the main provider,
 * when this picks the fallback (a provider can't stand in for itself).
 *
 * Unreliable providers are listed only when [showUnreliable] (the user's
 * "Show unreliable providers" setting), each labelled with why — but the
 * [selected] and [unavailable] ones always are, so a choice never vanishes.
 */
@Composable
fun ProviderPickerSheet(
    selected: ApiProvider?,
    onDismiss: () -> Unit,
    onPicked: (ApiProvider) -> Unit,
    title: String = stringResource(id = R.string.api_title),
    unavailable: ApiProvider? = null,
    showUnreliable: Boolean = rememberShowUnreliableProviders(),
) {
    val listed =
        remember(showUnreliable, selected, unavailable) {
            ApiProvider.listed(showUnreliable, keep = setOfNotNull(selected, unavailable))
        }
    LedgerBottomSheet(title = title, onDismiss = onDismiss) {
        listed.groupBy { it.needsApiKey }.forEach { (needsKey, providers) ->
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
        SheetListEnd()
    }
}

// The user's "Show unreliable providers" setting, followed while the sheet is up.
@Composable
private fun rememberShowUnreliableProviders(): Boolean {
    val enabled by rememberDatabase().providers.showUnreliable.collectAsStateWithLifecycle()
    return enabled
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
        value = chip?.let { activeChipSlot(isActive = true, chipRes = it) },
    )
}

/**
 * Label slot for the provider picker row — name (title), short description,
 * how often it updates, why it's unreliable if it is, and an optional hint (e.g. "requires API key").
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
        SupportingText(provider.getDescriptionShort(context).toString())
        SupportingText(stringResource(provider.cadence.labelRes))
        ReliabilityLabel(provider.reliability)
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

/** Why a provider is unreliable ("Downtime" / "Inaccurate data"), in the error color; nothing for a reliable one. */
@Composable
internal fun ReliabilityLabel(reliability: ProviderReliability) {
    val label = reliability.labelRes ?: return
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@get:StringRes
private val ProviderReliability.labelRes: Int?
    get() =
        when (this) {
            ProviderReliability.RELIABLE -> null
            ProviderReliability.DOWNTIME -> R.string.provider_reliability_downtime
            ProviderReliability.INACCURATE_DATA -> R.string.provider_reliability_inaccurate
        }
