package com.eliormachlev.currencix.view.main.compose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseInOutSine
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.theme.BillGreen
import com.eliormachlev.currencix.view.compose.theme.Stamp
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

// Vertical gap between the two stacked receipt panels ("You pay" over
// "You get"). Matches the mock's 18px so the labels breathe without
// separating the panels into disconnected cards.
private val PANEL_STACK_GAP: Dp = 14.dp

// Ambient shadow under the hero card so it lifts off the background. Kept
// modest — Material3 elevated cards usually sit at 1–3dp for the "resting"
// affordance; anything higher starts to feel floaty over the dark keypad.
private val CARD_ELEVATION: Dp = 3.dp

// Swap FAB rotation animation — one 180° flip per tap. The counter drives
// a target angle so successive taps keep spinning in the same direction
// (never snap back), and animateFloatAsState handles the tween.
private const val SWAP_FAB_ROTATION_STEP = 180f
private const val SWAP_FAB_ROTATION_MILLIS = 320

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

// Currency symbol renders at ~62% of the digits size so it reads as a
// label riding alongside the number instead of competing with it.
private const val SYMBOL_SIZE_RATIO = 0.62f
private val AMOUNT_HERO_SYMBOL_SIZE = AMOUNT_HERO_SIZE * SYMBOL_SIZE_RATIO
private val AMOUNT_SUBTOTAL_SYMBOL_SIZE = AMOUNT_SUBTOTAL_SIZE * SYMBOL_SIZE_RATIO
private val AMOUNT_TO_SYMBOL_SIZE = AMOUNT_TO_SIZE * SYMBOL_SIZE_RATIO

// Pinned-symbol dim so the number is the primary read.
private const val SYMBOL_ALPHA = 0.65f
private val SYMBOL_GAP: Dp = 6.dp

// Gap between the medium subtotal row and the fee chip that follows it.
// Tuned so the two read as one vertical equation without doubling the
// card height.
private val SUBTOTAL_TO_CHIP_GAP: Dp = 4.dp

// The two hero panels share a fieldset-legend frame — "YOU PAY" and
// "YOU GET" labels sit ON the top border. The label paints a strip of
// the card's surface color behind itself so the border appears to break
// for the text and resume after it. Vertical centering of the label on
// the stroke is done at measure time in [ReceiptPanel] using the label's
// actual rendered height, so there's no static half-height constant here.
private val PANEL_BORDER_WIDTH: Dp = 1.dp
private val PANEL_CORNER_RADIUS: Dp = 16.dp
private val PANEL_HORIZONTAL_PADDING: Dp = 14.dp
private val PANEL_VERTICAL_PADDING: Dp = 12.dp

// How far the legend sits from the panel's leading corner along the top
// border, and the horizontal padding around the label's surface-colored
// mask so it's a bit wider than the text on each side — that padding is
// what makes the border look like it "breaks" for the legend.
private val PANEL_LABEL_START_INSET: Dp = 14.dp
private val PANEL_LABEL_MASK_PADDING: Dp = 6.dp
private val PANEL_LABEL_LETTER_SPACING = 0.14.em

// Thin receipt-rule between the fee stamp and the engraved final source,
// rendered only when the fee chain is showing. Right-anchored and narrow
// so it reads as a subtotal line on the receipt.
private val PAY_RULE_HEIGHT: Dp = 1.dp
private const val PAY_RULE_WIDTH_FRACTION = 0.6f
private val PAY_RULE_TOP_GAP: Dp = 6.dp
private val PAY_RULE_BOTTOM_GAP: Dp = 4.dp

