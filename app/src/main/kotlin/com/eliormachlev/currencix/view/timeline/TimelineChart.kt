package com.eliormachlev.currencix.view.timeline

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.stripTimePattern
import com.eliormachlev.currencix.view.compose.Ltr
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
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
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import kotlinx.collections.immutable.ImmutableList
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private typealias ChartPoints = List<Pair<LocalDate, Float>>

/** The series to draw and the reference values drawn over it, observed. */
class ChartSeries(
    val entries: LiveData<ImmutableList<Pair<LocalDate, Float>>?>,
    // The range's extremes, independent of the scrub position, so the min /
    // max lines stay pinned to the period's low and high while a finger drags.
    val highlightMin: LiveData<Double?>,
    val highlightMax: LiveData<Double?>,
)

/** The chart's display preferences (Settings → Graph options), observed. */
class ChartPrefs(
    val showGrid: LiveData<Boolean>,
    val showXAxis: LiveData<Boolean>,
    val showYAxis: LiveData<Boolean>,
    val highlightExtremes: LiveData<Boolean>,
    val highlightPeriodChange: LiveData<Boolean>,
    val dateFormat: LiveData<String>,
)

@Immutable
data class ChartColors(
    val line: Color,
    val baseline: Color,
    val axis: Color,
    val scrubLine: Color,
)

@Composable
fun TimelineChart(
    series: ChartSeries,
    prefs: ChartPrefs,
    colors: ChartColors,
    onScrub: (LocalDate?) -> Unit,
) {
    val entries by series.entries.observeAsState()
    val highlightMin by series.highlightMin.observeAsState()
    val highlightMax by series.highlightMax.observeAsState()
    val highlightExtremes by prefs.highlightExtremes.observeAsState(initial = true)
    val highlightPeriodChange by prefs.highlightPeriodChange.observeAsState(initial = true)
    val dateFormat by prefs.dateFormat.observeAsState(initial = DEFAULT_DATE_FORMAT)

    val data = entries.orEmpty()
    val scrub = rememberScrub(data, onScrub)
    val periodChanges = rememberPeriodChanges(data, highlightPeriodChange)
    val decorations =
        buildList {
            addPeriodChangeLines(periodChanges.months, MONTH_CHANGE_COLOR)
            addPeriodChangeLines(periodChanges.years, YEAR_CHANGE_COLOR)
            scrub.index?.let { add(VerticalLineOver(x = it.toDouble(), line = chartLine(colors.scrubLine))) }
            data.lastOrNull()?.let { add(HorizontalLineUnder(y = it.second.toDouble(), line = chartLine(colors.baseline))) }
            if (highlightExtremes) addExtremeLines(highlightMin, highlightMax, colors.line)
        }

    ChartHost(
        data = data,
        axes = rememberAxes(data, prefs, colors.axis, dateFormat, periodChanges),
        scrub = scrub,
        marker = rememberScrubMarker(data, dateFormat),
        line = ChartLine(colors.line, rememberRangeProvider(data), decorations),
    )
}

// The plotted line: its color, the y-range it is drawn in, and what is drawn around it.
private class ChartLine(
    val color: Color,
    val rangeProvider: CartesianLayerRangeProvider,
    val decorations: List<Decoration>,
)

private class ChartAxes(
    val start: VerticalAxis<Axis.Position.Vertical.Start>,
    val bottom: HorizontalAxis<Axis.Position.Horizontal.Bottom>,
)

@Composable
private fun ChartHost(
    data: ChartPoints,
    axes: ChartAxes,
    scrub: Scrub,
    marker: CartesianMarker,
    line: ChartLine,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data.size, data.hashCode()) {
        if (data.isNotEmpty()) {
            modelProducer.runTransaction {
                lineModel { series(data.map { it.second }) }
            }
        }
    }
    val chartSummary = stringResource(id = R.string.a11y_chart_summary)
    // Rebuild the host when the series length changes: Vico's scroll/marker state
    // caches the previous point count and crashes when the dataset shrinks.
    key(data.size) {
        // Vico maps touch x-coordinates against the host's layout direction, so
        // under an RTL locale (e.g. Hebrew) the scrub marker mirrors to the
        // wrong side of the finger and clamps to the left edge.
        Ltr {
            CartesianChartHost(
                // Vico exposes no data to accessibility services, so a screen
                // reader that lands on the host only hears "chart". Provide a
                // summary that points to the accessible readout below (past,
                // current, min, avg, max) rather than reading raw data points.
                modifier =
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = chartSummary },
                chart =
                    rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider =
                                LineCartesianLayer.LineProvider.series(
                                    LineCartesianLayer.rememberLine(
                                        fill = LineCartesianLayer.LineFill.single(Fill(line.color)),
                                    ),
                                ),
                            rangeProvider = line.rangeProvider,
                        ),
                        startAxis = axes.start,
                        bottomAxis = axes.bottom,
                        marker = marker,
                        markerVisibilityListener = scrub.listener,
                        decorations = line.decorations,
                    ),
                modelProducer = modelProducer,
                scrollState = rememberVicoScrollState(scrollEnabled = false),
            )
        }
    }
}

