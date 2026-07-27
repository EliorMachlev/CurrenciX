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

        return HttpClientProvider
            .fetch(context, "$baseUrl/$dateString?base=$base") { body ->
                adapter.fromJson(body.source()) ?: throw IOException("Frankfurter: empty JSON")
            }.map { rates -> rates.copy(provider = ApiProvider.FRANKFURTER_APP) }
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
                adapter.fromJson(body.source()) ?: throw IOException("Frankfurter: empty JSON")
            }.map { timeline ->
                when (base) {
                    // change dkk base back to fok, if needed
                    Currency.FOK -> timeline.copy(base = base.iso4217Alpha())
                    else -> timeline
                }
            }.map { timeline ->
                timeline.copy(provider = ApiProvider.FRANKFURTER_APP)
            }
    }
}
