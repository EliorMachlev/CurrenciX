package com.eliormachlev.currencix.util

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NetworkStatusLiveDataTest {
    @get:Rule
    val instantTasks = InstantTaskExecutorRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val status = NetworkStatusLiveData(context)
    private val seen = mutableListOf<Boolean>()
    private val observer = Observer<Boolean> { seen += it }

    private val wifi: Network = ShadowNetwork.newInstance(WIFI_ID)
    private val cellular: Network = ShadowNetwork.newInstance(CELLULAR_ID)

    @Before
    fun observe() {
        status.observeForever(observer)
    }

    @After
    fun stopObserving() {
        status.removeObserver(observer)
    }

    private fun callback(): ConnectivityManager.NetworkCallback = shadowOf(connectivity).networkCallbacks.single()

    @Test
    fun `losing one network while another is still validated stays online`() {
        callback().onAvailable(wifi)
        callback().onAvailable(cellular)
        callback().onLost(wifi)
        assertEquals(true, status.value)
    }

    @Test
    fun `losing the last validated network goes offline, and one returning goes back online`() {
        callback().onAvailable(wifi)
        callback().onLost(wifi)
        assertEquals(false, status.value)
        callback().onAvailable(cellular)
        assertEquals(true, status.value)
    }

    @Test
    fun `stops listening once nobody observes`() {
        assertTrue(shadowOf(connectivity).networkCallbacks.isNotEmpty())
        status.removeObserver(observer)
        assertTrue(shadowOf(connectivity).networkCallbacks.isEmpty())
    }

    private companion object {
        const val WIFI_ID = 100
        const val CELLULAR_ID = 101
    }
}
