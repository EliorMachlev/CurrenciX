package com.eliormachlev.currencix.view.preference.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.util.DISABLED_ROW_ALPHA
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.dialogs.LedgerPromptSheet
import com.eliormachlev.currencix.view.compose.flagPainter
import com.eliormachlev.currencix.view.main.spinner.CurrencyPickerSheet
import java.math.BigDecimal
import java.util.UUID

private val FEE_EDITOR_SECTION_GAP: Dp = 16.dp
private val FEE_EDITOR_LABEL_GAP: Dp = 4.dp
private val FEE_EDITOR_INTERNAL_PADDING: Dp = 4.dp
private val ROW_TIGHT_VERTICAL: Dp = 4.dp

// Fee picker dialog: rows are slightly denser than the standard choice row
// because they carry a title+description pair rather than a single line, and
// they need a leading radio *plus* an entire clickable body.
private val PICKER_ROW_VERTICAL: Dp = 10.dp

// With the radio's own touch-target inset, lines the rows up with the
// sheet's title.
private val PICKER_ROW_HORIZONTAL: Dp = 8.dp
private val PICKER_RADIO_GAP: Dp = 12.dp
private val ADD_ICON_HORIZONTAL_PAD: Dp = 12.dp

// Currency-picker button (from / to) chrome — matches the outlined MaterialButton
// look of the XML version so pairs of specific-pair rows read the same.
private val CURRENCY_BUTTON_HEIGHT: Dp = 48.dp
private val CURRENCY_BUTTON_RADIUS: Dp = 20.dp
private val CURRENCY_BUTTON_HORIZONTAL: Dp = 16.dp

// Inline flag glyph height. Rounded to match the currency-picker button
// visuals — small enough to sit inline with body-medium text without
// throwing off the line box.
internal val INLINE_FLAG_HEIGHT: Dp = 18.dp
internal val FLAG_TEXT_GAP: Dp = 6.dp
private val FLAG_CORNER_RADIUS: Dp = 2.dp

// Arrow / separator glyphs — reused by the pair summary so on-screen and
// in-picker summaries stay identical.
internal const val ARROW_ONE_WAY = "\u2192"
internal const val ARROW_BOTH_WAYS = "\u2194"
internal const val SUMMARY_SEPARATOR = "  ·  "

/**
 * Fee editor — shared by both global-fee kinds (exchange, bank) and the
 * specific-pair variant. Renders the active switch, name field, optional
 * pair rows (from / to / both-ways), and percent field. Delete is only
 * exposed when [onDelete] is non-null (i.e. editing an existing entry).
 *
 * Owns the currency-picker sheet state internally — from/to buttons flip
 * [pickerState] on tap and the picker opens over the editor's sheet, so
 * callers don't have to thread a picker callback through the compose tree.
 */
