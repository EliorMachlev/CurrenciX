package com.eliormachlev.currencix.model.provider

import android.content.Context
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.model.adapter.FrankfurterAppRatesAdapter
import com.eliormachlev.currencix.model.adapter.FrankfurterAppTimelineAdapter
import java.time.LocalDate

class FrankfurterApp : ApiProvider.Api() {
    override val name = "Frankfurter.app"

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
    ): Result<ExchangeRates> {
        // Currency conversions are done relatively to each other - so it basically doesn't matter
        // which base is used here. However, Euro is a strong currency, preventing rounding errors.
        val base = Currency.EUR
        val adapter =
            moshi {
                add(FrankfurterAppRatesAdapter(base))
                add(SHARED_LOCAL_DATE_ADAPTER)
            }.adapter(ExchangeRates::class.java)

        return fetchJson(context, ratesUrl(base, date), name, adapter)
            .map { it.copy(provider = ApiProvider.FRANKFURTER_APP) }
    }

    override suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Result<Timeline> {
        val adapter =
            moshi {
                add(FrankfurterAppRatesAdapter(base))
                add(SHARED_LOCAL_DATE_ADAPTER)
                add(FrankfurterAppTimelineAdapter(symbol))
            }.adapter(Timeline::class.java)

        return fetchJson(context, timelineUrl(base, symbol, startDate, endDate), name, adapter)
            .map { timeline ->
                // change dkk base back to fok, if needed
                if (base == Currency.FOK) timeline.copy(base = base.iso4217Alpha()) else timeline
            }.map { it.copy(provider = ApiProvider.FRANKFURTER_APP) }
    }

    private fun ratesUrl(
        base: Currency,
        date: LocalDate?,
    ): String {
        val datePart = date?.format(ISO_DATE) ?: "latest"
        return "$baseUrl/$datePart?base=$base"
    }

    private fun timelineUrl(
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate,
    ): String =
        "$baseUrl/${startDate.format(ISO_DATE)}..${endDate.format(ISO_DATE)}" +
            "?base=${base.apiCodeOrDkkForFok()}&symbols=${symbol.apiCodeOrDkkForFok()}"
}
