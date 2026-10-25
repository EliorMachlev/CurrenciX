package com.eliormachlev.currencix.repository

import android.content.Context
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.RateAlert
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.alertsStore
import com.eliormachlev.currencix.repository.persistence.fieldPref
import java.math.BigDecimal

/** The rate alerts waiting to fire (Protobuf, in alerts.pb; backed up like the fees). */
class RateAlertStore(
    context: Context,
) {
    val alerts: Pref<List<RateAlert>> =
        context.alertsStore().fieldPref(
            field = { it.alerts },
            decode = { list -> list.mapNotNull { it.toModel() } },
            write = { data, list -> data.copy(alerts = list.map { it.toProto() }) },
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
