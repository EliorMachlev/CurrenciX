package com.eliormachlev.currencix.view.preference.compose

import androidx.compose.runtime.Composable

// Release builds have no debug rows in Settings. Debug builds add them
// (src/debug/…/DebugPreferenceRows.kt).
internal val debugPreferenceRows: (@Composable () -> Unit)? = null
