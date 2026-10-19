package com.eliormachlev.currencix.problems

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Crash traces the app wrote itself, under filesDir (they're kept until
// replaced, not cleared to free space).
private const val PROBLEMS_DIR = "problems"
private const val CRASH_PREFIX = "crash_"
private const val CRASH_SUFFIX = ".txt"
private const val TAG = "ProblemReports"

// How many problems are kept and shown: the latest few are what helps.
private const val MAX_PROBLEMS = 5

// A freeze's report can run to megabytes (every thread's stack); the head,
// with the main thread first, is the part that says what was stuck.
private const val MAX_DETAILS_CHARS = 8_000
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** A crash or freeze the app had, newest first in [ProblemReports.recent]. */
data class Problem(
    val kind: Kind,
    val timeMillis: Long,
    val details: String,
) {
    enum class Kind { CRASH, FREEZE, NATIVE_CRASH }
}

/**
 * Crashes and freezes, kept on the phone so the user can choose to share
 * them (About → Report a problem); nothing is ever sent by itself. Crashes
 * in the app's own code are written down as they happen ([install]); freezes
 * ("app not responding") and crashes in native code come from Android's
 * record of why the app's process last ended ([ApplicationExitInfo]).
 */
object ProblemReports {
    /**
     * Writes each crash's stack trace before handing it on to the handler
     * that was there (the system's, or the debug build's crash screen).
     */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                recordCrash(app, thread, error)
            } catch (e: IOException) {
                Timber.tag(TAG).w(e, "Couldn't record the crash")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    internal fun recordCrash(
        context: Context,
        thread: Thread,
        error: Throwable,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val dir = problemsDir(context).apply { mkdirs() }
        File(
            dir,
            "$CRASH_PREFIX$nowMillis$CRASH_SUFFIX",
        ).writeText("Thread: ${thread.name}\n${error.stackTraceToString()}".take(MAX_DETAILS_CHARS))
        crashFiles(dir).drop(MAX_PROBLEMS).forEach { it.delete() }
    }

    /** The latest problems, newest first. Reads files: call off the main thread. */
    fun recent(context: Context): List<Problem> =
        (recordedCrashes(context) + systemExits(context)).sortedByDescending { it.timeMillis }.take(MAX_PROBLEMS)

    /** What's shared: the app and phone, then each problem. */
    fun reportText(
        context: Context,
        versionName: String,
        problems: List<Problem>,
    ): String =
        buildString {
            appendLine("CurrenciX $versionName (${context.packageName})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            problems.forEach { problem ->
                appendLine()
                appendLine(
                    "${problem.kind} at ${TIME_FORMAT.format(Instant.ofEpochMilli(problem.timeMillis).atZone(ZoneId.systemDefault()))}",
                )
                appendLine(problem.details)
            }
        }

    private fun recordedCrashes(context: Context): List<Problem> =
        crashFiles(problemsDir(context)).mapNotNull { file ->
            val time =
                file.name
                    .removePrefix(CRASH_PREFIX)
                    .removeSuffix(CRASH_SUFFIX)
                    .toLongOrNull() ?: return@mapNotNull null
            val text =
                try {
                    file.readText()
                } catch (e: IOException) {
                    Timber.tag(TAG).w(e, "Couldn't read %s", file.name)
                    return@mapNotNull null
                }
            Problem(Problem.Kind.CRASH, time, text)
        }

    // Freezes and native crashes. Crashes in Kotlin code are left out here:
    // Android's record of those has no stack trace, and recordCrash wrote one.
    private fun systemExits(context: Context): List<Problem> {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        return manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_PROBLEMS).mapNotNull { exit ->
            val kind =
                when (exit.reason) {
                    ApplicationExitInfo.REASON_ANR -> Problem.Kind.FREEZE
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> Problem.Kind.NATIVE_CRASH
                    else -> return@mapNotNull null
                }
            Problem(kind, exit.timestamp, exitDetails(exit))
        }
    }

    private fun exitDetails(exit: ApplicationExitInfo): String {
        val trace =
            try {
                exit.traceInputStream?.bufferedReader()?.use { reader ->
                    val buffer = CharArray(MAX_DETAILS_CHARS)
                    String(buffer, 0, reader.read(buffer).coerceAtLeast(0))
                }
            } catch (e: IOException) {
                Timber.tag(TAG).w(e, "Couldn't read an exit trace")
                null
            }
        return listOfNotNull(exit.description, trace).joinToString("\n").ifEmpty { "(no details)" }
    }

    private fun problemsDir(context: Context) = File(context.filesDir, PROBLEMS_DIR)

    // Newest first.
    private fun crashFiles(dir: File): List<File> =
        dir
            .listFiles { file -> file.name.startsWith(CRASH_PREFIX) && file.name.endsWith(CRASH_SUFFIX) }
            .orEmpty()
            .sortedByDescending {
                it.name
                    .removePrefix(CRASH_PREFIX)
                    .removeSuffix(CRASH_SUFFIX)
                    .toLongOrNull() ?: 0L
            }
}
