package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.FeeType
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.prefStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID

private const val KEY_FEES_JSON = "_fees_json"
private const val KEY_ACTIVE_EXCHANGE_ID = "_active_exchange_id"
private const val KEY_ACTIVE_BANK_ID = "_active_bank_id"

private val feesMapper: (Preferences) -> ImmutableList<Fee> = {
    parseFeeList(it[stringPreferencesKey(KEY_FEES_JSON)] ?: "[]").toImmutableList()
}

private val activeExchangeIdMapper: (Preferences) -> String? = {
    it[stringPreferencesKey(KEY_ACTIVE_EXCHANGE_ID)]
}

private val activeBankIdMapper: (Preferences) -> String? = {
    it[stringPreferencesKey(KEY_ACTIVE_BANK_ID)]
}

// Fees JSON parsing lives at file scope so the mapper above (which is invoked
// by both LiveData and Flow getters) can share it with the blocking accessor
// and the fee mutators without threading a Database instance through.
private fun parseFeeList(json: String): List<Fee> =
    try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i -> parseFeeEntry(arr.optJSONObject(i)) }
    } catch (e: JSONException) {
        Timber.tag("Database").w(e, "Malformed fee JSON, resetting")
        emptyList()
    }

private fun parseFeeEntry(obj: JSONObject?): Fee? {
    obj ?: return null
    val id = obj.optString("id", "").ifEmpty { UUID.randomUUID().toString() }
    val name = obj.optString("name", "")
    val percent = obj.optString("percent", "0").toBigDecimalOrNull() ?: return null
    val isActive = obj.optBoolean("isActive", true)
    return when (FeeType.fromWire(obj.optString("type"))) {
        FeeType.GLOBAL_EXCHANGE -> Fee.GlobalExchange(id, name, percent, isActive)
        FeeType.GLOBAL_BANK -> Fee.GlobalBank(id, name, percent, isActive)
        FeeType.SPECIFIC_PAIR ->
            Fee.SpecificPair(
                id = id,
                name = name,
                percent = percent,
                from = obj.optString("from", ""),
                to = obj.optString("to", ""),
                bothWays = obj.optBoolean("bothWays", false),
                isActive = isActive,
            )
        null -> null
    }
}

/** The saved fees, and which exchange / bank fee is the one in force. */
class FeeStore(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    // fees

    // ImmutableList so Compose stability inference can skip recomposition
    // when the collection identity changes but the content is equal (#161).
    fun getFees(): LiveData<ImmutableList<Fee>> = store.mappedLiveData(feesMapper)

    fun getFeesFlow(): Flow<ImmutableList<Fee>> = store.mappedFlow(feesMapper)

    fun getFeesBlocking(): ImmutableList<Fee> = feesMapper(store.snapshot())

    fun addFee(fee: Fee) {
        writeFees(getFeesBlocking() + fee)
    }

    fun updateFee(fee: Fee) {
        writeFees(getFeesBlocking().map { if (it.id == fee.id) fee else it })
    }

    fun deleteFee(id: String) {
        writeFees(getFeesBlocking().filter { it.id != id })
    }

    // Active-picker IDs — which single named exchange / bank-or-card entry
    // participates in the fee stack. `null` means "no explicit pick"; the
    // FeeCalculator falls back to the first active entry of that category.

    fun getActiveExchangeId(): LiveData<String?> = store.mappedLiveData(activeExchangeIdMapper)

    fun getActiveExchangeIdFlow(): Flow<String?> = store.mappedFlow(activeExchangeIdMapper)

    fun getActiveExchangeIdBlocking(): String? = activeExchangeIdMapper(store.snapshot())

    fun setActiveExchangeId(id: String?) {
        store.edit {
            if (id == null) {
                remove(stringPreferencesKey(KEY_ACTIVE_EXCHANGE_ID))
            } else {
                this[stringPreferencesKey(KEY_ACTIVE_EXCHANGE_ID)] = id
            }
        }
    }

    fun getActiveBankId(): LiveData<String?> = store.mappedLiveData(activeBankIdMapper)

    fun getActiveBankIdFlow(): Flow<String?> = store.mappedFlow(activeBankIdMapper)

    fun getActiveBankIdBlocking(): String? = activeBankIdMapper(store.snapshot())

    fun setActiveBankId(id: String?) {
        store.edit {
            if (id == null) {
                remove(stringPreferencesKey(KEY_ACTIVE_BANK_ID))
            } else {
                this[stringPreferencesKey(KEY_ACTIVE_BANK_ID)] = id
            }
        }
    }

    private fun writeFees(list: List<Fee>) {
        store.edit { this[stringPreferencesKey(KEY_FEES_JSON)] = serializeFeeList(list) }
    }

    private fun serializeFeeList(list: List<Fee>): String {
        val arr = JSONArray()
        list.forEach { fee ->
            val obj = JSONObject()
            obj.put("id", fee.id)
            obj.put("name", fee.name)
            obj.put("percent", fee.percent.toPlainString())
            obj.put("isActive", fee.isActive)
            obj.put("type", fee.type.wire)
            if (fee is Fee.SpecificPair) {
                obj.put("from", fee.from)
                obj.put("to", fee.to)
                obj.put("bothWays", fee.bothWays)
            }
            arr.put(obj)
        }
        return arr.toString()
    }
}
