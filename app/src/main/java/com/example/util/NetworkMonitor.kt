package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object NetworkMonitor {
    // Fail closed until Android reports a validated network. This prevents a
    // Wi-Fi connection without real internet from being treated as online.
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline

    @Volatile
    private var isMonitoring = false

    @Volatile
    private var connectivityManager: ConnectivityManager? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            updateFromCapabilities(capabilities)
        }

        override fun onAvailable(network: Network) {
            // Do not mark online from onAvailable alone. Android documents that
            // NET_CAPABILITY_INTERNET means the network is configured for internet,
            // while NET_CAPABILITY_VALIDATED is the actual validated public access.
            val cm = connectivityManager ?: return
            updateFromCapabilities(cm.getNetworkCapabilities(network))
        }

        override fun onLost(network: Network) {
            // Re-check the current default network because another network may have
            // taken over at the same time.
            val cm = connectivityManager ?: run {
                _isOnline.value = false
                return
            }
            val active = cm.activeNetwork
            val capabilities = active?.let { cm.getNetworkCapabilities(it) }
            updateFromCapabilities(capabilities)
        }
    }

    fun startMonitoring(context: Context) {
        if (isMonitoring) return

        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: run {
                _isOnline.value = false
                return
            }

        connectivityManager = cm
        _isOnline.value = isCurrentlyOnline(cm)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, callback)
            isMonitoring = true
        } catch (e: Exception) {
            _isOnline.value = isCurrentlyOnline(cm)
            android.util.Log.e("NetworkMonitor", "Failed to register network callback", e)
        }
    }

    private fun updateFromCapabilities(capabilities: NetworkCapabilities?) {
        _isOnline.value = capabilities?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } ?: false
    }

    private fun isCurrentlyOnline(connectivityManager: ConnectivityManager): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
