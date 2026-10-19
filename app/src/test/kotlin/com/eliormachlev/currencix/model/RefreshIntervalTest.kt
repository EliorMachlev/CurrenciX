package com.eliormachlev.currencix.model

import android.app.Application
import com.eliormachlev.currencix.view.preference.compose.label
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

// ICU's MeasureFormat is Android's: Robolectric supplies it.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RefreshIntervalTest {
    @Test
    fun `nothing stored, or a value no interval has, means an hour`() {
        assertEquals(RefreshInterval.HOUR_1, RefreshInterval.DEFAULT)
        assertEquals(RefreshInterval.DEFAULT, RefreshInterval.fromMinutes(null))
        assertEquals(RefreshInterval.DEFAULT, RefreshInterval.fromMinutes(45))
    }

    @Test
    fun `every interval reads back from the minutes it's stored as`() {
        RefreshInterval.entries.forEach { interval ->
            assertEquals(interval, RefreshInterval.fromMinutes(interval.minutes.toInt()))
        }
    }

    @Test
    fun `no interval is shorter than WorkManager allows, and they only get longer`() {
        assertEquals(RefreshInterval.entries.sortedBy { it.minutes }, RefreshInterval.entries)
        assertEquals(15L, RefreshInterval.entries.first().minutes)
    }

    @Test
    fun `labels are whole hours in hours, the rest in minutes`() {
        assertEquals("15 minutes", RefreshInterval.MINUTES_15.label(Locale.US))
        assertEquals("1 hour", RefreshInterval.HOUR_1.label(Locale.US))
        assertEquals("24 hours", RefreshInterval.HOURS_24.label(Locale.US))
    }
}
