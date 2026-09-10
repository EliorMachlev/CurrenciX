package com.eliormachlev.currencix.view.main.compose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.rateFor
import com.eliormachlev.currencix.util.feePercentDelta
import com.eliormachlev.currencix.util.fromHtmlLegacy
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.util.hapticCombinedClickable
import com.eliormachlev.currencix.util.hasAppendedCurrencySymbol
import com.eliormachlev.currencix.util.stripRtlMark
import com.eliormachlev.currencix.util.stripTimePattern
import com.eliormachlev.currencix.util.toCompactHumanReadableNumber
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.theme.Amber
import com.eliormachlev.currencix.view.main.spinner.SearchableSpinnerDialog
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Hero-card visual metrics — mirror the v1 Fluid Converter mockup.
private val MATH_LINE_BOTTOM_GAP: Dp = 2.dp
private val CARD_OUTER_MARGIN: Dp = 16.dp
private val CARD_RADIUS: Dp = 28.dp
private val CARD_PADDING: Dp = 16.dp
private val PILL_RADIUS: Dp = 999.dp
private val PILL_HEIGHT: Dp = 44.dp
private val PILL_HORIZONTAL_PADDING: Dp = 14.dp

// Rectangular flag matching the picker's 24×17 aspect; the pill height is
// 44dp, so scaling up to 28×20 keeps the ratio and stays comfortably within
// the pill.
private val FLAG_WIDTH: Dp = 28.dp
private val FLAG_HEIGHT: Dp = 20.dp
private val FLAG_CORNER_RADIUS: Dp = 2.dp
private val SWAP_FAB_SIZE: Dp = 44.dp
private val PILLS_ROW_GAP: Dp = 8.dp
private val PILLS_ROW_BOTTOM_GAP: Dp = 12.dp

// Breathing room above the tinted "you get" band that hosts the
// converted-amount cluster (chip + amount + pill).
private val AMOUNT_BAND_TOP_GAP: Dp = 8.dp

// Ambient shadow under the hero card so it lifts off the background. Kept
// modest — Material3 elevated cards usually sit at 1–3dp for the "resting"
// affordance; anything higher starts to feel floaty over the dark keypad.
private val CARD_ELEVATION: Dp = 3.dp

// Swap FAB rotation animation — one 180° flip per tap. The counter drives
// a target angle so successive taps keep spinning in the same direction
// (never snap back), and animateFloatAsState handles the tween.
private const val SWAP_FAB_ROTATION_STEP = 180f
private const val SWAP_FAB_ROTATION_MILLIS = 320

// Interior padding and corner rounding for the "you get" band itself.
private val AMOUNT_BAND_PADDING: Dp = 10.dp
private val AMOUNT_BAND_RADIUS: Dp = 20.dp

private val RATE_FOOTER_TOP_MARGIN: Dp = 8.dp
private val RATE_FOOTER_PADDING_TOP: Dp = 8.dp
private val CURSOR_WIDTH: Dp = 2.dp
private val CURSOR_HEIGHT: Dp = 44.dp
private val CURSOR_HEIGHT_SUBTOTAL: Dp = 26.dp
private val FLAG_GAP: Dp = 10.dp
private val CHEVRON_GAP: Dp = 4.dp
private val CHEVRON_SIZE: Dp = 14.dp

// Amount-hero / amount-to display sizes. One-off, not part of the Typography
// scale (Material3 would try to apply them elsewhere). AMOUNT_SUBTOTAL_SIZE
// renders the pre-fee subtotal in the top hero (medium weight, sits above
// the fee chip); the fee-adjusted final claims AMOUNT_HERO_SIZE below.
private val AMOUNT_HERO_SIZE = 52.sp
private val AMOUNT_SUBTOTAL_SIZE = 28.sp
private val AMOUNT_TO_SIZE = 34.sp
private val FEE_CHIP_TEXT_SIZE = 11.sp
private val MATH_LINE_TEXT_SIZE = 14.sp

// Gap between the medium subtotal row and the fee chip that follows it,
// and between the fee chip and the hero final. Tuned so the three read as
// one vertical equation without doubling the card height.
private val SUBTOTAL_TO_CHIP_GAP: Dp = 4.dp
private val CHIP_TO_FINAL_GAP: Dp = 2.dp

