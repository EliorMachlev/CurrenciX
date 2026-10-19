package com.eliormachlev.currencix.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Real (wall-clock) timing — generous gaps between the delay/min thresholds
// and the test's own sleeps keep it stable under CI load.
private const val SHOW_DELAY = 150L
private const val MIN_VISIBLE = 400L

// The recorder timestamps each value a moment after the operator read its
// own clock, so a hold of exactly MIN_VISIBLE can measure a millisecond or
// two short.
private const val CLOCK_SLACK = 10L

/**
 * [asRefreshIndicator]: the display debounce behind RefreshState's
 * pull / passive indicators.
 */
class RefreshIndicatorTest {
    private class Recorder {
        private val start = System.currentTimeMillis()
        val events = mutableListOf<Pair<Boolean, Long>>()

        fun record(value: Boolean) {
            events += value to (System.currentTimeMillis() - start)
        }

        val values get() = events.map { it.first }
    }

    private fun runScenario(
        showDelay: Long,
        scenario: suspend (MutableStateFlow<Boolean>) -> Unit,
    ): Recorder =
        runBlocking {
            val busy = MutableStateFlow(false)
            val recorder = Recorder()
            val job =
                launch {
                    busy.asRefreshIndicator(showDelay, MIN_VISIBLE, System::currentTimeMillis).collect(recorder::record)
                }
            // Wait for the initial `false` to reach the recorder: StateFlow is
            // conflated, so flipping `busy` before the collector subscribes
            // (a cold JVM can take a while) would skip it.
            while (recorder.events.isEmpty()) delay(5)
            scenario(busy)
            job.cancel()
            recorder
        }

    @Test
    fun `a refresh faster than the show delay shows nothing`() {
        val r =
            runScenario(SHOW_DELAY) { busy ->
                busy.value = true
                delay(40)
                busy.value = false
                delay(SHOW_DELAY + MIN_VISIBLE)
            }
        assertEquals(listOf(false), r.values)
    }

    @Test
    fun `a slow refresh shows after the delay and holds the minimum`() {
        val r =
            runScenario(SHOW_DELAY) { busy ->
                busy.value = true
                delay(SHOW_DELAY + 50) // now visible
                busy.value = false // finishes almost immediately after showing
                delay(MIN_VISIBLE + 200)
            }
        assertEquals(listOf(false, true, false), r.values)
        val (shownAt, hiddenAt) = r.events[1].second to r.events[2].second
        assertTrue("shown after the delay, was ${shownAt}ms", shownAt >= SHOW_DELAY)
        assertTrue("held ≥ minimum, was ${hiddenAt - shownAt}ms", hiddenAt - shownAt >= MIN_VISIBLE - CLOCK_SLACK)
    }

    @Test
    fun `a pull indicator shows immediately`() {
        val r =
            runScenario(showDelay = 0L) { busy ->
                busy.value = true
                delay(40)
                busy.value = false
                delay(MIN_VISIBLE + 200)
            }
        assertEquals(listOf(false, true, false), r.values)
        assertTrue("held ≥ minimum", r.events[2].second - r.events[1].second >= MIN_VISIBLE - CLOCK_SLACK)
    }

    @Test
    fun `back-to-back refreshes during the hold don't flicker`() {
        val r =
            runScenario(SHOW_DELAY) { busy ->
                busy.value = true
                delay(SHOW_DELAY + 50)
                busy.value = false
                delay(100) // still inside the minimum-visible hold
                busy.value = true
                delay(100)
                busy.value = false
                delay(MIN_VISIBLE + 200)
            }
        assertEquals(listOf(false, true, false), r.values)
    }
}
