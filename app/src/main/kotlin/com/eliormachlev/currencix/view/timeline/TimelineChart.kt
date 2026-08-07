package com.eliormachlev.currencix.view.timeline

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.eliormachlev.currencix.util.stripTimePattern
import com.eliormachlev.currencix.view.compose.Ltr
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
@Suppress("LongParameterList", "LongMethod")
fun TimelineChart(
    entriesLive: LiveData<List<Pair<LocalDate, Float>>?>,
    showGridLive: LiveData<Boolean>,
    showXAxisLive: LiveData<Boolean>,
    showYAxisLive: LiveData<Boolean>,
    highlightExtremesLive: LiveData<Boolean>,
    dateFormatLive: LiveData<String>,
    // Scrub-aware highlight values from the viewmodel. Passing these in (rather
    // than deriving from `entries`) keeps the red/blue highlight lines aligned
    // with the MIN/MAX readouts when the user scrubs, since the readouts also
    // apply the scrub filter.
    highlightMinLive: LiveData<Double?>,
    highlightMaxLive: LiveData<Double?>,
    lineColor: Color,
    baselineColor: Color,
    axisColor: Color,
    onScrub: (LocalDate?) -> Unit,
) {
    val entries by entriesLive.observeAsState()
    val showGrid by showGridLive.observeAsState(initial = true)
    val showXAxis by showXAxisLive.observeAsState(initial = true)
    val showYAxis by showYAxisLive.observeAsState(initial = true)
    val highlightExtremes by highlightExtremesLive.observeAsState(initial = true)
    val highlightMin by highlightMinLive.observeAsState()
    val highlightMax by highlightMaxLive.observeAsState()
    val dateFormat by dateFormatLive.observeAsState(initial = DEFAULT_DATE_FORMAT)
    val axisDateFormatter =
        remember(dateFormat) {
            DateTimeFormatter.ofPattern(stripYear(stripTimePattern(dateFormat)))
        }

    val data = entries.orEmpty()
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data.size, data.hashCode()) {
        if (data.isNotEmpty()) {
            modelProducer.runTransaction {
                lineModel { series(data.map { it.second }) }
            }
        }
    }

    val minValue = remember(data) { data.minOfOrNull { it.second }?.toDouble() }
    val maxValue = remember(data) { data.maxOfOrNull { it.second }?.toDouble() }
    val baseline = remember(data) { data.lastOrNull()?.second?.toDouble() }

    // Vico's axis measurement (getMaxLabelWidth) may call this with x-values outside
    // data.indices while the model is transitioning to a smaller series. Returning a
    // blank string throws IllegalStateException, so clamp to the valid range and fall
    // back to a non-blank placeholder when the series is empty.
    val bottomAxisValueFormatter =
        remember(data, axisDateFormatter) {
            CartesianValueFormatter { _, value, _ ->
                val lastIdx = data.size - 1
                if (lastIdx < 0) {
                    AXIS_LABEL_EMPTY_PLACEHOLDER
                } else {
                    val idx = value.toInt().coerceIn(0, lastIdx)
                    data[idx].first.format(axisDateFormatter)
                }
            }
        }

    val rangeProvider =
        remember(minValue, maxValue) {
            when {
                minValue != null && maxValue != null && minValue < maxValue -> {
                    val pad = (maxValue - minValue) * Y_AXIS_PADDING
                    CartesianLayerRangeProvider.fixed(minY = minValue - pad, maxY = maxValue + pad)
                }
                // Constant series (e.g. AUD → AUD, all rates = 1.0). auto()
                // stretches to [0, 1], which puts the top Y-axis label at the
                // layer boundary and overflows above the chart region. Pin a
                // symmetric ±FLAT_SERIES_PADDING band around the value so the
                // chart shows a centered flat line with a bounded axis.
                minValue != null && maxValue != null -> {
                    val pad = FLAT_SERIES_PADDING.coerceAtLeast(kotlin.math.abs(minValue) * Y_AXIS_PADDING)
                    CartesianLayerRangeProvider.fixed(minY = minValue - pad, maxY = maxValue + pad)
                }
                else -> CartesianLayerRangeProvider.auto()
            }
        }

    val markerListener =
        remember(data, onScrub) {
            object : CartesianMarkerVisibilityListener {
                override fun onShown(
                    marker: CartesianMarker,
                    targets: List<CartesianMarker.Target>,
                ) {
                    val idx = targets.firstOrNull()?.x?.toInt() ?: return
                    onScrub(data.getOrNull(idx)?.first)
                }

                override fun onHidden(marker: CartesianMarker) {
                    onScrub(null)
                }
            }
        }

    val axisLabelStyle = TextStyle(color = axisColor, fontSize = AXIS_LABEL_FONT_SIZE_SP.sp)

    val markerValueFormatter =
        remember {
            DefaultCartesianMarker.ValueFormatter.default(decimalCount = MARKER_DECIMAL_COUNT)
        }
    val marker =
        rememberDefaultCartesianMarker(
            label = rememberTextComponent(style = axisLabelStyle),
            valueFormatter = markerValueFormatter,
        )

    // Solid verticals at year and month boundaries. Suppress the month lines
    // on the year view (heuristic: >90 data points) since ~12 of them just
    // add noise. Year boundaries always imply month boundaries, so skip the
    // month line at the same index to avoid stacking two colors.
    //
    // Solid (not dashed) because vico's DashedShape renders inconsistently on
    // vertical lines across chart contexts (weekly vs monthly view), even
    // with FitStrategy.Fixed and whole-pixel x-snapping.
    val showMonthChangeLines = data.size <= YEAR_VIEW_MIN_POINTS
    val yearChangeIndices =
        remember(data) {
            mutableListOf<Int>().apply {
                for (i in 1 until data.size) {
                    if (data[i - 1].first.year != data[i].first.year) add(i)
                }
            }
        }
    val monthChangeIndices =
        remember(data, showMonthChangeLines) {
            mutableListOf<Int>().apply {
                if (!showMonthChangeLines) return@apply
                for (i in 1 until data.size) {
                    val prev = data[i - 1].first
                    val curr = data[i].first
                    if (prev.year == curr.year && prev.monthValue != curr.monthValue) add(i)
                }
            }
        }

    val decorations =
        buildList {
            monthChangeIndices.forEach { idx ->
                add(
                    VerticalLine(
                        x = idx.toDouble(),
                        line =
                            LineComponent(
                                fill = Fill(MONTH_CHANGE_COLOR),
                                thickness = CHART_LINE_THICKNESS_DP.dp,
                            ),
                    ),
                )
            }
            yearChangeIndices.forEach { idx ->
                add(
                    VerticalLine(
                        x = idx.toDouble(),
                        line =
                            LineComponent(
                                fill = Fill(YEAR_CHANGE_COLOR),
                                thickness = CHART_LINE_THICKNESS_DP.dp,
                            ),
                    ),
                )
            }
            if (baseline != null) {
                add(
                    HorizontalLineUnder(
                        y = baseline,
                        line = LineComponent(fill = Fill(baselineColor), thickness = CHART_LINE_THICKNESS_DP.dp),
                    ),
                )
            }
            val hMin = highlightMin
            val hMax = highlightMax
            if (highlightExtremes && hMin != null && hMax != null && hMin != hMax) {
                val maxFill = Fill(lineColor.copy(alpha = HIGHLIGHT_ALPHA))
                val minFill = Fill(MIN_LINE_COLOR.copy(alpha = HIGHLIGHT_ALPHA))
                add(
                    HorizontalLineUnder(
                        y = hMin,
                        line = LineComponent(fill = minFill, thickness = CHART_LINE_THICKNESS_DP.dp),
                    ),
                )
                add(
                    HorizontalLineUnder(
                        y = hMax,
                        line = LineComponent(fill = maxFill, thickness = CHART_LINE_THICKNESS_DP.dp),
                    ),
                )
            }
        }

    val yAxisItemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { Y_AXIS_TARGET_LABEL_COUNT }) }
    val startAxis =
        VerticalAxis.rememberStart(
            label = if (showYAxis) rememberAxisLabelComponent(style = axisLabelStyle) else null,
            guideline = if (showGrid) rememberAxisGuidelineComponent() else null,
            itemPlacer = yAxisItemPlacer,
        )
    val axisItemPlacer =
        remember(data.size, yearChangeIndices, monthChangeIndices) {
            // Aligned placer emits labels at 0, spacing, 2*spacing, … up to n-1, so the
            // label count is floor((n-1)/spacing) + 1. To cap at exactly
            // X_AXIS_TARGET_LABEL_COUNT (never one over), pick the smallest spacing that
            // fits (count-1) hops across (n-1) values: ceil((n-1) / (count-1)). For a
            // 365-point year this gives spacing=61 → 7 labels instead of spacing=52 → 8.
            val span = (data.size - 1).coerceAtLeast(1)
            val spacing =
                ((span + X_AXIS_TARGET_LABEL_COUNT - 2) / (X_AXIS_TARGET_LABEL_COUNT - 1))
                    .coerceAtLeast(1)
            val aligned = HorizontalAxis.ItemPlacer.aligned(spacing = { spacing })
            val skipX = (yearChangeIndices + monthChangeIndices).map { it.toDouble() }.toSet()
            if (skipX.isEmpty()) aligned else SuppressGuidelineItemPlacer(aligned, skipX)
        }
    val bottomAxis =
        HorizontalAxis.rememberBottom(
            label = if (showXAxis) rememberAxisLabelComponent(style = axisLabelStyle) else null,
            guideline = if (showGrid) rememberAxisGuidelineComponent() else null,
            valueFormatter = bottomAxisValueFormatter,
            labelRotationDegrees = X_AXIS_LABEL_ROTATION,
            itemPlacer = axisItemPlacer,
        )

    // Rebuild the host when the series length changes: Vico's scroll/marker state
    // caches the previous point count and crashes when the dataset shrinks.
    key(data.size) {
        // Vico maps touch x-coordinates against the host's layout direction, so
        // under an RTL locale (e.g. Hebrew) the scrub marker mirrors to the
        // wrong side of the finger and clamps to the left edge.
        Ltr {
            CartesianChartHost(
                modifier = Modifier.fillMaxSize(),
                chart =
                    rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider =
                                LineCartesianLayer.LineProvider.series(
                                    LineCartesianLayer.rememberLine(
                                        fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
                                    ),
                                ),
                            rangeProvider = rangeProvider,
                        ),
                        startAxis = startAxis,
                        bottomAxis = bottomAxis,
                        marker = marker,
                        markerVisibilityListener = markerListener,
                        decorations = decorations,
                    ),
                modelProducer = modelProducer,
                scrollState = rememberVicoScrollState(scrollEnabled = false),
            )
        }
    }
}