// Framed "final cost" box that wraps the hero final. The label sits ON
// the top border (fieldset-legend style) — the label paints a strip of the
// card's surface color behind itself so the border appears to break for
// the text and resume after it. Vertical centering of the label on the
// border stroke is done at measure time in [FinalCostBox] using the label's
// actual rendered height, so there's no static half-height constant here.
private val FINAL_BOX_BORDER_WIDTH: Dp = 1.dp
private val FINAL_BOX_CORNER_RADIUS: Dp = 14.dp
private val FINAL_BOX_HORIZONTAL_PADDING: Dp = 12.dp
private val FINAL_BOX_VERTICAL_PADDING: Dp = 8.dp

// How far the label sits from the box's leading corner along the top border.
private val FINAL_LABEL_START_INSET: Dp = 14.dp

// Horizontal padding on the label's surface-colored background so the
// masked strip is a bit wider than the text on each side — creates the
// visible "gap" in the border.
private val FINAL_LABEL_MASK_PADDING: Dp = 6.dp

// Applied to the big-value texts so Android's default font padding
// (~4-6 dp above/below the glyph on top of lineHeight) doesn't inflate
// the visual gap between the number and whatever renders right below it.
// `tnum` forces tabular (fixed-advance) digits so the amount doesn't jitter
// horizontally as digits change (1→8, 3→4, …). Applies to Inter which
// ships with a `tnum` feature.
private val TIGHT_TEXT_STYLE =
    TextStyle(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        fontFeatureSettings = "tnum",
    )

// Fallback rounding for the final-value pill amount when the user's
// decimal-places preference hasn't loaded yet — matches the ViewModel's
// own default seed for the result formatter.
private const val FINAL_VALUE_DECIMAL_PLACES_FALLBACK = 2

// Trailing glyph on the math line so a running expression reads as
// "1+2+3=" rather than a bare list of operands.
private const val OP_EQUALS = "="

// Rounding hint for the info-conversion mid-rate in the footer. Four places
// keeps small majors (e.g. JPY→USD ≈ 0.0067) legible without over-precisioning
// big pairs (USD→EUR ≈ 0.93).
private const val FOOTER_RATE_DECIMAL_PLACES = 4
private const val FEE_PERCENT_DECIMAL_PLACES = 2

// Bullet separator between "when" and provider name in the footer.
private const val FOOTER_SEPARATOR = " · "

// Comma-space between multiple fee names in the fee chip ("Wise, Chase +2.5%").
private const val FEE_NAME_SEPARATOR = ", "

// Under this age we show a relative label ("12h ago") instead of the date.
private const val RELATIVE_TIME_WINDOW_MS = 24L * 60L * 60L * 1000L

private const val CURSOR_BLINK_MILLIS = 1200

// Feathered background tint applied under the amber fee text. 15% of amber
// composited over the pill's normal surface variant.
private const val FEE_CHIP_BG_ALPHA = 0.15f

// Cap the amber fee chip at a fraction of its parent row so it can never grow
// past that even when nothing else competes; the red final-value pill is
// weighted instead so it absorbs whatever row space the chip leaves free
// (up to its own natural width, then scrolls).
private const val FEE_CHIP_MAX_WIDTH_FRACTION = 0.5f

// Pill auto-scroll: after this long without a user drag on a scrollable pill,
// resume an automatic ping-pong scroll so overflowing content can still be
// read passively. Trip time is computed per-state from the scroll distance
// and a fixed dp/second speed so pills with different content lengths visually
// scroll at the same pace instead of racing (short trips would look slow at
// a fixed millis budget and long trips would race by).
private const val PILL_AUTO_SCROLL_IDLE_MILLIS = 10_000L
private const val PILL_AUTO_SCROLL_SPEED_DP_PER_S = 30f
private const val PILL_AUTO_SCROLL_MIN_TRIP_MILLIS = 1500
private const val PILL_AUTO_SCROLL_RETURN_MILLIS = 1200
private const val PILL_AUTO_SCROLL_DWELL_MILLIS = 1500L

/**
 * Callbacks the [MainDisplay] emits back to the hosting Activity. Copy hits
 * the Activity so it can reach the clipboard + snackbar helpers; the fees
 * chip opens the same "Fees" sub-screen the overflow menu launches; the swap
 * long-press mirrors the pre-Compose behaviour.
 */
internal data class MainDisplayCallbacks(
    val onCopy: (CharSequence) -> Unit,
    val onOpenFees: () -> Unit,
    val onOpenProvider: () -> Unit,
    val onSwapLongPress: () -> Unit,
)

