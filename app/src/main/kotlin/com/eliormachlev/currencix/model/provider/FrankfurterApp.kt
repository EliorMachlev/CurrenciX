package com.eliormachlev.currencix.model.provider

import android.content.Context
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.ApiSecrets
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.model.adapter.FrankfurterAppRatesAdapter
import com.eliormachlev.currencix.model.adapter.FrankfurterAppTimelineAdapter
import com.eliormachlev.currencix.model.provider.api.FrankfurterApi
import com.eliormachlev.currencix.util.RetrofitProvider
import com.eliormachlev.currencix.util.retrofitCall
import java.time.LocalDate

// Path segment sent to the /latest endpoint (versus an ISO-8601 date).
private const val LATEST_DATE_PATH = "latest"

class FrankfurterApp : ApiProvider.Api() {
    override val name = "Frankfurter.app"
    override val nameRes = R.string.api_frankfurterApp_name

    override fun descriptionShort(context: Context) = context.getText(R.string.api_frankfurterApp_descriptionShort)

    override fun getDescriptionLong(context: Context) = context.getText(R.string.api_frankfurterApp_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) = context.getText(R.string.api_frankfurterApp_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) = null

    // api.frankfurter.app started returning a 301 to api.frankfurter.dev/v1 —
    // point at the new host directly so we don't rely on redirect behaviour.
    override val baseUrl = "https://api.frankfurter.dev/v1"

    override suspend fun getRates(
        context: Context?,
        date: LocalDate?,
        @Suppress("UNUSED_PARAMETER") secrets: ApiSecrets,
    ): Result<ExchangeRates> {
        // Currency conversions are done relatively to each other - so it basically doesn't matter
        // which base is used here. However, Euro is a strong currency, preventing rounding errors.
        val base = Currency.EUR
        val api = api(context, base, symbol = null)
        val datePath = date?.format(ISO_DATE) ?: LATEST_DATE_PATH
        return retrofitCall {
            api.getRates(datePath = datePath, base = base.toString())
        }.map { it.copy(provider = ApiProvider.FRANKFURTER_APP) }
    }

    override suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Result<Timeline> {
        val api = api(context, base, symbol)
        return retrofitCall {
            api.getTimeline(
                start = startDate.format(ISO_DATE),
                end = endDate.format(ISO_DATE),
                base = base.apiCodeOrDkkForFok(),
                symbols = symbol.apiCodeOrDkkForFok(),
            )
        }.map { timeline ->
            // change dkk base back to fok, if needed
            if (base == Currency.FOK) timeline.copy(base = base.iso4217Alpha()) else timeline
        }.map { it.copy(provider = ApiProvider.FRANKFURTER_APP) }
    }

    // Builds a per-request Retrofit-bound API. The two custom Moshi adapters
    // are per-request (they close over the requested [base] / [symbol]), so
    // the Retrofit instance and its FrankfurterApi proxy have to be built
    // per-request too. Cost is negligible — no I/O, no reflection.
    private fun api(
        context: Context?,
        base: Currency,
        symbol: Currency?,
    ): FrankfurterApi {
        val moshi =
            moshi {
                add(FrankfurterAppRatesAdapter(base))
                add(SHARED_LOCAL_DATE_ADAPTER)
                if (symbol != null) add(FrankfurterAppTimelineAdapter(symbol))
            }
        return RetrofitProvider
            .retrofit(context, baseUrl, moshi)
            .create(FrankfurterApi::class.java)
    }
}
