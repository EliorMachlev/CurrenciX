package com.eliormachlev.currencix.repository

import android.content.Context
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.booleanPref
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

    val grid: Pref<Boolean> = store.booleanPref(KEY_CHART_GRID, default = true)
    val xAxisLabel: Pref<Boolean> = store.booleanPref(KEY_CHART_X_AXIS_LABEL, default = true)
    val yAxisLabel: Pref<Boolean> = store.booleanPref(KEY_CHART_Y_AXIS_LABEL, default = true)
    val highlightExtremes: Pref<Boolean> = store.booleanPref(KEY_CHART_HIGHLIGHT_EXTREMES, default = true)
    val highlightPeriodChange: Pref<Boolean> = store.booleanPref(KEY_CHART_HIGHLIGHT_PERIOD_CHANGE, default = true)
}
