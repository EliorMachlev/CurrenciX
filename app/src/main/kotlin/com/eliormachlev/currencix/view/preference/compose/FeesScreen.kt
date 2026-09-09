package com.eliormachlev.currencix.view.preference.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.viewmodel.preference.FeeManagerViewModel
import com.eliormachlev.currencix.view.compose.AppTheme as AppComposeTheme

// Sections match the three fee categories from the old
// PreferenceFragmentCompat-backed FeeManagerFragment so muscle memory carries
// over: two globals up top, specific pairs at the bottom.
private enum class FeeSection {
    GLOBAL_EXCHANGE,
    GLOBAL_BANK,
    SPECIFIC_PAIR,
}

// Which global fee category a picker/editor dialog is currently addressing.
// Threaded through the dialog state so a single [FeeEditorDialog] instance can
// serve both categories without callers having to `when` on the fee kind.
internal enum class GlobalFeeKind(
    @StringRes val titleRes: Int,
) {
    EXCHANGE(R.string.fee_section_global_exchange),
    BANK(R.string.fee_section_global_bank),
}

/**
 * Target of the currently-open editor dialog: which category, and whether we
 * are editing an existing entry or creating a new one. `null` for [existing]
 * = new draft, so the dialog opens with defaults + no delete button.
 */
internal data class EditorTarget(
    val kind: EditorKind,
    val existing: Fee? = null,
)

internal sealed interface EditorKind {
    data class Global(
        val globalKind: GlobalFeeKind,
    ) : EditorKind

    data object Pair : EditorKind
}

/**
 * Full fee-manager screen — Compose replacement for the old
 * PreferenceFragmentCompat-backed FeeManagerFragment. Observes fees via the
 * [viewModel]; opens per-category picker/editor dialogs via local state so a
 * single [FeeEditorDialog] instance is reused across categories. Currency
 * selection routes back through [onPickCurrency] because the shared
 * [com.eliormachlev.currencix.view.main.spinner.SearchableSpinnerDialog] is a
 * DialogFragment and needs the hosting fragment's FragmentManager.
 */
@Composable
fun FeesScreen(
    viewModel: FeeManagerViewModel,
    onPickCurrency: (disabled: Currency?, onPicked: (String) -> Unit) -> Unit,
) {
    val fees by viewModel.getFees().observeAsState(emptyList())
    val activeExchangeId by viewModel.getActiveExchangeId().observeAsState()
    val activeBankId by viewModel.getActiveBankId().observeAsState()

    val globalExchange = remember(fees) { fees.filterIsInstance<Fee.GlobalExchange>() }
    val globalBank = remember(fees) { fees.filterIsInstance<Fee.GlobalBank>() }
    val specificPair = remember(fees) { fees.filterIsInstance<Fee.SpecificPair>() }

    var openPicker by remember { mutableStateOf<GlobalFeeKind?>(null) }
    var openEditor by remember { mutableStateOf<EditorTarget?>(null) }

    AppComposeTheme {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = dimensionResource(id = R.dimen.margin2x),
                    vertical = dimensionResource(id = R.dimen.margin1x),
                ),
        ) {
            item(key = FeeSection.GLOBAL_EXCHANGE) {
                GlobalFeeSection(
                    kind = GlobalFeeKind.EXCHANGE,
                    entries = globalExchange,
                    activeId = activeExchangeId,
                    onClick = { openPicker = GlobalFeeKind.EXCHANGE },
                )
            }
            item(key = FeeSection.GLOBAL_BANK) {
                GlobalFeeSection(
                    kind = GlobalFeeKind.BANK,
                    entries = globalBank,
                    activeId = activeBankId,
                    onClick = { openPicker = GlobalFeeKind.BANK },
                )
            }
            item(key = FeeSection.SPECIFIC_PAIR) {
                SpecificPairSection(
                    entries = specificPair,
                    onEdit = { openEditor = EditorTarget(EditorKind.Pair, it) },
                    onAdd = { openEditor = EditorTarget(EditorKind.Pair) },
                )
            }
        }
    }

    openPicker?.let { kind ->
        val (entries, activeId) =
            when (kind) {
                GlobalFeeKind.EXCHANGE -> globalExchange to activeExchangeId
                GlobalFeeKind.BANK -> globalBank to activeBankId
            }
        val active = entries.firstOrNull { it.isActive }
        val effectiveId = activeId?.takeIf { id -> entries.any { it.id == id && it.isActive } } ?: active?.id
        FeePickerDialog(
            title = stringResource(id = kind.titleRes),
            entries = entries,
            effectiveId = effectiveId,
            onDismiss = { openPicker = null },
            onPicked = { picked ->
                when (kind) {
                    GlobalFeeKind.EXCHANGE -> viewModel.setActiveExchangeId(picked)
                    GlobalFeeKind.BANK -> viewModel.setActiveBankId(picked)
                }
            },
            onAdd = { openEditor = EditorTarget(EditorKind.Global(kind)) },
            onEdit = { openEditor = EditorTarget(EditorKind.Global(kind), it) },
        )
    }

    openEditor?.let { target ->
        val kind = target.kind
        val isPair = kind is EditorKind.Pair
        val titleRes =
            when (kind) {
                is EditorKind.Global -> kind.globalKind.titleRes
                EditorKind.Pair -> R.string.fee_section_specific_pair
            }
        FeeEditorDialog(
            titleRes = titleRes,
            existing = target.existing,
            isPair = isPair,
            onDismiss = { openEditor = null },
            onConfirm = { draft ->
                persistEditorConfirm(viewModel, target, draft)
                openEditor = null
            },
            onPickCurrency = onPickCurrency,
            onDelete =
                target.existing?.let { existing ->
                    {
                        viewModel.deleteFee(existing.id)
                        openEditor = null
                    }
                },
        )
    }
}

