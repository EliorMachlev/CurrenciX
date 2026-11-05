package com.eliormachlev.currencix.model.provider.api

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Type-safe Retrofit binding for the European Commission's InforEuro public
 * API. Both endpoints answer a JSON array on success and `{message}` on
 * failure ([InforEuroMonth], [InforEuroHistory]); `InforEuroRatesAdapter` /
 * `InforEuroTimelineAdapter` turn them into the app's model.
 */
internal interface InforEuroApi {
    // `year` / `month` are either both set (a historical month) or both null
    // (the current month) — Retrofit omits null query parameters.
    @GET("monthly-rates")
    suspend fun getMonthlyRates(
        @Query("year") year: Int?,
        @Query("month") month: Int?,
    ): Response<InforEuroMonth>

    // Full EUR <-> [currency] history; the adapter trims it to the requested
    // window.
    @GET("currencies/{currency}")
    suspend fun getCurrencyHistory(
        @Path("currency") currency: String,
    ): Response<InforEuroHistory>
}

/** A month's rates, or why there are none. */
@Serializable(with = InforEuroMonthSerializer::class)
internal sealed interface InforEuroMonth

/** One currency's history, or why there is none. */
@Serializable(with = InforEuroHistorySerializer::class)
internal sealed interface InforEuroHistory

/** `[{isoA3Code, value}, …]`: euros' worth of each currency. */
@Serializable
@JvmInline
internal value class InforEuroMonthRates(
    val rates: List<InforEuroRate>,
) : InforEuroMonth

@Serializable
internal class InforEuroRate(
    val isoA3Code: String? = null,
    val value: Decimal? = null,
)

/** `[{currencyIso, amount, dateStart, dateEnd}, …]`, the dates as dd/MM/yyyy. */
@Serializable
@JvmInline
internal value class InforEuroPeriods(
    val periods: List<InforEuroPeriod>,
) : InforEuroHistory

@Serializable
internal class InforEuroPeriod(
    val currencyIso: String? = null,
    val amount: Decimal? = null,
    val dateStart: String? = null,
    val dateEnd: String? = null,
)

/** `{message}`: what either endpoint answers instead of an array. */
@Serializable
internal class InforEuroFailure(
    val message: String? = null,
) : InforEuroMonth,
    InforEuroHistory

internal object InforEuroMonthSerializer : JsonContentPolymorphicSerializer<InforEuroMonth>(InforEuroMonth::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<InforEuroMonth> =
        if (element is JsonArray) InforEuroMonthRates.serializer() else InforEuroFailure.serializer()
}

internal object InforEuroHistorySerializer : JsonContentPolymorphicSerializer<InforEuroHistory>(InforEuroHistory::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<InforEuroHistory> =
        if (element is JsonArray) InforEuroPeriods.serializer() else InforEuroFailure.serializer()
}
