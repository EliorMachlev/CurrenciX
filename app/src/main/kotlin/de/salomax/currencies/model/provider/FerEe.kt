package de.salomax.currencies.model.provider

import android.content.Context
import com.squareup.moshi.Moshi
import de.salomax.currencies.R
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Timeline
import de.salomax.currencies.model.adapter.FrankfurterAppRatesAdapter
import de.salomax.currencies.model.adapter.FrankfurterAppTimelineAdapter
import de.salomax.currencies.util.HttpClientProvider
import de.salomax.currencies.util.fetch
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FerEe : ApiProvider.Api() {
    override val name = "Fer.ee"

    override fun descriptionShort(context: Context) = context.getText(R.string.api_ferEe_descriptionShort)

    override fun getDescriptionLong(context: Context) = context.getText(R.string.api_ferEe_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) = context.getText(R.string.api_ferEe_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) = null

    override val baseUrl = "https://api.fer.ee"

    override suspend fun getRates(
        context: Context?,
        date: LocalDate?,
    ): Result<ExchangeRates> {
        val base = Currency.EUR
        val dateString = if (date != null) date.format(DateTimeFormatter.ISO_LOCAL_DATE) else "latest"
        val adapter =
            Moshi
                .Builder()
                .addLast(SHARED_KOTLIN_JSON_ADAPTER_FACTORY)
                .apply {
                    add(FrankfurterAppRatesAdapter(base))
                    add(SHARED_LOCAL_DATE_ADAPTER)
                }.build()
                .adapter(ExchangeRates::class.java)

        return HttpClientProvider.fetch(context, "$baseUrl/$dateString?base=$base") { body ->
            adapter.fromJson(body.source()) ?: throw IOException("Fer.ee: empty JSON")
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
                .apply {
                    add(FrankfurterAppRatesAdapter(base))
                    add(SHARED_LOCAL_DATE_ADAPTER)
                    add(FrankfurterAppTimelineAdapter(symbol))
                }.build()
                .adapter(Timeline::class.java)

        val url =
            "$baseUrl/" +
                startDate.format(dateFormatter) + ".." + endDate.format(dateFormatter) +
                "?base=$parameterBase&symbols=$parameterSymbol"

        return HttpClientProvider
            .fetch(context, url) { body ->
                adapter.fromJson(body.source()) ?: throw IOException("Fer.ee: empty JSON")
            }.map { timeline ->
                when (base) {
                    Currency.FOK -> timeline.copy(base = base.iso4217Alpha())
                    else -> timeline
                }
            }
    }
}
