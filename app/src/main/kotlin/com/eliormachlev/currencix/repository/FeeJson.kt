package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.FeeType
import com.eliormachlev.currencix.repository.json.JsonObject
import com.eliormachlev.currencix.repository.json.buildJsonObject

// A fee as JSON, in backups. On the phone the fees are Protobuf (SavedProtos.kt).
private const val FEE_KEY_ID = "id"
private const val FEE_KEY_NAME = "name"
private const val FEE_KEY_PERCENT = "percent"
private const val FEE_KEY_ACTIVE = "isActive"
private const val FEE_KEY_TYPE = "type"
private const val FEE_KEY_FROM = "from"
private const val FEE_KEY_TO = "to"
private const val FEE_KEY_BOTH_WAYS = "bothWays"

internal fun serializeFee(fee: Fee): Map<String, Any> =
    buildJsonObject {
        val stored = fee.toStored()
        put(FEE_KEY_ID, stored.id)
        put(FEE_KEY_NAME, stored.name)
        put(FEE_KEY_PERCENT, stored.percent)
        put(FEE_KEY_ACTIVE, stored.isActive)
        put(FEE_KEY_TYPE, fee.type.wire)
        if (fee is Fee.SpecificPair) {
            put(FEE_KEY_FROM, stored.pair.from)
            put(FEE_KEY_TO, stored.pair.to)
            put(FEE_KEY_BOTH_WAYS, stored.pair.bothWays)
        }
    }

// Null for an entry that can't be a fee (no valid percent, unknown type).
internal fun parseFee(obj: JsonObject): Fee? =
    StoredFee(
        id = obj.optString(FEE_KEY_ID),
        name = obj.optString(FEE_KEY_NAME),
        percent = obj.optString(FEE_KEY_PERCENT),
        isActive = obj.optBoolean(FEE_KEY_ACTIVE, true),
        type = FeeType.fromWire(obj.optString(FEE_KEY_TYPE)),
        pair = StoredPair(obj.optString(FEE_KEY_FROM), obj.optString(FEE_KEY_TO), obj.optBoolean(FEE_KEY_BOTH_WAYS)),
    ).toFee()