/**
 * Pure-Compose replacement for the old `main_display.xml`. Renders the hero
 * card (currency pills + amount hero + amount to + rate footer). All state is
 * pulled from [viewModel] via [observeAsState]; the currency-picker dialog
 * ([SearchableSpinnerDialog]) is invoked with [fragmentManager] so behavior
 * matches the pre-Compose version.
 */
@Composable
internal fun MainDisplay(
    viewModel: MainViewModel,
    fragmentManager: FragmentManager,
    callbacks: MainDisplayCallbacks,
    dateFormatPattern: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val baseCurrency by viewModel.getBaseCurrency().observeAsState()
    val destCurrency by viewModel.getDestinationCurrency().observeAsState()
    val baseFormatted by viewModel.getCurrentBaseValueFormatted().observeAsState()
    val resultFormatted by viewModel.getResultFormatted().observeAsState()
    val rates by viewModel.getExchangeRates().observeAsState()
    val isUpdating by viewModel.isUpdating().observeAsState(false)
    val feeStack by viewModel.getFeeStack().observeAsState()
    val activeFees by viewModel.getActiveFees().observeAsState()
    val mathText by viewModel.getCalculationInputFormatted().observeAsState()
    val baseValueNumber by viewModel.getCurrentBaseValueAsNumber().observeAsState()
    val resultNumber by viewModel.getResultAsNumber().observeAsState()
    val trueCost by viewModel.getTrueCost().observeAsState()
    val decimalPlaces by viewModel.getDecimalPlaces().observeAsState(FINAL_VALUE_DECIMAL_PLACES_FALLBACK)

    val baseFull = baseFormatted?.toString().orEmpty()
    val resultFull = resultFormatted?.toString().orEmpty()
    // trueCost is the fee-adjusted final in the source currency. Format it the
    // same way as the typed subtotal (compact for long values, symbol on the
    // locale-appropriate side) so the two amounts read as a matching pair.
    val originalFinalFormatted =
        formatMoneyForDisplay(context, trueCost, baseCurrency, decimalPlaces) ?: baseFull
    HeroCard(
        baseCurrency = baseCurrency,
        destCurrency = destCurrency,
        baseFormatted = compactAmountOrFull(context, baseFull, baseValueNumber, baseCurrency),
        originalFinalFormatted = originalFinalFormatted,
        resultFormatted = compactAmountOrFull(context, resultFull, resultNumber, destCurrency),
        baseCopyText = baseFull,
        originalFinalCopyText = originalFinalFormatted,
        resultCopyText = resultFull,
        rates = rates,
        isUpdating = isUpdating,
        feeStack = feeStack,
        activeFees = activeFees.orEmpty(),
        mathText = mathText,
        originalBig = baseValueNumber,
        originalOther = trueCost,
        dateFormatPattern = dateFormatPattern,
        onPillFromClick = {
            openCurrencyPicker(context, viewModel, fragmentManager, PickSide.FROM, baseCurrency, destCurrency, rates)
        },
        onPillToClick = {
            openCurrencyPicker(context, viewModel, fragmentManager, PickSide.TO, baseCurrency, destCurrency, rates)
        },
        onSwapClick = {
            val newBase = destCurrency
            val newDest = baseCurrency
            if (newBase != null && newDest != null && newBase != newDest) {
                viewModel.setBaseCurrency(newBase)
                viewModel.setDestinationCurrency(newDest)
            }
        },
        callbacks = callbacks,
        modifier = modifier,
    )
}

