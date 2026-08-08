package com.eliormachlev.currencix.model.provider

import android.content.Context
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.model.adapter.BankRossiiCurrencyCodesXmlParser
import com.eliormachlev.currencix.model.adapter.BankRossiiRatesXmlParser
import com.eliormachlev.currencix.model.adapter.BankRossiiTimelineXmlParser
import com.eliormachlev.currencix.model.adapter.dateSequence
import com.eliormachlev.currencix.util.HttpClientProvider
import com.eliormachlev.currencix.util.fetch
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// The Bank Rossii URL endpoints accept dates as dd/MM/yyyy in the query
// string. The XML *response* uses dd.MM.yyyy — that formatter lives in
// AdapterUtils as BANK_ROSSII_DATE_FORMATTER and shouldn't be reused here.
private val URL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private val RUB_CODE: String = Currency.RUB.iso4217Alpha()

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
        val dateQuery = date?.let { "?date_req=${it.format(URL_DATE)}" } ?: ""
        return HttpClientProvider.fetch(context, "$baseUrl/XML_daily.asp$dateQuery") { body ->
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

        val ids = fetchCurrencyIds(context).getOrElse { return Result.failure(it) }

        val idBase =
            resolveCurrencyId(parameterBase, ids)
                ?: return Result.failure(Throwable("No currency ID found for: $parameterBase"))
        val idSymbol =
            resolveCurrencyId(parameterSymbol, ids)
                ?: return Result.failure(Throwable("No currency ID found for: $parameterSymbol"))

        val rubTimeline = buildRubTimeline(startDate, endDate)
        val baseTimeline = timelineFor(context, parameterBase, startDate, endDate, idBase, ids, rubTimeline)
        val symbolTimeline = timelineFor(context, parameterSymbol, startDate, endDate, idSymbol, ids, rubTimeline)

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

    // Returns the numeric ID for [code], or null when unknown. RUB itself
    // has no ID (it's the API's implicit quote currency) — signal that with
    // a sentinel string that only [timelineFor] recognises.
    private fun resolveCurrencyId(
        code: String,
        ids: Map<String, String>,
    ): String? = if (code == RUB_CODE) RUB_CODE else ids.entries.find { it.value == code }?.key

    // Picks either the synthetic RUB timeline (1:1) or hits the API for the
    // real currency series.
    private suspend fun timelineFor(
        context: Context?,
        code: String,
        startDate: LocalDate,
        endDate: LocalDate,
        id: String,
        ids: Map<String, String>,
        rubTimeline: Timeline,
    ): Result<Timeline> =
        if (code == RUB_CODE) {
            Result.success(rubTimeline)
        } else {
            fetchCurrencyTimeline(context, startDate, endDate, id, ids)
        }

    private fun buildRubTimeline(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Timeline {
        val rubRate = Rate(Currency.RUB, BigDecimal.ONE)
        val rubMap = dateSequence(startDate, endDate).associateWith { rubRate }
        return Timeline(
            success = true,
            error = null,
            base = RUB_CODE,
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
                "?date_req1=${startDate.format(URL_DATE)}" +
                "&date_req2=${endDate.format(URL_DATE)}" +
                "&VAL_NM_RQ=$currencyId",
        ) { body ->
            BankRossiiTimelineXmlParser(ids).parse(body.byteStream())
        }
}
