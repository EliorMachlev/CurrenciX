package com.eliormachlev.currencix.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.system.exitProcess

// Debug-only landing screen shown after the UncaughtExceptionHandler
// catches a crash. Kept intentionally plain (no Compose, no AppCompat) so
// the reporter itself has the smallest possible surface to fail on — a
// crash here would defeat the entire tool. The stack trace is copied to
// the clipboard on entry and again on tap; a second button restarts the
// app into a fresh MainActivity.
internal class CrashReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra(EXTRA_TRACE).orEmpty()
        setContentView(buildLayout(trace))
        copyToClipboard(trace)
    }

    private fun buildLayout(trace: String): View {
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(PADDING, PADDING, PADDING, PADDING)
            }
        root.addView(headerView())
        root.addView(traceScroll(trace))
        root.addView(actionRow(trace))
        return root
    }

    private fun headerView(): TextView =
        TextView(this).apply {
            text = getString(com.eliormachlev.currencix.R.string.crash_header)
            textSize = HEADER_TEXT_SP
            setPadding(0, 0, 0, PADDING)
        }

    private fun traceScroll(trace: String): ScrollView {
        val text =
            TextView(this).apply {
                text = trace
                textSize = TRACE_TEXT_SP
                typeface = android.graphics.Typeface.MONOSPACE
                setTextIsSelectable(true)
            }
        return ScrollView(this).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )
            addView(text)
        }
    }

    private fun actionRow(trace: String): LinearLayout {
        val row =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, PADDING, 0, 0)
            }
        row.addView(
            Button(this).apply {
                text = getString(com.eliormachlev.currencix.R.string.crash_copy)
                setOnClickListener {
                    copyToClipboard(trace)
                    Toast
                        .makeText(
                            this@CrashReportActivity,
                            getString(com.eliormachlev.currencix.R.string.crash_copied),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            },
        )
        row.addView(
            Button(this).apply {
                text = getString(com.eliormachlev.currencix.R.string.crash_restart)
                setOnClickListener { restartApp() }
            },
        )
        return row
    }

    private fun restartApp() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(launch)
        }
        finish()
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }

    companion object {
        const val EXTRA_TRACE = "trace"
        private const val PADDING = 32
        private const val HEADER_TEXT_SP = 18f
        private const val TRACE_TEXT_SP = 12f
    }
}

private fun Context.copyToClipboard(text: String) {
    val cm = getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("CurrenciX crash", text))
}