@Composable
private fun HeroCard(
    baseCurrency: Currency?,
    destCurrency: Currency?,
    baseFormatted: String,
    originalFinalFormatted: String,
    resultFormatted: String,
    baseCopyText: String,
    originalFinalCopyText: String,
    resultCopyText: String,
    rates: ExchangeRates?,
    isUpdating: Boolean,
    feeStack: BigDecimal?,
    activeFees: List<Fee>,
    mathText: String?,
    originalBig: BigDecimal?,
    originalOther: BigDecimal?,
    dateFormatPattern: String,
    onPillFromClick: () -> Unit,
    onPillToClick: () -> Unit,
    onSwapClick: () -> Unit,
    callbacks: MainDisplayCallbacks,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = CARD_OUTER_MARGIN)
            .padding(top = CARD_OUTER_MARGIN)
            .shadow(elevation = CARD_ELEVATION, shape = RoundedCornerShape(CARD_RADIUS))
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(MaterialTheme.colorScheme.surface)
            .padding(CARD_PADDING),
    ) {
        Column(Modifier.fillMaxWidth()) {
            PillsRow(
                fromCurrency = baseCurrency,
                toCurrency = destCurrency,
                onPillFromClick = onPillFromClick,
                onPillToClick = onPillToClick,
                onSwapClick = onSwapClick,
                onSwapLongPress = callbacks.onSwapLongPress,
            )
            Spacer(Modifier.height(PILLS_ROW_BOTTOM_GAP))
            AmountHero(
                subtotalText = baseFormatted,
                finalText = originalFinalFormatted,
                mathText = mathText,
                onSubtotalLongClick = { if (baseCopyText.isNotEmpty()) callbacks.onCopy(baseCopyText) },
                onFinalLongClick = {
                    if (originalFinalCopyText.isNotEmpty()) callbacks.onCopy(originalFinalCopyText)
                },
                stack = feeStack,
                fees = activeFees,
                bigValue = originalBig,
                otherValue = originalOther,
                onFeeChipClick = callbacks.onOpenFees,
            )
            Spacer(Modifier.height(AMOUNT_BAND_TOP_GAP))
            AmountToRow(
                text = resultFormatted,
                onLongClick = { if (resultCopyText.isNotEmpty()) callbacks.onCopy(resultCopyText) },
            )
            Spacer(Modifier.height(RATE_FOOTER_TOP_MARGIN))
            RateFooter(
                base = baseCurrency,
                dest = destCurrency,
                rates = rates,
                dateFormatPattern = dateFormatPattern,
                onProviderClick = callbacks.onOpenProvider,
            )
        }
    }
}

