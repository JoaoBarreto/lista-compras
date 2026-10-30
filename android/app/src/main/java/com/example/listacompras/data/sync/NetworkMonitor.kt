package com.example.listacompras.data.sync

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Whether the device has a usable underlying network. An interface so tests can fake it. */
interface NetworkMonitor {
    val online: Flow<Boolean>
}

/**
 * VPN networks are excluded on purpose: Tailscale runs as a VPN, so the *default* network can stay
 * "available" in airplane mode. Only a real (non-VPN) network with internet counts.
 */
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    override val online: Flow<Boolean> = callbackFlow {
        val networks = mutableSetOf<Network>()

        fun hasUsableNetwork(network: Network): Boolean =
            connectivity.getNetworkCapabilities(network)?.let {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            } ?: false

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(networks) {
                    networks += network
                    trySend(true)
                }
            }

            override fun onLost(network: Network) {
                synchronized(networks) {
                    networks -= network
                    trySend(networks.isNotEmpty())
                }
            }
        }
        // No callback fires when there is no network, so state the initial value explicitly.
        synchronized(networks) {
            networks += connectivity.allNetworks.filter(::hasUsableNetwork)
            trySend(networks.isNotEmpty())
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        connectivity.registerNetworkCallback(request, callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
