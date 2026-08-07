package com.eliormachlev.currencix.util

import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import timber.log.Timber

private const val LOG_DIR = "logs"
private const val CURRENT_LOG = "app.log"
private const val PREVIOUS_LOG = "app.log.1"
private const val MAX_BYTES = 256L * 1024L
private const val TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"

// Timber tree that appends log lines to an app-private rotating file. All I/O
// runs on a single background thread so callers never block; rotation swaps
// [CURRENT_LOG] → [PREVIOUS_LOG] when the active file exceeds [MAX_BYTES],
// bounding on-disk footprint at ~512 KiB regardless of runtime volume.
class FileLoggingTree(
    filesDir: File,
) : Timber.Tree() {
    private val logDir = File(filesDir, LOG_DIR).apply { mkdirs() }
    private val currentFile = File(logDir, CURRENT_LOG)
    private val previousFile = File(logDir, PREVIOUS_LOG)
    private val executor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "file-log").apply { isDaemon = true }
        }
    private val timestampFormat = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US)

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        val line = formatLine(priority, tag, message, t)
        executor.execute {
            runCatching {
                rotateIfNeeded()
                currentFile.appendText(line)
            }
        }
    }

    private fun formatLine(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ): String {
        val builder =
            StringBuilder()
                .append(timestampFormat.format(Date()))
                .append(' ')
                .append(priorityLabel(priority))
                .append('/')
                .append(tag ?: "-")
                .append(": ")
                .append(message)
                .append('\n')
        if (t != null) {
            builder.append(stackTraceOf(t))
        }
        return builder.toString()
    }

    private fun stackTraceOf(t: Throwable): String {
        val writer = java.io.StringWriter()
        PrintWriter(writer).use { t.printStackTrace(it) }
        return writer.toString()
    }

    private fun rotateIfNeeded() {
        if (currentFile.length() < MAX_BYTES) return
        if (previousFile.exists()) previousFile.delete()
        currentFile.renameTo(previousFile)
    }

    private fun priorityLabel(priority: Int): String =
        when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }
}
