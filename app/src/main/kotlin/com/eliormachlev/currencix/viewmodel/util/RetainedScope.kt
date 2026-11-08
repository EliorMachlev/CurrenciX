package com.eliormachlev.currencix.viewmodel.util

import androidx.compose.runtime.retain.RetainObserver
import com.eliormachlev.currencix.util.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * What a state holder kept with `retain { ... }` owns: a [CoroutineScope] that lives as long as
 * the holder is retained, so work it started survives a rotation and stops when the screen is
 * gone for good (what `viewModelScope` did for a ViewModel). A holder takes one as a constructor
 * parameter and delegates [RetainObserver] to it (`: RetainObserver by retained`). It is not a
 * base class: the Compose compiler adds a `$stable` field to every class, and a subclass's would
 * hide its base's.
 */
class RetainedScope(
    dispatchers: AppDispatchers = AppDispatchers.production,
) : RetainObserver {
    val scope = CoroutineScope(SupervisorJob() + dispatchers.main)

    /** Run once the holder is let go of, after its [scope] is cancelled (wipe what must not linger). */
    var onCleared: () -> Unit = {}

    override fun onRetained() = Unit

    override fun onEnteredComposition() = Unit

    override fun onExitedComposition() = Unit

    override fun onRetired() = clear()

    override fun onUnused() = clear()

    private fun clear() {
        scope.cancel()
        onCleared()
    }
}
