package com.eliormachlev.currencix.model.adapter

import com.squareup.moshi.JsonReader
import okio.buffer
import okio.source
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate

// One "official fixing" observation from the Bank of Israel SDMX-JSON feed.
// `rawValue` is quoted as ILS per [BankOfIsrael.UNIT_PER_CURRENCY]-many foreign
// currency units (matching the PublicApi convention); the provider converts it
// into ILS-based rates.
internal data class BankOfIsraelObservation(
    val currency: String,
    val date: LocalDate,
    val rawValue: BigDecimal,
)

private const val DATA_TYPE_OFFICIAL_FIXING = "OF00"
private const val DIM_BASE_CURRENCY = "BASE_CURRENCY"
private const val DIM_DATA_TYPE = "DATA_TYPE"
private const val DIM_TIME_PERIOD = "TIME_PERIOD"

/**
 * Parses the SDMX-JSON response from
 * `edge.boi.gov.il/FusionEdgeServer/sdmx/v2/data/dataflow/BOI.STATISTICS/EXR/1.0`.
 *
 * The feed keys each time series by dimension-index tuples (e.g. `"0:0:6:0:0:0"`
 * — SERIES_CODE:FREQ:BASE_CURRENCY:COUNTER_CURRENCY:UNIT_MEASURE:DATA_TYPE).
 * The parser walks the `structures` block to build code-lookup tables per
 * dimension, then filters the `series` block to `DATA_TYPE = OF00` (Official
 * Fixing — the same series exposed by the simpler PublicApi endpoint).
 */
internal class BankOfIsraelSdmxParser {
    fun parse(inputStream: InputStream): List<BankOfIsraelObservation> =
        JsonReader.of(inputStream.source().buffer()).use { reader ->
            val data = (reader.readJsonValue() as? Map<*, *>)?.get("data") as? Map<*, *>
            data?.let(::officialFixings).orEmpty()
        }

    private fun officialFixings(data: Map<*, *>): List<BankOfIsraelObservation> {
        val seriesDimensions = dimensions(data, "series")
        val currencyPos = seriesDimensions.indexOfFirst { it.id == DIM_BASE_CURRENCY }
        val dataTypePos = seriesDimensions.indexOfFirst { it.id == DIM_DATA_TYPE }
        val dates = dates(data)
        if (currencyPos < 0 || dataTypePos < 0 || dates.isEmpty()) return emptyList()

        return seriesOf(data).flatMap { (key, series) ->
            val codes = codesOf(key, seriesDimensions)
            val currency = codes.getOrNull(currencyPos)
            if (currency == null || codes.getOrNull(dataTypePos) != DATA_TYPE_OFFICIAL_FIXING) {
                emptyList()
            } else {
                observationsOf(series).mapNotNull { (dateIndex, value) -> observation(currency, dates.getOrNull(dateIndex), value) }
            }
        }
    }

    // A dimension's declared codes, in the order the keys index them. A slot
    // is null when its entry has no id, so the positions stay aligned.
    private fun dimensions(
        data: Map<*, *>,
        kind: String,
    ): List<Dimension> {
        val structures = data["structures"] as? List<*> ?: listOfNotNull(data["structure"])
        val declared = ((structures.firstOrNull() as? Map<*, *>)?.get("dimensions") as? Map<*, *>)?.get(kind) as? List<*>
        return declared.orEmpty().filterIsInstance<Map<*, *>>().map { dimension ->
            Dimension(
                id = dimension["id"] as? String ?: "",
                codes = (dimension["values"] as? List<*>).orEmpty().map { (it as? Map<*, *>)?.get("id") as? String },
            )
        }
    }

    // The observation dates, by observation index; empty when none parses.
    private fun dates(data: Map<*, *>): List<LocalDate?> =
        dimensions(data, "observation")
            .firstOrNull { it.id == DIM_TIME_PERIOD }
            ?.codes
            .orEmpty()
            .map { code -> code?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
            .takeIf { parsed -> parsed.any { it != null } }
            .orEmpty()

    private fun seriesOf(data: Map<*, *>): Map<*, *> =
        ((data["dataSets"] as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("series") as? Map<*, *> ?: emptyMap<Any?, Any?>()

    // "0:0:6:0:0:0" → the code each position selects in its dimension; null
    // where a position doesn't resolve.
    private fun codesOf(
        seriesKey: Any?,
        dimensions: List<Dimension>,
    ): List<String?> =
        (seriesKey as? String)?.split(':').orEmpty().mapIndexed { position, index ->
            index.toIntOrNull()?.let { dimensions.getOrNull(position)?.codes?.getOrNull(it) }
        }

    // A series' observations as (date index, raw entry).
    private fun observationsOf(series: Any?): List<Pair<Int, Any?>> =
        ((series as? Map<*, *>)?.get("observations") as? Map<*, *>)
            .orEmpty()
            .mapNotNull { (key, value) -> (key as? String)?.toIntOrNull()?.let { it to value } }

    private fun observation(
        currency: String,
        date: LocalDate?,
        entry: Any?,
    ): BankOfIsraelObservation? {
        // An entry is an array: [value, ...attributes].
        val rawValue = (entry as? List<*>)?.firstOrNull()?.let { runCatching { BigDecimal(it.toString()) }.getOrNull() }
        return if (date != null && rawValue != null) BankOfIsraelObservation(currency, date, rawValue) else null
    }
}

private class Dimension(
    val id: String,
    val codes: List<String?>,
)
