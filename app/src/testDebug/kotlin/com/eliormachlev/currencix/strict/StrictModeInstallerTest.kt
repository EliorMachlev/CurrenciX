package com.eliormachlev.currencix.strict

import android.app.Application
import android.os.StrictMode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The debug build turns StrictMode on; the policies are put back so other
// tests in the same JVM are not watched.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StrictModeInstallerTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun installingSetsBothTheThreadAndTheVMPolicy() {
        val threadBefore = StrictMode.getThreadPolicy()
        val vmBefore = StrictMode.getVmPolicy()
        try {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
            StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
            val threadLax = StrictMode.getThreadPolicy().toString()
            val vmLax = StrictMode.getVmPolicy().toString()

            app.installStrictMode()

            assertNotEquals(threadLax, StrictMode.getThreadPolicy().toString())
            assertNotEquals(vmLax, StrictMode.getVmPolicy().toString())
        } finally {
            StrictMode.setThreadPolicy(threadBefore)
            StrictMode.setVmPolicy(vmBefore)
        }
    }
}