@Composable
private fun PillsRow(
    fromCurrency: Currency?,
    toCurrency: Currency?,
    onPillFromClick: () -> Unit,
    onPillToClick: () -> Unit,
    onSwapClick: () -> Unit,
    onSwapLongPress: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PILLS_ROW_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CurrencyPill(currency = fromCurrency, onClick = onPillFromClick, modifier = Modifier.weight(1f))
        SwapFab(onClick = onSwapClick, onLongClick = onSwapLongPress)
        CurrencyPill(currency = toCurrency, onClick = onPillToClick, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CurrencyPill(
    currency: Currency?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val flagPainter = remember(currency) { currency?.flag(context)?.let(::drawableToPainter) }
    Row(
        modifier
            .height(PILL_HEIGHT)
            .clip(RoundedCornerShape(PILL_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .hapticClickable(enabled = currency != null, onClick = onClick)
            .padding(horizontal = PILL_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (flagPainter != null) {
            Image(
                painter = flagPainter,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(width = FLAG_WIDTH, height = FLAG_HEIGHT)
                        .clip(RoundedCornerShape(FLAG_CORNER_RADIUS)),
            )
            Spacer(Modifier.width(FLAG_GAP))
        }
        Text(
            text = currency?.iso4217Alpha().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(CHEVRON_GAP))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(CHEVRON_SIZE),
        )
    }
}

@Composable
private fun SwapFab(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var tapCount by remember { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(
        targetValue = tapCount * SWAP_FAB_ROTATION_STEP,
        animationSpec = tween(durationMillis = SWAP_FAB_ROTATION_MILLIS),
        label = "swapFabRotation",
    )
    Box(
        Modifier
            .size(SWAP_FAB_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .hapticCombinedClickable(
                onClick = {
                    tapCount += 1
                    onClick()
                },
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.SwapHoriz,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier =
                Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = rotation },
        )
    }
}

// AmountHero — chain layout with three tiers when a fee is present:
//   math line (top, small)   "60 + 60 ="
//   subtotal (medium, cursor) "₪ 120"
//   fee chip (small)          "+ 1% · Max Executive"
//   final (big, hero)         "₪ 121.2"
// When there's no fee the subtotal IS the final, so we collapse to the
// classic single-hero layout to keep the card compact.
@Composable
private fun AmountHero(
    subtotalText: String,
    finalText: String,
    mathText: String?,
    onSubtotalLongClick: () -> Unit,
    onFinalLongClick: () -> Unit,
    stack: BigDecimal?,
    fees: List<Fee>,
    bigValue: BigDecimal?,
    otherValue: BigDecimal?,
    onFeeChipClick: () -> Unit,
) {
    val hasFee = stack.hasFee()
    val showChain = hasFee && bigValue.isMeaningful() && otherValue != null
    Column(Modifier.fillMaxWidth()) {
        MathLine(mathText)
        // Subtotal always renders at the compact size — the "you get" band
        // below (and the framed Final cost when a fee is active) carry the
        // hero-sized answer role, so the input stays visually stable whether
        // the fee chain is showing or not.
        AmountRow(
            text = subtotalText,
            fontSize = AMOUNT_SUBTOTAL_SIZE,
            fontWeight = FontWeight.Medium,
            cursorHeight = CURSOR_HEIGHT_SUBTOTAL,
            onLongClick = onSubtotalLongClick,
        )
        if (showChain) {
            Spacer(Modifier.height(SUBTOTAL_TO_CHIP_GAP))
            ChipBelow(stack = stack!!, fees = fees, onClick = onFeeChipClick)
            Spacer(Modifier.height(CHIP_TO_FINAL_GAP))
            FinalCostBox(text = finalText, onLongClick = onFinalLongClick)
        }
    }
}

// Framed hero for the fee-adjusted final. A thin-bordered rounded box
// wraps the big number; the "Final cost" label sits on top of the border
// at the leading edge, painted over a surface-colored strip so the border
// visually breaks for the text and resumes after it (fieldset legend).
// A custom [Layout] measures the label's real height and offsets the box
// down by half of it so the top border stroke lands on the label's
// vertical center — no static "half height" guess.
@Composable
private fun FinalCostBox(
    text: String,
    onLongClick: () -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderWidthPx = with(LocalDensity.current) { FINAL_BOX_BORDER_WIDTH.roundToPx() }
    val labelInsetPx = with(LocalDensity.current) { FINAL_LABEL_START_INSET.roundToPx() }
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(
                        width = FINAL_BOX_BORDER_WIDTH,
                        color = borderColor,
                        shape = RoundedCornerShape(FINAL_BOX_CORNER_RADIUS),
                    ).padding(
                        horizontal = FINAL_BOX_HORIZONTAL_PADDING,
                        vertical = FINAL_BOX_VERTICAL_PADDING,
                    ),
            ) {
                AmountRow(
                    text = text,
                    fontSize = AMOUNT_HERO_SIZE,
                    fontWeight = FontWeight.Medium,
                    cursorHeight = null,
                    onLongClick = onLongClick,
                )
            }
            Text(
                text = stringResource(R.string.hero_final_cost_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier =
                    Modifier
                        .background(surfaceColor)
                        .padding(horizontal = FINAL_LABEL_MASK_PADDING),
            )
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val labelPlaceable = measurables[1].measure(loose)
        val boxPlaceable = measurables[0].measure(constraints)
        val labelHalf = labelPlaceable.height / 2
        val boxTop = (labelHalf - borderWidthPx / 2).coerceAtLeast(0)
        val width = constraints.maxWidth
        val height = boxTop + boxPlaceable.height
        layout(width, height) {
            boxPlaceable.place(0, boxTop)
            labelPlaceable.placeRelative(labelInsetPx, 0)
        }
    }
}

// Right-aligned amount row shared by the subtotal and final tiers. When
// [cursorHeight] is non-null a blinking primary-colored cursor renders to
// the right of the number (the subtotal is the one being typed; the derived
// final never carries a cursor).
@Composable
private fun AmountRow(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    cursorHeight: Dp?,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = fontWeight,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.End,
            style = TIGHT_TEXT_STYLE,
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberStartAnchoredScrollState(text)),
        )
        if (cursorHeight != null) BlinkingCursor(height = cursorHeight)
    }
}

@Composable
private fun MathLine(text: String?) {
    val hasMath = !text.isNullOrEmpty()
    Reserved(hasMath) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = MATH_LINE_BOTTOM_GAP),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = if (hasMath) "$text $OP_EQUALS" else " ",
                fontSize = MATH_LINE_TEXT_SIZE,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberEndAnchoredScrollState(text)),
            )
        }
    }
}

@Composable
private fun BlinkingCursor(height: Dp = CURSOR_HEIGHT) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = CURSOR_BLINK_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "cursorAlpha",
    )
    val primary = MaterialTheme.colorScheme.primary
    Spacer(
        Modifier
            .padding(start = 6.dp)
            .width(CURSOR_WIDTH)
            .height(height)
            .graphicsLayer { this.alpha = alpha }
            .background(primary),
    )
}

