package com.eliormachlev.currencix.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class PublicationScheduleTest {
    private val ecb = PublicationSchedule.Daily(LocalTime.of(16, 0), ZoneId.of("Europe/Berlin"))

    private fun at(text: String) = Instant.parse(text)

    @Test
    fun `before today's publication, the refresh is half an hour after it`() {
        // 10:00 UTC is 12:00 in Berlin (summer time): 16:00 + 30 min there is 14:30 UTC.
        assertEquals(at("2026-07-01T14:30:00Z"), ecb.nextRefreshAfter(at("2026-07-01T10:00:00Z")))
    }

    @Test
    fun `once today's refresh time has passed, it's tomorrow's`() {
        assertEquals(at("2026-07-02T14:30:00Z"), ecb.nextRefreshAfter(at("2026-07-01T14:30:00Z")))
        assertEquals(at("2026-07-02T14:30:00Z"), ecb.nextRefreshAfter(at("2026-07-01T20:00:00Z")))
    }

    @Test
    fun `between publication and its refresh, the refresh is still today's`() {
        assertEquals(at("2026-07-01T14:30:00Z"), ecb.nextRefreshAfter(at("2026-07-01T14:10:00Z")))
    }

    @Test
    fun `the local time holds across a daylight-saving change`() {
        // Berlin leaves summer time on 2026-10-25: 16:30 there is 14:30 UTC before, 15:30 UTC after.
        assertEquals(at("2026-10-24T14:30:00Z"), ecb.nextRefreshAfter(at("2026-10-24T10:00:00Z")))
        assertEquals(at("2026-10-25T15:30:00Z"), ecb.nextRefreshAfter(at("2026-10-24T15:00:00Z")))
    }

    @Test
    fun `a monthly table is refreshed on its day, next month once this one's has passed`() {
        val monthly = PublicationSchedule.Monthly(dayOfMonth = 1, at = LocalTime.of(6, 0), zone = ZoneId.of("UTC"))
        assertEquals(at("2026-11-01T06:30:00Z"), monthly.nextRefreshAfter(at("2026-10-12T12:00:00Z")))
        assertEquals(at("2026-11-01T06:30:00Z"), monthly.nextRefreshAfter(at("2026-11-01T06:00:00Z")))
        assertEquals(at("2026-12-01T06:30:00Z"), monthly.nextRefreshAfter(at("2026-11-01T07:00:00Z")))
        assertEquals(at("2027-01-01T06:30:00Z"), monthly.nextRefreshAfter(at("2026-12-15T00:00:00Z")))
    }

    @Test
    fun `only a provider that updates many times a day follows the user's interval`() {
        assertEquals(
            listOf(ApiProvider.OPEN_EXCHANGERATES, ApiProvider.ALL_RATES_TODAY),
            ApiProvider.entries.filter { it.publication is PublicationSchedule.Frequent },
        )
        assertEquals(UpdateCadence.MONTHLY, ApiProvider.INFOR_EURO.cadence)
    }
}
