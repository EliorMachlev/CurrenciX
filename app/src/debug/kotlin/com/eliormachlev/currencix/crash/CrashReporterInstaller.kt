package com.eliormachlev.currencix.crash

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Process
import kotlin.system.exitProcess

// Debug variant: install a process-wide UncaughtExceptionHandler that
// captures every crash the app would otherwise die on, copies the full
// stack trace to the clipboard, and hands control to CrashReportActivity
// in a fresh process so the crash text is readable *inside* the app
// instead of only via `adb logcat`.
//
// Chains to the platform's default handler last so LeakCanary / other
// registered handlers still see the throwable — we don't want to
// silently swallow anything the tooling relies on. The final
// killProcess/exitProcess is intentional: the crashed process is in an
// undefined state, and Android will start a new process to host
// CrashReportActivity anyway.
fun Application.installDebugCrashReporter() {
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching { reportCrash(throwable) }
        runCatching { defaultHandler?.uncaughtException(thread, throwable) }
        Process.killProcess(Process.myPid())
        exitProcess(CRASH_EXIT_CODE)
    }
}

private fun Application.reportCrash(throwable: Throwable) {
    val trace = throwable.stackTraceToString()
    copyToClipboard(trace)
    startActivity(
        Intent(this, CrashReportActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(CrashReportActivity.EXTRA_TRACE, trace)
        },
    )
}

private fun Context.copyToClipboard(text: String) {
    val cm = getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
}

private const val CLIP_LABEL = "CurrenciX crash"
private const val CRASH_EXIT_CODE = 10
