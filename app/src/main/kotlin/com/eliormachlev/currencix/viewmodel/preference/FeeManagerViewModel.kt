package com.eliormachlev.currencix.viewmodel.preference

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.repository.Database

/**
 * Thin ViewModel over [Database] for the Compose fee-manager screen. All fee
 * state lives in SharedPreferences already; this exists so the composable can
 * observe LiveData without wiring the DB directly and can survive rotation
 * without re-reading through the DB constructor each recomposition.
 */
class FeeManagerViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val db = Database(app)

    fun getFees(): LiveData<List<Fee>> = db.getFees()

    fun getActiveExchangeId(): LiveData<String?> = db.getActiveExchangeId()

    fun getActiveBankId(): LiveData<String?> = db.getActiveBankId()

    fun setActiveExchangeId(id: String) = db.setActiveExchangeId(id)

    fun setActiveBankId(id: String) = db.setActiveBankId(id)

    fun addFee(fee: Fee) = db.addFee(fee)

    fun updateFee(fee: Fee) = db.updateFee(fee)

    fun deleteFee(id: String) = db.deleteFee(id)
}
