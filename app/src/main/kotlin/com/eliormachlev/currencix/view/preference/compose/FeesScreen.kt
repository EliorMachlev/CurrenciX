package com.eliormachlev.currencix.view.preference.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.viewmodel.preference.FeeManagerViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
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
    val fees by viewModel.fees.collectAsStateWithLifecycle()
    val activeExchangeId by viewModel.activeExchangeId.collectAsStateWithLifecycle()
    val activeBankId by viewModel.activeBankId.collectAsStateWithLifecycle()

    val globalExchange = remember(fees) { fees.filterIsInstance<Fee.GlobalExchange>().toImmutableList() }
    val globalBank = remember(fees) { fees.filterIsInstance<Fee.GlobalBank>().toImmutableList() }
    val specificPair = remember(fees) { fees.filterIsInstance<Fee.SpecificPair>().toImmutableList() }

    var openPicker by remember { mutableStateOf<GlobalFeeKind?>(null) }
    var openEditor by remember { mutableStateOf<EditorTarget?>(null) }

    AppComposeTheme {
        FeesSectionsList(
            globalExchange = globalExchange,
            activeExchangeId = activeExchangeId,
            globalBank = globalBank,
            activeBankId = activeBankId,
            specificPair = specificPair,
            onOpenPicker = { openPicker = it },
            onOpenEditor = { openEditor = it },
        )
    }

    openPicker?.let { kind ->
        GlobalPickerHost(
            kind = kind,
            globalExchange = globalExchange,
            globalBank = globalBank,
            activeExchangeId = activeExchangeId,
            activeBankId = activeBankId,
            viewModel = viewModel,
            onDismiss = { openPicker = null },
            onOpenEditor = { openEditor = it },
        )
    }

    openEditor?.let { target ->
        EditorHost(
            target = target,
            viewModel = viewModel,
            onPickCurrency = onPickCurrency,
            onDismiss = { openEditor = null },
        )
    }
}

/**
 * Resolves the picker dialog for whichever global fee kind is currently open,
 * threading the right list + active-id into [FeePickerDialog]. Extracted so
 * [FeesScreen] stays under the LongMethod threshold.
 */
@Composable
@Suppress("LongParameterList")
private fun GlobalPickerHost(
    kind: GlobalFeeKind,
    globalExchange: ImmutableList<Fee.GlobalExchange>,
    globalBank: ImmutableList<Fee.GlobalBank>,
    activeExchangeId: String?,
    activeBankId: String?,
    viewModel: FeeManagerViewModel,
    onDismiss: () -> Unit,
    onOpenEditor: (EditorTarget) -> Unit,
) {
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
        onDismiss = onDismiss,
        onPicked = { picked ->
            when (kind) {
                GlobalFeeKind.EXCHANGE -> viewModel.setActiveExchangeId(picked)
                GlobalFeeKind.BANK -> viewModel.setActiveBankId(picked)
            }
        },
        onAdd = { onOpenEditor(EditorTarget(EditorKind.Global(kind))) },
        onEdit = { onOpenEditor(EditorTarget(EditorKind.Global(kind), it)) },
    )
}

/**
 * Hosts the fee editor dialog for the currently-open [target]. Splits out the
 * title-lookup + delete-callback wiring so [FeesScreen] itself stays short.
 */
@Composable
private fun EditorHost(
    target: EditorTarget,
    viewModel: FeeManagerViewModel,
    onPickCurrency: (disabled: Currency?, onPicked: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
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
        onDismiss = onDismiss,
        onConfirm = { draft ->
            persistEditorConfirm(viewModel, target, draft)
            onDismiss()
        },
        onPickCurrency = onPickCurrency,
        onDelete =
            target.existing?.let { existing ->
                {
                    viewModel.deleteFee(existing.id)
                    onDismiss()
                }
            },
    )
}

/**
 * The three-section LazyColumn body of [FeesScreen]. Extracted so the screen
 * composable stays under the LongMethod threshold and this list-layout block
 * is readable on its own without wading past the dialog state below.
 */