// Guilloché plate — a stack of faint concentric ellipses drawn behind the
// engraved final so the number reads as it's been pressed into a banknote
// rosette. Ring geometry mirrors the design mock (30 rings, rx grows by
// 6dp per ring from 30dp, ry grows by 4dp from 18dp). The center is
// biased to the right so the plate anchors under the amount cluster (the
// Row is right-aligned), and the color derives from the app's primary
// BillGreen at very low opacity so it never fights the number.
private const val GUILLOCHE_RING_COUNT = 30
private val GUILLOCHE_BASE_RX: Dp = 30.dp
private val GUILLOCHE_BASE_RY: Dp = 18.dp
private val GUILLOCHE_STEP_RX: Dp = 6.dp
private val GUILLOCHE_STEP_RY: Dp = 4.dp
private val GUILLOCHE_STROKE_WIDTH: Dp = 0.6.dp
private const val GUILLOCHE_CENTER_X_FRACTION = 0.72f
private const val GUILLOCHE_CENTER_Y_FRACTION = 0.5f

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

// Fade duration when engraved amounts (final source, You-get destination)
// swap digits. Short enough that fast keystrokes just read as a subtle
// blur rather than a laggy slide-in; long enough that a rate refresh
// visibly "reads" as a change instead of a hard replace.
private const val ENGRAVED_DIGITS_FADE_MILLIS = 160

// Pulsing dot next to the footer timestamp. Signals that the rates in view
// are live (the timestamp exists, we're not offline). Slow, low-contrast
// pulse — deliberately more heartbeat than blinker.
private const val LIVE_PULSE_MILLIS = 1400
private val LIVE_PULSE_DOT_SIZE: Dp = 6.dp
private val LIVE_PULSE_DOT_GAP: Dp = 6.dp
private const val LIVE_PULSE_ALPHA_MIN = 0.35f
private const val LIVE_PULSE_ALPHA_MAX = 1f

// Feathered background tint applied under the amber fee text. 15% of amber
// composited over the pill's normal surface variant.
private const val FEE_CHIP_BG_ALPHA = 0.15f

// Cap the crimson fee-stamp at a fraction of its parent row so it can
// never grow past that even when nothing else competes; when the name
// exceeds the cap it marquees inside the stamp under a leading fade.
private const val FEE_CHIP_MAX_WIDTH_FRACTION = 0.5f

