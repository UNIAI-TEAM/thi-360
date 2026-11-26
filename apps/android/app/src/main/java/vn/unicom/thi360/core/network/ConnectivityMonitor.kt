package vn.unicom.thi360.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ConnectivityMonitor {
    val online: StateFlow<Boolean>
}

class MutableConnectivityMonitor(initialOnline: Boolean = true) : ConnectivityMonitor {
    private val _online = MutableStateFlow(initialOnline)
    override val online: StateFlow<Boolean> = _online.asStateFlow()

    fun setOnline(isOnline: Boolean) {
        _online.value = isOnline
    }
}

class AndroidConnectivityMonitor(
    context: Context,
) : ConnectivityMonitor, AutoCloseable {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val availableNetworks = ConcurrentHashMap.newKeySet<Network>()
    private val _online = MutableStateFlow(computeCurrentOnline())
    override val online: StateFlow<Boolean> = _online.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = connectivityManager.getNetworkCapabilities(network)
            if (caps == null || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                availableNetworks.add(network)
                _online.value = true
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                availableNetworks.add(network)
                _online.value = true
            } else {
                availableNetworks.remove(network)
                _online.value = availableNetworks.isNotEmpty() || computeCurrentOnline()
            }
        }

        override fun onLost(network: Network) {
            availableNetworks.remove(network)
            _online.value = availableNetworks.isNotEmpty() || computeCurrentOnline()
        }

        override fun onUnavailable() {
            availableNetworks.clear()
            _online.value = false
        }
    }

    init {
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
        }
    }

    private fun computeCurrentOnline(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun close() {
        runCatching {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}
