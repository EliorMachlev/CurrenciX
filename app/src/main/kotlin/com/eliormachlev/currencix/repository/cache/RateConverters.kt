package com.eliormachlev.currencix.repository.cache

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import org.json.JSONObject
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime

// Field names for the on-disk JSON envelope. Hoisted to constants so a rename
// is a compile-time break rather than a silent read miss.
private const val FIELD_BASE = "base"
private const val FIELD_DATE = "date"
private const val FIELD_TIME = "time"
private const val FIELD_PROVIDER = "provider"
private const val FIELD_RATES = "rates"
private const val FIELD_START = "start"
private const val FIELD_END = "end"
private const val FIELD_SUCCESS = "success"

private const val TAG = "RateConverters"

/**
 * JSON [Converter] for [ExchangeRates]. Deliberately hand-rolled with
 * `org.json` rather than routed through Moshi:
 *
 *  - Mirrors the existing `Database.parseExchangeRates` pattern for
 *    consistency with the preferences-backed store.
 *  - Preserves the `@Transient` `time` and `provider` fields — Moshi would
 *    silently drop them on the way through the disk tier.
 *  - Zero reflection cost per read/write.
 */
internal object ExchangeRatesConverter : Converter<ExchangeRates> {
    override fun encode(value: ExchangeRates): String {
        val obj = JSONObject()
        value.base?.let { obj.put(FIELD_BASE, it.iso4217Alpha()) }
        value.date?.let { obj.put(FIELD_DATE, it.toString()) }
        value.time?.let { obj.put(FIELD_TIME, it.toString()) }
        value.provider?.let { obj.put(FIELD_PROVIDER, it.id) }
        val ratesObj = JSONObject()
        value.rates?.forEach { rate -> ratesObj.put(rate.currency.iso4217Alpha(), rate.value.toPlainString()) }
        obj.put(FIELD_RATES, ratesObj)
        return obj.toString()
    }

    override fun decode(encoded: String): ExchangeRates? =
        runCatching {
            val obj = JSONObject(encoded)
            val date = obj.optString(FIELD_DATE).takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
            val ratesObj = obj.optJSONObject(FIELD_RATES) ?: return@runCatching null
            val rates = decodeRates(ratesObj)
            if (rates.isEmpty()) return@runCatching null
            ExchangeRates(
                success = true,
                error = null,
                base = obj.optString(FIELD_BASE).takeIf { it.isNotEmpty() }?.let(Currency::fromString),
                date = date,
                rates = rates,
                time = obj.optString(FIELD_TIME).takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                provider =
                    obj.opt(FIELD_PROVIDER)?.let { raw ->
                        (raw as? Int)?.let(ApiProvider::fromId)
                    },
            )
        }.onFailure { Timber.tag(TAG).w(it, "Failed to decode ExchangeRates") }
            .getOrNull()

    private fun decodeRates(ratesObj: JSONObject): List<Rate> =
        ratesObj
            .keys()
            .asSequence()
            .mapNotNull { key ->
                val currency = Currency.fromString(key) ?: return@mapNotNull null
                val value = ratesObj.optString(key).toBigDecimalOrNull() ?: return@mapNotNull null
                Rate(currency, value)
            }.toList()
}

/**
 * JSON [Converter] for [Timeline]. Same rationale as [ExchangeRatesConverter]
 * — hand-rolled preserves the `@Transient` `provider` field and stays
 * consistent with the preferences-backed cached timeline layout.
 */
internal class TimelineConverter(
    private val symbol: Currency,
) : Converter<Timeline> {
    override fun encode(value: Timeline): String {
        val obj = JSONObject()
        obj.put(FIELD_SUCCESS, value.success ?: true)
        value.base?.let { obj.put(FIELD_BASE, it) }
        value.startDate?.let { obj.put(FIELD_START, it.toString()) }
        value.endDate?.let { obj.put(FIELD_END, it.toString()) }
        value.provider?.let { obj.put(FIELD_PROVIDER, it.id) }
        val ratesObj = JSONObject()
        value.rates?.forEach { (date, rate) -> ratesObj.put(date.toString(), rate.value.toPlainString()) }
        obj.put(FIELD_RATES, ratesObj)
        return obj.toString()
    }

    override fun decode(encoded: String): Timeline? =
        runCatching {
            val obj = JSONObject(encoded)
            val ratesObj = obj.optJSONObject(FIELD_RATES) ?: return@runCatching null
            val rates = decodeTimelineRates(ratesObj)
            if (rates.isEmpty()) return@runCatching null
            Timeline(
                success = obj.optBoolean(FIELD_SUCCESS, true),
                error = null,
                base = obj.optString(FIELD_BASE).takeIf { it.isNotEmpty() },
                startDate = obj.optString(FIELD_START).takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                endDate = obj.optString(FIELD_END).takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                rates = rates,
                provider =
                    obj.opt(FIELD_PROVIDER)?.let { raw ->
                        (raw as? Int)?.let(ApiProvider::fromId)
                    },
            )
        }.onFailure { Timber.tag(TAG).w(it, "Failed to decode Timeline") }
            .getOrNull()

    private fun decodeTimelineRates(ratesObj: JSONObject): Map<LocalDate, Rate> =
        ratesObj
            .keys()
            .asSequence()
            .mapNotNull { key ->
                val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@mapNotNull null
                val value = ratesObj.optString(key).toBigDecimalOrNull() ?: return@mapNotNull null
                date to Rate(symbol, value)
            }.toMap(sortedMapOf())
}
