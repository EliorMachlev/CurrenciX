package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.OffsetDateTime

private const val FIELD_EXCHANGE_RATES = "exchangeRates"
private const val FIELD_KEY = "key"
private const val FIELD_CURRENT_RATE = "currentExchangeRate"
private const val FIELD_UNIT = "unit"
private const val FIELD_LAST_UPDATE = "lastUpdate"

/**
 * Bank of Israel's PublicApi: `{exchangeRates: [{key, currentExchangeRate, unit, lastUpdate}, …]}`,
 * shekels per [unit] of each currency, dated by the newest update.
 */
internal class BankOfIsraelRatesAdapter : ResponseAdapter<ExchangeRates> {
    override fun decode(json: JsonElement): ExchangeRates? {
        val response = json as? JsonObject ?: return null
        val rates = mutableListOf<Rate>()
        val date = response[FIELD_EXCHANGE_RATES]?.let { readEntries(it, rates) }

        if (rates.isNotEmpty()) {
            rates.add(Rate(Currency.ILS, BigDecimal.ONE))
            rates.addFokFromDkkIfMissing()
        }

        return ExchangeRates(
            success = rates.isNotEmpty(),
            error = if (rates.isEmpty()) NO_DATA_ERROR else null,
            base = Currency.ILS,
            date = date,
            rates = rates,
            provider = ApiProvider.BANK_OF_ISRAEL,
        )
    }

    // Adds each entry's rate, and returns the newest update's date.
    private fun readEntries(
        entries: JsonElement,
        rates: MutableList<Rate>,
    ): LocalDate? =
        entries.jsonArray
            .map(::readEntry)
            .onEach { (rate, _) -> rate?.let(rates::add) }
            .mapNotNull { (_, date) -> date }
            .maxOrNull()

    private fun readEntry(entry: JsonElement): Pair<Rate?, LocalDate?> {
        val fields = entry.jsonObject
        val currency = fields[FIELD_KEY]?.let { Currency.fromString(it.text()) }
        val value = fields[FIELD_CURRENT_RATE]?.decimal()
        val unit = fields[FIELD_UNIT]?.decimal() ?: BigDecimal.ONE
        val date = fields[FIELD_LAST_UPDATE]?.let { OffsetDateTime.parse(it.text()).toLocalDate() }
        val rate =
            if (currency != null && value != null && value.signum() > 0) {
                Rate(currency, unit.divide(value, MathContext.DECIMAL128))
            } else {
                null
            }
        return rate to date
    }
}
