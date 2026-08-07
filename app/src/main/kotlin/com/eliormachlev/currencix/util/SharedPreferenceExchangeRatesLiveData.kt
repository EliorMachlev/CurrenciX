package com.eliormachlev.currencix.util

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

// SharedPreferences file names — these are the on-disk XML basenames that
// `getSharedPreferences(...)` maps to `/data/data/<pkg>/shared_prefs/<name>.xml`.
// Hoisted here so Database (writer), CurrencyWidget (reader), and the LiveData
// wrapper below all agree; a rename in one place used to silently break the
// widget without touching Database.
internal const val PREFS_RATES = "rates"
internal const val PREFS_TIMELINES = "timelines"
internal const val PREFS_LAST_STATE = "last_state"
internal const val PREFS_STARRED_CURRENCIES = "starred_currencies"
internal const val PREFS_APP = "prefs"

// SharedPreferences keys for the cached "rates" bucket. Shared with Database so
// writer and reader agree on the schema. The leading underscore separates
// metadata keys from currency-code entries (e.g. "USD", "EUR").
internal const val KEY_RATES_BASE = "_base"
internal const val KEY_RATES_DATE = "_date"
internal const val KEY_RATES_TIME = "_time"
internal const val KEY_RATES_PROVIDER = "_provider"

// Sentinel for "no API provider stored yet"; ApiProvider.fromId maps it to the
// default provider. Kept as -1 to match previously persisted values.
internal const val NO_PROVIDER_ID = -1

private const val METADATA_KEY_PREFIX = "_"

// insertExchangeRates() writes ~180 currency keys in a single edit(); the
// OnSharedPreferenceChangeListener still fires once per key. Debouncing the
// recompute collapses that burst into a single O(n) pass instead of O(n²).
// 50 ms is well below any human-perceptible refresh latency and long enough
// to cover the whole apply() cycle even on slow devices.
private const val DEBOUNCE_MS = 50L

class SharedPreferenceExchangeRatesLiveData(
    private val sharedPrefs: SharedPreferences,
) : LiveData<ExchangeRates?>() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recomputeRunnable = Runnable { postValue(getValueFromPreferences()) }

    private fun getValueFromPreferences(): ExchangeRates? {
        // Capture the metadata strings once — the guard-then-read pattern in
        // the previous version risked a concurrent clear() nulling them out
        // between the guard and the `!!` deref inside the constructor call.
        val baseString = sharedPrefs.getString(KEY_RATES_BASE, null) ?: return null
        val dateString = sharedPrefs.getString(KEY_RATES_DATE, null) ?: return null
        // values were previously stored as Float; skip stale entries and return null if all are stale
        val rates =
            sharedPrefs.all.entries
                .filter { !it.key.startsWith(METADATA_KEY_PREFIX) }
                .sortedBy { it.key }
                .mapNotNull { entry ->
                    val str = entry.value as? String ?: return@mapNotNull null
                    Currency.fromString(entry.key!!)?.let { Rate(it, BigDecimal(str)) }
                }
        if (rates.isEmpty()) return null
        return ExchangeRates(
            success = true, // success always true, when serving cached data
            error = null, // error message always null, when serving cached data
            base = Currency.fromString(baseString),
            date = LocalDate.parse(dateString),
            time = sharedPrefs.getString(KEY_RATES_TIME, null)?.let { LocalTime.parse(it) },
            rates = rates,
            provider = sharedPrefs.getInt(KEY_RATES_PROVIDER, NO_PROVIDER_ID).let { ApiProvider.fromId(it) },
        )
    }

    private val preferenceChangeListener =
        OnSharedPreferenceChangeListener { _: SharedPreferences?, _: String? ->
            mainHandler.removeCallbacks(recomputeRunnable)
            mainHandler.postDelayed(recomputeRunnable, DEBOUNCE_MS)
        }

    override fun onActive() {
        super.onActive()
        postValue(getValueFromPreferences())
        sharedPrefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onInactive() {
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        mainHandler.removeCallbacks(recomputeRunnable)
        super.onInactive()
    }
}
