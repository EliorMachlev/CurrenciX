package com.eliormachlev.currencix.model.adapter

import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

// One currency: Bank Rossii's internal id for it, and its ISO 4217 code.
private const val ITEM_TAG = "Item"
private const val ITEM_ID = "ID"
private const val ISO_CODE_TAG = "ISO_Char_Code"

class BankRossiiCurrencyCodesXmlParser {
    private val items = mutableMapOf<String, String>()

    fun parse(inputStream: InputStream): MutableMap<String, String> {
        val parser = newXmlPullParser(inputStream)

        var tagname: String? = null
        var eventType = parser.eventType
        var id: String? = null
        var iso4217Alpha: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            tagname = parser.name ?: tagname
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagname == ITEM_TAG) {
                        id = parser.getAttributeValue(null, ITEM_ID)
                    }
                }

                XmlPullParser.TEXT -> {
                    if (tagname == ISO_CODE_TAG) {
                        iso4217Alpha = parser.text
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (tagname == ITEM_TAG) {
                        recordItem(id, iso4217Alpha)
                        id = null
                        iso4217Alpha = null
                    }
                }
            }
            eventType = parser.next()
        }

        return items
    }

    private fun recordItem(
        id: String?,
        iso4217Alpha: String?,
    ) {
        if (id != null && iso4217Alpha != null) {
            items[id] = iso4217Alpha
        }
    }
}