@Composable
private fun AmountToRow(
    text: String,
    onLongClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AMOUNT_BAND_RADIUS))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(AMOUNT_BAND_PADDING)
                .combinedClickable(onClick = {}, onLongClick = onLongClick),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = text,
            fontSize = AMOUNT_TO_SIZE,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.End,
            style = TIGHT_TEXT_STYLE,
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberStartAnchoredScrollState(text)),
        )
    }
}

// True when the fee stack is a real markup/markdown (not `1`, i.e. not
// a no-op). Used to decide whether the amber chip should render at all.
private fun BigDecimal?.hasFee(): Boolean = this != null && this.compareTo(BigDecimal.ONE) != 0

// True only when the big value is a real amount worth showing the red
// "after fee" pill for. If it's zero, the pill would read as `0` — not
// useful — so we drop it and let the chip alone convey the fee.
private fun BigDecimal?.isMeaningful(): Boolean = this != null && this.signum() != 0

// Sits between the subtotal and the hero final in the top card, taking no
// bottom padding of its own — the surrounding column adds symmetric spacers
// instead.
@Composable
private fun ChipBelow(
    stack: BigDecimal,
    fees: List<Fee>,
    onClick: () -> Unit,
) {
    Ltr {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeeChip(stack, fees, onClick)
        }
    }
}

// Wraps [content] so it always takes its natural layout size but only
// paints ink when [visible]. Used sparingly to hold a fixed slot open
// (math line) while its glyph fades in/out with state.
@Composable
private fun Reserved(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    Box(Modifier.alpha(if (visible) 1f else 0f)) { content() }
}

// Caps the child's max width at [fraction] of the parent's max width so it
// never grows past that fraction even when nothing else in the parent row
// competes for horizontal space.
private fun Modifier.maxWidthFraction(fraction: Float): Modifier =
    layout { measurable, constraints ->
        val capped = (constraints.maxWidth * fraction).toInt()
        val placeable = measurable.measure(constraints.copy(maxWidth = capped))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

// A ScrollState that snaps back to the start whenever [key] changes, so a
// fresh number always renders from its leading digit. Drags between changes
// are preserved.
@Composable
private fun rememberStartAnchoredScrollState(key: Any?): ScrollState {
    val state = rememberScrollState()
    LaunchedEffect(key) {
        state.scrollTo(0)
    }
    return state
}

// A ScrollState that snaps to the end whenever [key] changes or the content
// grows, so the freshest characters stay visible. Drags between changes are
// preserved.
@Composable
private fun rememberEndAnchoredScrollState(key: Any?): ScrollState {
    val state = rememberScrollState()
    LaunchedEffect(key, state.maxValue) {
        state.scrollTo(state.maxValue)
    }
    return state
}

// A ScrollState that lets the user drag the content freely, and — after
// [PILL_AUTO_SCROLL_IDLE_MILLIS] with no drag interaction — resumes an
// automatic end↔start scroll loop so overflowing pill content stays
// discoverable without requiring the user to interact. When [resetKey]
// changes, the scroll snaps back to the start so a fresh value renders
// from its leading digit.
@Composable
private fun rememberIdleAutoScrollState(resetKey: Any? = null): ScrollState {
    val state = rememberScrollState()
    val pxPerMs = PILL_AUTO_SCROLL_SPEED_DP_PER_S * LocalDensity.current.density / 1000f
    LaunchedEffect(resetKey) {
        state.scrollTo(0)
    }
    LaunchedEffect(state, pxPerMs) {
        val restart = MutableStateFlow(0L)
        launch {
            state.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start ||
                    interaction is DragInteraction.Stop ||
                    interaction is DragInteraction.Cancel
                ) {
                    restart.value = restart.value + 1L
                }
            }
        }
        restart.collectLatest {
            delay(PILL_AUTO_SCROLL_IDLE_MILLIS)
            while (state.maxValue > 0) {
                val trip = (state.maxValue / pxPerMs).toInt().coerceAtLeast(PILL_AUTO_SCROLL_MIN_TRIP_MILLIS)
                state.animateScrollTo(
                    value = state.maxValue,
                    animationSpec = tween(trip, easing = LinearEasing),
                )
                delay(PILL_AUTO_SCROLL_DWELL_MILLIS)
                state.animateScrollTo(
                    value = 0,
                    animationSpec = tween(PILL_AUTO_SCROLL_RETURN_MILLIS, easing = LinearEasing),
                )
                delay(PILL_AUTO_SCROLL_DWELL_MILLIS)
            }
        }
    }
    return state
}