// Fee-stamp shape metrics — a thin-bordered rectangle (small radius so
// it reads as an ink stamp, not a pill) with pinned percent + marquee
// name inside.
private val FEE_STAMP_CORNER_RADIUS: Dp = 4.dp
private val FEE_STAMP_BORDER_WIDTH: Dp = 1.dp
private val FEE_STAMP_HORIZONTAL_PADDING: Dp = 8.dp
private val FEE_STAMP_VERTICAL_PADDING: Dp = 3.dp
private val FEE_STAMP_INNER_GAP: Dp = 8.dp
private val FEE_STAMP_LETTER_SPACING = 0.06.em
private val FEE_STAMP_OP_GAP: Dp = 6.dp
private const val FEE_STAMP_OP_PREFIX = "+"

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
    val resultFairFormatted by viewModel.getResultFormatted().observeAsState()
    val resultWithFeesFormatted by viewModel.getResultWithFeesFormatted().observeAsState()
    val rates by viewModel.getExchangeRates().observeAsState()
    val isUpdating by viewModel.isUpdating().observeAsState(false)
    val feeStack by viewModel.getFeeStack().observeAsState()
    val activeFees by viewModel.getActiveFees().observeAsState()
    val mathText by viewModel.getCalculationInputFormatted().observeAsState()
    val resultFairNumber by viewModel.getResultAsNumber().observeAsState()
    val resultWithFeesNumber by viewModel.getResultWithFeesAsNumber().observeAsState()

    val baseFull = baseFormatted?.toString().orEmpty()
    val resultFairFull = resultFairFormatted?.toString().orEmpty()
    val resultWithFeesFull = resultWithFeesFormatted?.toString().orEmpty()
    // Split each formatted string into its (symbol, digits) parts so the
    // symbol can be pinned outside the scrolling digits row.
    val baseParts = remember(baseFull, baseCurrency) { splitAmount(context, baseFull, baseCurrency) }
    val resultParts = remember(resultFairFull, destCurrency) { splitAmount(context, resultFairFull, destCurrency) }
    val trueCostParts =
        remember(resultWithFeesFull, destCurrency) { splitAmount(context, resultWithFeesFull, destCurrency) }
    HeroCard(
        baseCurrency = baseCurrency,
        destCurrency = destCurrency,
        baseParts = baseParts,
        resultParts = resultParts,
        trueCostParts = trueCostParts,
        baseCopyText = baseFull,
        resultCopyText = resultFairFull,
        trueCostCopyText = resultWithFeesFull,
        rates = rates,
        isUpdating = isUpdating,
        feeStack = feeStack,
        activeFees = activeFees.orEmpty(),
        mathText = mathText,
        resultFairNumber = resultFairNumber,
        resultWithFeesNumber = resultWithFeesNumber,
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
    baseParts: AmountParts,
    resultParts: AmountParts,
    trueCostParts: AmountParts,
    baseCopyText: String,
    resultCopyText: String,
    trueCostCopyText: String,
    rates: ExchangeRates?,
    isUpdating: Boolean,
    feeStack: BigDecimal?,
    activeFees: List<Fee>,
    mathText: String?,
    resultFairNumber: BigDecimal?,
    resultWithFeesNumber: BigDecimal?,
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
                currency = baseCurrency,
                subtotalParts = baseParts,
                mathText = mathText,
                onSubtotalLongClick = { if (baseCopyText.isNotEmpty()) callbacks.onCopy(baseCopyText) },
            )
            Spacer(Modifier.height(PANEL_STACK_GAP))
            AmountToRow(
                currency = destCurrency,
                resultParts = resultParts,
                trueCostParts = trueCostParts,
                stack = feeStack,
                fees = activeFees,
                bigValue = resultFairNumber,
                otherValue = resultWithFeesNumber,
                onResultLongClick = { if (resultCopyText.isNotEmpty()) callbacks.onCopy(resultCopyText) },
                onTrueCostLongClick = { if (trueCostCopyText.isNotEmpty()) callbacks.onCopy(trueCostCopyText) },
                onFeeChipClick = callbacks.onOpenFees,
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
        horizontalArrangement = Arrangement.Center,
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

// AmountHero — the source-currency receipt panel. Renders only the running
// calculator math line and the typed subtotal; the fee stamp + engraved
// final live in the destination panel below (since real-world FX fees are
// always charged on the post-conversion amount, not the source subtotal).
@Composable
private fun AmountHero(
    currency: Currency?,
    subtotalParts: AmountParts,
    mathText: String?,
    onSubtotalLongClick: () -> Unit,
) {
    val context = LocalContext.current
    ReceiptPanel(label = currency.panelLabel(context)) {
        Column(Modifier.fillMaxWidth()) {
            MathLine(mathText)
            ScrollingAmount(
                parts = subtotalParts,
                digitsSize = AMOUNT_SUBTOTAL_SIZE,
                symbolSize = AMOUNT_SUBTOTAL_SYMBOL_SIZE,
                fontWeight = FontWeight.Medium,
                cursorHeight = CURSOR_HEIGHT_SUBTOTAL,
                onLongClick = onSubtotalLongClick,
            )
            // Mirrors the math-line reservation above so the subtotal
            // sits at the panel's vertical center instead of the bottom
            // when there's no math text to render.
            MathLine(null)
        }
    }
}

// Fieldset-legend framed panel shared by "You pay" and "You get". A
// thin-bordered rounded box wraps the content; the [label] sits on top
// of the border at the leading edge, painted over a surface-colored
// strip so the border visually breaks for the text and resumes after it.
// A custom [Layout] measures the label's real height and offsets the box
// down by half of it so the top border stroke lands on the label's
// vertical center — no static "half height" guess.
@Composable
private fun ReceiptPanel(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderWidthPx = with(LocalDensity.current) { PANEL_BORDER_WIDTH.roundToPx() }
    val labelInsetPx = with(LocalDensity.current) { PANEL_LABEL_START_INSET.roundToPx() }
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(
                        width = PANEL_BORDER_WIDTH,
                        color = borderColor,
                        shape = RoundedCornerShape(PANEL_CORNER_RADIUS),
                    ).padding(
                        horizontal = PANEL_HORIZONTAL_PADDING,
                        vertical = PANEL_VERTICAL_PADDING,
                    ),
            ) {
                content()
            }
            Text(
                text = label.uppercase(),
                fontSize = FEE_CHIP_TEXT_SIZE,
                fontFamily = FontFamily.Monospace,
                letterSpacing = PANEL_LABEL_LETTER_SPACING,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier =
                    Modifier
                        .background(surfaceColor)
                        .padding(horizontal = PANEL_LABEL_MASK_PADDING),
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

// Thin receipt-rule between the fee stamp and the engraved final source.
// Right-anchored at 60% width so it reads as a subtotal line — the same
// place the human eye expects the running math to end on a receipt.
@Composable
private fun PayRule() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Spacer(
            Modifier
                .fillMaxWidth(PAY_RULE_WIDTH_FRACTION)
                .height(PAY_RULE_HEIGHT)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}

// Right-aligned amount row shared by the subtotal and final tiers. The
// currency symbol is pinned on the leading edge outside the scrolling
// digits, so `$ € ¥` stays visible while long numbers scroll horizontally
// under a leading fade mask. When [cursorHeight] is non-null a blinking
// primary-colored cursor renders after the number.
@Composable
private fun ScrollingAmount(
    parts: AmountParts,
    digitsSize: TextUnit,
    symbolSize: TextUnit,
    fontWeight: FontWeight,
    cursorHeight: Dp?,
    onLongClick: () -> Unit,
    fontFamily: FontFamily? = null,
) {
    val color = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.End,
    ) {
        if (parts.symbol.isNotEmpty()) {
            Text(
                text = parts.symbol,
                fontSize = symbolSize,
                lineHeight = digitsSize,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                color = color.copy(alpha = SYMBOL_ALPHA),
                maxLines = 1,
                softWrap = false,
                style = TIGHT_TEXT_STYLE,
                modifier = Modifier.padding(end = SYMBOL_GAP),
            )
        }
        val digitsText: @Composable (String) -> Unit = { value ->
            Text(
                text = value,
                fontSize = digitsSize,
                lineHeight = digitsSize,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                color = color,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                style = TIGHT_TEXT_STYLE,
                modifier = Modifier.horizontalScroll(rememberStartAnchoredScrollState(value)),
            )
        }
        if (cursorHeight != null) {
            // Typed input — direct render so the caret follows keystrokes
            // without an inter-glyph crossfade.
            Box(Modifier.weight(1f, fill = false)) { digitsText(parts.digits) }
        } else {
            Crossfade(
                targetState = parts.digits,
                modifier = Modifier.weight(1f, fill = false),
                animationSpec = tween(durationMillis = ENGRAVED_DIGITS_FADE_MILLIS),
                label = "engraved-digits",
            ) { value -> digitsText(value) }
        }
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
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                style = TIGHT_TEXT_STYLE,
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

// AmountToRow — the "True Cost" receipt panel. Mirrors the receipt-math
// stack that used to live on the You-pay side, but on the destination
// currency: fee-free result (small subtotal) → fee stamp → receipt rule
// → engraved fee-adjusted final (hero, serif, guilloché-backed). Without
// a fee, only the fair-conversion number renders at the medium hero
// size — no stamp, no rule.
@Composable
private fun AmountToRow(
    currency: Currency?,
    resultParts: AmountParts,
    trueCostParts: AmountParts,
    stack: BigDecimal?,
    fees: List<Fee>,
    bigValue: BigDecimal?,
    otherValue: BigDecimal?,
    onResultLongClick: () -> Unit,
    onTrueCostLongClick: () -> Unit,
    onFeeChipClick: () -> Unit,
) {
    val context = LocalContext.current
    val hasFee = stack.hasFee()
    val showChain = hasFee && bigValue.isMeaningful() && otherValue != null
    ReceiptPanel(label = currency.panelLabel(context)) {
        Column(Modifier.fillMaxWidth()) {
            if (showChain) {
                ScrollingAmount(
                    parts = resultParts,
                    digitsSize = AMOUNT_SUBTOTAL_SIZE,
                    symbolSize = AMOUNT_SUBTOTAL_SYMBOL_SIZE,
                    fontWeight = FontWeight.Medium,
                    cursorHeight = null,
                    onLongClick = onResultLongClick,
                )
                Spacer(Modifier.height(SUBTOTAL_TO_CHIP_GAP))
                ChipBelow(stack = stack!!, fees = fees, onClick = onFeeChipClick)
                Spacer(Modifier.height(PAY_RULE_TOP_GAP))
                PayRule()
                Spacer(Modifier.height(PAY_RULE_BOTTOM_GAP))
                Box(Modifier.guillocheBackground()) {
                    ScrollingAmount(
                        parts = trueCostParts,
                        digitsSize = AMOUNT_HERO_SIZE,
                        symbolSize = AMOUNT_HERO_SYMBOL_SIZE,
                        fontWeight = FontWeight.SemiBold,
                        cursorHeight = null,
                        onLongClick = onTrueCostLongClick,
                        fontFamily = FontFamily.Serif,
                    )
                }
            } else {
                ScrollingAmount(
                    parts = resultParts,
                    digitsSize = AMOUNT_TO_SIZE,
                    symbolSize = AMOUNT_TO_SYMBOL_SIZE,
                    fontWeight = FontWeight.SemiBold,
                    cursorHeight = null,
                    onLongClick = onResultLongClick,
                    fontFamily = FontFamily.Serif,
                )
            }
        }
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
// instead. Renders as receipt math: a dim `+` operator outside the stamp,
// then the crimson revenue-stamp with the percent + fee name inside it.
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
            Text(
                text = FEE_STAMP_OP_PREFIX,
                fontSize = FEE_CHIP_TEXT_SIZE,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = FEE_STAMP_OP_GAP),
            )
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

// The pinned-symbol + scrolling-digits display shape needs the two parts
// separated so the digits can scroll under a fade mask while the symbol
// stays visible.  [full] holds the joined form for clipboard / copy paths.
internal data class AmountParts(
    val symbol: String,
    val digits: String,
) {
    val full: String get() = if (symbol.isEmpty()) digits else "$symbol $digits"
}

// Receipt-panel legend for the given currency: localized full name +
// "(ISO SYMBOL)" (or "(ISO)" when no symbol is defined). Returns an empty
// string for a null currency so the panel renders without a label until
// the pair resolves.
private fun Currency?.panelLabel(context: Context): String {
    val currency = this ?: return ""
    val name = currency.fullName(context)
    val iso = currency.iso4217Alpha()
    val symbol = currency.symbol()
    return if (symbol.isNullOrEmpty()) "$name ($iso)" else "$name ($iso $symbol)"
}

// Peel the currency symbol off a preformatted "$ 240.00" / "240.00 $"
// string. Locale decides which side the symbol was appended to; that
// same decision is what we invert here.
private fun splitAmount(
    context: Context,
    formatted: String,
    currency: Currency?,
): AmountParts {
    val symbol = currency?.symbol().orEmpty()
    if (symbol.isEmpty() || formatted.isEmpty()) return AmountParts("", formatted)
    val digits =
        if (hasAppendedCurrencySymbol(context)) {
            formatted.removeSuffix(symbol).trimEnd()
        } else {
            formatted.removePrefix(symbol).trimStart()
        }
    return AmountParts(symbol, digits)
}

// Guilloché plate — 30 concentric ellipses centered to the right of the
// engraved final, drawn faintly in the primary bill-green so the number
// reads as it's been engraved over a banknote's rosette. Ring geometry
// mirrors the mock (rx=30+i·6, ry=18+i·4, cx=70% of width, cy=middle).
// The stroke color derives from the theme so we get the right ink shade
// for both paper and ink surfaces.
@Composable
private fun Modifier.guillocheBackground(): Modifier {
    val ringColor =
        BillGreen.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.10f else 0.055f)
    val strokeWidthPx = with(LocalDensity.current) { GUILLOCHE_STROKE_WIDTH.toPx() }
    return drawBehind {
        val cx = size.width * GUILLOCHE_CENTER_X_FRACTION
        val cy = size.height * GUILLOCHE_CENTER_Y_FRACTION
        val stroke = Stroke(width = strokeWidthPx)
        repeat(GUILLOCHE_RING_COUNT) { i ->
            val rx = GUILLOCHE_BASE_RX.toPx() + i * GUILLOCHE_STEP_RX.toPx()
            val ry = GUILLOCHE_BASE_RY.toPx() + i * GUILLOCHE_STEP_RY.toPx()
            drawOval(
                color = ringColor,
                topLeft = Offset(cx - rx, cy - ry),
                size = Size(rx * 2, ry * 2),
                style = stroke,
            )
        }
    }
}

// Revenue-stamp for the fee row. Rectangular thin border, crimson ink,
// monospaced uppercase — reads as an ink stamp pressed onto the receipt.
// Percent stays pinned at the leading edge; the fee name is capped at
// the stamp's max width and marquees inside a leading fade when it
// overflows. No delta value on the row — the reader gets that from the
// visible typed → engraved-final math below.
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
                .toHumanReadableNumber(context, suffix = "%", trim = true)
        }
    val namesText =
        remember(fees) {
            fees
                .mapNotNull { it.name.trim().takeIf(String::isNotEmpty) }
                .joinToString(FEE_NAME_SEPARATOR)
                .uppercase()
        }
    val bg = Stamp.copy(alpha = FEE_CHIP_BG_ALPHA).compositeOver(MaterialTheme.colorScheme.surface)
    Row(
        modifier
            .maxWidthFraction(FEE_CHIP_MAX_WIDTH_FRACTION)
            .clip(RoundedCornerShape(FEE_STAMP_CORNER_RADIUS))
            .background(bg)
            .border(
                width = FEE_STAMP_BORDER_WIDTH,
                color = Stamp,
                shape = RoundedCornerShape(FEE_STAMP_CORNER_RADIUS),
            ).hapticClickable(onClick = onClick)
            .padding(
                horizontal = FEE_STAMP_HORIZONTAL_PADDING,
                vertical = FEE_STAMP_VERTICAL_PADDING,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (namesText.isEmpty()) {
            FeeChipText(text = stringResource(R.string.fee_chip_label, percentText))
        } else {
            FeeChipText(text = percentText)
            Spacer(Modifier.width(FEE_STAMP_INNER_GAP))
            FeeChipText(
                text = namesText,
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberIdleAutoScrollState(namesText)),
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
        fontFamily = FontFamily.Monospace,
        letterSpacing = FEE_STAMP_LETTER_SPACING,
        color = Stamp,
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onProviderClick),
    ) {
        LivePulseDot()
        Spacer(Modifier.width(LIVE_PULSE_DOT_GAP))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Small primary-tinted dot that pulses its alpha slowly to signal that the
// visible rates are live. Sits at the leading edge of the timestamp row.
@Composable
private fun LivePulseDot() {
    val transition = rememberInfiniteTransition(label = "live-pulse")
    val alpha by transition.animateFloat(
        initialValue = LIVE_PULSE_ALPHA_MAX,
        targetValue = LIVE_PULSE_ALPHA_MIN,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = LIVE_PULSE_MILLIS, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "live-pulse-alpha",
    )
    val color = MaterialTheme.colorScheme.primary
    Spacer(
        Modifier
            .size(LIVE_PULSE_DOT_SIZE)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(color),
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
