package com.eliormachlev.currencix.model.provider.api

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET

/**
 * Type-safe Retrofit binding for the Bank of Israel PublicApi — the
 * fixed-shape JSON endpoint behind live rates, decoded to [BankOfIsraelRates]
 * and turned into the app's model by `BankOfIsraelRatesAdapter`.
 *
 * Deliberately not covered here: the SDMX-JSON feed behind historical rates
 * and the timeline. Its payload is keyed by dimension-index tuples rather than
 * a fixed schema, so `BankOfIsraelSdmxParser` walks it as a generic JSON tree
 * off the raw body. Retrofit would add nothing but URL building on top of
 * that, so the SDMX path stays on `HttpClientProvider.fetch`.
 */
internal interface BankOfIsraelApi {
    @GET("PublicApi/GetExchangeRates")
    suspend fun getLatestRates(): Response<BankOfIsraelRates>
}

/** `{exchangeRates: [{key, currentExchangeRate, unit, lastUpdate}, …]}`. */
@Serializable
internal class BankOfIsraelRates(
    val exchangeRates: List<BankOfIsraelRate>? = null,
)

/** Shekels for [unit] of the currency [key], as of [lastUpdate] (ISO-8601 with an offset). */
@Serializable
internal class BankOfIsraelRate(
    val key: String? = null,
    val currentExchangeRate: Decimal? = null,
    val unit: Decimal? = null,
    val lastUpdate: String? = null,
)