// Displayable version of a full grouped amount ("310,500,000,000,000 ILS"),
// folded into compact form ("310.5T ILS") once the integer part crosses the
// K/M/B/T/Q threshold. Falls back to [full] when the value is null or short
// enough to stay legible as-is; the ViewModel's raw string is still what
// long-press-copy delivers, so precision isn't lost on the clipboard side.
private fun compactAmountOrFull(
    context: Context,
    full: String,
    value: BigDecimal?,
    currency: Currency?,
): String {
    val compact = value?.toCompactHumanReadableNumber(context) ?: return full
    return formatWithSymbol(compact, currency?.symbol(), hasAppendedCurrencySymbol(context))
}

// Full-formatting path for a derived money [value] (no ViewModel-supplied
// pre-formatted string available). Prefers the compact form for very long
// values, falls back to the standard grouped decimal, then prepends /
// appends the currency symbol per locale. Returns null when [value] is
// null so callers can substitute a fallback.
private fun formatMoneyForDisplay(
    context: Context,
    value: BigDecimal?,
    currency: Currency?,
    decimalPlaces: Int,
): String? {
    if (value == null) return null
    val body =
        value.toCompactHumanReadableNumber(context)
            ?: value.toHumanReadableNumber(context, trim = true, decimalPlaces = decimalPlaces)
    return formatWithSymbol(body, currency?.symbol(), hasAppendedCurrencySymbol(context))
}

// Locale-aware "$symbol number" / "number $symbol" — mirrors the ViewModel's
// buildBoldNumberWithSymbol so the pill never disagrees with the main
// amount display on which side the symbol lands.
private fun formatWithSymbol(
    number: String,
    symbol: String?,
    appended: Boolean,
): String =
    when {
        symbol.isNullOrEmpty() -> number
        appended -> "$number $symbol"
        else -> "$symbol $number"
    }

@Composable
private fun FeeChip(
    stack: BigDecimal,
    fees: List<Fee>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val percentText =
        remember(stack) {
            stack
                .feePercentDelta(FEE_PERCENT_DECIMAL_PLACES)
                .toHumanReadableNumber(context, showPositiveSign = true, suffix = "%", trim = true)
        }
    val namesText =
        remember(fees) {
            fees
                .mapNotNull { it.name.trim().takeIf(String::isNotEmpty) }
                .joinToString(FEE_NAME_SEPARATOR)
        }
    val bg = Amber.copy(alpha = FEE_CHIP_BG_ALPHA).compositeOver(MaterialTheme.colorScheme.surfaceVariant)
    Row(
        modifier
            .maxWidthFraction(FEE_CHIP_MAX_WIDTH_FRACTION)
            .clip(RoundedCornerShape(PILL_RADIUS))
            .background(bg)
            .hapticClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (namesText.isEmpty()) {
            FeeChipText(text = stringResource(R.string.fee_chip_label, percentText))
        } else {
            FeeChipText(text = "$percentText$FOOTER_SEPARATOR")
            FeeChipText(
                text = namesText,
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberIdleAutoScrollState()),
            )
        }
    }
}

@Composable
private fun FeeChipText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontSize = FEE_CHIP_TEXT_SIZE,
        fontWeight = FontWeight.Medium,
        color = Amber,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

@Composable
private fun RateFooter(
    base: Currency?,
    dest: Currency?,
    rates: ExchangeRates?,
    dateFormatPattern: String,
    onProviderClick: () -> Unit,
) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(Modifier.height(RATE_FOOTER_PADDING_TOP))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RateText(base = base, dest = dest, rates = rates)
            TimestampText(
                rates = rates,
                dateFormatPattern = dateFormatPattern,
                onProviderClick = onProviderClick,
            )
        }
    }
}

