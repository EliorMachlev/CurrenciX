package de.salomax.currencies.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.LiveData

// Emits true when the device has a validated internet connection, false when
// it does not. Uses [ConnectivityManager.registerNetworkCallback] so
// transitions surface promptly (screen on, wifi flap, plane-mode toggle)
// without polling. Initial value is derived from the currently-active network
// so observers get a value on the first frame.
class NetworkStatusLiveData(
    context: Context,
) : LiveData<Boolean>() {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val request =
        NetworkRequest
            .Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = postValue(true)

            override fun onLost(network: Network) = postValue(currentlyOnline())

            override fun onUnavailable() = postValue(false)
        }

    private fun currentlyOnline(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun onActive() {
        super.onActive()
        postValue(currentlyOnline())
        connectivityManager.registerNetworkCallback(request, callback)
    }

    override fun onInactive() {
        connectivityManager.unregisterNetworkCallback(callback)
        super.onInactive()
    }
}
