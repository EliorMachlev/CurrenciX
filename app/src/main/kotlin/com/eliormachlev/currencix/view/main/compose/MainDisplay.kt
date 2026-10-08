package com.eliormachlev.currencix.view.main.compose

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.eliormachlev.currencix.util.inReadingOrder
import com.eliormachlev.currencix.util.stripRtlMark
import com.eliormachlev.currencix.util.stripTimePattern
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.compose.CurrencyPairRow
import com.eliormachlev.currencix.view.compose.LayerCapture
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.PairRowActions
import com.eliormachlev.currencix.view.compose.captureInto
import com.eliormachlev.currencix.view.compose.onboarding.OnboardingAnchor
import com.eliormachlev.currencix.view.compose.onboarding.onboardingAnchor
import com.eliormachlev.currencix.view.compose.shimmer
import com.eliormachlev.currencix.view.compose.theme.AmberContainer
import com.eliormachlev.currencix.view.compose.theme.Motion
import com.eliormachlev.currencix.view.compose.theme.OnAmberContainer
import com.eliormachlev.currencix.view.compose.theme.Stamp
import com.eliormachlev.currencix.view.main.spinner.CurrencyPickerSheet
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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

private val SWAP_FAB_SIZE: Dp = 44.dp
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
// (never snap back). A spring, so a second tap mid-flip carries the
// current spin forward instead of restarting it.
private const val SWAP_FAB_ROTATION_STEP = 180f

private val RATE_FOOTER_TOP_MARGIN: Dp = 8.dp
private val RATE_FOOTER_PADDING_TOP: Dp = 8.dp
private val STATUS_PILL_GAP: Dp = 8.dp
private val STATUS_PILL_RADIUS: Dp = 999.dp
private val STATUS_PILL_PADDING_H: Dp = 10.dp
private val STATUS_PILL_PADDING_V: Dp = 4.dp
private val STATUS_PILL_ICON_GAP: Dp = 6.dp
private val STATUS_PILL_ICON_SIZE: Dp = 14.dp
private val CURSOR_WIDTH: Dp = 2.dp
private val CURSOR_HEIGHT: Dp = 44.dp
private val CURSOR_HEIGHT_SUBTOTAL: Dp = 26.dp

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

// How one tier of amount is set: its digits' size, weight and face.
private class AmountStyle(
    val digitsSize: TextUnit,
    val fontWeight: FontWeight,
    val fontFamily: FontFamily? = null,
) {
    val symbolSize: TextUnit = digitsSize * SYMBOL_SIZE_RATIO
}

// The typed amount, and the fair result when a fee-adjusted one follows it.
private val SUBTOTAL_STYLE = AmountStyle(AMOUNT_SUBTOTAL_SIZE, FontWeight.Medium)

// The fair result on its own, and the fee-adjusted final: engraved serif.
private val RESULT_STYLE = AmountStyle(AMOUNT_TO_SIZE, FontWeight.SemiBold, FontFamily.Serif)
private val FINAL_STYLE = AmountStyle(AMOUNT_HERO_SIZE, FontWeight.SemiBold, FontFamily.Serif)

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
private const val RELATIVE_DAYS_WINDOW_MS = 7L * RELATIVE_TIME_WINDOW_MS

// Caret blink half-period — the platform EditText's rate. A discrete on/off
// toggle costs two frames per cycle; the old infinite fade asked for a new
// frame on every vsync for as long as the main screen was open.
private const val CURSOR_BLINK_MILLIS = 500L

// Engraved amounts (the result side) animate only when they change from
// outside the keypad — a rate refresh, a currency pick, a fee toggle. Then a
// short crossfade plus a scale pulse (up to the peak, springing back) reads
// as an emphatic "updated". Keystroke-driven changes render instantly, so
// typing stays crisp at any speed instead of stacking fades and pulses.
// No first-appearance ramp — an initial alpha or translation would swallow
// the digits every time the hero swaps branches (fee ↔ no-fee).
private const val HERO_EMPHASIS_PEAK = 1.03f

