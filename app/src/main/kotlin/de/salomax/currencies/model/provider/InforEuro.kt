package de.salomax.currencies.model.provider

import android.content.Context
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import de.salomax.currencies.R
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Rate
import de.salomax.currencies.model.Timeline
import de.salomax.currencies.model.adapter.InforEuroRatesAdapter
import de.salomax.currencies.model.adapter.InforEuroTimelineAdapter
import de.salomax.currencies.util.HttpClientProvider
import de.salomax.currencies.util.fetch
import java.io.IOException
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
        val adapter =
            Moshi
                .Builder()
                .addLast(SHARED_KOTLIN_JSON_ADAPTER_FACTORY)
                .apply {
                    add(InforEuroRatesAdapter(date ?: LocalDate.now(ZoneOffset.UTC)))
                }.build()
                .adapter(ExchangeRates::class.java)
        val url =
            baseUrl + "/monthly-rates" +
                if (date != null) "?year=${date.year}&month=${date.monthValue}" else ""

        return HttpClientProvider
            .fetch(context, url) { body ->
                adapter.fromJson(body.source()) ?: throw IOException("InforEuro: empty JSON")
            }.map { rates -> rates.copy(provider = ApiProvider.INFOR_EURO) }
    }

    override suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Result<Timeline> {
        val parameterBase = base.apiCodeOrDkkForFok()
        val parameterSymbol = symbol.apiCodeOrDkkForFok()

        // InforEuro needs 2 calls: the API only provides EUR <-> symbol, without changing the base.
        // So, we make 2 calls: EUR <-> base & EUR <-> symbol
        val adapter =
            Moshi
                .Builder()
                .addLast(SHARED_KOTLIN_JSON_ADAPTER_FACTORY)
                .add(InforEuroTimelineAdapter(startDate, endDate))
                .build()
                .adapter(Timeline::class.java)

        val resultBase = fetchTimeline(context, parameterBase, adapter)
        val resultSymbol = fetchTimeline(context, parameterSymbol, adapter)

        return when {
            resultBase.isFailure -> resultBase
            resultSymbol.isFailure -> resultSymbol
            else ->
                runCatching {
                    val baseTimeline = resultBase.getOrThrow()
                    val symbolTimeline = resultSymbol.getOrThrow()
                    baseTimeline.copy(
                        provider = ApiProvider.INFOR_EURO,
                        base = (if (base == Currency.FOK) Currency.FOK else base).iso4217Alpha(),
                        rates =
                            symbolTimeline.rates?.mapValues { symbolEntry ->
                                val baseValue = baseTimeline.rates?.get(symbolEntry.key)
                                Rate(
                                    symbol,
                                    symbolEntry.value.value.divide(
                                        baseValue?.value ?: BigDecimal.ONE,
                                        MathContext.DECIMAL128,
                                    ),
                                )
                            },
                    )
                }
        }
    }

    private suspend fun fetchTimeline(
        context: Context?,
        parameter: String,
        adapter: JsonAdapter<Timeline>,
    ): Result<Timeline> =
        HttpClientProvider.fetch(context, "$baseUrl/currencies/$parameter") { body ->
            adapter.fromJson(body.source()) ?: throw IOException("InforEuro: empty JSON")
        }
}
