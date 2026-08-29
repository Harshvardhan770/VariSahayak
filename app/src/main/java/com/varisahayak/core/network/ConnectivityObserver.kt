package com.varisahayak.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports whether the device currently has validated internet access.
 *
 * This drives UI messaging only — it never gates a write. Incident creation always
 * succeeds locally regardless of what this reports, and WorkManager's own
 * NetworkType.CONNECTED constraint is what actually schedules sync.
 */
interface ConnectivityObserver {
    val isOnline: Flow<Boolean>
    fun isCurrentlyOnline(): Boolean
}

@Singleton
class AndroidConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConnectivityObserver {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(hasValidatedInternet())
            }

            override fun onLost(network: Network) {
                trySend(hasValidatedInternet())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                trySend(hasValidatedInternet())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)
        trySend(hasValidatedInternet())

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    override fun isCurrentlyOnline(): Boolean = hasValidatedInternet()

    /**
     * NET_CAPABILITY_VALIDATED matters on the route: a phone can be attached to a captive
     * portal or a mast with no backhaul and still report a connected network.
     */
    private fun hasValidatedInternet(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
