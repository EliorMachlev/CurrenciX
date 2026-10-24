package com.eliormachlev.currencix.repository.persistence

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.eliormachlev.currencix.proto.CartsData
import com.eliormachlev.currencix.proto.FeesData
import com.eliormachlev.currencix.proto.RateAlertsData
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

// What the user saves (carts and trips, fees, rate alerts), one typed Protobuf DataStore file per kind
// (files/datastore/<name>.pb), with messages from src/main/proto. Kept out of
// the preference files: a cart is a tree of values, not a setting.

private const val TAG = "SavedDataFile"

/** A DataStore [Serializer] for a Wire message: its [adapter] reads and writes the bytes. */
internal class WireSerializer<M : Message<M, *>>(
    private val adapter: ProtoAdapter<M>,
    override val defaultValue: M,
) : Serializer<M> {
    override suspend fun readFrom(input: InputStream): M =
        try {
            adapter.decode(input)
        } catch (e: IOException) {
            throw CorruptionException("Unreadable ${adapter.type?.simpleName}", e)
        }

    override suspend fun writeTo(
        t: M,
        output: OutputStream,
    ) = adapter.encode(output, t)
}

// A file that can't be read starts over empty rather than failing every read.
private fun <M : Message<M, *>> emptyOnCorruption(empty: M) =
    ReplaceFileCorruptionHandler { e ->
        Timber.tag(TAG).w(e, "Saved data unreadable, starting over")
        empty
    }

private val Context.cartsFile by dataStore(
    fileName = "carts.pb",
    serializer = WireSerializer(CartsData.ADAPTER, CartsData()),
    corruptionHandler = emptyOnCorruption(CartsData()),
)
private val Context.alertsFile by dataStore(
    fileName = "alerts.pb",
    serializer = WireSerializer(RateAlertsData.ADAPTER, RateAlertsData()),
    corruptionHandler = emptyOnCorruption(RateAlertsData()),
)
private val Context.feesFile by dataStore(
    fileName = "fees.pb",
    serializer = WireSerializer(FeesData.ADAPTER, FeesData()),
    corruptionHandler = emptyOnCorruption(FeesData()),
)

// One store per file per process, like the preference stores: each owns a
// write pump and a disk collector.
private class StoreHolder<M>(
    private val open: (Context) -> DataStore<M>,
) {
    @Volatile private var store: CachedStore<M>? = null

    fun get(context: Context): CachedStore<M> =
        store ?: synchronized(this) {
            store ?: CachedStore(open(context.applicationContext)).also { store = it }
        }
}

private val carts = StoreHolder { it.cartsFile }
private val fees = StoreHolder { it.feesFile }
private val alerts = StoreHolder { it.alertsFile }

/** The working cart, the saved carts and the trips. */
internal fun Context.cartsStore(): CachedStore<CartsData> = carts.get(this)

/** The saved fees. */
internal fun Context.feesStore(): CachedStore<FeesData> = fees.get(this)

/** The rate alerts waiting to fire. */
internal fun Context.alertsStore(): CachedStore<RateAlertsData> = alerts.get(this)

/** Reads each saved-data file once, so a screen's first read doesn't wait on the disk (call off the main thread). */
internal fun Context.warmUpSavedData() {
    cartsStore().snapshot()
    feesStore().snapshot()
    alertsStore().snapshot()
}
