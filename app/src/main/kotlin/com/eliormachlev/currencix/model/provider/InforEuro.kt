package com.eliormachlev.currencix.model.provider

import android.content.Context
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.model.adapter.InforEuroRatesAdapter
import com.eliormachlev.currencix.model.adapter.InforEuroTimelineAdapter
import com.squareup.moshi.JsonAdapter
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.ZoneOffset

class InforEuro : ApiProvider.Api() {
    override val name = "InforEuro"

    override fun descriptionShort(context: Context) = context.getText(R.string.api_inforEuro_descriptionShort)

    override fun getDescriptionLong(context: Context) = context.getText(R.string.api_inforEuro_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) = context.getText(R.string.api_inforEuro_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) = context.getText(R.string.api_inforEuro_hint)

    override val baseUrl = "https://ec.europa.eu/budg/inforeuro/api/public"

    override suspend fun getRates(
        context: Context?,
        date: LocalDate?,
    ): Result<ExchangeRates> {
        val effective = date ?: LocalDate.now(ZoneOffset.UTC)
        val adapter =
            moshi { add(InforEuroRatesAdapter(effective)) }
                .adapter(ExchangeRates::class.java)
        val dateQuery = if (date != null) "?year=${date.year}&month=${date.monthValue}" else ""

        return fetchJson(context, "$baseUrl/monthly-rates$dateQuery", name, adapter)
            .map { it.copy(provider = ApiProvider.INFOR_EURO) }
    }

    override suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Result<Timeline> {
        // InforEuro needs 2 calls: the API only provides EUR <-> symbol, without changing the base.
        // So, we make 2 calls: EUR <-> base & EUR <-> symbol
        val adapter =
            moshi { add(InforEuroTimelineAdapter(startDate, endDate)) }
                .adapter(Timeline::class.java)

        val resultBase = fetchTimeline(context, base.apiCodeOrDkkForFok(), adapter)
        val resultSymbol = fetchTimeline(context, symbol.apiCodeOrDkkForFok(), adapter)

        return when {
            resultBase.isFailure -> resultBase
            resultSymbol.isFailure -> resultSymbol
            else ->
                runCatching {
                    val baseTimeline = resultBase.getOrThrow()
                    val symbolTimeline = resultSymbol.getOrThrow()
                    baseTimeline.copy(
                        provider = ApiProvider.INFOR_EURO,
                        base = base.iso4217Alpha(),
                        rates =
                            symbolTimeline.rates?.mapValues { (date, symbolRate) ->
                                val baseValue = baseTimeline.rates?.get(date)?.value ?: BigDecimal.ONE
                                Rate(symbol, symbolRate.value.divide(baseValue, MathContext.DECIMAL128))
                            },
                    )
                }
        }
    }

    private suspend fun fetchTimeline(
        context: Context?,
        parameter: String,
        adapter: JsonAdapter<Timeline>,
    ): Result<Timeline> = fetchJson(context, "$baseUrl/currencies/$parameter", name, adapter)
}