internal const val DEFAULT_DATE_FORMAT = "dd/MM/yy"

private fun stripYear(pattern: String): String = pattern.replace("/yy", "").replace("yy/", "")

private const val HIGHLIGHT_ALPHA = 0.4f
private const val Y_AXIS_PADDING = 0.05
private const val FLAT_SERIES_PADDING = 0.01
private const val X_AXIS_LABEL_ROTATION = 0f
private const val X_AXIS_TARGET_LABEL_COUNT = 7
private const val Y_AXIS_TARGET_LABEL_COUNT = 6
private const val MARKER_DECIMAL_COUNT = 5
private const val YEAR_VIEW_MIN_POINTS = 90
private const val AXIS_LABEL_FONT_SIZE_SP = 12
private const val CHART_LINE_THICKNESS_DP = 1
private const val AXIS_LABEL_EMPTY_PLACEHOLDER = "—"
private val MIN_LINE_COLOR = Color(0xFFE53935)
private val YEAR_CHANGE_COLOR = Color(0xFF1E88E5)
private val MONTH_CHANGE_COLOR = Color(0xFF8E24AA)

// Vico 3.2.3 ships HorizontalLine but no VerticalLine. Mirror the x mapping used
// by HorizontalAxis (see HorizontalAxis.kt in vico:compose): the parent forces
// LTR so layoutDirectionMultiplier is 1 and getStart(isLtr) == layerBounds.left.
private class VerticalLine(
    private val x: Double,
    private val line: LineComponent,
) : Decoration {
    override fun drawUnderLayers(context: CartesianDrawingContext) {
        with(context) {
            val baseCanvasX = layerBounds.left - scroll + layerDimensions.startPadding
            val rawCanvasX =
                baseCanvasX +
                    ((x - ranges.minX) / ranges.xStep).toFloat() * layerDimensions.xSpacing
            // Snap to whole pixel: a 1-px-thick line at a fractional x is anti-aliased
            // per scanline, which reads as a jagged column when xSpacing pushes the
            // boundary off-grid.
            val canvasX = kotlin.math.round(rawCanvasX)
            if (canvasX < layerBounds.left || canvasX > layerBounds.right) return
            line.drawVertical(this, canvasX, layerBounds.top, layerBounds.bottom)
        }
    }
}

