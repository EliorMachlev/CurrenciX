package com.eliormachlev.currencix.repository.persistence

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-scoped fanout for "the widget's underlying data changed, redraw it"
 * signals. The repository layer emits into this bus after any write that
 * affects the widget's rendered surface (rates cache, last-used pair). The
 * view layer subscribes and drives Glance's `updateAll()`.
 *
 * The inversion exists to satisfy the Konsist rule that forbids the
 * repository layer from importing anything under `view.*`. Previously
 * `Database.kt` reached into `view.widget.CurrencyWidget.refreshWidgets()`
 * directly — that direction is now flipped through this bus.
 *
 * Buffer=1 with DROP_OLDEST because the widget only needs "there's a fresh
 * value pending" — coalescing bursts of writes into a single redraw is
 * correct and avoids piling up work on the widget's own coroutine scope.
 */
object WidgetRefreshBus {
    private val relay =
        MutableSharedFlow<Unit>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val events: SharedFlow<Unit> = relay.asSharedFlow()

    fun signal() {
        relay.tryEmit(Unit)
    }
}
