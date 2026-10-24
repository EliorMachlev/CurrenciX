package com.eliormachlev.currencix.model.provider.api

import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Timeline
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
 * `{rate, source, target, time}`, decoded by `AllRatesTodayRatesAdapter` /
 * `AllRatesTodayTimelineAdapter`.
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
    ): Response<ExchangeRates>

    // A series is always one rate per day.
    @GET("api/v1/rates?group=day")
    suspend fun getTimeline(
        @Header(AUTHORIZATION) authorization: String,
        @Query("source") source: String,
        @Query("target") target: String,
        @Query("from") from: String,
        @Query("to") toExclusive: String,
    ): Response<Timeline>
}