// The y-range: the series' own, padded so the line doesn't touch the edges.
@Composable
private fun rememberRangeProvider(data: ChartPoints): CartesianLayerRangeProvider {
    val minValue = remember(data) { data.minOfOrNull { it.second }?.toDouble() }
    val maxValue = remember(data) { data.maxOfOrNull { it.second }?.toDouble() }
    return remember(minValue, maxValue) {
        when {
            minValue == null || maxValue == null -> CartesianLayerRangeProvider.auto()

            minValue < maxValue -> paddedRange(minValue, maxValue, (maxValue - minValue) * Y_AXIS_PADDING)

            // Constant series (e.g. AUD → AUD, all rates = 1.0). auto()
            // stretches to [0, 1], which puts the top Y-axis label at the
            // layer boundary and overflows above the chart region. Pin a
            // symmetric ±FLAT_SERIES_PADDING band around the value so the
            // chart shows a centered flat line with a bounded axis.
            else -> paddedRange(minValue, maxValue, FLAT_SERIES_PADDING.coerceAtLeast(abs(minValue) * Y_AXIS_PADDING))
        }
    }
}

private fun paddedRange(
    min: Double,
    max: Double,
    padding: Double,
): CartesianLayerRangeProvider = CartesianLayerRangeProvider.fixed(minY = min - padding, maxY = max + padding)

// Where the finger is on the chart ([index], null when lifted) and the
// listener that keeps it — and the screen's readouts, via onScrub — current.
private class Scrub(
    val index: Int?,
    val listener: CartesianMarkerVisibilityListener,
)

@Composable
private fun rememberScrub(
    data: ChartPoints,
    onScrub: (LocalDate?) -> Unit,
): Scrub {
    var scrubIndex by remember { mutableStateOf<Int?>(null) }
    val listener =
        remember(data, onScrub) {
            object : CartesianMarkerVisibilityListener {
                private fun update(targets: List<CartesianMarker.Target>) {
                    val idx = targets.firstOrNull()?.x?.toInt() ?: return
                    scrubIndex = idx
                    onScrub(data.getOrNull(idx)?.first)
                }

                override fun onShown(
                    marker: CartesianMarker,
                    targets: List<CartesianMarker.Target>,
                ) = update(targets)

                // Fires while the finger drags across the plot; without this
                // the scrub line and rate label freeze at the initial press
                // position instead of following the finger.
                override fun onUpdated(
                    marker: CartesianMarker,
                    targets: List<CartesianMarker.Target>,
                ) = update(targets)

                override fun onHidden(marker: CartesianMarker) {
                    scrubIndex = null
                    onScrub(null)
                }
            }
        }
    return Scrub(scrubIndex, listener)
}

// Scrub bubble: "12/03/26 · 3.0714" above the finger, in Material's
// tooltip colors so it reads on any line or grid behind it.
@Composable
private fun rememberScrubMarker(
    data: ChartPoints,
    dateFormat: String,
): CartesianMarker {
    val dateFormatter = remember(dateFormat) { DateTimeFormatter.ofPattern(stripTimePattern(dateFormat)) }
    val numberFormat =
        remember {
            NumberFormat.getNumberInstance().apply {
                minimumFractionDigits = MARKER_MIN_DECIMALS
                maximumFractionDigits = MARKER_MAX_DECIMALS
            }
        }
    val valueFormatter =
        remember(data, dateFormatter, numberFormat) {
            DefaultCartesianMarker.ValueFormatter { _, targets ->
                val point =
                    targets
                        .firstOrNull()
                        ?.x
                        ?.toInt()
                        ?.let(data::getOrNull)
                if (point == null) {
                    AXIS_LABEL_EMPTY_PLACEHOLDER
                } else {
                    "${point.first.format(dateFormatter)}$MARKER_SEPARATOR${numberFormat.format(point.second)}"
                }
            }
        }
    val indicatorRing = MaterialTheme.colorScheme.surface
    return rememberDefaultCartesianMarker(
        label =
            rememberTextComponent(
                style = TextStyle(color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = AXIS_LABEL_FONT_SIZE_SP.sp),
                padding = Insets(horizontal = MARKER_PADDING_H, vertical = MARKER_PADDING_V),
                background =
                    rememberShapeComponent(
                        fill = Fill(MaterialTheme.colorScheme.inverseSurface),
                        shape = RoundedCornerShape(MARKER_CORNER),
                    ),
            ),
        valueFormatter = valueFormatter,
        indicator = { color ->
            ShapeComponent(fill = Fill(color), shape = CircleShape, strokeFill = Fill(indicatorRing), strokeThickness = MARKER_RING)
        },
        indicatorSize = MARKER_DOT,
    )
}

