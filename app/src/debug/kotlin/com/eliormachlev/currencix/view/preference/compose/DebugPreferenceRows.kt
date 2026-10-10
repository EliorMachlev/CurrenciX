package com.eliormachlev.currencix.view.preference.compose

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.Database

// What only a debug build's Settings has: a row that replays the first-run
// onboarding tour (so QA can re-enter it without wiping app data). Release
// builds add nothing
// (src/release/…/DebugPreferenceRows.kt).
internal val debugPreferenceRows: (@Composable () -> Unit)? = {
    ResetOnboardingRow()
}

@Composable
private fun ResetOnboardingRow() {
    val context = LocalContext.current
    val resetToast = stringResource(id = R.string.pref_debug_reset_onboarding_toast)
    PreferenceRow(
        title = stringResource(id = R.string.pref_debug_reset_onboarding_title),
        summary = stringResource(id = R.string.pref_debug_reset_onboarding_summary),
        iconRes = R.drawable.ic_refresh,
        onClick = {
            Database(context).display.setHasSeenOnboarding(false)
            Toast.makeText(context, resetToast, Toast.LENGTH_SHORT).show()
        },
    )
}
