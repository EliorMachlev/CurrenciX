package com.eliormachlev.currencix.view.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import com.eliormachlev.currencix.model.Timeline
import java.math.BigDecimal
import java.time.LocalDate

/** Days of history the widget's trend line covers. */
internal const val SPARKLINE_DAYS = 30L

// Fewer points than this is not a trend.
private const val MIN_POINTS = 2

/**
 * The last [SPARKLINE_DAYS] of [timeline] up to [today], oldest first — the
 * widget's trend line. Empty when there's too little to draw.
 */
internal fun sparklinePoints(
    timeline: Timeline?,
    today: LocalDate,
): List<BigDecimal> {
    val from = today.minusDays(SPARKLINE_DAYS)
    val points =
        timeline
            ?.rates
            .orEmpty()
            .filterKeys { !it.isBefore(from) && !it.isAfter(today) }
            .toSortedMap()
            .values
            .map { it.value }
    return if (points.size >= MIN_POINTS) points else emptyList()
}

/**
 * [points] drawn as a line into a [widthPx] × [heightPx] bitmap, scaled to
 * their own min–max, in [color]. A flat series draws across the middle.
 */
internal fun sparklineBitmap(
    points: List<BigDecimal>,
    widthPx: Int,
    heightPx: Int,
    strokePx: Float,
    color: Int,
): Bitmap {
    val bitmap = createBitmap(widthPx, heightPx)
    val values = points.map { it.toDouble() }
    val min = values.min()
    val span = (values.max() - min).takeIf { it > 0.0 }
    val inset = strokePx / 2
    val usableHeight = heightPx - strokePx
    val stepX = (widthPx - strokePx) / (values.size - 1)
    val path = Path()
    values.forEachIndexed { i, v ->
        val x = inset + i * stepX
        val y = inset + if (span == null) usableHeight / 2 else (usableHeight * (1 - (v - min) / span)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }
    Canvas(bitmap).drawPath(path, paint)
    return bitmap
}
