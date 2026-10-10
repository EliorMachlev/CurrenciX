package com.eliormachlev.currencix.model.provider.api

import com.eliormachlev.currencix.model.ExchangeRates
import retrofit2.Response
import retrofit2.http.GET

/**
 * Type-safe Retrofit binding for the Bank of Israel PublicApi — the
 * fixed-shape JSON endpoint behind live rates, decoded by
 * `BankOfIsraelRatesAdapter`.
 *
 * Deliberately not covered here: the SDMX-JSON feed behind historical rates
 * and the timeline. Its payload is keyed by dimension-index tuples rather than
 * a fixed schema, so `BankOfIsraelSdmxParser` walks it as a generic JSON tree
 * off the raw body. Retrofit would add nothing but URL building on top of
 * that, so the SDMX path stays on `HttpClientProvider.fetch`.
 */
internal interface BankOfIsraelApi {
    @GET("PublicApi/GetExchangeRates")
    suspend fun getLatestRates(): Response<ExchangeRates>
}
