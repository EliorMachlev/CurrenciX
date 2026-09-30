package com.eliormachlev.currencix.view.compose

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The app's one message surface: a Material snackbar at the bottom of the
 * window, over every screen. Replaces toasts, and — unlike them — can carry
 * an action, which is how destructive actions offer Undo.
 *
 * Owned by the Activity (it outlives screens, so a message sent as a screen
 * closes still shows) and drawn by [AppSnackbarHost]. A new message replaces
 * the one showing rather than queuing behind it.
 */
@Stable
class AppSnackbar(
    private val scope: CoroutineScope,
) {
    val hostState = SnackbarHostState()

    fun show(message: CharSequence) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(message.toString(), duration = SnackbarDuration.Short)
        }
    }

    /** Shows [message] with an [actionLabel] button (Undo) that runs [onUndo] if tapped before it times out. */
    fun showWithUndo(
        message: CharSequence,
        actionLabel: String,
        onUndo: () -> Unit,
    ) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(message.toString(), actionLabel = actionLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }
}

/** The snackbar from the nearest [AppSnackbarHost]; null in previews and screenshot tests. */
val LocalAppSnackbar = staticCompositionLocalOf<AppSnackbar?> { null }

/** Draws [snackbar]'s messages clear of the navigation bar and keyboard. */
@Composable
fun AppSnackbarHost(
    snackbar: AppSnackbar,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = snackbar.hostState,
        modifier = modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
    )
}

/**
 * Shows [message] on the app snackbar, or as a toast where there's none
 * (a screen rendered outside the app shell).
 */
fun AppSnackbar?.showOrToast(
    context: Context,
    message: CharSequence,
) {
    if (this != null) show(message) else Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
