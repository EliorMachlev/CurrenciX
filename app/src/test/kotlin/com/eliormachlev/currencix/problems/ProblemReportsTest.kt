package com.eliormachlev.currencix.problems

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Crashes are written down on the phone, the latest few kept, newest first,
// and shared only as text the user chooses to send.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProblemReportsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `a crash is kept with its stack trace`() {
        ProblemReports.recordCrash(app, Thread.currentThread(), IllegalStateException("boom"), nowMillis = 1_000L)

        val problem = ProblemReports.recent(app).single()
        assertEquals(Problem.Kind.CRASH, problem.kind)
        assertTrue(problem.details.contains("IllegalStateException: boom"))
    }

    @Test
    fun `only the latest five are kept, newest first`() {
        (1L..7L).forEach { ProblemReports.recordCrash(app, Thread.currentThread(), RuntimeException("crash $it"), nowMillis = it * 1_000) }

        val times = ProblemReports.recent(app).map { it.timeMillis }
        assertEquals(listOf(7_000L, 6_000L, 5_000L, 4_000L, 3_000L), times)
    }

    @Test
    fun `the report says which app and phone, then each problem`() {
        val problems = listOf(Problem(Problem.Kind.FREEZE, 0L, "main thread stuck"))

        val text = ProblemReports.reportText(app, "1.2.3", problems)

        assertTrue(text.startsWith("CurrenciX 1.2.3"))
        assertTrue(text.contains("Android "))
        assertTrue(text.contains("FREEZE at "))
        assertTrue(text.contains("main thread stuck"))
    }
}
