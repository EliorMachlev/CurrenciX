package com.eliormachlev.currencix.view.main.compose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.SideFees
import com.eliormachlev.currencix.model.SideStacks
import com.eliormachlev.currencix.model.rateFor
import com.eliormachlev.currencix.util.feePercentDelta
import com.eliormachlev.currencix.util.fromHtmlLegacy
import com.eliormachlev.currencix.util.hasAppendedCurrencySymbol
import com.eliormachlev.currencix.util.stripRtlMark
import com.eliormachlev.currencix.util.stripTimePattern
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.theme.Amber
import com.eliormachlev.currencix.view.compose.theme.Brass
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
private val CARD_PADDING: Dp = 20.dp
private val PILL_RADIUS: Dp = 999.dp
private val PILL_HEIGHT: Dp = 44.dp
private val PILL_HORIZONTAL_PADDING: Dp = 14.dp
private val FLAG_SIZE: Dp = 28.dp
private val SWAP_FAB_SIZE: Dp = 44.dp
private val PILLS_ROW_GAP: Dp = 8.dp
private val PILLS_ROW_BOTTOM_GAP: Dp = 20.dp
private val AMOUNT_DIVIDER_MARGIN_TOP: Dp = 12.dp
private val AMOUNT_DIVIDER_MARGIN_BOTTOM: Dp = 10.dp
private val FEE_CHIP_TOP_GAP: Dp = 0.dp

// Visual lift applied to fee rows so they render on top of the big
// value's font descender space (which is empty ink). Cheating layout
// via [Modifier.offset] keeps the divider below in place while pulling
// the fee cluster closer to the number above.
private val FEE_ROW_LIFT: Dp = 12.dp
private val RATE_FOOTER_TOP_MARGIN: Dp = 14.dp
private val RATE_FOOTER_PADDING_TOP: Dp = 12.dp
private val LIVE_DOT_SIZE: Dp = 6.dp
private val CURSOR_WIDTH: Dp = 2.dp
private val CURSOR_HEIGHT: Dp = 44.dp
private val FLAG_GAP: Dp = 10.dp
private val CHEVRON_GAP: Dp = 4.dp
private val CHEVRON_SIZE: Dp = 14.dp

// Amount-hero / amount-to display sizes. One-off, not part of the Typography
// scale (Material3 would try to apply them elsewhere).
private val AMOUNT_HERO_SIZE = 52.sp
private val AMOUNT_TO_SIZE = 34.sp
private val FEE_CHIP_TEXT_SIZE = 11.sp
private val MATH_LINE_TEXT_SIZE = 14.sp

// Applied to the big-value texts so Android's default font padding
// (~4-6 dp above/below the glyph on top of lineHeight) doesn't inflate
// the visual gap between the number and whatever renders right below it.
private val TIGHT_TEXT_STYLE = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

// Fallback rounding for the final-value pill amount when the user's
// decimal-places preference hasn't loaded yet — matches the ViewModel's
// own default seed for the result formatter.
private const val FINAL_VALUE_DECIMAL_PLACES_FALLBACK = 2

// Horizontal gap between the fee-equation row elements (operator glyphs,
// amber chip, red final-value pill).
private val FEE_ROW_PILL_GAP: Dp = 4.dp

// Vertical gap inside the operator stack (Column of "+" over "=") for the
// markup case. Wider than a bare line height so the "+" isn't jammed
// against the "=" but centers between the big value above and the chip
// baseline below.
private val FEE_OP_STACK_GAP: Dp = 4.dp

// Operator glyphs joining the big value, fee chip, and final-value pill
// into a readable equation. Uses U+2212 (minus) rather than a hyphen so the
// glyph stays visually balanced with the "+".
private const val OP_PLUS = "+"
private const val OP_MINUS = "\u2212"
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

