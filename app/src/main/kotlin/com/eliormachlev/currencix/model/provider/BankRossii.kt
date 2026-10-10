package com.eliormachlev.currencix.model.provider

import android.content.Context
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.ApiSecrets
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

class BankRossii : ApiProvider.Api {
    override val name = "Bank Rossii"
    override val nameRes = R.string.api_bankRossii_name

    override fun descriptionShort(context: Context) = context.getText(R.string.api_bankRossii_descriptionShort)

    override fun getDescriptionLong(context: Context) = context.getText(R.string.api_bankRossii_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) = context.getText(R.string.api_bankRossii_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) = null

    override val baseUrl = "https://www.cbr.ru/scripts"

    override suspend fun getRates(
        context: Context?,
        date: LocalDate?,
        secrets: ApiSecrets,
    ): Result<ExchangeRates> {
        val dateQuery = date?.let { "?date_req=${it.format(URL_DATE)}" }.orEmpty()
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
    ): Result<Timeline> =
        fetchCurrencyIds(context)
            // Both legs are resolved before either series is fetched.
            .mapCatching { ids -> TimelineQuery(context, startDate, endDate, ids, legOf(base, ids), legOf(symbol, ids)) }
            .flatMap { query ->
                timelineFor(query, query.base).flatMap { baseTimeline ->
                    timelineFor(query, query.symbol).mapCatching { symbolTimeline -> symbolTimeline.pricedIn(baseTimeline) }
                }
            }

    // One side of the pair: its API code and the bank's ID for it. RUB has
    // no ID (it's the API's implicit quote currency), so its code stands in.
    private fun legOf(
        currency: Currency,
        ids: Map<String, String>,
    ): Leg {
        val code = currency.apiCodeOrDkkForFok()
        val id = if (code == RUB_CODE) RUB_CODE else ids.entries.find { it.value == code }?.key
        return Leg(code, checkNotNull(id) { "No currency ID found for: $code" })
    }

    // The synthetic RUB series (1:1), or the bank's series for a real currency.
    private suspend fun timelineFor(
        query: TimelineQuery,
        leg: Leg,
    ): Result<Timeline> =
        if (leg.code == RUB_CODE) {
            Result.success(buildRubTimeline(query.startDate, query.endDate))
        } else {
            fetchCurrencyTimeline(query, leg.id)
        }

    // This series (the symbol's, in RUB) priced in [base]'s, on the days both have.
    private fun Timeline.pricedIn(base: Timeline): Timeline {
        val baseRates = base.rates
        val symbolRates = rates
        check(baseRates != null && symbolRates != null) { "Timeline data unavailable for base or symbol currency" }
        return copy(
            rates =
                symbolRates
                    .mapNotNull { (date, rate) ->
                        baseRates[date]?.let { date to rate.copy(value = rate.value.divide(it.value, MathContext.DECIMAL128)) }
                    }.toMap(),
        )
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
        query: TimelineQuery,
        currencyId: String,
    ): Result<Timeline> =
        HttpClientProvider.fetch(
            query.context,
            "$baseUrl/XML_dynamic.asp" +
                "?date_req1=${query.startDate.format(URL_DATE)}" +
                "&date_req2=${query.endDate.format(URL_DATE)}" +
                "&VAL_NM_RQ=$currencyId",
        ) { body ->
            BankRossiiTimelineXmlParser(query.ids).parse(body.byteStream())
        }
}

// One side of a timeline's pair: its API code and the bank's ID for it.
private class Leg(
    val code: String,
    val id: String,
)

// What a timeline request carries to each of its fetches.
private class TimelineQuery(
    val context: Context?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val ids: Map<String, String>,
    val base: Leg,
    val symbol: Leg,
)
