package com.eliormachlev.currencix.strict

import android.app.Application
import android.os.Build
import android.os.StrictMode
import timber.log.Timber
import java.util.concurrent.Executors

// Debug variant: Android's StrictMode reports disk and network work on the
// main thread (what makes the app stutter or freeze) and leaked resources
// (unclosed streams, unregistered receivers, leaked activities). Reports go
// to the log, so they show in logcat and the on-device log file; nothing
// crashes, since the point is to find these, not to stop debugging. The
// release variant is a no-op.
fun Application.installStrictMode() {
    val executor = Executors.newSingleThreadExecutor()
    val thread =
        StrictMode.ThreadPolicy
            .Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .detectCustomSlowCalls()
            .detectResourceMismatches()
            .detectUnbufferedIo()
    // Explicit System.gc() calls are only reported from Android 14.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) thread.detectExplicitGc()
    StrictMode.setThreadPolicy(
        thread.penaltyListener(executor) { violation -> Timber.tag(TAG).w(violation, "Main-thread violation") }.build(),
    )
    // Untagged sockets are left out: every OkHttp request would report.
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy
            .Builder()
            .detectLeakedClosableObjects()
            .detectLeakedSqlLiteObjects()
            .detectLeakedRegistrationObjects()
            .detectActivityLeaks()
            .detectFileUriExposure()
            .detectContentUriWithoutPermission()
            .detectUnsafeIntentLaunch()
            .detectIncorrectContextUse()
            .penaltyListener(executor) { violation -> Timber.tag(TAG).w(violation, "VM violation") }
            .build(),
    )
}

private const val TAG = "StrictMode"
