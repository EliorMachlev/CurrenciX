package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.FeeType
import org.json.JSONObject

// Wire keys for the saved fees, a JSON array in one preference (and in
// backups, which copy preferences as they are).
private const val TAG = "FeeJson"
private const val FEE_KEY_ID = "id"
private const val FEE_KEY_NAME = "name"
private const val FEE_KEY_PERCENT = "percent"
private const val FEE_KEY_ACTIVE = "isActive"
private const val FEE_KEY_TYPE = "type"
private const val FEE_KEY_FROM = "from"
private const val FEE_KEY_TO = "to"
private const val FEE_KEY_BOTH_WAYS = "bothWays"
private const val NO_PERCENT = "0"

internal fun serializeFeeList(list: List<Fee>): String = list.toJsonArray(::serializeFee).toString()

internal fun parseFeeList(json: String?): List<Fee> = parseJsonList(json, TAG, "fee", ::parseFee)

private fun serializeFee(fee: Fee): JSONObject =
    JSONObject().apply {
        put(FEE_KEY_ID, fee.id)
        put(FEE_KEY_NAME, fee.name)
        put(FEE_KEY_PERCENT, fee.percent.toPlainString())
        put(FEE_KEY_ACTIVE, fee.isActive)
        put(FEE_KEY_TYPE, fee.type.wire)
        if (fee is Fee.SpecificPair) {
            put(FEE_KEY_FROM, fee.from)
            put(FEE_KEY_TO, fee.to)
            put(FEE_KEY_BOTH_WAYS, fee.bothWays)
        }
    }

// Null for an entry that can't be a fee (no valid percent, unknown type).
private fun parseFee(obj: JSONObject): Fee? {
    val id = obj.optIdOrNew(FEE_KEY_ID)
    val name = obj.optString(FEE_KEY_NAME, "")
    val percent = obj.optString(FEE_KEY_PERCENT, NO_PERCENT).toBigDecimalOrNull() ?: return null
    val isActive = obj.optBoolean(FEE_KEY_ACTIVE, true)
    return when (FeeType.fromWire(obj.optString(FEE_KEY_TYPE))) {
        FeeType.GLOBAL_EXCHANGE -> {
            Fee.GlobalExchange(id, name, percent, isActive)
        }

        FeeType.GLOBAL_BANK -> {
            Fee.GlobalBank(id, name, percent, isActive)
        }

        FeeType.SPECIFIC_PAIR -> {
            Fee.SpecificPair(
                id = id,
                name = name,
                percent = percent,
                from = obj.optString(FEE_KEY_FROM, ""),
                to = obj.optString(FEE_KEY_TO, ""),
                bothWays = obj.optBoolean(FEE_KEY_BOTH_WAYS, false),
                isActive = isActive,
            )
        }

        null -> {
            null
        }
    }
}
