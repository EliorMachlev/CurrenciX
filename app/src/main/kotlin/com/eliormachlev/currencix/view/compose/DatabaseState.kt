package com.eliormachlev.currencix.view.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.persistence.Pref

/** The app's [Database], kept across recompositions (for UI that reads settings directly, without a view model). */
@Composable
internal fun rememberDatabase(): Database {
    val context = LocalContext.current
    return remember(context) { Database(context) }
}

/**
 * [this] setting as Compose state, following its changes while the screen is
 * started; it starts from the stored value, so the first frame is already right.
 */
@Composable
internal fun <T> Pref<T>.collectAsStateWithLifecycle(): State<T> = flow.collectAsStateWithLifecycle(initialValue = remember(this) { get() })
