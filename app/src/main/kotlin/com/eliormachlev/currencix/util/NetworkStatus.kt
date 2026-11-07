package com.eliormachlev.currencix.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.util.concurrent.ConcurrentHashMap

// Emits true when the device has a validated internet connection, false when
// it does not. Uses [ConnectivityManager.registerNetworkCallback] so
// transitions surface promptly (screen on, wifi flap, plane-mode toggle)
// without polling. The first value is derived from the currently-active
// network so a collector gets one at once. The callback is registered only
// while the flow is collected, and unregistered when the collection ends.
//
// The callbacks keep their own set of the validated networks instead of
// asking ConnectivityManager inside them: what it answers during a callback
// can lag behind it (still the network just lost, or not yet the one taking
// over), which left the status stuck until something else changed.
fun networkStatus(context: Context): Flow<Boolean> {
    val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return callbackFlow {
        // Every network that currently meets the request; online while any
        // does. Per collection: registering reports every network that
        // already qualifies, so the set is rebuilt from scratch each time.
        val validated: MutableSet<Network> = ConcurrentHashMap.newKeySet()
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    validated += network
                    trySend(true)
                }

                override fun onLost(network: Network) {
                    validated -= network
                    trySend(validated.isNotEmpty())
                }
            }
        trySend(connectivityManager.currentlyOnline())
        connectivityManager.registerNetworkCallback(validatedInternet(), callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.conflate()
}

private fun validatedInternet(): NetworkRequest =
    NetworkRequest
        .Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        .build()

private fun ConnectivityManager.currentlyOnline(): Boolean {
    val active = activeNetwork ?: return false
    val caps = getNetworkCapabilities(active) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
