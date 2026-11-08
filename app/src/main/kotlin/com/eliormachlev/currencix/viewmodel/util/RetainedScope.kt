package com.eliormachlev.currencix.viewmodel.util

import androidx.compose.runtime.retain.RetainObserver
import com.eliormachlev.currencix.util.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The base of a state holder kept with `retain { ... }`: it owns a
 * [CoroutineScope] that lives as long as the holder is retained, so work it
 * started survives a rotation and stops when the screen is gone for good
 * (what `viewModelScope` did for a ViewModel).
 */
abstract class RetainedScope(
    dispatchers: AppDispatchers = AppDispatchers.production,
) : RetainObserver {
    protected val scope = CoroutineScope(SupervisorJob() + dispatchers.main)

    /** Called once the holder is let go of, after its [scope] is cancelled (wipe what must not linger). */
    protected open fun onCleared() {}

    override fun onRetained() {}

    override fun onEnteredComposition() {}

    override fun onExitedComposition() {}

    override fun onRetired() = clear()

    override fun onUnused() = clear()

    private fun clear() {
        scope.cancel()
        onCleared()
    }
}
