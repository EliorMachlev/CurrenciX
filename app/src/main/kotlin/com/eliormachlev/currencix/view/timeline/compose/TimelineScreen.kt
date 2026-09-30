package com.eliormachlev.currencix.view.timeline.compose

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.window.layout.FoldingFeature
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.compose.LayerCapture
import com.eliormachlev.currencix.view.compose.captureInto
import com.eliormachlev.currencix.viewmodel.timeline.TimelineViewModel
import java.time.format.DateTimeFormatter

@Composable
@Suppress("LongParameterList")
internal fun TimelineScreen(
    model: TimelineViewModel,
    formatter: DateTimeFormatter,
    foldingFeature: FoldingFeature?,
    onChangeProvider: () -> Unit,
    modifier: Modifier = Modifier,
    chartCapture: LayerCapture? = null,
    chartContent: @Composable () -> Unit,
) {
    AppTheme {
        // Transparent: the screen frame paints the window background (true
        // black on OLED), the same color the top bar above uses.
        Surface(
            modifier = modifier.fillMaxSize(),
            color = Color.Transparent,
        ) {
            val isRefreshing by model.isRefreshing().observeAsState(initial = false)
            val error by model.getError().observeAsState()
            val rangeEmpty by model.isRangeEmpty().observeAsState(initial = false)
            val provider by model.getProvider().observeAsState()
            val ratePast by model.getRatePast().observeAsState()
            val rateCurrent by model.getRateCurrent().observeAsState()
            val diffPercent by model.getRatesDifferencePercent().observeAsState()
            val ratesMax by model.getRatesMax().observeAsState()
            val ratesAvg by model.getRatesAverage().observeAsState()
            val ratesMed by model.getRatesMedian().observeAsState()
            val ratesMin by model.getRatesMin().observeAsState()

            val selectedPeriod by model.getPeriod().observeAsState(TimelineViewModel.Period.YEAR)
            val customRange by model.getCustomRange().observeAsState()
            val period =
                PeriodControl(
                    selected = selectedPeriod,
                    customRange = customRange,
                    onPeriod = model::setTimePeriod,
                    onCustomRange = model::setCustomRange,
                )

            val chartCard: @Composable (Modifier) -> Unit = { mod ->
                TimelineChartCard(
                    isRefreshing = isRefreshing,
                    error = error,
                    empty = rangeEmpty,
                    provider = provider,
                    onRetry = model::retry,
                    onChangeProvider = onChangeProvider,
                    modifier = mod.captureInto(chartCapture),
                    chart = chartContent,
                )
            }
            val secondary: @Composable (Modifier) -> Unit = { mod ->
                TimelineSecondary(
                    ratePast = ratePast,
                    rateCurrent = rateCurrent,
                    diffPercent = diffPercent,
                    ratesMax = ratesMax,
                    ratesAvg = ratesAvg,
                    ratesMed = ratesMed,
                    ratesMin = ratesMin,
                    formatter = formatter,
                    period = period,
                    modifier = mod,
                )
            }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val layout = foldingFeature?.let { orientationFor(it) } ?: defaultLayoutFor(maxWidth > maxHeight)
                TimelinePanes(layout, chartCard, secondary)
            }
        }
    }
}

// Chart and statistics side by side ([TimelineLayout.ROW]), or the chart
// above taking whatever the compact statistics leave.
@Composable
private fun TimelinePanes(
    layout: TimelineLayout,
    chartCard: @Composable (Modifier) -> Unit,
    secondary: @Composable (Modifier) -> Unit,
) {
    if (layout == TimelineLayout.ROW) {
        Row(modifier = Modifier.fillMaxSize()) {
            chartCard(Modifier.weight(1f).fillMaxHeight())
            secondary(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            chartCard(Modifier.weight(1f).fillMaxWidth())
            secondary(Modifier.fillMaxWidth())
        }
    }
}
