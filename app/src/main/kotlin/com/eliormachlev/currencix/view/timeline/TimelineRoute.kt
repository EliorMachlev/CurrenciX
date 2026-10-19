package com.eliormachlev.currencix.view.timeline

import android.app.Application
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.map
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.layout.FoldingFeature
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.alert.ALERT_RATE_DECIMALS
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.rateFor
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.buildImageShareChooser
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.util.resolveThemeColor
import com.eliormachlev.currencix.util.roundForDisplay
import com.eliormachlev.currencix.util.stripTimePattern
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.alert.RateAlertSheet
import com.eliormachlev.currencix.view.alert.rememberSetRateAlert
import com.eliormachlev.currencix.view.compose.CurrencyPairRow
import com.eliormachlev.currencix.view.compose.LayerCapture
import com.eliormachlev.currencix.view.compose.OverflowAction
import com.eliormachlev.currencix.view.compose.PairRowActions
import com.eliormachlev.currencix.view.compose.ReadingDirection
import com.eliormachlev.currencix.view.compose.ScreenScaffold
import com.eliormachlev.currencix.view.compose.TopBarOverflowMenu
import com.eliormachlev.currencix.view.compose.TopBarStyle
import com.eliormachlev.currencix.view.compose.rememberDatabase
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.view.main.spinner.CurrencyPickerSheet
import com.eliormachlev.currencix.view.navigation.PillSide
import com.eliormachlev.currencix.view.navigation.Screen
import com.eliormachlev.currencix.view.preference.compose.GraphOptionsSheet
import com.eliormachlev.currencix.view.preference.compose.ProviderPickerWithKeyPrompt
import com.eliormachlev.currencix.view.timeline.compose.TIMELINE_CONTENT_PADDING
import com.eliormachlev.currencix.view.timeline.compose.TimelineScreen
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel
import com.eliormachlev.currencix.viewmodel.timeline.TimelineViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Rate history for [screen]'s pair, shown under the small top bar as the
 * converter shows it: two currency pills and the swap button. After a touch
 * on the chart, a chip offers the converter at the day last touched
 * ([onConvertAt]).
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
    val db = rememberDatabase()
    val formatter = remember(db) { DateTimeFormatter.ofPattern(stripTimePattern(db.display.dateFormat.get())) }
    val pair by model.getCurrencyPair().observeAsState()
    var sheet by rememberSaveable { mutableStateOf<TimelineSheet?>(null) }
    val chartCapture = remember { LayerCapture() }
    val scope = rememberCoroutineScope()
    // The rate a rate-alert sheet is open at: today's (the bell) or a touched day's.
    var alertAt by remember { mutableStateOf<BigDecimal?>(null) }
    val touched = rememberTouchedDay(model)

    // Small bar, leaving the height to the chart. The bar follows the language
    // (in Hebrew: back arrow and title on the right, actions on the left);
    // the pair's pills under it, the chart and figures set their own.
    ReadingDirection {
        ScreenScaffold(
            title = { Text(stringResource(R.string.menu_timeline), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
            Column(Modifier.fillMaxSize().padding(padding)) {
                // The screen's own pair until the data (and any swap) has loaded,
                // so the pills are there while offline or still fetching.
                TimelinePairRow(model, db, pair ?: (screen.from to screen.to))
                TimelineScreen(
                    model = model,
                    formatter = formatter,
                    foldingFeature = foldingFeature,
                    onChangeProvider = { sheet = TimelineSheet.Provider },
                    modifier = Modifier.weight(1f),
                    chartCapture = chartCapture,
                    chartContent = { TouchableChart(model, db, touched) },
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
    }
    TimelineSheets(sheet = sheet, db = db, onProviderPicked = { model.retry() }, onDismiss = { sheet = null })
    alertAt?.let { rate -> TimelineAlertSheet(model = model, rate = rate, onClose = { alertAt = null }) }
}

// The bar's actions, under ⋮: a rate alert at today's rate, share and graph
// options (swap is the pills' round button).
@Composable
private fun TimelineBarActions(
    model: TimelineViewModel,
    onOptions: () -> Unit,
    onAlert: (BigDecimal) -> Unit,
    onShare: () -> Unit,
) {
    val error by model.getError().observeAsState()
    val rateCurrent by remember(model) { model.getRateCurrent() }.observeAsState()
    val today = rateCurrent?.first?.value?.value
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
            ProviderPickerWithKeyPrompt(
                selected = current,
                viewModel = preferences,
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

// The chart, remembering the day a touch lands on (for the chips under it)
// and showing that day's figures.
@Composable
private fun TouchableChart(
    model: TimelineViewModel,
    db: Database,
    touched: TouchedDay,
) {
    TimelineChartContent(model, db) { day ->
        if (day != null) touched.day = day
        model.setPastDate(day)
    }
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
// keeps its size when they appear. They read as text, from the language's
// starting edge.
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
    // In the language's direction: the hint and the chips start on the right in Hebrew.
    ReadingDirection {
        Row(
            modifier = modifier.fillMaxWidth().heightIn(min = DAY_ACTIONS_HEIGHT).padding(horizontal = TIMELINE_CONTENT_PADDING),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (from, to) = pair ?: return@Row
            when {
                day != null -> {
                    ChartChip(
                        label = stringResource(R.string.timeline_convert_at, day.format(formatter)),
                        icon = R.drawable.ic_swap_horiz,
                    ) {
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

// The pair as the converter shows it: a pill per currency (a tap opens the
// picker for it) and the round swap button, which waits while a refresh or
// an error is in the way, since swapping re-fetches. Opening the chart from
// the converter, its pills fly into these.
@Composable
private fun TimelinePairRow(
    model: TimelineViewModel,
    db: Database,
    pair: Pair<Currency, Currency>,
) {
    val inFlight by model.isRefreshInFlight().observeAsState(false)
    val error by model.getError().observeAsState()
    var picking by remember { mutableStateOf<PillSide?>(null) }
    val actions =
        remember(model) {
            PairRowActions(
                onFromClick = { picking = PillSide.FROM },
                onToClick = { picking = PillSide.TO },
                onSwap = model::toggleCurrencies,
                onSwapLongPress = {},
            )
        }
    CurrencyPairRow(
        from = pair.first,
        to = pair.second,
        actions = actions,
        swapEnabled = !inFlight && error == null,
        modifier = Modifier.padding(horizontal = Spacing.Large, vertical = Spacing.Small),
    )
    picking?.let { side -> TimelineCurrencyPicker(side, model, db, pair) { picking = null } }
}

// The currency picker for the chart's [side]: the latest rates, against one
// of the other side's currency, which can't be picked.
@Composable
private fun TimelineCurrencyPicker(
    side: PillSide,
    model: TimelineViewModel,
    db: Database,
    pair: Pair<Currency, Currency>,
    onDismiss: () -> Unit,
) {
    val rates by db.rates.getExchangeRates().observeAsState()
    val (from, to) = pair
    val other = if (side == PillSide.FROM) to else from
    CurrencyPickerSheet(
        currentRate = rates?.rateFor(other)?.let { Rate(other, it.value) },
        currentSum = BigDecimal.ONE,
        disabledCurrency = other,
        onRateClicked = { rate ->
            if (side == PillSide.FROM) model.setCurrencies(rate.currency, to) else model.setCurrencies(from, rate.currency)
        },
        onDismiss = onDismiss,
        selectedCurrency = if (side == PillSide.FROM) from else to,
    )
}