/**
 * Applies a confirmed [FeeDraft] to persistence, either adding a new fee or
 * updating an existing one. Split out from the composable so the dialog stays
 * focused on presentation and the `when` on editor kind lives in one spot.
 */
private fun persistEditorConfirm(
    viewModel: FeeManagerViewModel,
    target: EditorTarget,
    draft: FeeDraft,
) {
    val existing = target.existing
    if (existing != null) {
        val updated =
            when (target.kind) {
                is EditorKind.Global -> existing.withEditableFields(draft.name, draft.percent, draft.isActive)
                EditorKind.Pair -> draft.toSpecificPair(id = existing.id)
            }
        viewModel.updateFee(updated)
    } else {
        val created =
            when (target.kind) {
                is EditorKind.Global ->
                    when (target.kind.globalKind) {
                        GlobalFeeKind.EXCHANGE -> draft.toGlobalExchange()
                        GlobalFeeKind.BANK -> draft.toGlobalBank()
                    }
                EditorKind.Pair -> draft.toSpecificPair()
            }
        viewModel.addFee(created)
        // First entry in a global category becomes the active one so the user
        // sees their new fee reflected on the main screen without an extra tap.
        if (target.kind is EditorKind.Global) {
            when (target.kind.globalKind) {
                GlobalFeeKind.EXCHANGE ->
                    if (viewModel.getActiveExchangeId().value == null) viewModel.setActiveExchangeId(created.id)
                GlobalFeeKind.BANK ->
                    if (viewModel.getActiveBankId().value == null) viewModel.setActiveBankId(created.id)
            }
        }
    }
}

@Composable
private fun <T : Fee> GlobalFeeSection(
    kind: GlobalFeeKind,
    entries: List<T>,
    activeId: String?,
    onClick: () -> Unit,
) {
    val active = entries.filter { it.isActive }
    val effective = active.firstOrNull { it.id == activeId } ?: active.firstOrNull()
    PreferenceSection(text = stringResource(id = kind.titleRes)) {
        if (effective == null) {
            PreferenceRow(
                title = stringResource(id = R.string.fee_empty),
                onClick = onClick,
            )
        } else {
            PreferenceRow(
                title = displayNameOf(effective),
                summary = feeSummaryWithInactive(effective),
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun SpecificPairSection(
    entries: List<Fee.SpecificPair>,
    onEdit: (Fee.SpecificPair) -> Unit,
    onAdd: () -> Unit,
) {
    PreferenceSection(text = stringResource(id = R.string.fee_section_specific_pair)) {
        if (entries.isEmpty()) {
            PreferenceRow(
                title = stringResource(id = R.string.fee_empty),
                enabled = false,
            )
        } else {
            entries.forEach { fee ->
                PreferenceRow(
                    title = pairRowTitle(fee),
                    summary = feeSummaryWithInactive(fee),
                    onClick = { onEdit(fee) },
                    trailing = { PairSummaryTrailing(fee = fee) },
                )
            }
        }
        PreferenceRow(
            title = stringResource(id = R.string.fee_add),
            iconRes = R.drawable.ic_add,
            onClick = onAdd,
        )
    }
}

/**
 * Right-aligned "USD → EUR" summary using the shared inline-flag composable
 * so pair rows on the fees screen match the pair chips on the main screen.
 * Falls back to plain text when a currency ISO is unknown (defensive — the
 * fee editor rejects invalid ISOs, but persisted data may pre-date the check).
 */
@Composable
private fun PairSummaryTrailing(fee: Fee.SpecificPair) {
    val fromCurrency = remember(fee.from) { Currency.fromString(fee.from) }
    val toCurrency = remember(fee.to) { Currency.fromString(fee.to) }
    val arrow = if (fee.bothWays) ARROW_BOTH_WAYS else ARROW_ONE_WAY
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FLAG_TEXT_GAP),
    ) {
        if (fromCurrency != null) InlineFlag(currency = fromCurrency)
        Text(text = fee.from, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(2.dp))
        Text(text = arrow, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(2.dp))
        if (toCurrency != null) InlineFlag(currency = toCurrency)
        Text(text = fee.to, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Row title for a specific-pair fee. Prefers the user-provided name; falls
 * back to the "USD → EUR" text so unnamed pairs still identify themselves in
 * the list without relying on the trailing flag summary alone.
 */
@Composable
private fun pairRowTitle(fee: Fee.SpecificPair): String =
    if (fee.name.isNotBlank()) {
        fee.name
    } else {
        "${fee.from} ${if (fee.bothWays) ARROW_BOTH_WAYS else ARROW_ONE_WAY} ${fee.to}"
    }
