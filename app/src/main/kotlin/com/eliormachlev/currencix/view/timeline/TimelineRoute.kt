package com.eliormachlev.currencix.view.timeline

import android.app.Application
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.map
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.layout.FoldingFeature
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.alert.ALERT_RATE_DECIMALS
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.buildImageShareChooser
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.util.resolveThemeColor
import com.eliormachlev.currencix.util.roundForDisplay
import com.eliormachlev.currencix.util.stripTimePattern
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.alert.RateAlertSheet
import com.eliormachlev.currencix.view.alert.rememberSetRateAlert
import com.eliormachlev.currencix.view.compose.LayerCapture
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.OverflowAction
import com.eliormachlev.currencix.view.compose.ReadingDirection
import com.eliormachlev.currencix.view.compose.ScreenScaffold
import com.eliormachlev.currencix.view.compose.TopBarAction
import com.eliormachlev.currencix.view.compose.TopBarOverflowMenu
import com.eliormachlev.currencix.view.compose.TopBarStyle
import com.eliormachlev.currencix.view.compose.flagPainter
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.view.navigation.Screen
import com.eliormachlev.currencix.view.preference.compose.GraphOptionsSheet
import com.eliormachlev.currencix.view.preference.compose.ProviderPickerSheet
import com.eliormachlev.currencix.view.timeline.compose.TIMELINE_CONTENT_PADDING
import com.eliormachlev.currencix.view.timeline.compose.TimelineScreen
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel
import com.eliormachlev.currencix.viewmodel.timeline.TimelineViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val FLAG_FROM_ID = "flagFrom"
private const val FLAG_TO_ID = "flagTo"
private const val ARROW_ID = "arrow"

// Breathing room in the title: an en space after each flag and around the
// arrow, so the pieces don't run together.
private const val FLAG_GAP = "\u2002"
private const val TITLE_GAP = "\u2002"

// Inline flag in the title, in the flag artwork's 24×17 aspect.
private val TITLE_FLAG_WIDTH = 1.3.em
private val TITLE_FLAG_HEIGHT = 0.92.em
private val TITLE_FLAG_CORNER = 2.dp
private val TITLE_ARROW_SIZE = 1.1.em

/**
 * Rate history for [screen]'s pair, under a small top bar titled "🇺🇸 $ USD →
 * 🇮🇱 ₪ ILS". After a touch on the chart, a chip offers the converter at the
 * day last touched ([onConvertAt]).
 */
@Composable
fun TimelineRoute(
    screen: Screen.Timeline,
    onBack: () -> Unit,
    foldingFeature: FoldingFeature?,
    onConvertAt: (pair: CurrencyPair, date: LocalDate) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val model: TimelineViewModel = viewModel(factory = TimelineViewModel.factory(application, screen.from, screen.to))
    val db = remember(context) { Database(context) }
    val formatter = remember(db) { DateTimeFormatter.ofPattern(stripTimePattern(db.display.dateFormat.get())) }
    val pair by model.getCurrencyPair().observeAsState()
    var sheet by rememberSaveable { mutableStateOf<TimelineSheet?>(null) }
    val chartCapture = remember { LayerCapture() }
    val scope = rememberCoroutineScope()
    // The rate a rate-alert sheet is open at: today's (the bell) or a touched day's.
    var alertAt by remember { mutableStateOf<BigDecimal?>(null) }
    val touched = rememberTouchedDay(model)

    // Small bar: the pair sits beside the back arrow, leaving the height to the chart.
    // The bar follows the language (in Hebrew: back arrow and pair on the
    // right, actions on the left); the chart and figures below set their own.
    ReadingDirection {
        ScreenScaffold(
            // The screen's own pair until the data (and any swap) has loaded, so
            // the title is there while offline or still fetching.
            title = { TimelineTitle(pair ?: (screen.from to screen.to)) },
            onBack = onBack,
            style = TopBarStyle.Small,
            actions = {
                TimelineBarActions(
                    model = model,
                    onOptions = { sheet = TimelineSheet.GraphOptions },
                    onAlert = { alertAt = it },
                    onShare = { scope.launch { shareChart(context, chartCapture, model, formatter) } },
                )
            },
        ) { padding ->
            TimelineScreen(
                model = model,
                formatter = formatter,
                foldingFeature = foldingFeature,
                onChangeProvider = { sheet = TimelineSheet.Provider },
                modifier = Modifier.fillMaxSize().padding(padding),
                chartCapture = chartCapture,
                chartContent = {
                    TimelineChartContent(model, db) { day ->
                        if (day != null) touched.day = day
                        model.setPastDate(day)
                    }
                },
                chartActions = { mod ->
                    ChartDayActions(
                        model = model,
                        day = touched.day,
                        formatter = formatter,
                        onConvertAt = { pair, day ->
                            touched.day = null
                            onConvertAt(pair, day)
                        },
                        onAlertAt = { alertAt = it },
                        modifier = mod,
                    )
                },
            )
        }
    }
    TimelineSheets(sheet = sheet, db = db, onProviderPicked = { model.retry() }, onDismiss = { sheet = null })
    alertAt?.let { rate -> TimelineAlertSheet(model = model, rate = rate, onClose = { alertAt = null }) }
}

