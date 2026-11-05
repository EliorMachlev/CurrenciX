package com.eliormachlev.currencix.model.provider.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
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
 * Bodies decode to [ValetObservations], which `BankOfCanadaRatesAdapter` /
 * `BankOfCanadaTimelineAdapter` turn into the app's model.
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
    ): Response<ValetObservations>

    // [seriesNames] is a comma-separated list of Valet series, e.g.
    // `FXUSDCAD,FXEURCAD`.
    @GET("observations/{seriesNames}/json")
    suspend fun getSeries(
        @Path("seriesNames") seriesNames: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("order_dir") order: ValetOrder,
    ): Response<ValetObservations>
}

/**
 * `{observations: [{d: "2026-10-06", FXUSDCAD: {v: "1.3672"}, …}]}`, or
 * `{message}` when there's nothing to give. An observation's fields are
 * named by the series asked for, so each stays a JSON object.
 */
@Serializable
internal class ValetObservations(
    val observations: List<JsonObject>? = null,
    val message: String? = null,
)
