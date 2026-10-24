package com.eliormachlev.currencix.repository

import android.content.Context
import com.eliormachlev.currencix.proto.CartsData
import com.eliormachlev.currencix.proto.FeesData
import com.eliormachlev.currencix.repository.json.JsonObject
import com.eliormachlev.currencix.repository.json.buildJsonObject
import com.eliormachlev.currencix.repository.persistence.CachedStore
import com.eliormachlev.currencix.repository.persistence.cartsStore
import com.eliormachlev.currencix.repository.persistence.feesStore

// The saved carts, trips and fees in a backup. On the phone they're Protobuf;
// in the backup they're JSON, next to the preference namespaces (in the same
// encrypted block when there's a password), so a plain backup stays readable.
//
//   "carts": {"current": {cart}, "saved": [{cart}, …], "trips": [{trip}, …]}
//   "fees":  {"fees": [{fee}, …]}
//
// Older readers skip these keys; a backup without them leaves the data as it is.

private const val CARTS_KEY_CURRENT = "current"
private const val CARTS_KEY_SAVED = "saved"
private const val CARTS_KEY_TRIPS = "trips"
private const val FEES_KEY_FEES = "fees"

/** One Protobuf file in a backup: its key there, its store, and its JSON both ways. */
internal class SavedDataSection<M>(
    val key: String,
    private val store: (Context) -> CachedStore<M>,
    private val toJson: (M) -> Map<String, Any>,
    private val fromJson: (JsonObject) -> M,
) {
    fun export(context: Context): Map<String, Any> = toJson(store(context).snapshot())

    /** Replaces the file with the backup's [section]; returns once it's on disk. */
    suspend fun restore(
        context: Context,
        section: JsonObject,
    ) {
        val data = fromJson(section)
        store(context).updateAndAwait { data }
    }
}

internal val SAVED_DATA_SECTIONS: List<SavedDataSection<*>> =
    listOf(
        SavedDataSection("carts", Context::cartsStore, ::cartsToJson, ::cartsFromJson),
        SavedDataSection("fees", Context::feesStore, ::feesToJson, ::feesFromJson),
    )

private fun cartsToJson(data: CartsData): Map<String, Any> =
    buildJsonObject {
        data.current?.let { put(CARTS_KEY_CURRENT, serializeCart(it.toModel())) }
        put(CARTS_KEY_SAVED, data.saved.map { serializeCart(it.toModel()) })
        put(CARTS_KEY_TRIPS, data.trips.mapNotNull { it.toModel() }.map(::serializeTrip))
    }

private fun cartsFromJson(obj: JsonObject): CartsData =
    CartsData(
        current = parseCart(obj.optObject(CARTS_KEY_CURRENT))?.toProto(),
        saved = obj.optObjects(CARTS_KEY_SAVED).mapNotNull(::parseCart).map { it.toProto() },
        trips = obj.optObjects(CARTS_KEY_TRIPS).mapNotNull(::parseTrip).map { it.toProto() },
    )

private fun feesToJson(data: FeesData): Map<String, Any> = mapOf(FEES_KEY_FEES to data.fees.mapNotNull { it.toModel() }.map(::serializeFee))

private fun feesFromJson(obj: JsonObject): FeesData =
    FeesData(
        fees =
            obj.optObjects(FEES_KEY_FEES).mapNotNull(::parseFee).map {
                it.toProto()
            },
    )
