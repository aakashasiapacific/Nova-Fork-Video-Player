package com.aakash.novafork.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log

private const val TAG = "Connectivity"

/**
 * Whether the device can reach the internet: its default network is one Android has validated.
 * When the system can't tell (no service, permission trouble) it answers online, so a quirk
 * never blocks lookups for good.
 */
internal class Connectivity(context: Context) {
    private val manager: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    val isOnline: Boolean
        get() {
            val manager = manager ?: return true
            return try {
                val network = manager.activeNetwork ?: return false
                manager.getNetworkCapabilities(network)?.isValidated() ?: false
            } catch (e: SecurityException) {
                true
            }
        }

    /** Calls [onOnline] on a system thread whenever the default network is (re)validated. Call once. */
    fun whenValidated(onOnline: () -> Unit) {
        val manager = manager ?: return
        try {
            manager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        if (networkCapabilities.isValidated()) onOnline()
                    }
                },
            )
        } catch (e: RuntimeException) {
            // SecurityException, or too many callbacks for this app: lookups still retry on refresh.
            Log.w(TAG, "Can't watch the network", e)
        }
    }

    private fun NetworkCapabilities.isValidated(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
