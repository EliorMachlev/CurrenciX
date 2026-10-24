package com.eliormachlev.currencix.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.util.DroppedCacheEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

// Each pair's history is a file of its own; what was in the old shared
// preferences file moves over on first use.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TimelineFilesTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val provider = ApiProvider.entries.first()
    private val day = LocalDate.of(2026, 10, 1)

    @Test
    fun `a saved history reads back and lives in its own file`() {
        val files = TimelineFiles.get(app)
        files.write(timeline(mapOf(day to "3.7", day.plusDays(1) to "3.71")), Currency.USD, Currency.ILS)

        val read = files.read(provider, Currency.USD, Currency.ILS)
        assertEquals(BigDecimal("3.71"), read?.rates?.get(day.plusDays(1))?.value)
        assertTrue(File(app.filesDir, "timelines/${provider.id}_USD_ILS.json").isFile)
    }

    @Test
    fun `an unreadable file is dropped and counted, not a crash`() {
        val dir = File(app.filesDir, "timelines").apply { mkdirs() }
        File(dir, "${provider.id}_GBP_JPY.json").writeText("not json")
        val before = DroppedCacheEntries.count("TimelineFiles")

        assertNull(TimelineFiles.get(app).read(provider, Currency.GBP, Currency.JPY))
        assertEquals(before + 1, DroppedCacheEntries.count("TimelineFiles"))
    }

    private fun timeline(rates: Map<LocalDate, String>): Timeline {
        val sorted = rates.mapValues { (_, v) -> Rate(Currency.ILS, BigDecimal(v)) }.toSortedMap()
        return Timeline(true, null, "USD", sorted.firstKey(), sorted.lastKey(), sorted, provider)
    }
}