// The bar's actions: swap (once a refresh or an error is out of the way,
// since swapping re-fetches), and under ⋮ a rate alert at today's rate,
// share and graph options, so the pair keeps its room in the title.
@Composable
private fun TimelineBarActions(
    model: TimelineViewModel,
    onOptions: () -> Unit,
    onAlert: (BigDecimal) -> Unit,
    onShare: () -> Unit,
) {
    val inFlight by model.isRefreshInFlight().observeAsState(false)
    val error by model.getError().observeAsState()
    val rateCurrent by remember(model) { model.getRateCurrent() }.observeAsState()
    val today = rateCurrent?.first?.value?.value
    TopBarAction(
        icon = painterResource(R.drawable.ic_swap_horiz),
        contentDescription = stringResource(R.string.desc_toggle_currencies),
        onClick = model::toggleCurrencies,
        enabled = !inFlight && error == null,
    )
    val alertLabel = stringResource(R.string.alert_sheet_title)
    val shareLabel = stringResource(R.string.timeline_share)
    val optionsLabel = stringResource(R.string.graph_options_title)
    // Only what can be done now: an alert needs today's rate, a share the chart.
    val menu =
        remember(today, error, alertLabel, shareLabel, optionsLabel, onAlert, onShare, onOptions) {
            listOfNotNull(
                today?.let { rate -> OverflowAction(alertLabel, R.drawable.ic_alert) { onAlert(rate) } },
                OverflowAction(shareLabel, R.drawable.ic_share, onClick = onShare).takeIf { error == null },
                OverflowAction(optionsLabel, R.drawable.ic_tune, onClick = onOptions),
            )
        }
    TopBarOverflowMenu(menu)
}

// The rate alert sheet for the timeline's pair at [rate]; [onClose] when set or dismissed.
@Composable
private fun TimelineAlertSheet(
    model: TimelineViewModel,
    rate: BigDecimal,
    onClose: () -> Unit,
) {
    val pair by model.getCurrencyPair().observeAsState()
    val (from, to) = pair ?: return
    val setAlert = rememberSetRateAlert()
    RateAlertSheet(
        pair = CurrencyPair(from, to),
        current = rate,
        onSet = { alert ->
            setAlert(alert)
            onClose()
        },
        onDismiss = onClose,
    )
}

// The chart card as a PNG, captioned "USD → ILS · 29/09/25 – 29/09/26 ·
// Bank of Israel". Nothing to share until the chart has been drawn.
private suspend fun shareChart(
    context: Context,
    capture: LayerCapture,
    model: TimelineViewModel,
    formatter: DateTimeFormatter,
) {
    val image = capture.capture() ?: return
    val (from, to) = model.getCurrencyPair().value ?: return
    val span = model.span()
    val caption =
        context.getString(
            R.string.timeline_share_caption,
            from.iso4217Alpha(),
            to.iso4217Alpha(),
            span.start.format(formatter),
            span.end.format(formatter),
            model.getProvider().value ?: "",
        )
    context.startActivity(buildImageShareChooser(context, image, namePrefix = "currencix-chart", text = caption))
}

