package com.eliormachlev.currencix.repository

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

// Virtual time: each step below lands on the exact millisecond the indicator
// should change, and nothing waits on the wall clock.
private const val SHOW_DELAY = 150L
private const val MIN_VISIBLE = 400L

/**
 * [asRefreshIndicator]: the display debounce behind RefreshState's
 * pull / passive indicators.
 */
class RefreshIndicatorTest {
    private val busy = MutableStateFlow(false)

    // The indicator, collected on the test's virtual clock: [steps] drives
    // [busy] and the clock, then checks what the indicator did.
    private fun indicatorTest(
        showDelay: Long,
        steps: suspend ReceiveTurbine<Boolean>.(TestScope) -> Unit,
    ) = runTest {
        val start = testScheduler.timeSource.markNow()
        busy.asRefreshIndicator(showDelay, MIN_VISIBLE) { start.elapsedNow().inWholeMilliseconds }.test {
            assertEquals(false, awaitItem())
            steps(this@runTest)
        }
    }

    // Moves the clock [millis] on and runs what's due then.
    private fun TestScope.after(millis: Long) {
        testScheduler.advanceTimeBy(millis.milliseconds)
        testScheduler.runCurrent()
    }

    private fun TestScope.refreshing(value: Boolean) {
        busy.value = value
        testScheduler.runCurrent()
    }

    @Test
    fun `a refresh faster than the show delay shows nothing`() =
        indicatorTest(SHOW_DELAY) { scope ->
            scope.refreshing(true)
            scope.after(SHOW_DELAY - 1)
            scope.refreshing(false)
            scope.after(SHOW_DELAY + MIN_VISIBLE)
            expectNoEvents()
        }

    @Test
    fun `a slow refresh shows after the delay and holds the minimum`() =
        indicatorTest(SHOW_DELAY) { scope ->
            scope.refreshing(true)
            scope.after(SHOW_DELAY - 1)
            expectNoEvents()
            scope.after(1)
            assertEquals(true, awaitItem())
            // Finishes just after showing: still held until the minimum is up.
            scope.after(50)
            scope.refreshing(false)
            scope.after(MIN_VISIBLE - 50 - 1)
            expectNoEvents()
            scope.after(1)
            assertEquals(false, awaitItem())
        }

    @Test
    fun `a pull indicator shows immediately`() =
        indicatorTest(showDelay = 0L) { scope ->
            scope.refreshing(true)
            assertEquals(true, awaitItem())
            scope.after(40)
            scope.refreshing(false)
            scope.after(MIN_VISIBLE - 40 - 1)
            expectNoEvents()
            scope.after(1)
            assertEquals(false, awaitItem())
        }

    @Test
    fun `back-to-back refreshes during the hold don't flicker`() =
        indicatorTest(SHOW_DELAY) { scope ->
            scope.refreshing(true)
            scope.after(SHOW_DELAY)
            assertEquals(true, awaitItem())
            scope.after(50)
            scope.refreshing(false)
            // Back on while still held: it stays shown, and the hold runs
            // from when it was first shown.
            scope.after(100)
            scope.refreshing(true)
            scope.after(100)
            scope.refreshing(false)
            scope.after(MIN_VISIBLE - 250 - 1)
            expectNoEvents()
            scope.after(1)
            assertEquals(false, awaitItem())
        }
}
