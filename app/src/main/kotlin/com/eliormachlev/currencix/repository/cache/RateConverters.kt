package com.eliormachlev.currencix.repository.cache

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.repository.json.JsonObject
import com.eliormachlev.currencix.repository.json.buildJsonObject
import com.eliormachlev.currencix.repository.json.jsonText
import com.eliormachlev.currencix.repository.json.parseJsonObject
import com.eliormachlev.currencix.util.toLocalDateOrNull
import timber.log.Timber
import java.math.BigDecimal
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

// The provider the cached data came from, stored by its id.
private fun JsonObject.optProvider(): ApiProvider? = if (has(FIELD_PROVIDER)) ApiProvider.fromId(optInt(FIELD_PROVIDER)) else null

// One field written only when there's a value for it: numbers and booleans
// as they are, anything else (a date, a time) as its text.
private fun MutableMap<String, Any>.optionalField(
    name: String,
    value: Any?,
) {
    value ?: return
    put(name, if (value is Number || value is Boolean) value else value.toString())
}

// The "rates" field: each key with its rate as a plain string, so no
// precision is lost to a JSON double.
private fun <K> MutableMap<String, Any>.ratesField(
    rates: Iterable<Pair<K, Rate>>,
    key: (K) -> String,
) {
    put(FIELD_RATES, rates.associate { (k, rate) -> key(k) to rate.value.toPlainString() })
}

// Each entry of a "rates" object whose key [toKey] knows and whose value is a number.
private fun <K> JsonObject.rates(toKey: (String) -> K?): Sequence<Pair<K, BigDecimal>> =
    keys.asSequence().mapNotNull { name ->
        val key = toKey(name) ?: return@mapNotNull null
        val value = optString(name).toBigDecimalOrNull() ?: return@mapNotNull null
        key to value
    }

/**
 * JSON [Converter] for [ExchangeRates], written field by field: the cache
 * keeps the `time` and `provider` a provider's response doesn't carry.
 */
internal object ExchangeRatesConverter : Converter<ExchangeRates> {
    override fun encode(value: ExchangeRates): String =
        jsonText(
            buildJsonObject {
                optionalField(FIELD_BASE, value.base?.iso4217Alpha())
                optionalField(FIELD_DATE, value.date)
                optionalField(FIELD_TIME, value.time)
                optionalField(FIELD_PROVIDER, value.provider?.id)
                ratesField(value.rates.orEmpty().map { it.currency to it }, Currency::iso4217Alpha)
            },
        )

    override fun decode(encoded: String): ExchangeRates? =
        runCatching {
            val obj = parseJsonObject(encoded)
            val date = obj.optStringOrNull(FIELD_DATE)?.let(LocalDate::parse)
            val ratesObj = obj.optObject(FIELD_RATES) ?: return@runCatching null
            val rates = ratesObj.rates(Currency::fromString).map { (currency, rate) -> Rate(currency, rate) }.toList()
            if (rates.isEmpty()) return@runCatching null
            ExchangeRates(
                success = true,
                error = null,
                base = obj.optStringOrNull(FIELD_BASE)?.let(Currency::fromString),
                date = date,
                rates = rates,
                time = obj.optStringOrNull(FIELD_TIME)?.let(LocalTime::parse),
                provider = obj.optProvider(),
            )
        }.onFailure { Timber.tag(TAG).w(it, "Failed to decode ExchangeRates") }
            .getOrNull()
}

/**
 * JSON [Converter] for [Timeline]. Written field by field, as
 * [ExchangeRatesConverter] is, so the cache keeps the `provider`.
 */
internal class TimelineConverter(
    private val symbol: Currency,
) : Converter<Timeline> {
    override fun encode(value: Timeline): String =
        jsonText(
            buildJsonObject {
                optionalField(FIELD_SUCCESS, value.success ?: true)
                optionalField(FIELD_BASE, value.base)
                optionalField(FIELD_START, value.startDate)
                optionalField(FIELD_END, value.endDate)
                optionalField(FIELD_PROVIDER, value.provider?.id)
                ratesField(value.rates.orEmpty().toList(), LocalDate::toString)
            },
        )

    override fun decode(encoded: String): Timeline? =
        runCatching {
            val obj = parseJsonObject(encoded)
            val ratesObj = obj.optObject(FIELD_RATES) ?: return@runCatching null
            val rates = ratesObj.rates(String::toLocalDateOrNull).associateTo(sortedMapOf()) { (date, rate) -> date to Rate(symbol, rate) }
            if (rates.isEmpty()) return@runCatching null
            Timeline(
                success = obj.optBoolean(FIELD_SUCCESS, true),
                error = null,
                base = obj.optStringOrNull(FIELD_BASE),
                startDate = obj.optStringOrNull(FIELD_START)?.let(LocalDate::parse),
                endDate = obj.optStringOrNull(FIELD_END)?.let(LocalDate::parse),
                rates = rates,
                provider = obj.optProvider(),
            )
        }.onFailure { Timber.tag(TAG).w(it, "Failed to decode Timeline") }
            .getOrNull()
}
