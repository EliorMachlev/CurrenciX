package com.eliormachlev.currencix.view.timeline

import android.app.Application
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.buildImageShareChooser
import com.eliormachlev.currencix.util.resolveThemeColor
import com.eliormachlev.currencix.util.stripTimePattern
import com.eliormachlev.currencix.view.compose.LayerCapture
import com.eliormachlev.currencix.view.compose.ScreenScaffold
import com.eliormachlev.currencix.view.compose.TopBarAction
import com.eliormachlev.currencix.view.compose.TopBarStyle
import com.eliormachlev.currencix.view.compose.flagPainter
import com.eliormachlev.currencix.view.navigation.Screen
import com.eliormachlev.currencix.view.preference.compose.GraphOptionsSheet
import com.eliormachlev.currencix.view.preference.compose.ProviderPickerDialog
import com.eliormachlev.currencix.view.timeline.compose.TimelineScreen
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel
import com.eliormachlev.currencix.viewmodel.timeline.TimelineViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
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

/** Rate history for [screen]'s pair, under a small top bar titled "🇺🇸 $ USD → 🇮🇱 ₪ ILS". */
@Composable
fun TimelineRoute(
    screen: Screen.Timeline,
    onBack: () -> Unit,
    foldingFeature: FoldingFeature?,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val model: TimelineViewModel = viewModel(factory = TimelineViewModel.factory(application, screen.from, screen.to))
    val db = remember(context) { Database(context) }
    val formatter = remember(db) { DateTimeFormatter.ofPattern(stripTimePattern(db.getDateFormatBlocking())) }
    val pair by model.getCurrencyPair().observeAsState()
    val inFlight by model.isRefreshInFlight().observeAsState(false)
    val error by model.getError().observeAsState()
    var sheet by rememberSaveable { mutableStateOf<TimelineSheet?>(null) }
    val chartCapture = remember { LayerCapture() }
    val scope = rememberCoroutineScope()

    // Small bar: the pair sits beside the back arrow, leaving the height to the chart.
    ScreenScaffold(
        // The screen's own pair until the data (and any swap) has loaded, so
        // the title is there while offline or still fetching.
        title = { TimelineTitle(pair ?: (screen.from to screen.to)) },
        onBack = onBack,
        style = TopBarStyle.Small,
        actions = {
            TopBarAction(
                icon = painterResource(R.drawable.ic_tune),
                contentDescription = stringResource(R.string.graph_options_title),
                onClick = { sheet = TimelineSheet.GraphOptions },
            )
            // Swapping re-fetches the pair; wait out a refresh or an error first.
            TopBarAction(
                icon = painterResource(R.drawable.ic_swap_horiz),
                contentDescription = stringResource(R.string.desc_toggle_currencies),
                onClick = model::toggleCurrencies,
                enabled = !inFlight && error == null,
            )
            TopBarAction(
                icon = painterResource(R.drawable.ic_share),
                contentDescription = stringResource(R.string.timeline_share),
                onClick = { scope.launch { shareChart(context, chartCapture, model, formatter) } },
                enabled = error == null,
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
            chartContent = { TimelineChartContent(model, db) },
        )
    }
    TimelineSheets(sheet = sheet, db = db, onProviderPicked = { model.retry() }, onDismiss = { sheet = null })
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
        TimelineSheet.GraphOptions -> GraphOptionsSheet(db = db, onDismiss = onDismiss)
        TimelineSheet.Provider -> {
            val preferences: PreferenceViewModel = viewModel()
            val current by preferences.apiProvider.collectAsStateWithLifecycle()
            ProviderPickerDialog(
                selected = current,
                onDismiss = onDismiss,
                onPicked = { provider ->
                    preferences.setApiProvider(provider)
                    onProviderPicked()
                },
            )
        }
        null -> Unit
    }
}

@Composable
private fun TimelineChartContent(
    model: TimelineViewModel,
    db: Database,
) {
    val context = LocalContext.current
    val lineColor = remember(context) { Color(context.resolveThemeColor(R.attr.colorPrimary)) }
    val axisColor = remember(context) { Color(context.resolveThemeColor(android.R.attr.textColorSecondary)) }
    // Text-on-background keeps the scrub line distinct from the green primary
    // (max highlight), red min line, and the blue/purple period-change
    // verticals on every theme.
    val scrubLineColor = remember(context) { Color(context.resolveThemeColor(android.R.attr.textColorPrimary)) }
    val entriesLive =
        remember(model) {
            model.getRates().map { rates ->
                rates?.entries?.map { entry -> entry.key to entry.value.value.toFloat() }?.toImmutableList()
            }
        }
    // Created once per screen: a fresh LiveData per recomposition would make
    // the chart drop and re-add its observers every time.
    val chartPrefs = remember(db) { ChartPrefs(db) }
    TimelineChart(
        entriesLive = entriesLive,
        showGridLive = chartPrefs.showGrid,
        showXAxisLive = chartPrefs.showXAxis,
        showYAxisLive = chartPrefs.showYAxis,
        highlightExtremesLive = chartPrefs.highlightExtremes,
        highlightPeriodChangeLive = chartPrefs.highlightPeriodChange,
        dateFormatLive = chartPrefs.dateFormat,
        // Range extremes (scrub-independent) so the min/max reference lines
        // stay pinned to the visible period's low/high while the finger drags.
        highlightMinLive = model.getRatesRangeMin(),
        highlightMaxLive = model.getRatesRangeMax(),
        lineColor = lineColor,
        baselineColor = axisColor,
        axisColor = axisColor,
        scrubLineColor = scrubLineColor,
        onScrub = model::setPastDate,
    )
}

// The chart's display preferences (Graph options), read from DataStore.
private class ChartPrefs(
    db: Database,
) {
    val showGrid = db.isChartGridEnabled()
    val showXAxis = db.isChartXAxisLabelEnabled()
    val showYAxis = db.isChartYAxisLabelEnabled()
    val highlightExtremes = db.isChartHighlightExtremesEnabled()
    val highlightPeriodChange = db.isChartHighlightPeriodChangeEnabled()
    val dateFormat = db.getDateFormat()
}

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
    Text(
        text = text,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
    )
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
