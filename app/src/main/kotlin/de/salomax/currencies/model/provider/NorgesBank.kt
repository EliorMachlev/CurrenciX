package de.salomax.currencies.model.provider

import android.content.Context
import de.salomax.currencies.R
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Timeline
import de.salomax.currencies.model.adapter.NorgesBankRatesXmlParser
import de.salomax.currencies.model.adapter.NorgesBankTimelineXmlParser
import de.salomax.currencies.util.HttpClientProvider
import de.salomax.currencies.util.fetch
import java.time.LocalDate

private const val SDMX_FORMAT_QS = "&format=sdmx-compact-2.1"

class NorgesBank : ApiProvider.Api() {
    override val name = "Norges Bank"

    override fun descriptionShort(context: Context) = context.getText(R.string.api_norgesBank_descriptionShort)

    override fun getDescriptionLong(context: Context) = context.getText(R.string.api_norgesBank_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) = context.getText(R.string.api_norgesBank_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) = context.getText(R.string.api_norgesBank_hint)

    override val baseUrl = "https://data.norges-bank.no/api"

    override suspend fun getRates(
        context: Context?,
        date: LocalDate?,
    ): Result<ExchangeRates> {
        val dateQuery =
            if (date == null) {
                "?lastNObservations=1"
            } else {
                "?StartPeriod=${date.minusDays(TIMELINE_LOOKBACK_DAYS).format(ISO_DATE)}" +
                    "&EndPeriod=${date.format(ISO_DATE)}"
            }

        return HttpClientProvider.fetch(
            context,
            "$baseUrl/data/EXR/B..NOK.SP$dateQuery$SDMX_FORMAT_QS",
        ) { body ->
            NorgesBankRatesXmlParser().parse(body.byteStream(), date ?: LocalDate.now())
        }
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
        // if we request NOK->NOK, the response would be empty. Add EUR in that case.
        val eur = if (base == Currency.NOK && symbol == Currency.NOK) "EUR" else ""

        val url =
            "$baseUrl/data/EXR/B.$parameterBase+$parameterSymbol+$eur.NOK.SP" +
                "?StartPeriod=${startDate.format(ISO_DATE)}" +
                "&EndPeriod=${endDate.format(ISO_DATE)}" +
                SDMX_FORMAT_QS

        return HttpClientProvider.fetch(context, url) { body ->
            NorgesBankTimelineXmlParser(base, symbol, startDate, endDate).parse(body.byteStream())
        }
    }
}