@Composable
private fun RateText(
    base: Currency?,
    dest: Currency?,
    rates: ExchangeRates?,
) {
    val context = LocalContext.current
    val rateText =
        remember(base, dest, rates) {
            buildRateText(context, base, dest, rates)
        } ?: return
    Text(
        text = rateText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TimestampText(
    rates: ExchangeRates?,
    dateFormatPattern: String,
    onProviderClick: () -> Unit,
) {
    val context = LocalContext.current
    val date = rates?.date ?: return
    val whenText =
        remember(date, rates.time, dateFormatPattern) {
            formatWhen(context, date, rates.time, dateFormatPattern)
        }
    val provider = rates.provider?.getName(context)?.toString()
    val text = if (provider.isNullOrEmpty()) whenText else "$whenText$FOOTER_SEPARATOR$provider"
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clickable(onClick = onProviderClick),
    )
}

/**
 * Compact "when" label — relative time ("12h ago") if the timestamp is within
 * the last 24h and includes a wall-clock time, otherwise the formatted date.
 */
private fun formatWhen(
    context: Context,
    date: LocalDate,
    time: LocalTime?,
    pattern: String,
): String {
    if (time != null) {
        val millis =
            date
                .atTime(time)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val now = System.currentTimeMillis()
        val delta = now - millis
        if (delta in 0 until RELATIVE_TIME_WINDOW_MS) {
            return DateUtils
                .getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
                .toString()
                .stripRtlMark()
        }
    }
    val effective = if (time != null) pattern else stripTimePattern(pattern)
    val temporal = if (time != null) date.atTime(time) else date
    return DateTimeFormatter.ofPattern(effective).format(temporal).stripRtlMark()
}

private fun buildRateText(
    context: Context,
    base: Currency?,
    dest: Currency?,
    rates: ExchangeRates?,
): String? {
    if (base == null || dest == null || rates == null) return null
    val baseValue = rates.rateFor(base)?.value ?: return null
    val destValue = rates.rateFor(dest)?.value ?: return null
    val perOne = destValue.divide(baseValue, MathContext.DECIMAL128)
    val amount = perOne.toHumanReadableNumber(context, trim = true, decimalPlaces = FOOTER_RATE_DECIMAL_PLACES)
    return context
        .getString(R.string.info_conversion, "1", base.iso4217Alpha(), amount, dest.iso4217Alpha())
        .fromHtmlLegacy()
        .toString()
}

private enum class PickSide { FROM, TO }

private fun openCurrencyPicker(
    context: Context,
    viewModel: MainViewModel,
    fragmentManager: FragmentManager,
    picking: PickSide,
    baseCurrency: Currency?,
    destCurrency: Currency?,
    rates: ExchangeRates?,
) {
    val disabled = if (picking == PickSide.FROM) destCurrency else baseCurrency
    // Reference-rate anchor for the picker's preview column: when picking the
    // FROM side, the fixed side is the current DEST currency (and vice versa).
    // The sum we're "converting" is likewise the OTHER side's current value.
    val referenceRate =
        if (picking == PickSide.FROM) {
            destCurrency?.let { c -> rates?.rateFor(c)?.let { Rate(c, it.value) } }
        } else {
            baseCurrency?.let { c -> rates?.rateFor(c)?.let { Rate(c, it.value) } }
        }
    val referenceSum =
        if (picking == PickSide.FROM) {
            viewModel.getResultAsNumber().value ?: BigDecimal.ONE
        } else {
            viewModel.getCurrentBaseValueAsNumber().value ?: BigDecimal.ONE
        }
    SearchableSpinnerDialog(context)
        .apply {
            referenceRate?.let { setCurrentRate(it) }
            setCurrentSum(referenceSum)
            setDisabledCurrency(disabled)
            onRateClicked = { rate, _ ->
                if (picking == PickSide.FROM) {
                    viewModel.setBaseCurrency(rate.currency)
                } else {
                    viewModel.setDestinationCurrency(rate.currency)
                }
            }
        }.show(fragmentManager, null)
}

// --- Small helpers ------------------------------------------------------------

// Rasterise a legacy XML `Drawable` into a Compose `Painter` so the flag
// assets keep working without a per-currency Compose-native rewrite.
private fun drawableToPainter(drawable: Drawable): Painter {
    val w = drawable.intrinsicWidth.coerceAtLeast(1)
    val h = drawable.intrinsicHeight.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, w, h)
    drawable.draw(canvas)
    return BitmapPainter(bmp.asImageBitmap())
}

// Composite two Colors — Compose has no `color-mix()` analog. Alpha of `this`
// is used as the mix ratio; result is opaque against [background].
private fun Color.compositeOver(background: Color): Color {
    val a = this.alpha
    val r = this.red * a + background.red * (1 - a)
    val g = this.green * a + background.green * (1 - a)
    val b = this.blue * a + background.blue * (1 - a)
    return Color(r, g, b, 1f)
}
