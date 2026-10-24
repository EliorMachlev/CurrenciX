package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.repository.json.buildJsonObject

private const val TAG = "TripJson"

internal const val TRIP_KEY_ID = "id"
internal const val TRIP_KEY_NAME = "name"
internal const val TRIP_KEY_CURRENCY = "currency"
internal const val TRIP_KEY_BUDGET = "budget"

internal fun serializeTrips(list: List<Trip>): String =
    list.toJsonArrayText { trip ->
        buildJsonObject {
            put(TRIP_KEY_ID, trip.id)
            put(TRIP_KEY_NAME, trip.name)
            put(TRIP_KEY_CURRENCY, trip.currency.iso4217Alpha())
            // A string, so no precision is lost to a JSON double.
            trip.budget?.let { put(TRIP_KEY_BUDGET, it.toPlainString()) }
        }
    }

// A trip in a currency the app doesn't know is skipped; a bad budget reads as none.
internal fun parseTrips(json: String?): List<Trip> =
    parseJsonList(json, TAG, "trip") { obj ->
        val currency = Currency.fromString(obj.optString(TRIP_KEY_CURRENCY)) ?: return@parseJsonList null
        Trip(
            id = obj.optIdOrNew(TRIP_KEY_ID),
            name = obj.optString(TRIP_KEY_NAME, ""),
            currency = currency,
            budget = obj.optString(TRIP_KEY_BUDGET).toBigDecimalOrNull()?.takeIf { it.signum() > 0 },
        )
    }
