package com.eliormachlev.currencix.crash

import android.app.Application

// Release variant: no-op. The debug variant installs a process-wide
// UncaughtExceptionHandler that copies the trace to the clipboard and
// launches CrashReportActivity; release builds let the platform handle
// crashes the normal way (default OS crash dialog + Play Console
// signal).
fun Application.installDebugCrashReporter() = Unit
