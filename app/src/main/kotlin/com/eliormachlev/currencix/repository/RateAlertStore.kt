package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eliormachlev.currencix.model.AlertDirection
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.RateAlert
import com.eliormachlev.currencix.repository.json.buildJsonObject
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.keyedPref
import com.eliormachlev.currencix.repository.persistence.prefStore
import java.math.BigDecimal

private const val TAG = "RateAlertStore"
private const val KEY_RATE_ALERTS = "_rate_alerts"

internal const val ALERT_KEY_ID = "id"
internal const val ALERT_KEY_FROM = "from"
internal const val ALERT_KEY_TO = "to"
internal const val ALERT_KEY_THRESHOLD = "threshold"
internal const val ALERT_KEY_DIRECTION = "direction"

/** The rate alerts waiting to fire (in the backed-up settings, like the fees). */
class RateAlertStore(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    val alerts: Pref<List<RateAlert>> =
        store.keyedPref(
            stringPreferencesKey(KEY_RATE_ALERTS),
            decode = ::parseRateAlerts,
            encode = { list -> list.takeIf { it.isNotEmpty() }?.let(::serializeRateAlerts) },
        )

    fun add(alert: RateAlert) = alerts.update { it + alert }

    fun remove(id: String) = alerts.update { list -> list.filterNot { it.id == id } }

    /**
     * Takes the alerts that [rateOf] says have been reached out of the store,
     * in one edit, and returns them: each fires once, even when two checks
     * run at the same time.
     */
    fun takeReached(rateOf: (CurrencyPair) -> BigDecimal?): List<RateAlert> {
        var reached = emptyList<RateAlert>()
        alerts.update { list ->
            reached = list.filter { alert -> rateOf(alert.pair)?.let(alert::isReachedBy) == true }
            list - reached.toSet()
        }
        return reached
    }
}

internal fun serializeRateAlerts(list: List<RateAlert>): String =
    list.toJsonArrayText { alert ->
        buildJsonObject {
            put(ALERT_KEY_ID, alert.id)
            put(ALERT_KEY_FROM, alert.pair.from.iso4217Alpha())
            put(ALERT_KEY_TO, alert.pair.to.iso4217Alpha())
            // A string, so no precision is lost to a JSON double.
            put(ALERT_KEY_THRESHOLD, alert.threshold.toPlainString())
            put(ALERT_KEY_DIRECTION, alert.direction.key)
        }
    }

// An entry with an unknown currency or direction, or no threshold, is skipped.
internal fun parseRateAlerts(json: String?): List<RateAlert> =
    parseJsonList(json, TAG, "rate-alert") { obj ->
        val from = Currency.fromString(obj.optString(ALERT_KEY_FROM)) ?: return@parseJsonList null
        val to = Currency.fromString(obj.optString(ALERT_KEY_TO)) ?: return@parseJsonList null
        val threshold = obj.optString(ALERT_KEY_THRESHOLD).toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return@parseJsonList null
        val direction = AlertDirection.fromKey(obj.optString(ALERT_KEY_DIRECTION)) ?: return@parseJsonList null
        RateAlert(obj.optIdOrNew(ALERT_KEY_ID), CurrencyPair(from, to), threshold, direction)
    }
