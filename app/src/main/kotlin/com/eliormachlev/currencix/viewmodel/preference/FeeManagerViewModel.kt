package com.eliormachlev.currencix.viewmodel.preference

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.viewmodel.util.stateInWhileSubscribed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin ViewModel over [Database] for the Compose fee-manager screen. All fee
 * state lives in DataStore already; this exists so the composable can
 * subscribe via StateFlow without wiring the DB directly and can survive
 * rotation without re-reading through the DB constructor each recomposition.
 *
 * Migrated off LiveData in #149: exposes `StateFlow<T>` fields seeded from
 * blocking snapshot reads via the shared [stateInWhileSubscribed] helper so
 * Compose can consume them with `collectAsStateWithLifecycle` and imperative
 * callers (e.g. adopting the first-created global fee as the active one) can
 * read the initial value synchronously via `.value`.
 */
class FeeManagerViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val db = Database(app)

    val fees: StateFlow<ImmutableList<Fee>> =
        db.getFeesFlow().stateInWhileSubscribed(viewModelScope, db.getFeesBlocking())
    val activeExchangeId: StateFlow<String?> =
        db.getActiveExchangeIdFlow().stateInWhileSubscribed(viewModelScope, db.getActiveExchangeIdBlocking())
    val activeBankId: StateFlow<String?> =
        db.getActiveBankIdFlow().stateInWhileSubscribed(viewModelScope, db.getActiveBankIdBlocking())

    fun setActiveExchangeId(id: String) = db.setActiveExchangeId(id)

    fun setActiveBankId(id: String) = db.setActiveBankId(id)

    fun addFee(fee: Fee) = db.addFee(fee)

    fun updateFee(fee: Fee) = db.updateFee(fee)

    fun deleteFee(id: String) = db.deleteFee(id)
}