// Phase-offset the hero-final shimmer behind the subtotal's so the two
// stacked digit slots don't tile identically during a rates refresh — the
// eye reads the highlight cascading down the panel instead of two
// synchronized bars. Half the period lands the second sweep at the
// opposite side of its slot when the first crosses center.
private const val HERO_SHIMMER_STAGGER_MILLIS = 600

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

// An amount on the card: split for display, whole for the clipboard.
@Immutable
private data class Amount(
    val parts: AmountParts,
    val copyText: String,
) {
    fun copyTo(onCopy: (CharSequence) -> Unit) {
        if (copyText.isNotEmpty()) onCopy(copyText)
    }
}

// The "you get" panel.
@Immutable
private data class ResultState(
    val result: Amount,
    val trueCost: Amount,
    // The fees in force multiplied out, and the fees behind that figure.
    val feeStack: BigDecimal?,
    val fees: ImmutableList<Fee>,
    // Whether there's a fee-adjusted amount to show under the fair one.
    val hasTrueCost: Boolean,
    val isUpdating: Boolean,
    // The typed input the amounts were computed from, so keystroke-driven
    // changes can skip the "updated" animation.
    val inputKey: Any?,
)

// The line under the panels: the rate in use, how old it is, who published it.
@Immutable
private data class FooterState(
    val rates: ExchangeRates?,
    val dateFormatPattern: String,
    val banner: BannerContent?,
)

// Everything the hero card shows.
@Immutable
private data class HeroState(
    val baseCurrency: Currency?,
    val destCurrency: Currency?,
    val base: Amount,
    val mathText: String?,
    val result: ResultState,
    val footer: FooterState,
)

/**
 * Pure-Compose replacement for the old `main_display.xml`. Renders the hero
 * card (currency pills + amount hero + amount to + rate footer) and hosts the
 * compose-native [CurrencyPickerSheet] as a sibling — no fragment machinery,
 * so callers don't need to hand in a `FragmentManager`.
 */
@Composable
internal fun MainDisplay(
    viewModel: MainViewModel,
    callbacks: MainDisplayCallbacks,
    dateFormatPattern: String,
    banner: BannerContent?,
    modifier: Modifier = Modifier,
    captureController: LayerCapture? = null,
) {
    val rates by viewModel.getExchangeRates().observeAsState()
    val state = observeHeroState(viewModel, FooterState(rates, dateFormatPattern, banner))
    var pickerSide by remember { mutableStateOf<PickSide?>(null) }
    val pairActions =
        remember(viewModel, callbacks) {
            PairRowActions(
                onFromClick = { pickerSide = PickSide.FROM },
                onToClick = { pickerSide = PickSide.TO },
                onSwap = viewModel::swapCurrencies,
                onSwapLongPress = callbacks.onSwapLongPress,
            )
        }
    HeroCard(
        state = state,
        pair = pairActions,
        callbacks = callbacks,
        modifier = modifier,
        captureController = captureController,
    )
    pickerSide?.let { side ->
        CurrencyPickerHost(side = side, viewModel = viewModel, state = state, onDismiss = { pickerSide = null })
    }
}

@Composable
private fun observeHeroState(
    viewModel: MainViewModel,
    footer: FooterState,
): HeroState {
    val context = LocalContext.current
    val baseCurrency by viewModel.getBaseCurrency().observeAsState()
    val destCurrency by viewModel.getDestinationCurrency().observeAsState()
    val baseFormatted by viewModel.getCurrentBaseValueFormatted().observeAsState()
    val resultFairFormatted by viewModel.getResultFormatted().observeAsState()
    val resultWithFeesFormatted by viewModel.getResultWithFeesFormatted().observeAsState()
    val isUpdating by viewModel.isRefreshShimmerVisible().collectAsStateWithLifecycle()
    val feeStack by viewModel.getFeeStack().observeAsState()
    val activeFees by viewModel.getActiveFees().observeAsState()
    val mathText by viewModel.getCalculationInputFormatted().observeAsState()
    val resultWithFeesNumber by viewModel.getResultWithFeesAsNumber().observeAsState()

    val base = rememberAmount(context, baseFormatted, baseCurrency)
    return HeroState(
        baseCurrency = baseCurrency,
        destCurrency = destCurrency,
        base = base,
        mathText = mathText,
        result =
            ResultState(
                result = rememberAmount(context, resultFairFormatted, destCurrency),
                trueCost = rememberAmount(context, resultWithFeesFormatted, destCurrency),
                feeStack = feeStack,
                fees = activeFees ?: persistentListOf(),
                hasTrueCost = resultWithFeesNumber != null,
                isUpdating = isUpdating,
                // Digits only: a new "from" currency changes the symbol, not
                // the typed amount, so its result update still animates.
                inputKey = base.parts.digits,
            ),
        footer = footer,
    )
}

