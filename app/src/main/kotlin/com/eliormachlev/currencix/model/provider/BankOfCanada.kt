package com.eliormachlev.currencix.model.provider

import android.content.Context
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.model.adapter.BankOfCanadaRatesAdapter
import com.eliormachlev.currencix.model.adapter.BankOfCanadaTimelineAdapter
import java.time.LocalDate

class BankOfCanada : ApiProvider.Api() {
    override val name = "Bank of Canada"
    override val nameRes = R.string.api_bankOfCanada_name

    override fun descriptionShort(context: Context) = context.getText(R.string.api_bankOfCanada_descriptionShort)

    override fun getDescriptionLong(context: Context) = context.getText(R.string.api_bankOfCanada_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) = context.getText(R.string.api_bankOfCanada_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) = null

    override val baseUrl = "https://www.bankofcanada.ca/valet"

    override suspend fun getRates(
        context: Context?,
        date: LocalDate?,
    ): Result<ExchangeRates> {
        // `recent=1` returns just the latest observation; a start/end range asks
        // for the historical window ending on the requested date.
        val dateQuery =
            if (date == null) {
                "recent=1"
            } else {
                "start_date=${date.minusDays(TIMELINE_LOOKBACK_DAYS).format(ISO_DATE)}" +
                    "&end_date=${date.format(ISO_DATE)}"
            }
        val adapter =
            moshi { add(BankOfCanadaRatesAdapter()) }
                .adapter(ExchangeRates::class.java)

        return fetchJson(
            context,
            "$baseUrl/observations/group/FX_RATES_DAILY_CURRENT/json?$dateQuery&order_dir=desc",
            name,
            adapter,
        )
    }

    override suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Result<Timeline> {
        val adapter =
            moshi { add(BankOfCanadaTimelineAdapter(base, symbol)) }
                .adapter(Timeline::class.java)

        val url =
            "$baseUrl/observations/" +
                "FX${base.apiCodeOrDkkForFok()}CAD,FX${symbol.apiCodeOrDkkForFok()}CAD/json" +
                "?start_date=${startDate.format(ISO_DATE)}" +
                "&end_date=${endDate.format(ISO_DATE)}" +
                "&order_dir=asc"

        return fetchJson(context, url, name, adapter)
    }
}
