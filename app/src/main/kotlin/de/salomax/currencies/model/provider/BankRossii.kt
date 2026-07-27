package de.salomax.currencies.model.provider

import android.content.Context
import de.salomax.currencies.R
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Rate
import de.salomax.currencies.model.Timeline
import de.salomax.currencies.model.adapter.BankRossiiCurrencyCodesXmlParser
import de.salomax.currencies.model.adapter.BankRossiiRatesXmlParser
import de.salomax.currencies.model.adapter.BankRossiiTimelineXmlParser
import de.salomax.currencies.util.HttpClientProvider
import de.salomax.currencies.util.fetch
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// The Bank Rossii URL endpoints accept dates as dd/MM/yyyy in the query
// string. The XML *response* uses dd.MM.yyyy — that formatter lives in
// AdapterUtils as BANK_ROSSII_DATE_FORMATTER and shouldn't be reused here.
private val BANK_ROSSII_URL_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy")

class BankRossii : ApiProvider.Api() {
    override val name = "Bank Rossii"

    override fun descriptionShort(context: Context) = context.getText(R.string.api_bankRossii_descriptionShort)

    override fun getDescriptionLong(context: Context) = context.getText(R.string.api_bankRossii_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) = context.getText(R.string.api_bankRossii_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) = null

    override val baseUrl = "https://www.cbr.ru/scripts"

    override suspend fun getRates(
        context: Context?,
        date: LocalDate?,
    ): Result<ExchangeRates> {
        val dateString =
            if (date == null) "" else "?date_req=${date.format(BANK_ROSSII_URL_DATE_FORMATTER)}"
        return HttpClientProvider.fetch(context, "$baseUrl/XML_daily.asp$dateString") { body ->
            BankRossiiRatesXmlParser().parse(body.byteStream())
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

        val idsResult = fetchCurrencyIds(context)
        val ids = idsResult.getOrElse { return Result.failure(it) }

        val idBase =
            if (parameterBase != Currency.RUB.iso4217Alpha()) {
                ids.entries.find { it.value == parameterBase }?.key
            } else {
                null
            }
        val idSymbol =
            if (parameterSymbol != Currency.RUB.iso4217Alpha()) {
                ids.entries.find { it.value == parameterSymbol }?.key
            } else {
                null
            }
        val missingId =
            when {
                parameterBase != Currency.RUB.iso4217Alpha() && idBase == null -> parameterBase
                parameterSymbol != Currency.RUB.iso4217Alpha() && idSymbol == null -> parameterSymbol
                else -> null
            }
        if (missingId != null) {
            return Result.failure(Throwable("No currency ID found for: $missingId"))
        }

        val timelineRub = buildRubTimeline(startDate, endDate)
        val baseTimeline =
            if (parameterBase == Currency.RUB.iso4217Alpha()) {
                Result.success(timelineRub)
            } else {
                fetchCurrencyTimeline(context, startDate, endDate, idBase!!, ids)
            }

        val symbolTimeline =
            if (parameterSymbol == Currency.RUB.iso4217Alpha()) {
                Result.success(timelineRub)
            } else {
                fetchCurrencyTimeline(context, startDate, endDate, idSymbol!!, ids)
            }

        val baseRates: Map<LocalDate, Rate>? = baseTimeline.getOrNull()?.rates
        val symbolRates: Map<LocalDate, Rate>? = symbolTimeline.getOrNull()?.rates

        return if (baseRates == null || symbolRates == null) {
            Result.failure(Throwable("Timeline data unavailable for base or symbol currency"))
        } else {
            runCatching {
                symbolTimeline.getOrThrow().copy(
                    rates =
                        symbolRates
                            .filter { (date, _) -> baseRates[date] != null }
                            .mapValues { (date, rate) ->
                                rate.copy(value = rate.value.divide(baseRates[date]!!.value, MathContext.DECIMAL128))
                            },
                )
            }
        }
    }

    private fun buildRubTimeline(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Timeline {
        val rubMap = LinkedHashMap<LocalDate, Rate>()
        var currentDate = startDate
        while (currentDate.isBefore(endDate) || currentDate.isEqual(endDate)) {
            rubMap[currentDate] = Rate(Currency.RUB, BigDecimal.ONE)
            currentDate = currentDate.plusDays(1)
        }
        return Timeline(
            success = true,
            error = null,
            base = Currency.RUB.iso4217Alpha(),
            startDate = startDate,
            endDate = endDate,
            rates = rubMap.toSortedMap(),
            provider = ApiProvider.BANK_ROSSII,
        )
    }

    private suspend fun fetchCurrencyIds(context: Context?): Result<Map<String, String>> =
        HttpClientProvider.fetch(context, "$baseUrl/XML_valFull.asp") { body ->
            BankRossiiCurrencyCodesXmlParser().parse(body.byteStream())
        }

    private suspend fun fetchCurrencyTimeline(
        context: Context?,
        startDate: LocalDate,
        endDate: LocalDate,
        currencyId: String,
        ids: Map<String, String>,
    ): Result<Timeline> =
        HttpClientProvider.fetch(
            context,
            "$baseUrl/XML_dynamic.asp" +
                "?date_req1=${startDate.format(BANK_ROSSII_URL_DATE_FORMATTER)}" +
                "&date_req2=${endDate.format(BANK_ROSSII_URL_DATE_FORMATTER)}" +
                "&VAL_NM_RQ=$currencyId",
        ) { body ->
            BankRossiiTimelineXmlParser(ids).parse(body.byteStream())
        }
}
