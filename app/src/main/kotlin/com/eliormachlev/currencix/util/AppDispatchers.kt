package com.eliormachlev.currencix.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The coroutine dispatchers the app runs its work on, in one place. Classes
 * the app constructs take an [AppDispatchers] (defaulting to [production]),
 * so a test can hand in its own; the ones Android instantiates (the
 * Application, activities, the widget receiver) use [production] directly.
 */
class AppDispatchers(
    /** Disk and network work. */
    val io: CoroutineDispatcher = Dispatchers.IO,
    /** CPU work and long-lived observers off the main thread. */
    val default: CoroutineDispatcher = Dispatchers.Default,
    // Looked up on first use: on a plain JVM (unit tests) there is no main
    // dispatcher, and naming it at construction would fail there.
    private val mainDispatcher: () -> CoroutineDispatcher = { Dispatchers.Main.immediate },
) {
    /** Back to the main thread with a result (immediately, when already on it). */
    val main: CoroutineDispatcher by lazy(mainDispatcher)

    companion object {
        /** The real dispatchers. */
        val production = AppDispatchers()
    }
}