// The sheets the timeline opens over itself.
private enum class TimelineSheet { GraphOptions, Provider }

@Composable
private fun TimelineSheets(
    sheet: TimelineSheet?,
    db: Database,
    onProviderPicked: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (sheet) {
        TimelineSheet.GraphOptions -> {
            GraphOptionsSheet(db = db, onDismiss = onDismiss)
        }

        TimelineSheet.Provider -> {
            val preferences: PreferenceViewModel = viewModel()
            val current by preferences.apiProvider.collectAsStateWithLifecycle()
            ProviderPickerSheet(
                selected = current,
                onDismiss = onDismiss,
                onPicked = { provider ->
                    preferences.setApiProvider(provider)
                    onProviderPicked()
                },
            )
        }

        null -> {}
    }
}

@Composable
private fun TimelineChartContent(
    model: TimelineViewModel,
    db: Database,
    onScrub: (LocalDate?) -> Unit,
) {
    val context = LocalContext.current
    val colors =
        remember(context) {
            val axis = Color(context.resolveThemeColor(android.R.attr.textColorSecondary))
            ChartColors(
                line = Color(context.resolveThemeColor(R.attr.colorPrimary)),
                baseline = axis,
                axis = axis,
                // Text-on-background keeps the scrub line distinct from the green
                // primary (max highlight), red min line, and the blue/purple
                // period-change verticals on every theme.
                scrubLine = Color(context.resolveThemeColor(android.R.attr.textColorPrimary)),
            )
        }
    // Both created once per screen: a fresh LiveData per recomposition would
    // make the chart drop and re-add its observers every time.
    val series =
        remember(model) {
            ChartSeries(
                entries =
                    model.getRates().map { rates ->
                        rates?.entries?.map { entry -> entry.key to entry.value.value.toFloat() }?.toImmutableList()
                    },
                highlightMin = model.getRatesRangeMin(),
                highlightMax = model.getRatesRangeMax(),
            )
        }
    val prefs =
        remember(db) {
            ChartPrefs(
                showGrid = db.chart.grid.liveData,
                showXAxis = db.chart.xAxisLabel.liveData,
                showYAxis = db.chart.yAxisLabel.liveData,
                highlightExtremes = db.chart.highlightExtremes.liveData,
                highlightPeriodChange = db.chart.highlightPeriodChange.liveData,
                dateFormat = db.display.dateFormat.liveData,
            )
        }
    TimelineChart(series = series, prefs = prefs, colors = colors, onScrub = onScrub)
}

// The day last touched on the chart, which the actions under it act on.
// Forgotten with a new pair or period.
@Stable
private class TouchedDay {
    var day by mutableStateOf<LocalDate?>(null)
}

@Composable
private fun rememberTouchedDay(model: TimelineViewModel): TouchedDay {
    val touched = remember { TouchedDay() }
    val pair by model.getCurrencyPair().observeAsState()
    val period by model.getPeriod().observeAsState()
    LaunchedEffect(pair, period) { touched.day = null }
    return touched
}