// The indices where a new year or a new month starts — the solid vertical
// lines. Both are empty when the "highlight period change" option is off.
private class PeriodChanges(
    val years: List<Int>,
    val months: List<Int>,
) {
    val all: Set<Double> get() = (years + months).mapTo(HashSet()) { it.toDouble() }
}

// Month lines are dropped on the year view (heuristic: >90 data points),
// where ~12 of them just add noise. A year boundary is also a month boundary,
// so the month line at the same index is skipped to avoid stacking two colors.
//
// Solid (not dashed) because vico's DashedShape renders inconsistently on
// vertical lines across chart contexts (weekly vs monthly view), even
// with FitStrategy.Fixed and whole-pixel x-snapping.
@Composable
private fun rememberPeriodChanges(
    data: ChartPoints,
    enabled: Boolean,
): PeriodChanges =
    remember(data, enabled) {
        if (!enabled) {
            PeriodChanges(emptyList(), emptyList())
        } else {
            PeriodChanges(
                years = data.boundaryIndices { prev, curr -> prev.year != curr.year },
                months =
                    if (data.size > YEAR_VIEW_MIN_POINTS) {
                        emptyList()
                    } else {
                        data.boundaryIndices { prev, curr -> prev.year == curr.year && prev.monthValue != curr.monthValue }
                    },
            )
        }
    }

@Composable
private fun rememberAxes(
    data: ChartPoints,
    prefs: ChartPrefs,
    axisColor: Color,
    dateFormat: String,
    periodChanges: PeriodChanges,
): ChartAxes {
    val showGrid by prefs.showGrid.observeAsState(initial = true)
    val showXAxis by prefs.showXAxis.observeAsState(initial = true)
    val showYAxis by prefs.showYAxis.observeAsState(initial = true)
    val labelStyle = TextStyle(color = axisColor, fontSize = AXIS_LABEL_FONT_SIZE_SP.sp)
    val yAxisItemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { Y_AXIS_TARGET_LABEL_COUNT }) }
    val start =
        VerticalAxis.rememberStart(
            label = if (showYAxis) rememberAxisLabelComponent(style = labelStyle) else null,
            guideline = if (showGrid) rememberAxisGuidelineComponent() else null,
            itemPlacer = yAxisItemPlacer,
        )
    val bottom =
        HorizontalAxis.rememberBottom(
            label = if (showXAxis) rememberAxisLabelComponent(style = labelStyle) else null,
            guideline = if (showGrid) rememberAxisGuidelineComponent() else null,
            valueFormatter = rememberDateAxisFormatter(data, dateFormat),
            labelRotationDegrees = X_AXIS_LABEL_ROTATION,
            itemPlacer = remember(data.size, periodChanges) { dateAxisItemPlacer(data.size, periodChanges.all) },
        )
    return ChartAxes(start, bottom)
}

// Vico's axis measurement (getMaxLabelWidth) may call this with x-values outside
// data.indices while the model is transitioning to a smaller series. Returning a
// blank string throws IllegalStateException, so clamp to the valid range and fall
// back to a non-blank placeholder when the series is empty.
@Composable
private fun rememberDateAxisFormatter(
    data: ChartPoints,
    dateFormat: String,
): CartesianValueFormatter {
    val formatter = remember(dateFormat) { DateTimeFormatter.ofPattern(stripYear(stripTimePattern(dateFormat))) }
    return remember(data, formatter) {
        CartesianValueFormatter { _, value, _ ->
            data.getOrNull(value.toInt().coerceIn(0, (data.size - 1).coerceAtLeast(0)))?.first?.format(formatter)
                ?: AXIS_LABEL_EMPTY_PLACEHOLDER
        }
    }
}

// Aligned placer emits labels at 0, spacing, 2*spacing, … up to n-1, so the
// label count is floor((n-1)/spacing) + 1. To cap at exactly
// X_AXIS_TARGET_LABEL_COUNT (never one over), pick the smallest spacing that
// fits (count-1) hops across (n-1) values: ceil((n-1) / (count-1)). For a
// 365-point year this gives spacing=61 → 7 labels instead of spacing=52 → 8.
// Guidelines are left out at [skipX], where a period-change line is drawn.
private fun dateAxisItemPlacer(
    pointCount: Int,
    skipX: Set<Double>,
): HorizontalAxis.ItemPlacer {
    val span = (pointCount - 1).coerceAtLeast(1)
    val spacing = ((span + X_AXIS_TARGET_LABEL_COUNT - 2) / (X_AXIS_TARGET_LABEL_COUNT - 1)).coerceAtLeast(1)
    val aligned = HorizontalAxis.ItemPlacer.aligned(spacing = { spacing })
    return if (skipX.isEmpty()) aligned else SuppressGuidelineItemPlacer(aligned, skipX)
}

