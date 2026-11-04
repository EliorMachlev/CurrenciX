package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate

// One day's rate, and the internal id of its currency.
private const val RECORD_TAG = "Record"
private const val RECORD_CURRENCY_ID = "Id"

class BankRossiiTimelineXmlParser(
    private val ids: Map<String, String>,
) {
    private val rates = mutableMapOf<LocalDate, Rate>()

    fun parse(inputStream: InputStream): Timeline {
        val parser = newXmlPullParser(inputStream)

        var tagname: String? = null
        var eventType = parser.eventType
        var date: LocalDate? = null
        var currencyId: String? = null
        var value: BigDecimal? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            tagname = parser.name ?: tagname
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagname == RECORD_TAG) {
                        date =
                            parser.bankRossiiDate()
                        currencyId = parser.getAttributeValue(null, RECORD_CURRENCY_ID)
                    }
                }

                XmlPullParser.TEXT -> {
                    if (tagname == BANK_ROSSII_RATE_TAG) {
                        value = bankRossiiRate(parser.text)
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (tagname == RECORD_TAG) {
                        recordRate(date, value, currencyId)
                        date = null
                        currencyId = null
                        value = null
                    }
                }
            }
            eventType = parser.next()
        }

        return Timeline(
            success = rates.isNotEmpty(),
            error = null,
            base =
                rates.values
                    .firstOrNull()
                    ?.currency
                    ?.iso4217Alpha(),
            startDate = rates.keys.firstOrNull(),
            endDate = rates.keys.lastOrNull(),
            rates = rates,
            provider = ApiProvider.BANK_ROSSII,
        )
    }

    private fun recordRate(
        date: LocalDate?,
        value: BigDecimal?,
        currencyId: String?,
    ) {
        val isoCode = currencyId?.let { ids[it] } ?: return
        if (date == null || value == null) return
        val currency = Currency.fromString(isoCode) ?: return
        rates[date] = Rate(currency, value)
    }
}