@Composable
@Suppress("LongParameterList")
private fun FeesSectionsList(
    globalExchange: ImmutableList<Fee.GlobalExchange>,
    activeExchangeId: String?,
    globalBank: ImmutableList<Fee.GlobalBank>,
    activeBankId: String?,
    specificPair: ImmutableList<Fee.SpecificPair>,
    onOpenPicker: (GlobalFeeKind) -> Unit,
    onOpenEditor: (EditorTarget) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = dimensionResource(id = R.dimen.margin2x),
                vertical = dimensionResource(id = R.dimen.margin1x),
            ),
    ) {
        item(key = FeeSection.GLOBAL_EXCHANGE) {
            SectionEnter(index = FeeSection.GLOBAL_EXCHANGE.ordinal) {
                GlobalFeeSection(
                    kind = GlobalFeeKind.EXCHANGE,
                    entries = globalExchange,
                    activeId = activeExchangeId,
                    onClick = { onOpenPicker(GlobalFeeKind.EXCHANGE) },
                )
            }
        }
        item(key = FeeSection.GLOBAL_BANK) {
            SectionEnter(index = FeeSection.GLOBAL_BANK.ordinal) {
                GlobalFeeSection(
                    kind = GlobalFeeKind.BANK,
                    entries = globalBank,
                    activeId = activeBankId,
                    onClick = { onOpenPicker(GlobalFeeKind.BANK) },
                )
            }
        }
        specificPairSection(
            entries = specificPair,
            onEdit = { onOpenEditor(EditorTarget(EditorKind.Pair, it)) },
            onAdd = { onOpenEditor(EditorTarget(EditorKind.Pair)) },
        )
    }
}

// Content keys for the specific-pair section rows that aren't the pair fees
// themselves. Kept as constants so the LazyColumn's item slot table stays
// stable across recompositions — LazyColumn diffs by key, so drifting keys
// would defeat both slot recycling and animateItem() placement animations.
private const val SPECIFIC_PAIR_HEADER_KEY = "specific-pair-header"
private const val SPECIFIC_PAIR_EMPTY_KEY = "specific-pair-empty"
private const val SPECIFIC_PAIR_ADD_KEY = "specific-pair-add"

/**
 * Expands the specific-pair section directly into the enclosing [LazyColumn]
 * rather than nesting all rows inside a single `item {}`. Promoting each pair
 * row to its own LazyColumn item is what lets [androidx.compose.foundation.lazy.LazyItemScope.animateItem]
 * animate add / remove / reorder — inside a single item, individual rows are
 * just Column children and don't participate in list animations. The header
 * and the "add" affordance stay as fixed anchor items so only the pair-row
 * cluster in the middle moves.
 */
private fun LazyListScope.specificPairSection(
    entries: ImmutableList<Fee.SpecificPair>,
    onEdit: (Fee.SpecificPair) -> Unit,
    onAdd: () -> Unit,
) {
    item(key = SPECIFIC_PAIR_HEADER_KEY) {
        SectionEnter(index = FeeSection.SPECIFIC_PAIR.ordinal) {
            SpecificPairHeader()
        }
    }
    if (entries.isEmpty()) {
        item(key = SPECIFIC_PAIR_EMPTY_KEY) {
            PreferenceRow(
                title = stringResource(id = R.string.fee_empty),
                enabled = false,
            )
        }
    } else {
        items(items = entries, key = { it.id }) { fee ->
            PreferenceRow(
                modifier = Modifier.animateItem(),
                title = pairRowTitle(fee),
                summary = feeSummaryWithInactive(fee),
                onClick = { onEdit(fee) },
                trailing = { PairSummaryTrailing(fee = fee) },
            )
        }
    }
    item(key = SPECIFIC_PAIR_ADD_KEY) {
        PreferenceRow(
            title = stringResource(id = R.string.fee_add),
            iconRes = R.drawable.ic_add,
            onClick = onAdd,
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
        adoptFirstGlobalAsActive(viewModel, target.kind, created.id)
    }
}

/**
 * If the newly-created fee is the first entry in its global category and no
 * active-id is set yet, promote it to active so the main screen reflects the
 * new fee without an extra tap. No-op for pair fees. Extracted from
 * [persistEditorConfirm] to keep NestedBlockDepth under the detekt threshold.
 */
private fun adoptFirstGlobalAsActive(
    viewModel: FeeManagerViewModel,
    kind: EditorKind,
    createdId: String,
) {
    if (kind !is EditorKind.Global) return
    when (kind.globalKind) {
        GlobalFeeKind.EXCHANGE ->
            if (viewModel.activeExchangeId.value == null) viewModel.setActiveExchangeId(createdId)
        GlobalFeeKind.BANK ->
            if (viewModel.activeBankId.value == null) viewModel.setActiveBankId(createdId)
    }
}

@Composable
private fun <T : Fee> GlobalFeeSection(
    kind: GlobalFeeKind,
    entries: ImmutableList<T>,
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

/**
 * Standalone header for the specific-pair section. The section body (pair
 * rows + add row) is emitted as sibling LazyColumn items by
 * [specificPairSection] so each pair row can host its own
 * [androidx.compose.foundation.lazy.LazyItemScope.animateItem] placement
 * animation — the whole section is intentionally not wrapped in a single
 * grouping composable.
 */
@Composable
private fun SpecificPairHeader() {
    PreferenceSection(text = stringResource(id = R.string.fee_section_specific_pair)) {
        // Body rows are emitted as sibling LazyColumn items; the section
        // primitive is reused only for its brass header + surrounding gap.
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
