package com.eliormachlev.currencix.view.lock

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.view.compose.theme.Spacing

private val LOCK_ICON_SIZE = 48.dp

/**
 * [content], covered by the lock screen while [AppLock.locked]: content
 * stays composed underneath (so the screen and its state are where they
 * were once unlocked) but can't be seen, touched or read by TalkBack. The
 * prompt opens by itself; Unlock opens it again after a cancel.
 */
@Composable
fun LockGate(content: @Composable () -> Unit) {
    val locked = AppLock.locked
    Box(Modifier.fillMaxSize()) {
        Box(if (locked) Modifier.clearAndSetSemantics {} else Modifier) { content() }
        if (locked) LockScreen()
    }
}

@Composable
private fun LockScreen() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AppLock.unlock(context) }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                // Swallows every touch, so nothing reaches the screen beneath.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .padding(Spacing.XXLarge),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lock),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(LOCK_ICON_SIZE),
        )
        Text(stringResource(R.string.app_lock_locked), style = MaterialTheme.typography.titleLarge)
        Button(onClick = { AppLock.unlock(context) }) { Text(stringResource(R.string.app_lock_unlock)) }
    }
}

/**
 * While the app lock is on, the app's picture in Recents is left blank, so
 * the last screen isn't on show to anyone who opens Recents. Screenshots
 * still work.
 */
fun Activity.hideFromRecents(hidden: Boolean) = setRecentsScreenshotEnabled(!hidden)
