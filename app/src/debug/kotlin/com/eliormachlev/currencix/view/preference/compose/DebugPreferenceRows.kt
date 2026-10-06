package com.eliormachlev.currencix.view.preference.compose

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.airbnb.android.showkase.models.Showkase
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.showkase.getBrowserIntent

// What only a debug build's Settings has: a row that replays the first-run
// onboarding tour (so QA can re-enter it without wiping app data) and one that
// opens the Showkase gallery. Release builds add nothing
// (src/release/…/DebugPreferenceRows.kt).
internal val debugPreferenceRows: (@Composable () -> Unit)? = {
    ResetOnboardingRow()
    ComponentGalleryRow()
}

// The Showkase browser over the app's @Preview composables. Opened from here
// rather than from a launcher icon of its own, so a debug build exports no
// activity the release build doesn't.
@Composable
private fun ComponentGalleryRow() {
    val context = LocalContext.current
    PreferenceRow(
        title = stringResource(id = R.string.pref_debug_gallery_title),
        summary = stringResource(id = R.string.pref_debug_gallery_summary),
        iconRes = R.drawable.ic_palette,
        onClick = { context.startActivity(Showkase.getBrowserIntent(context)) },
    )
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
