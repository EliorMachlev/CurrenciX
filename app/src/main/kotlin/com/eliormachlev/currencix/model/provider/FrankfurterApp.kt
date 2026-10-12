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
import com.eliormachlev.currencix.model.adapter.register
import com.eliormachlev.currencix.model.provider.api.FrankfurterApi
import java.time.LocalDate

class FrankfurterApp : ApiProvider.Api {
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
        secrets: ApiSecrets,
    ): Result<ExchangeRates> = frankfurterRates(context, date, ApiProvider.FRANKFURTER_APP)

    override suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Result<Timeline> = frankfurterTimeline(context, base, symbol, startDate..endDate, ApiProvider.FRANKFURTER_APP)
}

// Frankfurter's API — which Fer.ee serves too, at its own [ApiProvider.Api.baseUrl]:
// the rates on [date] (null for the latest), marked as [provider]'s.
internal suspend fun ApiProvider.Api.frankfurterRates(
    context: Context?,
    date: LocalDate?,
    provider: ApiProvider,
): Result<ExchangeRates> {
    // Currency conversions are done relatively to each other - so it basically doesn't matter
    // which base is used here. However, Euro is a strong currency, preventing rounding errors.
    val base = Currency.EUR
    val api = frankfurterApi(context, base, symbol = null)
    val datePath = date?.format(ISO_DATE) ?: LATEST_DATE_PATH
    return fetchRetrofit {
        api.getRates(datePath = datePath, base = base.toString())
    }.map { it.copy(provider = provider) }
}

// The [symbol] against [base] over [dates] from Frankfurter's API, marked as [provider]'s.
internal suspend fun ApiProvider.Api.frankfurterTimeline(
    context: Context?,
    base: Currency,
    symbol: Currency,
    dates: ClosedRange<LocalDate>,
    provider: ApiProvider,
): Result<Timeline> {
    val api = frankfurterApi(context, base, symbol)
    return fetchRetrofit {
        api.getTimeline(
            start = dates.start.format(ISO_DATE),
            end = dates.endInclusive.format(ISO_DATE),
            base = base.apiCodeOrDkkForFok(),
            symbols = symbol.apiCodeOrDkkForFok(),
        )
    }.map { timeline ->
        // change dkk base back to fok, if needed
        val rebased = if (base == Currency.FOK) timeline.copy(base = base.iso4217Alpha()) else timeline
        rebased.copy(provider = provider)
    }
}

// Builds a per-request Retrofit-bound API. The two custom Moshi adapters
// are per-request (they close over the requested [base] / [symbol]), so
// the Retrofit instance and its FrankfurterApi proxy have to be built
// per-request too. Cost is negligible — no I/O, no reflection.
private fun ApiProvider.Api.frankfurterApi(
    context: Context?,
    base: Currency,
    symbol: Currency?,
): FrankfurterApi =
    retrofitApi(
        context,
        moshi {
            register(FrankfurterAppRatesAdapter(base))
            register(SHARED_LOCAL_DATE_ADAPTER)
            if (symbol != null) register(FrankfurterAppTimelineAdapter(symbol))
        },
    )
