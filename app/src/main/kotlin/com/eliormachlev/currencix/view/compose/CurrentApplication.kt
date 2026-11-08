package com.eliormachlev.currencix.view.compose

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.platform.LocalContext

/** The application, which the state holders here are built from (never the Activity, which `retain` must not hold). */
@Composable
internal fun currentApplication(): Application = LocalContext.current.applicationContext as Application

/**
 * A state holder built from the application and kept with `retain`. The [keys]
 * pass straight through to `retain`, so the holder is rebuilt only when they change.
 */
@Composable
internal inline fun <reified T : Any> retainWithApplication(
    vararg keys: Any?,
    crossinline create: (Application) -> T,
): T {
    val application = currentApplication()
    return retain(*keys) { create(application) }
}
