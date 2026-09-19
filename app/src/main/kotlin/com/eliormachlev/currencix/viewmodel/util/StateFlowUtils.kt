package com.eliormachlev.currencix.viewmodel.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

// How long the derived [StateFlow] keeps collecting the upstream after the
// last subscriber goes away. 5s covers config-change / navigation churn so we
// don't re-cold-start the upstream on every recomposition or fragment rebind.
// Shared across every ViewModel that exposes state via [stateInWhileSubscribed]
// so the fanout timing stays uniform (see #149).
internal const val STATE_FLOW_STOP_TIMEOUT_MS = 5_000L

/**
 * Shorthand for `stateIn(scope, WhileSubscribed(STATE_FLOW_STOP_TIMEOUT_MS), initial)`
 * — the pattern every ViewModel that bridges a persistence [Flow] into a
 * lifecycle-aware [StateFlow] uses. Centralised here so the sharing policy and
 * stop timeout stay consistent and the ViewModel call sites read as a single
 * declarative line (see #149).
 */
internal fun <T> Flow<T>.stateInWhileSubscribed(
    scope: CoroutineScope,
    initial: T,
): StateFlow<T> = stateIn(scope, SharingStarted.WhileSubscribed(STATE_FLOW_STOP_TIMEOUT_MS), initial)
