package com.eliormachlev.currencix.model.provider.api

import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Timeline
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Type-safe Retrofit binding for the Frankfurter.app v1 API.
 *
 * The endpoints mirror the raw-OkHttp URLs previously built by
 * `FrankfurterApp.ratesUrl` / `.timelineUrl` — see
 * [https://frankfurter.dev/](https://frankfurter.dev/) for the upstream
 * schema. Response decoding delegates to the same custom Moshi adapters
 * (`FrankfurterAppRatesAdapter`, `FrankfurterAppTimelineAdapter`) already
 * used by the raw path, so wire-format handling is unchanged.
 *
 * `datePath` accepts either `latest` or an ISO-8601 date; the timeline
 * variant expects a `<start>..<end>` range in a single path segment.
 */
internal interface FrankfurterApi {
    @GET("{datePath}")
    suspend fun getRates(
        @Path("datePath") datePath: String,
        @Query("base") base: String,
    ): ExchangeRates

    @GET("{start}..{end}")
    suspend fun getTimeline(
        @Path("start") start: String,
        @Path("end") end: String,
        @Query("base") base: String,
        @Query("symbols") symbols: String,
    ): Timeline
}