// Splits a formatted amount into its (symbol, digits) parts so the symbol can
// be pinned outside the scrolling digits row.
@Composable
private fun rememberAmount(
    context: Context,
    formatted: CharSequence?,
    currency: Currency?,
): Amount {
    val full = formatted?.toString().orEmpty()
    return remember(full, currency) { Amount(splitAmount(context, full, currency), full) }
}

/**
 * The picker sheet for the side the user tapped. It previews each currency
 * against the other side's amount, and keeps that side's currency unpickable.
 */
@Composable
private fun CurrencyPickerHost(
    side: PickSide,
    viewModel: MainViewModel,
    state: HeroState,
    onDismiss: () -> Unit,
) {
    val picksFrom = side == PickSide.FROM
    val other = if (picksFrom) state.destCurrency else state.baseCurrency
    val otherSum = if (picksFrom) viewModel.getResultAsNumber().value else viewModel.getCurrentBaseValueAsNumber().value
    CurrencyPickerSheet(
        currentRate =
            other?.let { c ->
                state.footer.rates
                    ?.rateFor(c)
                    ?.let { Rate(c, it.value) }
            },
        currentSum = otherSum ?: BigDecimal.ONE,
        disabledCurrency = other,
        onRateClicked = { rate ->
            if (picksFrom) viewModel.setBaseCurrency(rate.currency) else viewModel.setDestinationCurrency(rate.currency)
        },
        onDismiss = onDismiss,
        selectedCurrency = if (picksFrom) state.baseCurrency else state.destCurrency,
    )
}

