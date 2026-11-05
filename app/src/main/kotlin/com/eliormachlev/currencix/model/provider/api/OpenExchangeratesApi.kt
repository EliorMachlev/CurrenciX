package com.eliormachlev.currencix.model.provider.api

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Type-safe Retrofit binding for the Open Exchange Rates API
 * ([https://docs.openexchangerates.org/](https://docs.openexchangerates.org/)).
 *
 * Both endpoints take the same query parameters; the historical one adds the
 * ISO-8601 date as a path segment. The key (app ID) goes as
 * `Authorization: Token …`, which the API accepts in place of its `app_id`
 * parameter, so it stays out of the URL: out of the log and the HTTP cache. Bodies decode to
 * [OpenExchangeratesRates], which `OpenExchangeratesRatesAdapter` turns into
 * the app's model.
 */
internal interface OpenExchangeratesApi {
    @GET("latest.json")
    suspend fun getLatest(
        @Header(AUTHORIZATION) authorization: String,
        @Query("prettyprint") prettyPrint: Boolean,
        @Query("show_alternative") showAlternative: Boolean,
    ): Response<OpenExchangeratesRates>

    @GET("historical/{date}.json")
    suspend fun getHistorical(
        @Path("date") date: String,
        @Header(AUTHORIZATION) authorization: String,
        @Query("prettyprint") prettyPrint: Boolean,
        @Query("show_alternative") showAlternative: Boolean,
    ): Response<OpenExchangeratesRates>
}

/** `{timestamp, base, rates: {EUR: 0.92, …}}`, or `{message, …}` on failure. */
@Serializable
internal class OpenExchangeratesRates(
    val timestamp: Long? = null,
    val base: String? = null,
    val rates: Map<String, Decimal>? = null,
    val message: String? = null,
)
