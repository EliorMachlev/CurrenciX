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
import java.time.format.DateTimeFormatter

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
        val formattedDateStart = date?.minusDays(TIMELINE_LOOKBACK_DAYS)?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val formattedDateEnd = date?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dateString =
            if (date == null) "?lastNObservations=1" else "?StartPeriod=$formattedDateStart&EndPeriod=$formattedDateEnd"

        return HttpClientProvider.fetch(
            context,
            "$baseUrl/data/EXR/B..NOK.SP$dateString&format=sdmx-compact-2.1",
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
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val parameterBase = base.apiCodeOrDkkForFok()
        val parameterSymbol = symbol.apiCodeOrDkkForFok()

        // if we request NOK->NOK, the response would be empty. Add EUR in that case.
        val eur = if (base == Currency.NOK && symbol == Currency.NOK) "EUR" else ""
        val url =
            "$baseUrl/data/EXR/B.$parameterBase+$parameterSymbol+$eur.NOK.SP" +
                "?StartPeriod=${dateFormatter.format(startDate)}" +
                "&EndPeriod=${dateFormatter.format(endDate)}" +
                "&format=sdmx-compact-2.1"

        return HttpClientProvider.fetch(context, url) { body ->
            NorgesBankTimelineXmlParser(base, symbol, startDate, endDate).parse(body.byteStream())
        }
    }
}
