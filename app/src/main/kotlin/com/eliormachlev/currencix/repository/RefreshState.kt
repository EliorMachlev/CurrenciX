package com.eliormachlev.currencix.repository

import android.os.SystemClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

// A pull-to-refresh spinner is already on screen when the refresh starts, so
// it shows immediately; the minimum keeps an instant (cached) refresh from
// snapping it shut the frame after it opened.
private const val PULL_SHOW_DELAY_MS = 0L

// Passive indicators (the digit shimmer, the chart's progress bar) only appear
// once a refresh has taken long enough to notice — a cached refresh finishes
// well inside this and shows nothing at all.
private const val PASSIVE_SHOW_DELAY_MS = 150L

// Once any indicator is visible it stays up at least this long, so a refresh
// finishing just after the indicator appeared doesn't read as a flicker.
private const val INDICATOR_MIN_VISIBLE_MS = 400L

/**
 * Process-wide "an exchange-rate refresh is running" signal.
 *
 * Transient UI state, so it lives in memory. It used to be a DataStore key:
 * that cost two disk writes per refresh, the spinner only reacted once each
 * write had landed and re-emitted, and a process killed mid-refresh restored
 * as "updating". (The old `_isUpdating` key may linger in existing
 * last_state files; nothing reads it any more.)
 *
 * [inFlight] is the raw truth — use it for logic such as not starting a
 * second refresh. The UI shows one of the two debounced indicators instead.
 */
internal object RefreshState {
    private val running = MutableStateFlow(false)

    val inFlight: StateFlow<Boolean> = running.asStateFlow()

    /** For a pull-to-refresh spinner: immediate, with a minimum visible time. */
    val pullIndicator: Flow<Boolean> =
        running.asRefreshIndicator(PULL_SHOW_DELAY_MS, INDICATOR_MIN_VISIBLE_MS)

    /** For passive indicators: only if the refresh is slow enough to notice. */
    val passiveIndicator: Flow<Boolean> =
        running.asRefreshIndicator(PASSIVE_SHOW_DELAY_MS, INDICATOR_MIN_VISIBLE_MS)

    fun start() {
        running.value = true
    }

    fun finish() {
        running.value = false
    }
}

/**
 * Debounces a busy signal for display: `true` shows only once the source has
 * been busy for [showDelayMillis], and once shown it holds for at least
 * [minVisibleMillis]. A new busy period starting during that hold keeps it
 * up without a flicker. State is per collector.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<Boolean>.asRefreshIndicator(
    showDelayMillis: Long,
    minVisibleMillis: Long,
    now: () -> Long = SystemClock::uptimeMillis,
): Flow<Boolean> =
    flow {
        var shownAt: Long? = null
        emitAll(
            distinctUntilChanged().transformLatest { busy ->
                if (busy) {
                    if (shownAt == null) {
                        if (showDelayMillis > 0) delay(showDelayMillis)
                        shownAt = now()
                    }
                    emit(true)
                } else {
                    shownAt?.let { shown ->
                        val remaining = minVisibleMillis - (now() - shown)
                        if (remaining > 0) delay(remaining)
                    }
                    shownAt = null
                    emit(false)
                }
            },
        )
    }.distinctUntilChanged()
