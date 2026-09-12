package com.eliormachlev.currencix.view.preference.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.util.DISABLED_ROW_ALPHA
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.compose.AppTheme
import java.math.BigDecimal
import java.util.UUID

// Fee editor dialog: horizontal slice of the screen (95%) so wide rows
// (currency buttons + bothWays label) don't get truncated on typical phones.
private const val FEE_EDITOR_WIDTH_FRACTION = 0.95f
private val FEE_EDITOR_SECTION_GAP: Dp = 16.dp
private val FEE_EDITOR_LABEL_GAP: Dp = 4.dp
private val FEE_EDITOR_INTERNAL_PADDING: Dp = 4.dp
private val ROW_TIGHT_VERTICAL: Dp = 4.dp

// Fee picker dialog: rows are slightly denser than the standard choice row
// because they carry a title+description pair rather than a single line, and
// they need a leading radio *plus* an entire clickable body.
private val PICKER_ROW_VERTICAL: Dp = 10.dp
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
 */
@Composable
internal fun FeeEditorDialog(
    @StringRes titleRes: Int,
    existing: Fee?,
    isPair: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (FeeDraft) -> Unit,
    onPickCurrency: (disabled: Currency?, onPicked: (String) -> Unit) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    val percentText = rememberFeePercentState(existing?.percent)
    var active by rememberSaveable(existing?.id) { mutableStateOf(existing?.isActive != false) }
    val pair = existing as? Fee.SpecificPair
    var from by rememberSaveable(existing?.id) { mutableStateOf(pair?.from) }
    var to by rememberSaveable(existing?.id) { mutableStateOf(pair?.to) }
    var bothWays by rememberSaveable(existing?.id) { mutableStateOf(pair?.bothWays == true) }

    val confirm =
        rememberHapticOnClick {
            val draft =
                FeeDraft(
                    name = name.trim(),
                    percent = percentText.value.toFeePercentOrNull(feePercentSeparator) ?: BigDecimal.ZERO,
                    isActive = active,
                    from = from,
                    to = to,
                    bothWays = bothWays,
                )
            if (isPair && (draft.from == null || draft.to == null)) return@rememberHapticOnClick
            onConfirm(draft)
        }
    val cancel = rememberHapticOnClick(onDismiss)
    val delete = onDelete?.let { rememberHapticOnClick(it) }

    AppTheme {
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(FEE_EDITOR_WIDTH_FRACTION),
            title = { Text(text = stringResource(id = titleRes)) },
            text = {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(top = FEE_EDITOR_INTERNAL_PADDING),
                ) {
                    LabeledSwitchRow(
                        labelRes = R.string.fee_edit_active,
                        checked = active,
                        onCheckedChange = { active = it },
                    )
                    LabeledField(labelRes = R.string.fee_edit_name, topGap = FEE_EDITOR_SECTION_GAP) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (isPair) {
                        LabeledField(labelRes = R.string.fee_pair_from, topGap = FEE_EDITOR_SECTION_GAP) {
                            CurrencyPickerButton(
                                iso = from,
                                onClick = {
                                    onPickCurrency(to?.let(Currency::fromString)) { from = it }
                                },
                            )
                        }
                        LabeledField(labelRes = R.string.fee_pair_to, topGap = FEE_EDITOR_SECTION_GAP) {
                            CurrencyPickerButton(
                                iso = to,
                                onClick = {
                                    onPickCurrency(from?.let(Currency::fromString)) { to = it }
                                },
                            )
                        }
                        Spacer(Modifier.height(FEE_EDITOR_SECTION_GAP))
                        LabeledSwitchRow(
                            labelRes = R.string.fee_pair_both_ways,
                            checked = bothWays,
                            onCheckedChange = { bothWays = it },
                        )
                    }
                    LabeledField(labelRes = R.string.fee_edit_percent, topGap = FEE_EDITOR_SECTION_GAP) {
                        FeePercentField(
                            value = percentText.value,
                            onValueChange = { percentText.value = it },
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (delete != null) {
                        TextButton(onClick = delete) {
                            Text(stringResource(id = R.string.fee_delete))
                        }
                    } else {
                        Spacer(Modifier.width(0.dp))
                    }
                    Row {
                        TextButton(onClick = cancel) {
                            Text(stringResource(id = android.R.string.cancel))
                        }
                        TextButton(onClick = confirm) {
                            Text(stringResource(id = android.R.string.ok))
                        }
                    }
                }
            },
        )
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

/**
 * Global-fee picker dialog: radio list of every fee of a given kind. Tapping
 * a radio commits it as the active fee and dismisses; tapping the row body
 * opens the editor for that fee. The bottom "add" row creates a new entry.
 */
@Composable
internal fun <T : Fee> FeePickerDialog(
    title: String,
    entries: List<T>,
    effectiveId: String?,
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (T) -> Unit,
) {
    val cancel = rememberHapticOnClick(onDismiss)
    val add =
        rememberHapticOnClick {
            onDismiss()
            onAdd()
        }
    AppTheme {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    entries.forEach { fee ->
                        PickerRow(
                            fee = fee,
                            checked = fee.id == effectiveId,
                            onRadioClick = {
                                onPicked(fee.id)
                                onDismiss()
                            },
                            onEditClick = {
                                onDismiss()
                                onEdit(fee)
                            },
                        )
                    }
                    if (entries.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AddRow(onClick = add)
                }
            },
            confirmButton = {
                TextButton(onClick = cancel) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            },
        )
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
                .padding(vertical = PICKER_ROW_VERTICAL)
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
                .padding(vertical = PICKER_ROW_VERTICAL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PICKER_RADIO_GAP),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
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

internal fun FeeDraft.toSpecificPair(id: String? = null): Fee.SpecificPair =
    Fee.SpecificPair(
        id = id ?: UUID.randomUUID().toString(),
        name = name,
        percent = percent,
        from = from!!,
        to = to!!,
        bothWays = bothWays,
        isActive = isActive,
    )

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
    val context = LocalContext.current
    val drawable = remember(currency) { currency.flag(context) }
    val painter = remember(drawable) { drawable.toBitmapPainter() }
    val aspect = remember(drawable) { drawable.aspect() }
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

private fun Drawable.toBitmapPainter(): Painter {
    val w = intrinsicWidth.coerceAtLeast(1)
    val h = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, w, h)
    draw(Canvas(bitmap))
    return BitmapPainter(bitmap.asImageBitmap())
}

private fun Drawable.aspect(): Float = intrinsicWidth.toFloat() / intrinsicHeight.coerceAtLeast(1)
