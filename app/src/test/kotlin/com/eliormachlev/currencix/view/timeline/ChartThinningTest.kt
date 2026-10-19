package com.eliormachlev.currencix.view.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class ChartThinningTest {
    @Test
    fun `a series that fits is left alone`() {
        val points = List(10) { it.toFloat() }
        assertSame(points, thinForWidth(points, buckets = 5) { it })
    }

    @Test
    fun `a long series keeps at most two points a bucket, in order`() {
        val points = List(1_800) { it to sin(it / 30.0).toFloat() }
        val thinned = thinForWidth(points, buckets = 200) { it.second }
        assertTrue(thinned.size <= 400)
        val days = thinned.map { it.first }
        assertEquals(days.sorted(), days)
    }

    @Test
    fun `every peak and trough survives`() {
        val points =
            List(1_000) {
                if (it == 517) {
                    9f
                } else if (it == 3) {
                    -9f
                } else {
                    1f
                }
            }
        val thinned = thinForWidth(points, buckets = 50) { it }
        assertTrue(9f in thinned)
        assertTrue(-9f in thinned)
    }
}
