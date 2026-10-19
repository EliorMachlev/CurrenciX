package com.eliormachlev.currencix.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.LiveData
import java.util.concurrent.ConcurrentHashMap

// Emits true when the device has a validated internet connection, false when
// it does not. Uses [ConnectivityManager.registerNetworkCallback] so
// transitions surface promptly (screen on, wifi flap, plane-mode toggle)
// without polling. Initial value is derived from the currently-active network
// so observers get a value on the first frame.
//
// The callbacks keep their own set of the validated networks instead of
// asking ConnectivityManager inside them: what it answers during a callback
// can lag behind it (still the network just lost, or not yet the one taking
// over), which left the status stuck until something else changed.
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

    // Every network that currently meets [request]; online while any does.
    private val validated: MutableSet<Network> = ConcurrentHashMap.newKeySet()

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                validated += network
                postValue(true)
            }

            override fun onLost(network: Network) {
                validated -= network
                postValue(validated.isNotEmpty())
            }
        }

    private fun currentlyOnline(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun onActive() {
        super.onActive()
        // Registering reports every network that already qualifies, so the
        // set is rebuilt from scratch each time.
        validated.clear()
        value = currentlyOnline()
        connectivityManager.registerNetworkCallback(request, callback)
    }

    override fun onInactive() {
        connectivityManager.unregisterNetworkCallback(callback)
        super.onInactive()
    }
}
