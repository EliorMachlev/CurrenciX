package com.eliormachlev.currencix.util

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NetworkStatusTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val wifi: Network = ShadowNetwork.newInstance(WIFI_ID)
    private val cellular: Network = ShadowNetwork.newInstance(CELLULAR_ID)

    private fun callback(): ConnectivityManager.NetworkCallback = shadowOf(connectivity).networkCallbacks.single()

    // What the status has said so far, and the collection saying it.
    private class Watch(
        val seen: List<Boolean>,
        val collection: Job,
    )

    // Collects the status at once, as a screen would, then runs [steps].
    private fun statusTest(steps: TestScope.(Watch) -> Unit) =
        runTest {
            val seen = mutableListOf<Boolean>()
            val collection =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    networkStatus(context).collect { seen += it }
                }
            testScheduler.runCurrent()
            steps(Watch(seen, collection))
        }

    @Test
    fun `losing one network while another is still validated stays online`() =
        statusTest { watch ->
            callback().onAvailable(wifi)
            callback().onAvailable(cellular)
            callback().onLost(wifi)
            testScheduler.runCurrent()
            assertEquals(true, watch.seen.last())
        }

    @Test
    fun `losing the last validated network goes offline, and one returning goes back online`() =
        statusTest { watch ->
            callback().onAvailable(wifi)
            callback().onLost(wifi)
            testScheduler.runCurrent()
            assertEquals(false, watch.seen.last())
            callback().onAvailable(cellular)
            testScheduler.runCurrent()
            assertEquals(true, watch.seen.last())
        }

    @Test
    fun `stops listening once nobody collects`() =
        statusTest { watch ->
            assertTrue(shadowOf(connectivity).networkCallbacks.isNotEmpty())
            watch.collection.cancel()
            testScheduler.runCurrent()
            assertTrue(shadowOf(connectivity).networkCallbacks.isEmpty())
        }

    private companion object {
        const val WIFI_ID = 100
        const val CELLULAR_ID = 101
    }
}