private const val CURSOR_BLINK_MILLIS = 500
private const val LIVE_DOT_PULSE_MILLIS = 1000
private const val LIVE_DOT_PULSE_MIN_ALPHA = 0.4f

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
    val sideStacks by viewModel.getSideStacks().observeAsState()
    val sideFees by viewModel.getSideFees().observeAsState()
    val historicalDate by viewModel.getHistoricalLiveDate().observeAsState()
    val mathText by viewModel.getCalculationInputFormatted().observeAsState()
    val baseValueNumber by viewModel.getCurrentBaseValueAsNumber().observeAsState()
    val resultNumber by viewModel.getResultAsNumber().observeAsState()
    val trueCost by viewModel.getTrueCost().observeAsState()
    val originalValue by viewModel.getOriginalValue().observeAsState()
    val decimalPlaces by viewModel.getDecimalPlaces().observeAsState(FINAL_VALUE_DECIMAL_PLACES_FALLBACK)

    HeroCard(
        baseCurrency = baseCurrency,
        destCurrency = destCurrency,
        baseFormatted = baseFormatted?.toString().orEmpty(),
        resultFormatted = resultFormatted?.toString().orEmpty(),
        rates = rates,
        isUpdating = isUpdating,
        sideStacks = sideStacks,
        sideFees = sideFees,
        historicalDate = historicalDate,
        mathText = mathText,
        originalBig = baseValueNumber,
        originalOther = trueCost,
        convertedBig = resultNumber,
        convertedOther = originalValue,
        decimalPlaces = decimalPlaces,
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
    resultFormatted: String,
    rates: ExchangeRates?,
    isUpdating: Boolean,
    sideStacks: SideStacks?,
    sideFees: SideFees?,
    historicalDate: LocalDate?,
    mathText: String?,
    originalBig: BigDecimal?,
    originalOther: BigDecimal?,
    convertedBig: BigDecimal?,
    convertedOther: BigDecimal?,
    decimalPlaces: Int,
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
                text = baseFormatted,
                mathText = mathText,
                onLongClick = { if (baseFormatted.isNotEmpty()) callbacks.onCopy(baseFormatted) },
                stack = sideStacks?.original,
                fees = sideFees?.original.orEmpty(),
                bigValue = originalBig,
                otherValue = originalOther,
                currency = baseCurrency,
                decimalPlaces = decimalPlaces,
                onFeeChipClick = callbacks.onOpenFees,
            )
            AmountDivider()
            AmountToRow(
                text = resultFormatted,
                stack = sideStacks?.converted,
                fees = sideFees?.converted.orEmpty(),
                bigValue = convertedBig,
                otherValue = convertedOther,
                currency = destCurrency,
                decimalPlaces = decimalPlaces,
                onLongClick = { if (resultFormatted.isNotEmpty()) callbacks.onCopy(resultFormatted) },
                onFeeChipClick = callbacks.onOpenFees,
            )
            Spacer(Modifier.height(RATE_FOOTER_TOP_MARGIN))
            RateFooter(
                base = baseCurrency,
                dest = destCurrency,
                rates = rates,
                isUpdating = isUpdating,
                historicalDate = historicalDate,
                dateFormatPattern = dateFormatPattern,
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
            .clickable(enabled = currency != null, onClick = onClick)
            .padding(horizontal = PILL_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (flagPainter != null) {
            Image(
                painter = flagPainter,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(FLAG_SIZE)
                        .clip(CircleShape),
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
    Box(
        Modifier
            .size(SWAP_FAB_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.SwapHoriz,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun AmountHero(
    text: String,
    mathText: String?,
    onLongClick: () -> Unit,
    stack: BigDecimal?,
    fees: List<Fee>,
    bigValue: BigDecimal?,
    otherValue: BigDecimal?,
    currency: Currency?,
    decimalPlaces: Int,
    onFeeChipClick: () -> Unit,
) {
    val op = feeOpFor(stack)
    val meaningful = bigValue.isMeaningful()
    Column(Modifier.fillMaxWidth()) {
        MathLine(mathText)
        if (op == FeeOp.MINUS && meaningful) {
            FeeAboveRow(stack = stack!!, fees = fees, onClick = onFeeChipClick)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = onLongClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = text,
                fontSize = AMOUNT_HERO_SIZE,
                lineHeight = AMOUNT_HERO_SIZE,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                style = TIGHT_TEXT_STYLE,
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberEndAnchoredScrollState(text)),
            )
            BlinkingCursor()
        }
        FeeEquationTail(
            op = op,
            stack = stack,
            fees = fees,
            meaningful = meaningful,
            finalValue = otherValue,
            currency = currency,
            decimalPlaces = decimalPlaces,
            onClick = onFeeChipClick,
        )
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
private fun BlinkingCursor() {
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
            .height(CURSOR_HEIGHT)
            .graphicsLayer { this.alpha = alpha }
            .background(primary),
    )
}

@Composable
private fun AmountDivider() {
    Spacer(Modifier.height(AMOUNT_DIVIDER_MARGIN_TOP))
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
    Spacer(Modifier.height(AMOUNT_DIVIDER_MARGIN_BOTTOM))
}

@Composable
private fun AmountToRow(
    text: String,
    stack: BigDecimal?,
    fees: List<Fee>,
    bigValue: BigDecimal?,
    otherValue: BigDecimal?,
    currency: Currency?,
    decimalPlaces: Int,
    onLongClick: () -> Unit,
    onFeeChipClick: () -> Unit,
) {
    val op = feeOpFor(stack)
    val meaningful = bigValue.isMeaningful()
    Column(Modifier.fillMaxWidth()) {
        if (op == FeeOp.MINUS && meaningful) {
            FeeAboveRow(stack = stack!!, fees = fees, onClick = onFeeChipClick)
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = onLongClick),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = text,
                fontSize = AMOUNT_TO_SIZE,
                fontWeight = FontWeight.Medium,
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
        FeeEquationTail(
            op = op,
            stack = stack,
            fees = fees,
            meaningful = meaningful,
            finalValue = otherValue,
            currency = currency,
            decimalPlaces = decimalPlaces,
            onClick = onFeeChipClick,
        )
    }
}

// PLUS means the net fee stack is a markup (>1) and adds to the big value to
// reach the final; MINUS means it's a markdown (<1) and subtracts. Derived
// from the stack alone (not from the value comparison) so the chip still
// renders when the big value is zero — the sign reflects the fee's own
// direction, not a value delta.
private enum class FeeOp { PLUS, MINUS }

private fun feeOpFor(stack: BigDecimal?): FeeOp? {
    if (stack == null) return null
    val cmp = stack.compareTo(BigDecimal.ONE)
    return when {
        cmp > 0 -> FeeOp.PLUS
        cmp < 0 -> FeeOp.MINUS
        else -> null
    }
}

// True only when the big value is a real amount worth showing the equation
// closure for. If it's zero, `= 0` is uninformative, so we drop the operator
// and pill and just show the chip alone.
private fun BigDecimal?.isMeaningful(): Boolean = this != null && this.signum() != 0

// Rendered above the big value when the fee subtracts from it — user reads
// "amber chip minus, applied to value below, equals final pill".
@Composable
private fun FeeAboveRow(
    stack: BigDecimal,
    fees: List<Fee>,
    onClick: () -> Unit,
) {
    Ltr {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = FEE_CHIP_TOP_GAP),
            horizontalArrangement = Arrangement.spacedBy(FEE_ROW_PILL_GAP, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeeChip(stack, fees, onClick)
            FeeOperator(OP_MINUS)
        }
    }
}

// Below-the-big-value fee row. Chip, `=` and pill are added / removed
// with fee state, but the `+` slot in the operator column is always
// reserved so the vertical rhythm above the equation doesn't shift
// as the user types (`+` only paints ink for meaningful MARKUP; its
// layout footprint is always there).
@Composable
private fun FeeEquationTail(
    op: FeeOp?,
    stack: BigDecimal?,
    fees: List<Fee>,
    meaningful: Boolean,
    finalValue: BigDecimal?,
    currency: Currency?,
    decimalPlaces: Int,
    onClick: () -> Unit,
) {
    if (op == null || stack == null) return
    val showEquation = meaningful && finalValue != null
    if (op == FeeOp.PLUS) {
        FeeRowRightAligned(verticalAlignment = Alignment.Bottom) {
            FeeChip(stack, fees, onClick)
            Column(
                modifier = if (showEquation) Modifier else Modifier.zeroWidthKeepHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FEE_OP_STACK_GAP),
            ) {
                Reserved(showEquation) { FeeOperator(OP_PLUS) }
                Reserved(showEquation) { FeeOperator(OP_EQUALS) }
            }
            if (showEquation && finalValue != null) {
                FinalValueChip(
                    value = finalValue,
                    currency = currency,
                    decimalPlaces = decimalPlaces,
                    onClick = onClick,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    } else if (showEquation && finalValue != null) {
        FeeRowRightAligned {
            FeeOperator(OP_EQUALS)
            FinalValueChip(
                value = finalValue,
                currency = currency,
                decimalPlaces = decimalPlaces,
                onClick = onClick,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    } else {
        FeeRowRightAligned { FeeChip(stack, fees, onClick) }
    }
}

// Wraps [content] so it always takes its natural layout size but only
// paints ink when [visible]. Used sparingly to hold a fixed slot open
// (math line, `+` operator) while its glyph fades in/out with state.
@Composable
private fun Reserved(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    Box(Modifier.alpha(if (visible) 1f else 0f)) { content() }
}

// Measures children at their natural size but reports zero width to the
// parent — vertical rhythm above is preserved while the sibling to the
// right can flush against the edge as if this node weren't there.
private fun Modifier.zeroWidthKeepHeight(): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(0, placeable.height) { placeable.place(0, 0) }
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

// A ScrollState that snaps to the end whenever [key] changes or the content
// grows, so freshly typed digits stay visible without the user having to
// scroll. User drags between changes are preserved.
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
// discoverable without requiring the user to interact.
@Composable
private fun rememberIdleAutoScrollState(): ScrollState {
    val state = rememberScrollState()
    val pxPerMs = PILL_AUTO_SCROLL_SPEED_DP_PER_S * LocalDensity.current.density / 1000f
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

// Right-aligned row with a leading vertical gap — shared by every below-the-
// big-value fee row so their spacing stays consistent. Wrapped in [Ltr] so
// operator glyphs sit visually to the left of the chip/pill even under an
// RTL app locale (math notation reads L→R everywhere).
@Composable
private fun FeeRowRightAligned(
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Ltr {
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = -FEE_ROW_LIFT),
            horizontalArrangement = Arrangement.spacedBy(FEE_ROW_PILL_GAP, Alignment.End),
            verticalAlignment = verticalAlignment,
        ) {
            content()
        }
    }
}

@Composable
private fun FeeOperator(glyph: String) {
    Text(
        text = glyph,
        fontSize = FEE_CHIP_TEXT_SIZE,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FinalValueChip(
    value: BigDecimal,
    currency: Currency?,
    decimalPlaces: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val symbolAppended = remember(context) { hasAppendedCurrencySymbol(context) }
    val symbol = currency?.symbol()
    val label =
        remember(value, symbol, symbolAppended, decimalPlaces) {
            formatWithSymbol(
                value.toHumanReadableNumber(context, trim = true, decimalPlaces = decimalPlaces),
                symbol,
                symbolAppended,
            )
        }
    val errorColor = MaterialTheme.colorScheme.error
    val bg = errorColor.copy(alpha = FEE_CHIP_BG_ALPHA).compositeOver(MaterialTheme.colorScheme.surfaceVariant)
    Row(
        modifier
            .clip(RoundedCornerShape(PILL_RADIUS))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = FEE_CHIP_TEXT_SIZE,
            fontWeight = FontWeight.Medium,
            color = errorColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberIdleAutoScrollState()),
        )
    }
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
            .clickable(onClick = onClick)
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
    isUpdating: Boolean,
    historicalDate: LocalDate?,
    dateFormatPattern: String,
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
            LiveRate(
                base = base,
                dest = dest,
                rates = rates,
                isPulsing = isUpdating,
                isHistorical = historicalDate != null,
            )
            TimestampText(
                rates = rates,
                dateFormatPattern = dateFormatPattern,
                isHistorical = historicalDate != null,
            )
        }
    }
}

@Composable
private fun LiveRate(
    base: Currency?,
    dest: Currency?,
    rates: ExchangeRates?,
    isPulsing: Boolean,
    isHistorical: Boolean,
) {
    val context = LocalContext.current
    val rateText =
        remember(base, dest, rates) {
            buildRateText(context, base, dest, rates)
        } ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val dotColor = if (isHistorical) Brass else MaterialTheme.colorScheme.primary
        LiveDot(color = dotColor, pulsing = isPulsing)
        Text(
            text = rateText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveDot(
    color: Color,
    pulsing: Boolean,
) {
    val alpha =
        if (pulsing) {
            val transition = rememberInfiniteTransition(label = "livedot")
            val v by transition.animateFloat(
                initialValue = 1f,
                targetValue = LIVE_DOT_PULSE_MIN_ALPHA,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = LIVE_DOT_PULSE_MILLIS, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "livedotAlpha",
            )
            v
        } else {
            1f
        }
    Spacer(
        Modifier
            .size(LIVE_DOT_SIZE)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun TimestampText(
    rates: ExchangeRates?,
    dateFormatPattern: String,
    isHistorical: Boolean,
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
        color = if (isHistorical) Brass else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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
