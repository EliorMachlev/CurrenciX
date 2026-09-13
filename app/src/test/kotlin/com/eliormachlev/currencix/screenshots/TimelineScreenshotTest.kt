package com.eliormachlev.currencix.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.view.timeline.compose.TimelineChartCard
import com.eliormachlev.currencix.view.timeline.compose.TimelineSecondary
import com.eliormachlev.currencix.viewmodel.timeline.TimelineViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Timeline surfaces: the two split halves that TimelineScreen composes.
// TimelineChart itself is Vico-backed and its rendering (axes, lines,
// scrub marker) is exercised by Vico's own tests + manual QA; we golden
// the container (TimelineChartCard) with a stand-in "chart" and the
// stats/period column (TimelineSecondary) with representative rates.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5)
class TimelineScreenshotTest {
    @Test fun timelineChartCardIdle() =
        captureMatrix("timeline_chart_card_idle") {
            TimelineChartCardPreview(
                isRefreshing = false,
                error = null,
                provider = SAMPLE_PROVIDER_ATTRIBUTION,
            )
        }

    @Test fun timelineChartCardRefreshing() =
        captureMatrix("timeline_chart_card_refreshing") {
            TimelineChartCardPreview(
                isRefreshing = true,
                error = null,
                provider = SAMPLE_PROVIDER_ATTRIBUTION,
            )
        }

    @Test fun timelineChartCardError() =
        captureMatrix("timeline_chart_card_error") {
            TimelineChartCardPreview(
                isRefreshing = false,
                error = "<b>Network error</b><br/>Could not load historical rates.",
                provider = null,
            )
        }

    @Test fun timelineSecondary() =
        captureMatrix("timeline_secondary") {
            Column(modifier = Modifier.fillMaxSize()) {
                TimelineSecondary(
                    ratePast = SAMPLE_PAST_RATE_ENTRY to DECIMALS,
                    rateCurrent = SAMPLE_CURRENT_RATE_ENTRY to DECIMALS,
                    diffPercent = BigDecimal("2.37"),
                    ratesMax = Triple(SAMPLE_MAX_RATE, SAMPLE_MAX_DATE, DECIMALS),
                    ratesAvg = SAMPLE_AVG_RATE to DECIMALS,
                    ratesMed = SAMPLE_MED_RATE to DECIMALS,
                    ratesMin = Triple(SAMPLE_MIN_RATE, SAMPLE_MIN_DATE, DECIMALS),
                    formatter = FORMATTER,
                    selectedPeriod = TimelineViewModel.Period.YEAR,
                    onPeriodSelected = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
}

@Composable
private fun TimelineChartCardPreview(
    isRefreshing: Boolean,
    error: String?,
    provider: CharSequence?,
) {
    Column(modifier = Modifier.fillMaxWidth().height(CHART_PREVIEW_HEIGHT)) {
        TimelineChartCard(
            isRefreshing = isRefreshing,
            error = error,
            provider = provider,
            modifier = Modifier.fillMaxSize(),
            chart = {
                // Placeholder in lieu of a real Vico chart — the card's
                // paint order (background, progress bar, error text,
                // provider attribution) is what this golden guards.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text(text = "chart") }
            },
        )
    }
}

private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")
private val CHART_PREVIEW_HEIGHT = 260.dp
private const val DECIMALS = 4
private const val SAMPLE_PROVIDER_ATTRIBUTION = "Rates via <b>Sample Provider</b>"

private val SAMPLE_PAST_DATE: LocalDate = LocalDate.of(2025, 9, 13)
private val SAMPLE_CURRENT_DATE: LocalDate = LocalDate.of(2026, 9, 13)
private val SAMPLE_MAX_DATE: LocalDate = LocalDate.of(2026, 3, 4)
private val SAMPLE_MIN_DATE: LocalDate = LocalDate.of(2025, 11, 22)

private val SAMPLE_PAST_RATE: Rate = Rate(currency = Currency.EUR, value = BigDecimal("0.9153"))
private val SAMPLE_CURRENT_RATE: Rate = Rate(currency = Currency.EUR, value = BigDecimal("0.9370"))
private val SAMPLE_MAX_RATE: Rate = Rate(currency = Currency.EUR, value = BigDecimal("0.9521"))
private val SAMPLE_AVG_RATE: Rate = Rate(currency = Currency.EUR, value = BigDecimal("0.9285"))
private val SAMPLE_MED_RATE: Rate = Rate(currency = Currency.EUR, value = BigDecimal("0.9271"))
private val SAMPLE_MIN_RATE: Rate = Rate(currency = Currency.EUR, value = BigDecimal("0.8998"))

private val SAMPLE_PAST_RATE_ENTRY: Map.Entry<LocalDate, Rate?> =
    java.util.AbstractMap.SimpleImmutableEntry(SAMPLE_PAST_DATE, SAMPLE_PAST_RATE)
private val SAMPLE_CURRENT_RATE_ENTRY: Map.Entry<LocalDate, Rate?> =
    java.util.AbstractMap.SimpleImmutableEntry(SAMPLE_CURRENT_DATE, SAMPLE_CURRENT_RATE)
