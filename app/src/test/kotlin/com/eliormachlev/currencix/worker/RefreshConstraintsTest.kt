package com.eliormachlev.currencix.worker

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshConstraintsTest {
    @Test
    fun `without alerts the refresh waits for wi-fi and charge`() {
        val constraints = RateRefreshScheduler.refreshConstraints(forAlerts = false)
        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresBatteryNotLow())
    }

    @Test
    fun `waiting alerts need rates on any network at any charge`() {
        val constraints = RateRefreshScheduler.refreshConstraints(forAlerts = true)
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertFalse(constraints.requiresBatteryNotLow())
    }
}
