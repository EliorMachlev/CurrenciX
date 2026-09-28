package com.eliormachlev.currencix.model.provider

import android.content.Context
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.ApiSecrets
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.model.adapter.BankOfCanadaRatesAdapter
import com.eliormachlev.currencix.model.adapter.BankOfCanadaTimelineAdapter
import com.eliormachlev.currencix.model.provider.api.BankOfCanadaApi
import com.eliormachlev.currencix.model.provider.api.ValetOrder
import com.squareup.moshi.Moshi
import java.time.LocalDate

// `recent=1` asks Valet for just the latest observation.
private const val RECENT_LATEST_ONLY = 1

// The rates adapter is stateless, so one Moshi serves every rates request.
private val RATES_MOSHI: Moshi = moshi { add(BankOfCanadaRatesAdapter()) }

// Valet names each daily series `FX<currency>CAD`.
private fun valetSeries(currency: Currency): String = "FX${currency.apiCodeOrDkkForFok()}CAD"

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
        @Suppress("UNUSED_PARAMETER") secrets: ApiSecrets,
    ): Result<ExchangeRates> {
        val api = retrofitApi<BankOfCanadaApi>(context, RATES_MOSHI)
        // `recent=1` returns just the latest observation; a start/end range asks
        // for the historical window ending on the requested date.
        return fetchRetrofit {
            api.getRates(
                recent = if (date == null) RECENT_LATEST_ONLY else null,
                startDate = date?.minusDays(TIMELINE_LOOKBACK_DAYS)?.format(ISO_DATE),
                endDate = date?.format(ISO_DATE),
                order = ValetOrder.DESC,
            )
        }
    }

    override suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Result<Timeline> {
        // The adapter closes over the requested pair, so it's built per request.
        val api = retrofitApi<BankOfCanadaApi>(context, moshi { add(BankOfCanadaTimelineAdapter(base, symbol)) })

        return fetchRetrofit {
            api.getSeries(
                seriesNames = "${valetSeries(base)},${valetSeries(symbol)}",
                startDate = startDate.format(ISO_DATE),
                endDate = endDate.format(ISO_DATE),
                order = ValetOrder.ASC,
            )
        }
    }
}
