package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.prefStore

private const val KEY_CHART_GRID = "_chartGrid"
private const val KEY_CHART_X_AXIS_LABEL = "_chartXAxisLabel"
private const val KEY_CHART_Y_AXIS_LABEL = "_chartYAxisLabel"
private const val KEY_CHART_HIGHLIGHT_EXTREMES = "_chartHighlightExtremes"
private const val KEY_CHART_HIGHLIGHT_PERIOD_CHANGE = "_chartHighlightPeriodChange"

/** What the timeline chart draws. Everything defaults to on, so opting out is explicit. */
class ChartSettings(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    private fun setBool(
        key: String,
        value: Boolean,
    ) = store.edit { this[booleanPreferencesKey(key)] = value }

    private fun boolLive(
        key: String,
        default: Boolean,
    ): LiveData<Boolean> = store.mappedLiveData { it[booleanPreferencesKey(key)] ?: default }

    private fun boolBlocking(
        key: String,
        default: Boolean,
    ): Boolean = store.snapshot()[booleanPreferencesKey(key)] ?: default

    fun setChartGridEnabled(enabled: Boolean) = setBool(KEY_CHART_GRID, enabled)

    fun isChartGridEnabled(): LiveData<Boolean> = boolLive(KEY_CHART_GRID, true)

    fun isChartGridEnabledBlocking(): Boolean = boolBlocking(KEY_CHART_GRID, true)

    fun setChartXAxisLabelEnabled(enabled: Boolean) = setBool(KEY_CHART_X_AXIS_LABEL, enabled)

    fun isChartXAxisLabelEnabled(): LiveData<Boolean> = boolLive(KEY_CHART_X_AXIS_LABEL, true)

    fun isChartXAxisLabelEnabledBlocking(): Boolean = boolBlocking(KEY_CHART_X_AXIS_LABEL, true)

    fun setChartYAxisLabelEnabled(enabled: Boolean) = setBool(KEY_CHART_Y_AXIS_LABEL, enabled)

    fun isChartYAxisLabelEnabled(): LiveData<Boolean> = boolLive(KEY_CHART_Y_AXIS_LABEL, true)

    fun isChartYAxisLabelEnabledBlocking(): Boolean = boolBlocking(KEY_CHART_Y_AXIS_LABEL, true)

    fun setChartHighlightExtremesEnabled(enabled: Boolean) = setBool(KEY_CHART_HIGHLIGHT_EXTREMES, enabled)

    fun isChartHighlightExtremesEnabled(): LiveData<Boolean> = boolLive(KEY_CHART_HIGHLIGHT_EXTREMES, true)

    fun isChartHighlightExtremesEnabledBlocking(): Boolean = boolBlocking(KEY_CHART_HIGHLIGHT_EXTREMES, true)

    fun setChartHighlightPeriodChangeEnabled(enabled: Boolean) = setBool(KEY_CHART_HIGHLIGHT_PERIOD_CHANGE, enabled)

    fun isChartHighlightPeriodChangeEnabled(): LiveData<Boolean> = boolLive(KEY_CHART_HIGHLIGHT_PERIOD_CHANGE, true)

    fun isChartHighlightPeriodChangeEnabledBlocking(): Boolean = boolBlocking(KEY_CHART_HIGHLIGHT_PERIOD_CHANGE, true)
}
