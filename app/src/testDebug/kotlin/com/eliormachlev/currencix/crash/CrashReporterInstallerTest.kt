package com.eliormachlev.currencix.crash

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

// The debug crash reporter hooks the process-wide handler, except under
// Robolectric, where its exit would kill the test worker. The handler itself
// ends the process, so it is only installed here, never run.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CrashReporterInstallerTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `under Robolectric the handler is left alone`() {
        val before = Thread.getDefaultUncaughtExceptionHandler()

        app.installDebugCrashReporter()

        assertSame(before, Thread.getDefaultUncaughtExceptionHandler())
    }

    @Test
    fun `on a real device build it installs a handler`() {
        val handlerBefore = Thread.getDefaultUncaughtExceptionHandler()
        val fingerprintBefore = Build.FINGERPRINT
        try {
            ShadowBuild.setFingerprint("google/device/device:14/ABC/1:user/release-keys")

            app.installDebugCrashReporter()

            assertNotSame(handlerBefore, Thread.getDefaultUncaughtExceptionHandler())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(handlerBefore)
            ShadowBuild.setFingerprint(fingerprintBefore)
        }
    }
}