@Composable
private fun HeroCard(
    state: HeroState,
    pair: PairRowActions,
    callbacks: MainDisplayCallbacks,
    modifier: Modifier = Modifier,
    captureController: LayerCapture? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = CARD_OUTER_MARGIN)
            .padding(top = CARD_OUTER_MARGIN)
            .shadow(elevation = CARD_ELEVATION, shape = RoundedCornerShape(CARD_RADIUS))
            // Capture layer wraps clip+background so the recorded bitmap
            // contains the rounded surface and children — with the layer
            // outside .background, the recording would only see the pills/text
            // and the PNG would render on a transparent (→ black) canvas.
            .captureInto(captureController)
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(MaterialTheme.colorScheme.surface)
            .padding(CARD_PADDING),
    ) {
        Column(Modifier.fillMaxWidth()) {
            CurrencyPairRow(from = state.baseCurrency, to = state.destCurrency, actions = pair) {
                SwapFab(onClick = pair.onSwap, onLongClick = pair.onSwapLongPress)
            }
            Spacer(Modifier.height(PILLS_ROW_BOTTOM_GAP))
            AmountHero(
                currency = state.baseCurrency,
                subtotal = state.base,
                mathText = state.mathText,
                onCopy = callbacks.onCopy,
            )
            Spacer(Modifier.height(PANEL_STACK_GAP))
            AmountToRow(currency = state.destCurrency, state = state.result, callbacks = callbacks)
            Spacer(Modifier.height(RATE_FOOTER_TOP_MARGIN))
            RateFooter(
                base = state.baseCurrency,
                dest = state.destCurrency,
                state = state.footer,
                onProviderClick = callbacks.onOpenProvider,
            )
        }
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
        animationSpec = Motion.snappy(),
        label = "swapFabRotation",
    )
    Box(
        Modifier
            .size(SWAP_FAB_SIZE)
            .onboardingAnchor(OnboardingAnchor.SwapFab)
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
            painter = painterResource(R.drawable.ic_swap_horiz),
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
    subtotal: Amount,
    mathText: String?,
    onCopy: (CharSequence) -> Unit,
) {
    val context = LocalContext.current
    ReceiptPanel(label = currency.panelLabel(context)) {
        Column(Modifier.fillMaxWidth()) {
            MathLine(mathText)
            ScrollingAmount(
                parts = subtotal.parts,
                style = SUBTOTAL_STYLE,
                cursorHeight = CURSOR_HEIGHT_SUBTOTAL,
                onLongClick = { subtotal.copyTo(onCopy) },
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
//
// Non-cursor path adds a per-change scale emphasis (1.0 → 1.03 → 1.0) on
// top of the digit crossfade — reads as a soft "value updated" pulse
// without ever hiding the digits. No first-appearance animation: an
// initial-alpha ramp would swallow the very first value the panel shows
// (branch swaps between fee / no-fee reset the ScrollingAmount identity,
// so an entrance animation reruns every time the fee stack flips).
@Composable
private fun ScrollingAmount(
    parts: AmountParts,
    style: AmountStyle,
    cursorHeight: Dp?,
    onLongClick: () -> Unit,
    // Result side only: the typed input these digits were computed from, so
    // keystroke-driven changes can skip the "updated" animation.
    inputKey: Any? = null,
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
                fontSize = style.symbolSize,
                lineHeight = style.digitsSize,
                fontWeight = style.fontWeight,
                fontFamily = style.fontFamily,
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
                fontSize = style.digitsSize,
                lineHeight = style.digitsSize,
                fontWeight = style.fontWeight,
                fontFamily = style.fontFamily,
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
            EngravedDigitsCrossfade(digits = parts.digits, inputKey = inputKey, digitsText = digitsText)
        }
        if (cursorHeight != null) BlinkingCursor(height = cursorHeight, restartKey = parts.digits)
    }
}

// TransformOrigin vertical pivot: 0.5 = middle of the glyph's box. Used so
// the emphasis pulse scales the digits about their vertical center while
// still anchoring horizontally to the right edge (Arrangement.End).
private const val TRANSFORM_ORIGIN_CENTER = 0.5f

/**
 * Decides, once per digits change, whether the change came from outside the
 * keypad. [isExternal] is true when the digits changed while the typed input
 * stayed the same since the previous change (a rate refresh, currency pick,
 * fee toggle); a keystroke changes the input first, so its result update
 * reads as internal. The very first value is never external.
 *
 * Plain fields rather than snapshot state: they're written while composing
 * and never observed, so they must not trigger recomposition.
 */
private class DigitsChangeOrigin(
    private var inputAtLastChange: Any?,
) {
    private var seenFirst = false

    fun isExternal(input: Any?): Boolean {
        val external = seenFirst && input == inputAtLastChange
        seenFirst = true
        inputAtLastChange = input
        return external
    }
}

/**
 * Crossfade + emphasis-pulse render used by the non-typed
 * (result / true-cost) side of [ScrollingAmount]. Animates only changes that
 * came from outside the keypad (see [DigitsChangeOrigin]); [inputKey] is the
 * typed input the digits were computed from. Split out so the parent stays
 * short and the Modifier/animation stack for this branch reads on its own
 * without wading past the typed-input path.
 */
@Composable
private fun RowScope.EngravedDigitsCrossfade(
    digits: String,
    inputKey: Any?,
    digitsText: @Composable (String) -> Unit,
) {
    val emphasis = remember { Animatable(1f) }
    val origin = remember { DigitsChangeOrigin(inputKey) }
    val animateChange = remember(digits) { origin.isExternal(inputKey) }
    LaunchedEffect(digits) {
        if (animateChange) {
            // Ease up, spring back — no snap, so a change landing mid-pulse
            // continues from the current scale instead of jumping.
            emphasis.animateTo(HERO_EMPHASIS_PEAK, tween(Motion.SHORT_MILLIS / 2))
            emphasis.animateTo(1f, Motion.settle())
        }
    }
    Crossfade(
        targetState = digits,
        modifier =
            Modifier
                .weight(1f, fill = false)
                .graphicsLayer {
                    val s = emphasis.value
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(1f, TRANSFORM_ORIGIN_CENTER)
                },
        animationSpec = if (animateChange) Motion.fadeShort() else snap(),
        label = "engraved-digits",
    ) { value -> digitsText(value) }
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
private fun BlinkingCursor(
    height: Dp = CURSOR_HEIGHT,
    restartKey: Any? = null,
) {
    // Solid while typing: every keystroke ([restartKey]) restarts the blink
    // from "on", like a platform text field.
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(restartKey) {
        visible = true
        while (true) {
            delay(CURSOR_BLINK_MILLIS)
            visible = !visible
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    Spacer(
        Modifier
            .padding(start = 6.dp)
            .width(CURSOR_WIDTH)
            .height(height)
            // Read in the draw layer: a toggle redraws, never recomposes.
            .graphicsLayer { this.alpha = if (visible) 1f else 0f }
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
    state: ResultState,
    callbacks: MainDisplayCallbacks,
) {
    val context = LocalContext.current
    val stack = state.feeStack?.takeIf { it.hasFee() }
    ReceiptPanel(label = currency.panelLabel(context)) {
        Column(Modifier.fillMaxWidth()) {
            // When a fee is armed we always render the full receipt chain
            // (subtotal → chip → rule → fee-adjusted final), even if the
            // input is 0. Showing "0 + 1% = 0" is intentional: the final slot
            // stays present so the user sees the same visual anchor at any
            // input.
            if (stack != null && state.hasTrueCost) {
                AmountToChain(state = state, stack = stack, callbacks = callbacks)
            } else {
                AmountToBare(state = state, stack = stack, callbacks = callbacks)
            }
        }
    }
}

// Full receipt chain rendered inside [AmountToRow] when a fee is armed and
// a fee-adjusted result exists: fair subtotal → fee stamp → rule → engraved
// fee-adjusted hero final.
@Composable
private fun AmountToChain(
    state: ResultState,
    stack: BigDecimal,
    callbacks: MainDisplayCallbacks,
) {
    Box(Modifier.shimmer(enabled = state.isUpdating)) {
        ScrollingAmount(
            parts = state.result.parts,
            style = SUBTOTAL_STYLE,
            cursorHeight = null,
            onLongClick = { state.result.copyTo(callbacks.onCopy) },
            inputKey = state.inputKey,
        )
    }
    Spacer(Modifier.height(SUBTOTAL_TO_CHIP_GAP))
    ChipBelow(stack = stack, fees = state.fees, onClick = callbacks.onOpenFees)
    Spacer(Modifier.height(PAY_RULE_TOP_GAP))
    PayRule()
    Spacer(Modifier.height(PAY_RULE_BOTTOM_GAP))
    Box(
        Modifier.shimmer(enabled = state.isUpdating, startDelayMillis = HERO_SHIMMER_STAGGER_MILLIS),
    ) {
        ScrollingAmount(
            parts = state.trueCost.parts,
            style = FINAL_STYLE,
            cursorHeight = null,
            onLongClick = { state.trueCost.copyTo(callbacks.onCopy) },
            inputKey = state.inputKey,
        )
    }
}

// Fee-free destination render: single hero-sized fair conversion, plus the
// fee stamp if a fee is armed ([stack]) against a 0/missing input (so the
// user sees the fee will apply once they type something).
@Composable
private fun AmountToBare(
    state: ResultState,
    stack: BigDecimal?,
    callbacks: MainDisplayCallbacks,
) {
    Box(Modifier.shimmer(enabled = state.isUpdating)) {
        ScrollingAmount(
            parts = state.result.parts,
            style = RESULT_STYLE,
            cursorHeight = null,
            onLongClick = { state.result.copyTo(callbacks.onCopy) },
            inputKey = state.inputKey,
        )
    }
    if (stack != null) {
        Spacer(Modifier.height(SUBTOTAL_TO_CHIP_GAP))
        ChipBelow(stack = stack, fees = state.fees, onClick = callbacks.onOpenFees)
    }
}

// True when the fee stack is a real markup/markdown (not `1`, i.e. not
// a no-op). Used to decide whether the amber chip should render at all.
private fun BigDecimal.hasFee(): Boolean = compareTo(BigDecimal.ONE) != 0

// Sits between the subtotal and the hero final in the top card, taking no
// bottom padding of its own — the surrounding column adds symmetric spacers
// instead. Renders as receipt math: a dim `+` operator outside the stamp,
// then the crimson revenue-stamp with the percent + fee name inside it.
@Composable
private fun ChipBelow(
    stack: BigDecimal,
    fees: ImmutableList<Fee>,
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
            FeeChip(stack, fees, onClick, modifier = Modifier.onboardingAnchor(OnboardingAnchor.FeeStamp))
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
//
// The ISO+symbol chunk is wrapped in U+2066 LEFT-TO-RIGHT ISOLATE and
// U+2069 POP DIRECTIONAL ISOLATE so the parens sit *outside* the isolated
// LTR run. In an RTL paragraph (Hebrew, Arabic) the parens then take the
// paragraph direction and mirror correctly — the reader's eye meets "("
// first (right side) and ")" last (left side), instead of the default bidi
// behaviour which left ")" glued to the RTL name.
private const val LRI = "\u2066"
private const val PDI = "\u2069"

private fun Currency?.panelLabel(context: Context): String {
    val currency = this ?: return ""
    val name = currency.fullName(context)
    val iso = currency.iso4217Alpha()
    val symbol = currency.symbol()
    val inner = if (symbol.isNullOrEmpty()) iso else "$iso $symbol"
    return "$name ($LRI$inner$PDI)"
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

// Revenue-stamp for the fee row. Rectangular thin border, crimson ink,
// monospaced uppercase — reads as an ink stamp pressed onto the receipt.
// Percent stays pinned at the leading edge; the fee name is capped at
// the stamp's max width and marquees inside a leading fade when it
// overflows. No delta value on the row — the reader gets that from the
// visible typed → engraved-final math below.
@Composable
private fun FeeChip(
    stack: BigDecimal,
    fees: ImmutableList<Fee>,
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
    state: FooterState,
    onProviderClick: () -> Unit,
) {
    val (rates, dateFormatPattern, banner) = state
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(Modifier.height(RATE_FOOTER_PADDING_TOP))
        if (banner != null) {
            StatusPill(banner, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(STATUS_PILL_GAP))
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RateText(base = base, dest = dest, rates = rates)
            TimestampText(
                rates = rates,
                dateFormatPattern = dateFormatPattern,
                showWhen = banner == null,
                onProviderClick = onProviderClick,
            )
        }
    }
}

// Compact tinted pill surfaced inside the RateFooter (above the rate +
// timestamp row) when the app is showing offline or historical data. Keeps
// the status visible without stealing a full-width slot above the hero.
@Composable
private fun StatusPill(
    banner: BannerContent,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        when (banner.kind) {
            BannerKind.Offline -> MaterialTheme.colorScheme.errorContainer
            BannerKind.Unreachable, BannerKind.Fallback -> AmberContainer
            BannerKind.Historical -> MaterialTheme.colorScheme.secondaryContainer
        }
    val contentColor =
        when (banner.kind) {
            BannerKind.Offline -> MaterialTheme.colorScheme.onErrorContainer
            BannerKind.Unreachable, BannerKind.Fallback -> OnAmberContainer
            BannerKind.Historical -> MaterialTheme.colorScheme.onSecondaryContainer
        }
    val iconRes =
        when (banner.kind) {
            BannerKind.Offline -> R.drawable.ic_cloud_off
            BannerKind.Unreachable -> R.drawable.ic_sync_problem
            BannerKind.Fallback -> R.drawable.ic_data_provider
            BannerKind.Historical -> R.drawable.ic_history
        }
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(STATUS_PILL_RADIUS))
                .background(containerColor)
                .padding(horizontal = STATUS_PILL_PADDING_H, vertical = STATUS_PILL_PADDING_V),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(STATUS_PILL_ICON_GAP),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(STATUS_PILL_ICON_SIZE),
        )
        val context = LocalContext.current
        Text(
            text = remember(banner.text, context) { banner.text.inReadingOrder(context) },
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimestampText(
    rates: ExchangeRates?,
    dateFormatPattern: String,
    showWhen: Boolean,
    onProviderClick: () -> Unit,
) {
    val context = LocalContext.current
    val date = rates?.date ?: return
    // "12 min ago" goes stale on screen: tick once a minute while the label
    // is relative, so it keeps telling the truth.
    val now by produceState(System.currentTimeMillis(), date, rates.time) {
        while (isRelative(date, rates.time, value)) {
            delay(DateUtils.MINUTE_IN_MILLIS)
            value = System.currentTimeMillis()
        }
    }
    val whenText = remember(date, rates.time, dateFormatPattern, now) { formatWhen(date, rates.time, dateFormatPattern, now) }
    val fullText = remember(date, rates.time, dateFormatPattern) { formatFull(date, rates.time, dateFormatPattern) }
    val provider = rates.provider?.getName(context)?.toString()
    val label =
        when {
            !showWhen -> provider.orEmpty()
            provider.isNullOrEmpty() -> whenText
            else -> "$whenText$FOOTER_SEPARATOR$provider"
        }
    if (label.isEmpty()) return
    // In a right-to-left language the provider follows the date leftwards,
    // even when its name is in Latin script.
    val text = remember(label, context) { label.inReadingOrder(context) }
    // Tap: change provider. Long-press: the exact publication time.
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(fullText) } },
        state = rememberTooltipState(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onProviderClick),
        )
    }
}

private fun epochMillis(
    date: LocalDate,
    time: LocalTime?,
): Long =
    (time?.let(date::atTime) ?: date.atStartOfDay())
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

// Relative within a day when the provider gives a time ("12 min ago"), within
// a week when it publishes dates only ("Today", "Yesterday", "3 days ago").
private fun isRelative(
    date: LocalDate,
    time: LocalTime?,
    now: Long,
): Boolean = (now - epochMillis(date, time)) in 0 until (if (time != null) RELATIVE_TIME_WINDOW_MS else RELATIVE_DAYS_WINDOW_MS)

/**
 * Compact "when" label: relative ("12 min ago", "Yesterday") while recent
 * — see [isRelative] — otherwise the formatted date.
 */
private fun formatWhen(
    date: LocalDate,
    time: LocalTime?,
    pattern: String,
    now: Long,
): String {
    if (!isRelative(date, time, now)) return formatFull(date, time, pattern)
    val resolution = if (time != null) DateUtils.MINUTE_IN_MILLIS else DateUtils.DAY_IN_MILLIS
    return DateUtils
        .getRelativeTimeSpanString(epochMillis(date, time), now, resolution, DateUtils.FORMAT_ABBREV_RELATIVE)
        .toString()
        .stripRtlMark()
}

// The timestamp in the user's date format — without the time part when the
// provider only publishes dates.
private fun formatFull(
    date: LocalDate,
    time: LocalTime?,
    pattern: String,
): String {
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

// --- Small helpers ------------------------------------------------------------

// Composite two Colors — Compose has no `color-mix()` analog. Alpha of `this`
// is used as the mix ratio; result is opaque against [background].
private fun Color.compositeOver(background: Color): Color {
    val a = this.alpha
    val r = this.red * a + background.red * (1 - a)
    val g = this.green * a + background.green * (1 - a)
    val b = this.blue * a + background.blue * (1 - a)
    return Color(r, g, b, 1f)
}
