package com.eliormachlev.currencix.view.timeline

/**
 * [points] cut down to at most two per bucket, [buckets] of them across the
 * series: each bucket's lowest and highest, in their own order. A chart
 * [buckets] pixels-pairs wide can't show more, so the line keeps its shape
 * and every peak and trough, while drawing, hit-testing and scrubbing work
 * on a few hundred points instead of thousands (five years is ~1,800 days).
 * A series that already fits is returned as it is.
 */
internal fun <T> thinForWidth(
    points: List<T>,
    buckets: Int,
    value: (T) -> Float,
): List<T> {
    if (buckets <= 0 || points.size <= buckets * 2) return points
    val size = points.size
    return buildList {
        for (bucket in 0 until buckets) {
            val from = (bucket.toLong() * size / buckets).toInt()
            val to = ((bucket + 1).toLong() * size / buckets).toInt()
            if (from >= to) continue
            var low = from
            var high = from
            for (i in from until to) {
                if (value(points[i]) < value(points[low])) low = i
                if (value(points[i]) > value(points[high])) high = i
            }
            add(points[minOf(low, high)])
            if (low != high) add(points[maxOf(low, high)])
        }
    }
}
