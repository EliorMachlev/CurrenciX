package com.eliormachlev.currencix.model.provider.api

import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Timeline
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Type-safe Retrofit binding for the European Commission's InforEuro public
 * API. Decoding goes through `InforEuroRatesAdapter` /
 * `InforEuroTimelineAdapter`, unchanged from the raw-OkHttp path.
 */
internal interface InforEuroApi {
    // `year` / `month` are either both set (a historical month) or both null
    // (the current month) — Retrofit omits null query parameters.
    @GET("monthly-rates")
    suspend fun getMonthlyRates(
        @Query("year") year: Int?,
        @Query("month") month: Int?,
    ): Response<ExchangeRates>

    // Full EUR <-> [currency] history; the adapter trims it to the requested
    // window.
    @GET("currencies/{currency}")
    suspend fun getCurrencyHistory(
        @Path("currency") currency: String,
    ): Response<Timeline>
}
