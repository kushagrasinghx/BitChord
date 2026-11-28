package com.music.bitchord.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observes the device's default network connectivity and exposes a reactive
 * [isOffline] state for UI and playback components.
 */
object NetworkConnectivityMonitor {

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    @Volatile
    private var initialized = false
    private var connectivityManager: ConnectivityManager? = null

    fun init(context: Context) {
        if (initialized) return
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        connectivityManager = manager
        initialized = true
        _isOffline.value = computeOffline(manager)
        runCatching {
            manager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _isOffline.value = computeOffline(manager)
                    }

                    override fun onLost(network: Network) {
                        _isOffline.value = computeOffline(manager)
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        _isOffline.value = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    }
                },
            )
        }
    }

    fun isCurrentlyOffline(): Boolean {
        val manager = connectivityManager ?: return _isOffline.value
        val offline = computeOffline(manager)
        _isOffline.value = offline
        return offline
    }

    private fun computeOffline(manager: ConnectivityManager): Boolean {
        val capabilities = runCatching { manager.getNetworkCapabilities(manager.activeNetwork) }.getOrNull()
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true
    }
}
