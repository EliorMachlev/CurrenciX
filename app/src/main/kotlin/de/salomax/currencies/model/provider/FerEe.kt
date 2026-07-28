package de.salomax.currencies.model.provider

import android.content.Context
import de.salomax.currencies.R
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Timeline
import de.salomax.currencies.model.adapter.FrankfurterAppRatesAdapter
import de.salomax.currencies.model.adapter.FrankfurterAppTimelineAdapter
import java.time.LocalDate

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
        val datePart = date?.format(ISO_DATE) ?: "latest"
        val adapter =
            moshi {
                add(FrankfurterAppRatesAdapter(base))
                add(SHARED_LOCAL_DATE_ADAPTER)
            }.adapter(ExchangeRates::class.java)

        return fetchJson(context, "$baseUrl/$datePart?base=$base", name, adapter)
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

        val url =
            "$baseUrl/${startDate.format(ISO_DATE)}..${endDate.format(ISO_DATE)}" +
                "?base=${base.apiCodeOrDkkForFok()}&symbols=${symbol.apiCodeOrDkkForFok()}"

        return fetchJson(context, url, name, adapter).map { timeline ->
            if (base == Currency.FOK) timeline.copy(base = base.iso4217Alpha()) else timeline
        }
    }
}
