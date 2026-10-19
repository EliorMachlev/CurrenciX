package com.eliormachlev.currencix.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshRateTest {
    private val fhd60 = ScreenMode(id = 1, width = 1080, height = 2400, hz = 60f)
    private val fhd90 = ScreenMode(id = 2, width = 1080, height = 2400, hz = 90f)
    private val fhd120 = ScreenMode(id = 3, width = 1080, height = 2400, hz = 120f)
    private val qhd144 = ScreenMode(id = 4, width = 1440, height = 3200, hz = 144f)

    @Test
    fun `picks the fastest rate at the current resolution`() {
        assertEquals(fhd120, fastestOf(fhd60, listOf(fhd60, fhd90, fhd120)))
    }

    @Test
    fun `a faster rate at another resolution is passed over`() {
        assertEquals(fhd120, fastestOf(fhd60, listOf(fhd60, fhd120, qhd144)))
    }

    @Test
    fun `a 60 Hz screen has nothing to ask for`() {
        assertNull(fastestOf(fhd60, listOf(fhd60, ScreenMode(id = 5, width = 1080, height = 2400, hz = 59.94f))))
    }
}
