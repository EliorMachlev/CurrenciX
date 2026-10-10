package com.eliormachlev.currencix.model.provider.api

import com.eliormachlev.currencix.model.ExchangeRates
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Type-safe Retrofit binding for the Open Exchange Rates API
 * ([https://docs.openexchangerates.org/](https://docs.openexchangerates.org/)).
 *
 * Both endpoints take the same query parameters; the historical one adds the
 * ISO-8601 date as a path segment. Decoding goes through
 * `OpenExchangeratesRatesAdapter`, unchanged from the raw-OkHttp path.
 */
internal interface OpenExchangeratesApi {
    @GET("latest.json")
    suspend fun getLatest(
        @Query("app_id") appId: String,
        @Query("prettyprint") prettyPrint: Boolean,
        @Query("show_alternative") showAlternative: Boolean,
    ): Response<ExchangeRates>

    @GET("historical/{date}.json")
    suspend fun getHistorical(
        @Path("date") date: String,
        @Query("app_id") appId: String,
        @Query("prettyprint") prettyPrint: Boolean,
        @Query("show_alternative") showAlternative: Boolean,
    ): Response<ExchangeRates>
}
