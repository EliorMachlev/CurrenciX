package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.util.toLocalDateOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

// The data's series: their dimensions under "structure", their values under "dataSets".
private const val SERIES = "series"
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
        (
            Json
                .parseToJsonElement(inputStream.readBytes().decodeToString())
                .obj()
                ?.get("data")
                ?.obj()
        )?.let(::officialFixings)
            .orEmpty()

    private fun officialFixings(data: JsonObject): List<BankOfIsraelObservation> {
        val seriesDimensions = dimensions(data, SERIES)
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
        data: JsonObject,
        kind: String,
    ): List<Dimension> {
        val structures = data["structures"]?.array() ?: listOfNotNull(data["structure"])
        val declared =
            structures
                .firstOrNull()
                ?.obj()
                ?.get("dimensions")
                ?.obj()
                ?.get(kind)
                ?.array()
        return declared.orEmpty().mapNotNull { it.obj() }.map { dimension ->
            Dimension(
                id = dimension["id"]?.string().orEmpty(),
                codes = dimension["values"]?.array().orEmpty().map { it.obj()?.get("id")?.string() },
            )
        }
    }

    // The observation dates, by observation index; empty when none parses.
    private fun dates(data: JsonObject): List<LocalDate?> =
        dimensions(data, "observation")
            .firstOrNull { it.id == DIM_TIME_PERIOD }
            ?.codes
            .orEmpty()
            .map { code -> code?.toLocalDateOrNull() }
            .takeIf { parsed -> parsed.any { it != null } }
            .orEmpty()

    private fun seriesOf(data: JsonObject): Map<String, JsonElement> =
        data["dataSets"]
            ?.array()
            ?.firstOrNull()
            ?.obj()
            ?.get(SERIES)
            ?.obj()
            .orEmpty()

    // "0:0:6:0:0:0" → the code each position selects in its dimension; null
    // where a position doesn't resolve.
    private fun codesOf(
        seriesKey: String,
        dimensions: List<Dimension>,
    ): List<String?> =
        seriesKey.split(':').mapIndexed { position, index ->
            index.toIntOrNull()?.let { dimensions.getOrNull(position)?.codes?.getOrNull(it) }
        }

    // A series' observations as (date index, raw entry).
    private fun observationsOf(series: JsonElement): List<Pair<Int, JsonElement>> =
        series
            .obj()
            ?.get("observations")
            ?.obj()
            .orEmpty()
            .mapNotNull { (key, value) -> key.toIntOrNull()?.let { it to value } }

    private fun observation(
        currency: String,
        date: LocalDate?,
        entry: JsonElement,
    ): BankOfIsraelObservation? {
        // An entry is an array: [value, ...attributes]; the value a number or its text.
        val rawValue = (entry.array()?.firstOrNull() as? JsonPrimitive)?.contentOrNull?.toBigDecimalOrNull()
        return if (date != null && rawValue != null) BankOfIsraelObservation(currency, date, rawValue) else null
    }
}

private class Dimension(
    val id: String,
    val codes: List<String?>,
)

private fun JsonElement.obj(): JsonObject? = this as? JsonObject

private fun JsonElement.array(): JsonArray? = this as? JsonArray

// A string's text; null for anything else.
private fun JsonElement.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
