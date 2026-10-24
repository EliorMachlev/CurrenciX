package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.repository.json.JsonObject
import com.eliormachlev.currencix.repository.json.buildJsonObject
import com.eliormachlev.currencix.util.toLocalDateOrNull

// A trip as JSON, in backups. On the phone the trips are Protobuf (SavedProtos.kt).
internal const val TRIP_KEY_ID = "id"
internal const val TRIP_KEY_NAME = "name"
internal const val TRIP_KEY_CURRENCY = "currency"
internal const val TRIP_KEY_BUDGET = "budget"
internal const val TRIP_KEY_DESTINATION = "destination"
internal const val TRIP_KEY_START = "start"
internal const val TRIP_KEY_END = "end"

internal fun serializeTrip(trip: Trip): Map<String, Any> =
    buildJsonObject {
        put(TRIP_KEY_ID, trip.id)
        put(TRIP_KEY_NAME, trip.name)
        put(TRIP_KEY_CURRENCY, trip.currency.iso4217Alpha())
        // A string, so no precision is lost to a JSON double.
        trip.budget?.let { put(TRIP_KEY_BUDGET, it.toPlainString()) }
        trip.destination?.let { put(TRIP_KEY_DESTINATION, it.iso4217Alpha()) }
        // ISO dates ("2026-11-14").
        trip.start?.let { put(TRIP_KEY_START, it.toString()) }
        trip.end?.let { put(TRIP_KEY_END, it.toString()) }
    }

// A trip in a currency the app doesn't know is skipped; a bad budget reads as none.
internal fun parseTrip(obj: JsonObject): Trip? =
    tripOf(
        id = obj.optString(TRIP_KEY_ID),
        name = obj.optString(TRIP_KEY_NAME),
        currencyCode = obj.optString(TRIP_KEY_CURRENCY),
        budget = obj.optStringOrNull(TRIP_KEY_BUDGET),
    )?.withPlan(
        destinationCode = obj.optStringOrNull(TRIP_KEY_DESTINATION),
        start = obj.optStringOrNull(TRIP_KEY_START)?.toLocalDateOrNull(),
        end = obj.optStringOrNull(TRIP_KEY_END)?.toLocalDateOrNull(),
    )
