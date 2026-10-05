package com.eliormachlev.currencix.model.adapter

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BankOfIsraelSdmxParserTest {
    private fun parse(json: String) = BankOfIsraelSdmxParser().parse(json.byteInputStream())

    @Test
    fun `official fixings come out per currency and date, other data types are left out`() {
        assertEquals(
            setOf(
                BankOfIsraelObservation("USD", LocalDate.of(2024, 3, 14), BigDecimal("3.65")),
                BankOfIsraelObservation("USD", LocalDate.of(2024, 3, 15), BigDecimal("3.7")),
                BankOfIsraelObservation("EUR", LocalDate.of(2024, 3, 15), BigDecimal("4.0")),
            ),
            parse(SDMX_SAMPLE).toSet(),
        )
    }

    @Test
    fun `the single-structure form of the feed parses the same`() {
        val single = SDMX_SAMPLE.replace("\"structures\":[", "\"structure\":").replace("}}]}}", "}}}}")
        assertEquals(parse(SDMX_SAMPLE).toSet(), parse(single).toSet())
    }

    @Test
    fun `anything that isn't the feed yields nothing`() {
        assertEquals(emptyList<BankOfIsraelObservation>(), parse("null"))
        assertEquals(emptyList<BankOfIsraelObservation>(), parse("""{"data":{}}"""))
        assertEquals(emptyList<BankOfIsraelObservation>(), parse("""{"data":{"dataSets":[{"series":{"0:0":{}}}]}}"""))
    }

    @Test
    fun `a series key or observation that doesn't resolve is skipped, the rest stay`() {
        val broken =
            SDMX_SAMPLE
                .replace("\"0:0:1:0:0:0\"", "\"0:0:9:0:0:0\"")
                .replace("\"1\":[\"3.7\"]", "\"7\":[\"3.7\"]")
        assertEquals(listOf(BankOfIsraelObservation("USD", LocalDate.of(2024, 3, 14), BigDecimal("3.65"))), parse(broken))
    }
}

// Two daily observations; USD and EUR as official fixings (OF00), and one EUR
// series of another data type that must not be read.
internal const val SDMX_SAMPLE =
    """{"data":{"dataSets":[{"series":{""" +
        """"0:0:0:0:0:0":{"observations":{"0":["3.65"],"1":["3.7"]}},""" +
        """"0:0:1:0:0:1":{"observations":{"0":["9.9"]}},""" +
        """"0:0:1:0:0:0":{"observations":{"1":["4.0"]}}}}],""" +
        """"structures":[{"dimensions":{"series":[""" +
        """{"id":"SERIES_CODE","values":[{"id":"RER"}]},{"id":"FREQ","values":[{"id":"D"}]},""" +
        """{"id":"BASE_CURRENCY","values":[{"id":"USD"},{"id":"EUR"}]},""" +
        """{"id":"COUNTER_CURRENCY","values":[{"id":"ILS"}]},{"id":"UNIT_MEASURE","values":[{"id":"ILS"}]},""" +
        """{"id":"DATA_TYPE","values":[{"id":"OF00"},{"id":"XX00"}]}],""" +
        """"observation":[{"id":"TIME_PERIOD","values":[{"id":"2024-03-14"},{"id":"2024-03-15"}]}]}}]}}"""
