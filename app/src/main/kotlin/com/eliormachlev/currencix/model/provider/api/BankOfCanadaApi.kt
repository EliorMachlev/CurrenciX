package com.eliormachlev.currencix.model.provider.api

import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Timeline
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Sort order of the Valet API's `order_dir` parameter. [toString] yields the
 * wire value, which is what Retrofit sends for a `@Query` argument.
 */
internal enum class ValetOrder(
    private val wire: String,
) {
    ASC("asc"),
    DESC("desc"),
    ;

    override fun toString(): String = wire
}

/**
 * Type-safe Retrofit binding for the Bank of Canada Valet API
 * ([https://www.bankofcanada.ca/valet/docs](https://www.bankofcanada.ca/valet/docs)).
 * Decoding goes through `BankOfCanadaRatesAdapter` /
 * `BankOfCanadaTimelineAdapter`, unchanged from the raw-OkHttp path.
 */
internal interface BankOfCanadaApi {
    // Either `recent` (latest N observations) or a `start_date`..`end_date`
    // window is set; Retrofit omits the null ones.
    @GET("observations/group/FX_RATES_DAILY_CURRENT/json")
    suspend fun getRates(
        @Query("recent") recent: Int?,
        @Query("start_date") startDate: String?,
        @Query("end_date") endDate: String?,
        @Query("order_dir") order: ValetOrder,
    ): Response<ExchangeRates>

    // [seriesNames] is a comma-separated list of Valet series, e.g.
    // `FXUSDCAD,FXEURCAD`.
    @GET("observations/{seriesNames}/json")
    suspend fun getSeries(
        @Path("seriesNames") seriesNames: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("order_dir") order: ValetOrder,
    ): Response<Timeline>
}
