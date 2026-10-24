package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.repository.json.JsonObject
import com.eliormachlev.currencix.repository.json.buildJsonObject

// A trip as JSON, in backups. On the phone the trips are Protobuf (SavedProtos.kt).
internal const val TRIP_KEY_ID = "id"
internal const val TRIP_KEY_NAME = "name"
internal const val TRIP_KEY_CURRENCY = "currency"
internal const val TRIP_KEY_BUDGET = "budget"

internal fun serializeTrip(trip: Trip): Map<String, Any> =
    buildJsonObject {
        put(TRIP_KEY_ID, trip.id)
        put(TRIP_KEY_NAME, trip.name)
        put(TRIP_KEY_CURRENCY, trip.currency.iso4217Alpha())
        // A string, so no precision is lost to a JSON double.
        trip.budget?.let { put(TRIP_KEY_BUDGET, it.toPlainString()) }
    }

// A trip in a currency the app doesn't know is skipped; a bad budget reads as none.
internal fun parseTrip(obj: JsonObject): Trip? =
    tripOf(
        id = obj.optString(TRIP_KEY_ID),
        name = obj.optString(TRIP_KEY_NAME),
        currencyCode = obj.optString(TRIP_KEY_CURRENCY),
        budget = obj.optStringOrNull(TRIP_KEY_BUDGET),
    )
