package com.eliormachlev.currencix.util

import android.content.Context
import android.content.Intent

/**
 * Kill this process and relaunch the app from its default launcher intent.
 *
 * In-repo rather than Jake Wharton's ProcessPhoenix: a third-party
 * dependency for a ~10-line action isn't worth it. Call from any [Context] on the main thread; the
 * activity stack is wiped and the process exits after the new task is queued.
 *
 * Called from [com.eliormachlev.currencix.repository.BackupManager] after a
 * successful import so the whole app re-hydrates its in-memory caches from
 * the newly restored preference files instead of racing on live observers.
 */
fun restartApp(context: Context) {
    val packageManager = context.packageManager
    val intent =
        packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
            ?: return
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
