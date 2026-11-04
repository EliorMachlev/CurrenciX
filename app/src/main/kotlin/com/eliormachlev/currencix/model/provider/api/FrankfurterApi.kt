package com.eliormachlev.currencix.model.provider.api

import okhttp3.ResponseBody
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
 * schema. The bodies are read by `FrankfurterAppRatesAdapter` and
 * `FrankfurterAppTimelineAdapter`.
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
    ): Response<ResponseBody>

    @GET("{start}..{end}")
    suspend fun getTimeline(
        @Path("start") start: String,
        @Path("end") end: String,
        @Query("base") base: String,
        @Query("symbols") symbols: String,
    ): Response<ResponseBody>
}