// Vico's HorizontalLine draws in drawOverLayers, painting the min/max highlight
// on top of the chart line. Mirror its y mapping in drawUnderLayers so the
// highlights sit behind the chart line, consistent with the vertical change
// lines (which are also under-layer by user preference).
private class HorizontalLineUnder(
    private val y: Double,
    private val line: LineComponent,
) : Decoration {
    override fun drawUnderLayers(context: CartesianDrawingContext) {
        with(context) {
            val yRange = ranges.getYRange(null)
            val canvasY =
                layerBounds.bottom -
                    ((y - yRange.minY) / yRange.length).toFloat() * layerBounds.height
            line.drawHorizontal(this, layerBounds.left, layerBounds.right, canvasY)
        }
    }
}

// Wraps a HorizontalAxis.ItemPlacer to suppress ticks and guidelines at [skipX]
// x-values while leaving labels intact. Fixes a paint-order bug in the week
// view: spacing=1 puts a dashed vertical guideline on every data point, and
// vico draws guidelines after decoration.drawUnderLayers, so a guideline at
// the same x as a change line overpaints the solid line with a dashed one.
// Filtering the change indices out of getLineValues stops the overpaint at
// the source; labels (dates on the axis) still render at every index.
private class SuppressGuidelineItemPlacer(
    private val delegate: HorizontalAxis.ItemPlacer,
    private val skipX: Set<Double>,
) : HorizontalAxis.ItemPlacer by delegate {
    override fun getLineValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double>? {
        val base =
            delegate.getLineValues(context, visibleXRange, fullXRange, maxLabelWidth)
                ?: delegate.getLabelValues(context, visibleXRange, fullXRange, maxLabelWidth)
        return base.filterNot { it in skipX }
    }
}
