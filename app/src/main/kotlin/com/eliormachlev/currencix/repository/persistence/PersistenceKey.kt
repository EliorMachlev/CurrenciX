package com.eliormachlev.currencix.repository.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The set of DataStore Preferences files the app owns. Each entry carries the
 * on-disk file name; DataStore materializes it as
 * `/data/data/<pkg>/files/datastore/<fileName>.preferences_pb`.
 *
 * File names deliberately match the historical SharedPreferences basenames so
 * that [com.eliormachlev.currencix.repository.BackupManager]'s namespace tags
 * (which are persisted inside exported backup files) round-trip unchanged
 * across the migration — an export produced before this change can still be
 * imported after, and vice versa.
 */
enum class PersistenceKey(
    val fileName: String,
) {
    RATES("rates"),
    TIMELINES("timelines"),
    LAST_STATE("last_state"),
    STARRED_CURRENCIES("starred_currencies"),
    APP("prefs"),
    ;

    fun dataStore(context: Context): DataStore<Preferences> = context.dataStoreFor(this)

    companion object {
        // Wire-format backup names — the subset serialized by BackupManager.
        // "rates" is intentionally excluded (network cache, regenerates).
        val backupNamespaces: List<PersistenceKey> = listOf(APP, LAST_STATE, STARRED_CURRENCIES)

        fun byFileName(name: String): PersistenceKey? = entries.firstOrNull { it.fileName == name }
    }
}

// One property delegate per PersistenceKey — `preferencesDataStore` requires a
// unique property per file name (it registers a process-wide singleton), so we
// enumerate them here and fan them out through [dataStoreFor].
private val Context.rates by preferencesDataStore(name = "rates")
private val Context.timelines by preferencesDataStore(name = "timelines")
private val Context.lastState by preferencesDataStore(name = "last_state")
private val Context.starredCurrencies by preferencesDataStore(name = "starred_currencies")
private val Context.appPrefs by preferencesDataStore(name = "prefs")

private fun Context.dataStoreFor(key: PersistenceKey): DataStore<Preferences> =
    when (key) {
        PersistenceKey.RATES -> rates
        PersistenceKey.TIMELINES -> timelines
        PersistenceKey.LAST_STATE -> lastState
        PersistenceKey.STARRED_CURRENCIES -> starredCurrencies
        PersistenceKey.APP -> appPrefs
    }
