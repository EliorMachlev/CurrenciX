package com.eliormachlev.currencix.model.provider.api

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Type-safe Retrofit binding for AllRatesToday's `/api/v1/rates`
 * ([https://allratestoday.com/docs](https://allratestoday.com/docs)), which
 * answers both shapes the app asks for: every rate from [source] (no
 * `target`), now or at a past `time`, and one pair's daily series between
 * `from` and `to` (`to` exclusive). Both return a JSON array of
 * `{rate, source, target, time}`, or an error object ([AllRatesTodayAnswer]);
 * `AllRatesTodayRatesAdapter` / `AllRatesTodayTimelineAdapter` turn it into
 * the app's model.
 *
 * The key travels as `Authorization: Bearer …`, never in the URL, so it
 * stays out of logs and caches keyed by URL.
 */
internal interface AllRatesTodayApi {
    @GET("api/v1/rates")
    suspend fun getRates(
        @Header(AUTHORIZATION) authorization: String,
        @Query("source") source: String,
        @Query("time") time: String?,
    ): Response<AllRatesTodayAnswer>

    // A series is always one rate per day.
    @GET("api/v1/rates?group=day")
    suspend fun getTimeline(
        @Header(AUTHORIZATION) authorization: String,
        @Query("source") source: String,
        @Query("target") target: String,
        @Query("from") from: String,
        @Query("to") toExclusive: String,
    ): Response<AllRatesTodayAnswer>
}

/** AllRatesToday's quotes, or the error it answered with. */
@Serializable(with = AllRatesTodayAnswerSerializer::class)
internal sealed interface AllRatesTodayAnswer

@Serializable
@JvmInline
internal value class AllRatesTodayQuotes(
    val quotes: List<AllRatesTodayQuote>,
) : AllRatesTodayAnswer

/** One `{rate, source, target, time}` entry; [time] is ISO-8601, or a plain date in a day series. */
@Serializable
internal class AllRatesTodayQuote(
    val target: String? = null,
    val rate: Decimal? = null,
    val time: String? = null,
)

/** `{"error": …}` or `{"message": …}`; either may be something other than a string. */
@Serializable
internal class AllRatesTodayFailure(
    private val error: JsonElement? = null,
    private val message: JsonElement? = null,
) : AllRatesTodayAnswer {
    /** The first of the two that is a string. */
    val text: String? get() =
        listOfNotNull(error, message).firstNotNullOfOrNull {
            (it as? JsonPrimitive)
                ?.takeIf { p ->
                    p.isString
                }?.content
        }
}

internal object AllRatesTodayAnswerSerializer : JsonContentPolymorphicSerializer<AllRatesTodayAnswer>(AllRatesTodayAnswer::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<AllRatesTodayAnswer> =
        if (element is JsonArray) AllRatesTodayQuotes.serializer() else AllRatesTodayFailure.serializer()
}
