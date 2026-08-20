package com.eliormachlev.currencix.view.timeline.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.window.layout.FoldingFeature
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.viewmodel.timeline.TimelineViewModel
import java.time.format.DateTimeFormatter

@Composable
@Suppress("LongParameterList")
internal fun TimelineScreen(
    model: TimelineViewModel,
    formatter: DateTimeFormatter,
    foldingFeature: FoldingFeature?,
    chartContent: @Composable () -> Unit,
) {
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val isRefreshing by model.isUpdating().observeAsState(initial = false)
            val error by model.getError().observeAsState()
            val provider by model.getProvider().observeAsState()
            val ratePast by model.getRatePast().observeAsState()
            val rateCurrent by model.getRateCurrent().observeAsState()
            val diffPercent by model.getRatesDifferencePercent().observeAsState()
            val ratesMax by model.getRatesMax().observeAsState()
            val ratesAvg by model.getRatesAverage().observeAsState()
            val ratesMed by model.getRatesMedian().observeAsState()
            val ratesMin by model.getRatesMin().observeAsState()

            val period =
                remember { mutableStateOf(TimelineViewModel.Period.YEAR) }

            val layout =
                foldingFeature?.let { orientationFor(it) }
                    ?: defaultLayoutFor(LocalConfiguration.current.orientation)

            val chartCard: @Composable (Modifier) -> Unit = { mod ->
                TimelineChartCard(
                    isRefreshing = isRefreshing,
                    error = error,
                    provider = provider,
                    modifier = mod,
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
                    selectedPeriod = period.value,
                    onPeriodSelected = onPeriodChange(period, model),
                    modifier = mod,
                )
            }

            if (layout == TimelineLayout.ROW) {
                Row(modifier = Modifier.fillMaxSize()) {
                    chartCard(Modifier.weight(1f).fillMaxHeight())
                    secondary(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    chartCard(Modifier.weight(1f).fillMaxWidth())
                    secondary(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

private fun onPeriodChange(
    period: MutableState<TimelineViewModel.Period>,
    model: TimelineViewModel,
): (TimelineViewModel.Period) -> Unit =
    { next ->
        period.value = next
        model.setTimePeriod(next)
    }