// Under the chart, so they never cover its line or the touched day's label:
// "Convert at 04/03/26" and "Alert at 3.6512" for [day], or, before a touch,
// a line saying the chart can be touched. Always one chip tall, so the chart
// keeps its size when they appear.
@Composable
private fun ChartDayActions(
    model: TimelineViewModel,
    day: LocalDate?,
    formatter: DateTimeFormatter,
    onConvertAt: (CurrencyPair, LocalDate) -> Unit,
    onAlertAt: (BigDecimal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pair by model.getCurrencyPair().observeAsState()
    val rates by remember(model) { model.getRates() }.observeAsState()
    Row(
        modifier = modifier.heightIn(min = DAY_ACTIONS_HEIGHT).padding(horizontal = TIMELINE_CONTENT_PADDING),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (from, to) = pair ?: return@Row
        when {
            day != null -> {
                ChartChip(label = stringResource(R.string.timeline_convert_at, day.format(formatter)), icon = R.drawable.ic_swap_horiz) {
                    onConvertAt(CurrencyPair(from, to), day)
                }
                rates?.get(day)?.value?.let { rate ->
                    val shown =
                        rate
                            .roundForDisplay(
                                ALERT_RATE_DECIMALS,
                            ).toHumanReadableNumber(context, decimalPlaces = ALERT_RATE_DECIMALS)
                    ChartChip(label = stringResource(R.string.timeline_alert_at, shown), icon = R.drawable.ic_alert) { onAlertAt(rate) }
                }
            }

            !rates.isNullOrEmpty() -> {
                Text(
                    text = stringResource(R.string.timeline_touch_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChartChip(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = rememberHapticOnClick(onClick),
        label = { Text(label) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, Modifier.size(CONVERT_CHIP_ICON_SIZE)) },
    )
}

private val CONVERT_CHIP_ICON_SIZE = 18.dp

// An assist chip's 32 dp and Material's 8 dp around it.
private val DAY_ACTIONS_HEIGHT = 48.dp

// "🇺🇸 $ USD → 🇮🇱 ₪ ILS": each side's flag, symbol and code, joined by an
// arrow — shorter than the localized "to", so the pair fits beside the back
// arrow in a small bar. Screen readers still hear the localized sentence.
@Composable
internal fun TimelineTitle(pair: Pair<Currency, Currency>?) {
    val (from, to) = pair ?: return
    val spoken = AnnotatedString.fromHtml(stringResource(R.string.activity_timeline_title, from.iso4217Alpha(), to.iso4217Alpha())).text
    val symbolColor = MaterialTheme.colorScheme.onSurfaceVariant
    val text =
        remember(from, to, symbolColor) {
            buildAnnotatedString {
                appendSide(FLAG_FROM_ID, from, symbolColor)
                append(TITLE_GAP)
                appendInlineContent(ARROW_ID, alternateText = "→")
                append(TITLE_GAP)
                appendSide(FLAG_TO_ID, to, symbolColor)
            }
        }
    val flagFrom = from.flagPainter()
    val flagTo = to.flagPainter()
    val inlineContent =
        mapOf(
            FLAG_FROM_ID to inlineFlag { Image(flagFrom, contentDescription = null, modifier = it) },
            FLAG_TO_ID to inlineFlag { Image(flagTo, contentDescription = null, modifier = it) },
            ARROW_ID to
                InlineTextContent(Placeholder(TITLE_ARROW_SIZE, TITLE_ARROW_SIZE, PlaceholderVerticalAlign.TextCenter)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_forward),
                        contentDescription = null,
                        tint = symbolColor,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
        )
    // A pair reads from → to, left to right, in every language.
    Ltr {
        Text(
            text = text,
            inlineContent = inlineContent,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        )
    }
}

// Flag, then the symbol (when the currency has one besides its code) in a
// quieter tone, then the code in bold.
private fun AnnotatedString.Builder.appendSide(
    flagId: String,
    currency: Currency,
    symbolColor: Color,
) {
    val code = currency.iso4217Alpha()
    appendInlineContent(flagId, alternateText = code)
    append(FLAG_GAP)
    currency.symbol()?.takeIf { it != code }?.let { symbol ->
        withStyle(SpanStyle(color = symbolColor)) { append(symbol) }
        append(' ')
    }
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(code) }
}

private fun inlineFlag(image: @Composable (Modifier) -> Unit): InlineTextContent =
    InlineTextContent(
        Placeholder(width = TITLE_FLAG_WIDTH, height = TITLE_FLAG_HEIGHT, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter),
    ) {
        image(Modifier.fillMaxSize().clip(RoundedCornerShape(TITLE_FLAG_CORNER)))
    }
