package com.eliormachlev.currencix.crash

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

// The debug crash screen: it shows the stack trace, has already copied it,
// and copies it again on tap. (Restart kills the process, so it isn't run.)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CrashReportActivityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun launch(trace: String?): CrashReportActivity {
        val intent = Intent(context, CrashReportActivity::class.java)
        if (trace != null) intent.putExtra(CrashReportActivity.EXTRA_TRACE, trace)
        return Robolectric.buildActivity(CrashReportActivity::class.java, intent).create().get()
    }

    private fun View.descendants(): Sequence<View> =
        sequence {
            yield(this@descendants)
            if (this@descendants is ViewGroup) {
                for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
            }
        }

    private fun CrashReportActivity.views() = window.decorView.descendants()

    private fun clipboardText(): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        return clipboard.primaryClip
            ?.getItemAt(0)
            ?.text
            ?.toString()
    }

    @Test
    fun `shows the trace under a header`() {
        val activity = launch("java.lang.IllegalStateException: boom")

        val texts =
            activity
                .views()
                .filterIsInstance<TextView>()
                .map { it.text.toString() }
                .toList()

        assertTrue(texts.contains("java.lang.IllegalStateException: boom"))
        assertTrue(texts.contains(context.getString(R.string.crash_header)))
    }

    @Test
    fun `copies the trace to the clipboard on entry`() {
        launch("trace on entry")

        assertEquals("trace on entry", clipboardText())
    }

    @Test
    fun `the copy button copies again and says so`() {
        val activity = launch("trace to copy")
        val copy = activity.views().filterIsInstance<Button>().first { it.text == context.getString(R.string.crash_copy) }
        context.getSystemService(ClipboardManager::class.java).clearPrimaryClip()

        copy.performClick()

        assertEquals("trace to copy", clipboardText())
        assertEquals(context.getString(R.string.crash_copied), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `has a restart button`() {
        val activity = launch("trace")

        val labels =
            activity
                .views()
                .filterIsInstance<Button>()
                .map { it.text.toString() }
                .toList()

        assertTrue(labels.contains(context.getString(R.string.crash_restart)))
    }

    @Test
    fun `a missing trace shows an empty screen instead of crashing`() {
        val activity = launch(trace = null)

        assertNotNull(activity.views().filterIsInstance<Button>().firstOrNull())
        assertEquals("", clipboardText())
    }
}
