package de.salomax.currencies.model.provider

import android.content.Context
import com.squareup.moshi.Moshi
import de.salomax.currencies.R
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Timeline
import de.salomax.currencies.model.adapter.BankOfCanadaRatesAdapter
import de.salomax.currencies.model.adapter.BankOfCanadaTimelineAdapter
import de.salomax.currencies.util.HttpClientProvider
import de.salomax.currencies.util.fetch
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BankOfCanada : ApiProvider.Api() {
    override val name = "Bank of Canada"

    override fun descriptionShort(context: Context) = context.getText(R.string.api_bankOfCanada_descriptionShort)

    override fun getDescriptionLong(context: Context) = context.getText(R.string.api_bankOfCanada_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) = context.getText(R.string.api_bankOfCanada_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) = null

    override val baseUrl = "https://www.bankofcanada.ca/valet"

    override suspend fun getRates(
        context: Context?,
        date: LocalDate?,
    ): Result<ExchangeRates> {
        val formattedDateStart = date?.minusDays(TIMELINE_LOOKBACK_DAYS)?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val formattedDateEnd = date?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        // `recent=1` returns just the latest observation; a start/end range asks
        // for the historical window ending on the requested date.
        val dateString =
            if (date == null) "recent=1" else "start_date=$formattedDateStart&end_date=$formattedDateEnd"
        val adapter =
            Moshi
                .Builder()
                .addLast(SHARED_KOTLIN_JSON_ADAPTER_FACTORY)
                .add(BankOfCanadaRatesAdapter())
                .build()
                .adapter(ExchangeRates::class.java)

        return HttpClientProvider.fetch(
            context,
            "$baseUrl/observations/group/FX_RATES_DAILY_CURRENT/json?$dateString&order_dir=desc",
        ) { body ->
            adapter.fromJson(body.source()) ?: throw IOException("BankOfCanada: empty JSON")
        }
    }

    override suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Result<Timeline> {
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val parameterBase = base.apiCodeOrDkkForFok()
        val parameterSymbol = symbol.apiCodeOrDkkForFok()
        val adapter =
            Moshi
                .Builder()
                .addLast(SHARED_KOTLIN_JSON_ADAPTER_FACTORY)
                .add(BankOfCanadaTimelineAdapter(base, symbol))
                .build()
                .adapter(Timeline::class.java)

        val url =
            "$baseUrl/observations/FX${parameterBase}CAD,FX${parameterSymbol}CAD/json" +
                "?start_date=${startDate.format(dateFormatter)}" +
                "&end_date=${endDate.format(dateFormatter)}" +
                "&order_dir=asc"

        return HttpClientProvider.fetch(context, url) { body ->
            adapter.fromJson(body.source()) ?: throw IOException("BankOfCanada: empty JSON")
        }
    }
}
