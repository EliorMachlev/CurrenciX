package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.model.AlertDirection
import com.eliormachlev.currencix.model.RateAlert
import com.eliormachlev.currencix.repository.json.JsonObject
import com.eliormachlev.currencix.repository.json.buildJsonObject

// A rate alert as JSON, in backups. On the phone the alerts are Protobuf (SavedProtos.kt).
internal const val ALERT_KEY_ID = "id"
internal const val ALERT_KEY_FROM = "from"
internal const val ALERT_KEY_TO = "to"
internal const val ALERT_KEY_THRESHOLD = "threshold"
internal const val ALERT_KEY_DIRECTION = "direction"

internal fun serializeRateAlert(alert: RateAlert): Map<String, Any> =
    buildJsonObject {
        put(ALERT_KEY_ID, alert.id)
        put(ALERT_KEY_FROM, alert.pair.from.iso4217Alpha())
        put(ALERT_KEY_TO, alert.pair.to.iso4217Alpha())
        // A string, so no precision is lost to a JSON double.
        put(ALERT_KEY_THRESHOLD, alert.threshold.toPlainString())
        put(ALERT_KEY_DIRECTION, alert.direction.key)
    }

// An entry with an unknown currency or direction, or no threshold, is skipped.
internal fun parseRateAlert(obj: JsonObject): RateAlert? =
    rateAlertOf(
        id = obj.optString(ALERT_KEY_ID),
        fromCode = obj.optString(ALERT_KEY_FROM),
        toCode = obj.optString(ALERT_KEY_TO),
        threshold = obj.optString(ALERT_KEY_THRESHOLD),
        direction = AlertDirection.fromKey(obj.optString(ALERT_KEY_DIRECTION)),
    )