@Composable
internal fun FeeEditorSheet(
    @StringRes titleRes: Int,
    existing: Fee?,
    isPair: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (FeeDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val fields = rememberFeeEditorFields(existing)
    var pickerState by remember { mutableStateOf<CurrencyPickerRequest?>(null) }
    LedgerPromptSheet(
        title = stringResource(id = titleRes),
        confirmLabel = stringResource(id = android.R.string.ok),
        onConfirm = {
            val draft = fields.toDraft()
            if (!isPair || (draft.from != null && draft.to != null)) onConfirm(draft)
        },
        onDismiss = onDismiss,
        leadingAction = onDelete?.let { delete -> { DeleteFeeButton(delete) } },
    ) {
        FeeEditorSheetBody(
            fields = fields,
            isPair = isPair,
            onPickCurrency = { d, cb -> pickerState = CurrencyPickerRequest(d, cb) },
        )
    }
    FeeEditorPickerOverlay(request = pickerState, onDismiss = { pickerState = null })
}

// The from/to buttons' currency picker — a sheet of its own, opened over the
// editor's, without the picker state having to live above the editor.
@Composable
private fun FeeEditorPickerOverlay(
    request: CurrencyPickerRequest?,
    onDismiss: () -> Unit,
) {
    request ?: return
    CurrencyPickerSheet(
        currentRate = null,
        currentSum = BigDecimal.ONE,
        disabledCurrency = request.disabled,
        onRateClicked = { rate -> request.onPicked(rate.currency.iso4217Alpha()) },
        onDismiss = onDismiss,
    )
}

// Snapshot of a currency-picker request captured when the user taps a
// from/to button — the sheet reads back [disabled] to grey out the opposite
// side of the pair and calls [onPicked] with the chosen ISO. Held in the
// editor's own state so opening the picker doesn't have to bubble up to the
// fees screen.
private data class CurrencyPickerRequest(
    val disabled: Currency?,
    val onPicked: (String) -> Unit,
)

/**
 * Body of [FeeEditorSheet] — active switch, name/percent fields, and the
 * pair-only rows when [isPair] is true. Extracted so the sheet wrapper stays
 * short and this form reads on its own. The sheet scrolls it.
 */
@Composable
private fun FeeEditorSheetBody(
    fields: FeeEditorFields,
    isPair: Boolean,
    onPickCurrency: (disabled: Currency?, onPicked: (String) -> Unit) -> Unit,
) {
    Column(modifier = Modifier.padding(top = FEE_EDITOR_INTERNAL_PADDING)) {
        LabeledSwitchRow(
            labelRes = R.string.fee_edit_active,
            checked = fields.active.value,
            onCheckedChange = { fields.active.value = it },
        )
        LabeledField(labelRes = R.string.fee_edit_name, topGap = FEE_EDITOR_SECTION_GAP) {
            OutlinedTextField(
                value = fields.name.value,
                onValueChange = { fields.name.value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isPair) FeePairFields(fields, onPickCurrency)
        LabeledField(labelRes = R.string.fee_edit_percent, topGap = FEE_EDITOR_SECTION_GAP) {
            FeePercentField(value = fields.percentText.value, onValueChange = { fields.percentText.value = it })
        }
    }
}

// The rows only a specific-pair fee has: from, to, and "both ways". Each
// currency button greys out the other side's currency in the picker.
@Composable
private fun FeePairFields(
    fields: FeeEditorFields,
    onPickCurrency: (disabled: Currency?, onPicked: (String) -> Unit) -> Unit,
) {
    LabeledField(labelRes = R.string.fee_pair_from, topGap = FEE_EDITOR_SECTION_GAP) {
        CurrencyPickerButton(
            iso = fields.from.value,
            onClick = { onPickCurrency(fields.to.value?.let(Currency::fromString)) { fields.from.value = it } },
        )
    }
    LabeledField(labelRes = R.string.fee_pair_to, topGap = FEE_EDITOR_SECTION_GAP) {
        CurrencyPickerButton(
            iso = fields.to.value,
            onClick = { onPickCurrency(fields.from.value?.let(Currency::fromString)) { fields.to.value = it } },
        )
    }
    Spacer(Modifier.height(FEE_EDITOR_SECTION_GAP))
    LabeledSwitchRow(
        labelRes = R.string.fee_pair_both_ways,
        checked = fields.bothWays.value,
        onCheckedChange = { fields.bothWays.value = it },
    )
}

// The editor's fields, each saved across rotation and reset when another fee
// is opened.
@Stable
private class FeeEditorFields(
    val name: MutableState<String>,
    val percentText: MutableState<String>,
    val active: MutableState<Boolean>,
    val from: MutableState<String?>,
    val to: MutableState<String?>,
    val bothWays: MutableState<Boolean>,
) {
    // What the fields hold now, as the fee to save.
    fun toDraft(): FeeDraft =
        FeeDraft(
            name = name.value.trim(),
            percent = percentText.value.toFeePercentOrNull(feePercentSeparator) ?: BigDecimal.ZERO,
            isActive = active.value,
            from = from.value,
            to = to.value,
            bothWays = bothWays.value,
        )
}

@Composable
private fun rememberFeeEditorFields(existing: Fee?): FeeEditorFields {
    val pair = existing as? Fee.SpecificPair
    return FeeEditorFields(
        name = rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) },
        percentText = rememberFeePercentState(existing?.percent),
        active = rememberSaveable(existing?.id) { mutableStateOf(existing?.isActive != false) },
        from = rememberSaveable(existing?.id) { mutableStateOf(pair?.from) },
        to = rememberSaveable(existing?.id) { mutableStateOf(pair?.to) },
        bothWays = rememberSaveable(existing?.id) { mutableStateOf(pair?.bothWays == true) },
    )
}

// The editor's Delete, on the far side of the action row from Cancel and OK;
// only shown when editing an existing fee.
@Composable
private fun DeleteFeeButton(onDelete: () -> Unit) {
    TextButton(onClick = rememberHapticOnClick(onDelete)) {
        Text(stringResource(id = R.string.fee_delete))
    }
}

@Composable
private fun LabeledSwitchRow(
    @StringRes labelRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val toggle = rememberHapticOnClick { onCheckedChange(!checked) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hapticClickable(onClick = { onCheckedChange(!checked) })
                .padding(vertical = ROW_TIGHT_VERTICAL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = { toggle() })
    }
}

@Composable
private fun LabeledField(
    @StringRes labelRes: Int,
    topGap: Dp,
    content: @Composable () -> Unit,
) {
    Spacer(Modifier.height(topGap))
    Text(
        text = stringResource(id = labelRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = FEE_EDITOR_LABEL_GAP),
    )
    content()
}

@Composable
private fun CurrencyPickerButton(
    iso: String?,
    onClick: () -> Unit,
) {
    val currency = remember(iso) { iso?.let(Currency::fromString) }
    val placeholder = stringResource(id = R.string.fee_pair_pick_currency)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CURRENCY_BUTTON_HEIGHT)
                .clip(RoundedCornerShape(CURRENCY_BUTTON_RADIUS))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .hapticClickable(onClick = onClick)
                .padding(horizontal = CURRENCY_BUTTON_HORIZONTAL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (currency != null) {
            InlineFlag(currency = currency)
            Spacer(Modifier.width(FLAG_TEXT_GAP))
            Text(
                text = iso.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What the fee picker can do: make a fee the active one, add a new one, or open one for editing. */
internal class FeePickerActions<T : Fee>(
    val onPicked: (String) -> Unit,
    val onAdd: () -> Unit,
    val onEdit: (T) -> Unit,
)

/**
 * Global-fee picker sheet: radio list of every fee of a given kind. Tapping
 * a radio commits it as the active fee and dismisses; tapping the row body
 * opens the editor for that fee. The bottom "add" row creates a new entry.
 * A sheet, like the app's other pickers.
 */
@Composable
internal fun <T : Fee> FeePickerSheet(
    title: String,
    entries: List<T>,
    effectiveId: String?,
    actions: FeePickerActions<T>,
    onDismiss: () -> Unit,
) {
    val add =
        rememberHapticOnClick {
            onDismiss()
            actions.onAdd()
        }
    LedgerBottomSheet(title = title, onDismiss = onDismiss) {
        entries.forEach { fee ->
            PickerRow(
                fee = fee,
                checked = fee.id == effectiveId,
                onRadioClick = {
                    actions.onPicked(fee.id)
                    onDismiss()
                },
                onEditClick = {
                    onDismiss()
                    actions.onEdit(fee)
                },
            )
        }
        if (entries.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AddRow(onClick = add)
    }
}

@Composable
private fun <T : Fee> PickerRow(
    fee: T,
    checked: Boolean,
    onRadioClick: () -> Unit,
    onEditClick: () -> Unit,
) {
    val alpha = if (fee.isActive) 1f else DISABLED_ROW_ALPHA
    val summary = feeSummaryWithInactive(fee)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hapticClickable(onClick = onEditClick)
                .padding(horizontal = PICKER_ROW_HORIZONTAL, vertical = PICKER_ROW_VERTICAL)
                .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PICKER_RADIO_GAP),
    ) {
        RadioButton(selected = checked, onClick = onRadioClick)
        Column(Modifier.weight(1f)) {
            Text(
                text = displayNameOf(fee),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddRow(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hapticClickable(onClick = onClick)
                .padding(horizontal = PICKER_ROW_HORIZONTAL, vertical = PICKER_ROW_VERTICAL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PICKER_RADIO_GAP),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = ADD_ICON_HORIZONTAL_PAD),
        )
        Text(
            text = stringResource(id = R.string.fee_add),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * Snapshot of the fields shared between the fee editor dialogs. Pair-only
 * fields ([from], [to], [bothWays]) are nullable so global editors can leave
 * them out; the confirm handler in FeesScreen picks whichever subset matches
 * the fee kind being saved.
 */
internal data class FeeDraft(
    val name: String,
    val percent: BigDecimal,
    val isActive: Boolean,
    val from: String? = null,
    val to: String? = null,
    val bothWays: Boolean = false,
)

internal fun FeeDraft.toGlobalExchange(id: String? = null): Fee.GlobalExchange =
    Fee.GlobalExchange(
        id = id ?: UUID.randomUUID().toString(),
        name = name,
        percent = percent,
        isActive = isActive,
    )

internal fun FeeDraft.toGlobalBank(id: String? = null): Fee.GlobalBank =
    Fee.GlobalBank(
        id = id ?: UUID.randomUUID().toString(),
        name = name,
        percent = percent,
        isActive = isActive,
    )

// Null while either side of the pair is unset: there's no pair fee to make.
internal fun FeeDraft.toSpecificPair(id: String? = null): Fee.SpecificPair? {
    val from = from ?: return null
    val to = to ?: return null
    return Fee.SpecificPair(
        id = id ?: UUID.randomUUID().toString(),
        name = name,
        percent = percent,
        from = from,
        to = to,
        bothWays = bothWays,
        isActive = isActive,
    )
}

@Composable
internal fun displayNameOf(fee: Fee): String = if (fee.name.isBlank()) stringResource(id = R.string.fee_untitled) else fee.name

/**
 * Percent + optional "Inactive" marker — the standard fee summary line used
 * anywhere a fee row lists its rate. Formatted through the shared human-readable
 * number helper so thousands separators + locale decimal match the rest of the
 * app (e.g. hero card, receipt strip).
 */
@Composable
internal fun feeSummaryWithInactive(fee: Fee): String {
    val context = LocalContext.current
    val base = remember(fee.percent) { fee.percent.toHumanReadableNumber(context, suffix = "%") }
    return if (fee.isActive) {
        base
    } else {
        "$base$SUMMARY_SEPARATOR${stringResource(id = R.string.fee_inactive_marker)}"
    }
}

/**
 * Small flag glyph aligned with body text. Uses [Currency.flag] as the source
 * so it matches every other flag on the screen (main hero, cart chips, picker).
 * Height is fixed at [INLINE_FLAG_HEIGHT]; width scales to preserve aspect.
 */
@Composable
internal fun InlineFlag(currency: Currency) {
    val painter = currency.flagPainter()
    val aspect = painter.intrinsicSize.aspect()
    Box(
        modifier =
            Modifier
                .height(INLINE_FLAG_HEIGHT)
                .width(INLINE_FLAG_HEIGHT * aspect)
                .clip(RoundedCornerShape(FLAG_CORNER_RADIUS)),
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Width/height, guarding the degenerate sizes a painter can report.
private fun Size.aspect(): Float = if (height > 0f && width > 0f) width / height else 1f
