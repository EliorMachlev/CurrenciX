package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.WidgetRefreshBus
import com.eliormachlev.currencix.repository.persistence.feesStore
import com.eliormachlev.currencix.repository.persistence.fieldPref
import com.eliormachlev.currencix.repository.persistence.keyedPref
import com.eliormachlev.currencix.repository.persistence.prefStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

private const val KEY_ACTIVE_EXCHANGE_ID = "_active_exchange_id"
private const val KEY_ACTIVE_BANK_ID = "_active_bank_id"

/**
 * The saved fees (Protobuf, in fees.pb), and which exchange / bank fee is the
 * one in force. The widget applies them, so it redraws when any of them changes.
 */
class FeeStore(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    // ImmutableList so Compose stability inference can skip recomposition
    // when the collection identity changes but the content is equal (#161).
    val fees: Pref<ImmutableList<Fee>> =
        context.feesStore().fieldPref(
            field = { it.fees },
            decode = { list -> list.mapNotNull { it.toModel() }.toImmutableList() },
            write = { data, list -> data.copy(fees = list.map { it.toProto() }) },
            afterWrite = WidgetRefreshBus::signal,
        )

    // Which single named exchange / bank-or-card entry is in the fee stack.
    // Null means "no explicit pick": the FeeCalculator falls back to the
    // first active entry of that category.
    val activeExchangeId: Pref<String?> = activeIdPref(KEY_ACTIVE_EXCHANGE_ID)
    val activeBankId: Pref<String?> = activeIdPref(KEY_ACTIVE_BANK_ID)

    // A string with no default (null when unset, and setting null removes it).
    private fun activeIdPref(name: String): Pref<String?> =
        store.keyedPref(stringPreferencesKey(name), { it }, { it }, afterWrite = WidgetRefreshBus::signal)

    fun addFee(fee: Fee) = fees.update { (it + fee).toImmutableList() }

    fun updateFee(fee: Fee) = fees.update { list -> list.map { if (it.id == fee.id) fee else it }.toImmutableList() }

    fun deleteFee(id: String) = fees.update { list -> list.filterNot { it.id == id }.toImmutableList() }
}
