package com.eliormachlev.currencix.model.provider.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Type-safe Retrofit binding for the Frankfurter.app v1 API.
 *
 * The endpoints mirror the raw-OkHttp URLs previously built by
 * `FrankfurterApp.ratesUrl` / `.timelineUrl` — see
 * [https://frankfurter.dev/](https://frankfurter.dev/) for the upstream
 * schema. The bodies decode to [FrankfurterRates] and [FrankfurterTimeline],
 * which `FrankfurterAppRatesAdapter` / `FrankfurterAppTimelineAdapter` turn
 * into the app's model.
 *
 * `datePath` accepts either `latest` or an ISO-8601 date; the timeline
 * variant expects a `<start>..<end>` range in a single path segment.
 *
 * Methods return [Response] so `fetchRetrofit` can apply the shared
 * non-2xx / empty-body error contract.
 */
internal interface FrankfurterApi {
    @GET("{datePath}")
    suspend fun getRates(
        @Path("datePath") datePath: String,
        @Query("base") base: String,
    ): Response<FrankfurterRates>

    @GET("{start}..{end}")
    suspend fun getTimeline(
        @Path("start") start: String,
        @Path("end") end: String,
        @Query("base") base: String,
        @Query("symbols") symbols: String,
    ): Response<FrankfurterTimeline>
}

/** `{base, date, rates: {USD: 1.08, …}}`. */
@Serializable
internal class FrankfurterRates(
    val base: String? = null,
    val date: IsoDate? = null,
    val rates: Map<String, Decimal>? = null,
)

/** `{base, start_date, end_date, rates: {date: {USD: 1.09}}}`; a day may have no rate. */
@Serializable
internal class FrankfurterTimeline(
    val base: String? = null,
    @SerialName("start_date") val startDate: IsoDate? = null,
    @SerialName("end_date") val endDate: IsoDate? = null,
    val rates: Map<String, Map<String, Decimal>>? = null,
)