internal const val DEFAULT_DATE_FORMAT = "dd/MM/yy"

private fun stripYear(pattern: String): String = pattern.replace("/yy", "").replace("yy/", "")

private const val HIGHLIGHT_ALPHA = 0.4f
private const val Y_AXIS_PADDING = 0.05
private const val FLAT_SERIES_PADDING = 0.01
private const val X_AXIS_LABEL_ROTATION = 0f
private const val X_AXIS_TARGET_LABEL_COUNT = 7
private const val Y_AXIS_TARGET_LABEL_COUNT = 6
private const val MARKER_MIN_DECIMALS = 2
private const val MARKER_MAX_DECIMALS = 4
private const val MARKER_SEPARATOR = "  ·  "
private val MARKER_PADDING_H = 8.dp
private val MARKER_PADDING_V = 4.dp
private val MARKER_CORNER = 8.dp
private val MARKER_DOT = 10.dp
private val MARKER_RING = 2.dp
private const val YEAR_VIEW_MIN_POINTS = 90
private const val AXIS_LABEL_FONT_SIZE_SP = 12
private const val CHART_LINE_THICKNESS_DP = 1
private const val AXIS_LABEL_EMPTY_PLACEHOLDER = "—"
private val MIN_LINE_COLOR = Color(0xFFE53935)
private val YEAR_CHANGE_COLOR = Color(0xFF1E88E5)
private val MONTH_CHANGE_COLOR = Color(0xFF8E24AA)

// Walks the series and returns every index `i` where the (i-1, i) date pair
// satisfies the predicate — used to locate year and month change boundaries.
private inline fun List<Pair<LocalDate, Float>>.boundaryIndices(isBoundary: (prev: LocalDate, curr: LocalDate) -> Boolean): List<Int> =
    buildList {
        for (i in 1 until this@boundaryIndices.size) {
            if (isBoundary(this@boundaryIndices[i - 1].first, this@boundaryIndices[i].first)) add(i)
        }
    }

// The period's low (red) and high (the line's color), when it has both and they differ.
private fun MutableList<Decoration>.addExtremeLines(
    min: Double?,
    max: Double?,
    highColor: Color,
) {
    if (min == null || max == null || min == max) return
    add(HorizontalLineUnder(y = min, line = chartLine(MIN_LINE_COLOR, HIGHLIGHT_ALPHA)))
    add(HorizontalLineUnder(y = max, line = chartLine(highColor, HIGHLIGHT_ALPHA)))
}

private fun MutableList<Decoration>.addPeriodChangeLines(
    indices: List<Int>,
    color: Color,
) {
    indices.forEach { idx ->
        add(VerticalLine(x = idx.toDouble(), line = chartLine(color)))
    }
}

// Common shape for every decoration in this chart: a thin solid line component
// tinted with `color` (optionally at reduced `alpha`). Every call site used to
// spell out the same LineComponent/Fill/thickness triple.
private fun chartLine(
    color: Color,
    alpha: Float = 1f,
): LineComponent {
    val tinted = if (alpha == 1f) color else color.copy(alpha = alpha)
    return LineComponent(fill = Fill(tinted), thickness = CHART_LINE_THICKNESS_DP.dp)
}

// Vico 3.2.3 ships HorizontalLine but no VerticalLine. Mirror the x mapping used
// by HorizontalAxis (see HorizontalAxis.kt in vico:compose): the parent forces
// LTR so layoutDirectionMultiplier is 1 and getStart(isLtr) == layerBounds.left.
private fun CartesianDrawingContext.drawVerticalAtX(
    x: Double,
    line: LineComponent,
) {
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

private class VerticalLine(
    private val x: Double,
    private val line: LineComponent,
) : Decoration {
    override fun drawUnderLayers(context: CartesianDrawingContext) {
        context.drawVerticalAtX(x, line)
    }
}

// Scrub-line variant: paints atop the chart line so the finger indicator is
// legible against the plotted series (the min/max/period-change lines sit
// under the layers as static reference geometry).
private class VerticalLineOver(
    private val x: Double,
    private val line: LineComponent,
) : Decoration {
    override fun drawOverLayers(context: CartesianDrawingContext) {
        context.drawVerticalAtX(x, line)
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
