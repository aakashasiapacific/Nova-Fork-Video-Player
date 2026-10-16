package com.aakash.novafork.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.aakash.novafork.ui.model.CastDeviceUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.RendererDiscoverer
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.interfaces.ILibVLC

/**
 * Finds cast devices (Chromecast) on the local network with libVLC's renderer discoverers
 * (mDNS), without Google Play Services, and reports them through [onDevicesChanged].
 *
 * It keeps one reference of its own to each [RendererItem] it lists and lets go of it when the
 * device goes away or discovery stops. Whoever casts to an item takes a reference of its own
 * ([RendererItem.retain]), so stopping discovery never frees the renderer a cast is using.
 * While it runs it holds a Wi-Fi multicast lock (many devices drop mDNS answers without one) and
 * looks again when the default network changes. Main thread only.
 */
internal class CastDiscovery(
    context: Context,
    private val libVLC: ILibVLC,
    private val scope: CoroutineScope,
    private val onDevicesChanged: (List<CastDeviceUi>) -> Unit,
) {
    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wifiManager: WifiManager? = appContext.getSystemService(WifiManager::class.java)
    private val connectivityManager: ConnectivityManager? = appContext.getSystemService(ConnectivityManager::class.java)

    private val discoverers = ArrayList<RendererDiscoverer>()

    /** The devices found, by [RendererItem.name], each with a reference held here. In the order found. */
    private val items = LinkedHashMap<String, RendererItem>()

    /** Devices listed before a rescan that haven't answered since; dropped when it settles. */
    private val stale = HashSet<String>()

    /** Bumped whenever the discoverers are replaced: their late events and retries are ignored. */
    private var generation = 0
    private var startedAt = 0L
    private var startJob: Job? = null
    private var pruneJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** The default network discovery last started on; another one coming up means looking again. */
    private var knownNetwork: Network? = null
    private var released = false

    var isRunning = false
        private set

    /** Starts looking (no-op while running). */
    fun start() {
        if (released || isRunning) return
        isRunning = true
        acquireMulticastLock()
        registerNetworkCallback()
        launchDiscoverers()
    }

    /** Stops looking and empties the list. A renderer in use by a cast stays valid. */
    fun stop() {
        if (!isRunning) return
        isRunning = false
        closeDiscoverers()
        unregisterNetworkCallback()
        releaseMulticastLock()
        for (item in items.values) item.release()
        items.clear()
        publish()
        PlayerDiagnostics.record("cast discovery: stopped")
    }

    /**
     * Asks the network again (starts looking when stopped). Devices already listed stay until
     * the new discoverers have had time to answer for them, so the list doesn't blink.
     */
    fun rescan() {
        if (released) return
        if (!isRunning) {
            start()
            return
        }
        if (SystemClock.elapsedRealtime() - startedAt < MIN_RESCAN_INTERVAL_MS) return
        restartDiscoverers()
    }

    /** The device listed as [id], or null when it is gone. Retain it to keep it beyond the next change. */
    fun find(id: String): RendererItem? = items[id]

    fun release() {
        stop()
        released = true
    }

    private fun restartDiscoverers() {
        closeDiscoverers()
        launchDiscoverers()
        stale.addAll(items.keys)
        if (stale.isEmpty()) return
        val run = generation
        pruneJob = scope.launch {
            delay(RESCAN_SETTLE_MS)
            if (run == generation) pruneStale()
        }
    }

    private fun launchDiscoverers() {
        val run = ++generation
        startedAt = SystemClock.elapsedRealtime()
        val descriptions = try {
            RendererDiscoverer.list(libVLC)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "No renderer discoverers", e)
            null
        }
        val created = ArrayList<RendererDiscoverer>()
        if (descriptions != null) {
            for (description in descriptions) {
                val name = description?.name ?: continue
                val discoverer = try {
                    RendererDiscoverer(libVLC, name)
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Renderer discoverer $name unavailable", e)
                    continue
                }
                discoverer.setEventListener(listenerFor(run))
                discoverers.add(discoverer)
                created.add(discoverer)
            }
        }
        PlayerDiagnostics.record("cast discovery: started with ${created.size} discoverer(s)")
        startDiscoverers(run, created, attempt = 1)
    }

    /** start() fails while the network isn't up yet; those are tried again a few times. */
    private fun startDiscoverers(run: Int, waiting: List<RendererDiscoverer>, attempt: Int) {
        val failed = waiting.filterNot { it.start() }
        if (failed.isEmpty()) return
        if (attempt >= START_ATTEMPTS) {
            PlayerDiagnostics.record("cast discovery: ${failed.size} discoverer(s) didn't start")
            return
        }
        startJob = scope.launch {
            delay(START_RETRY_MS)
            if (run == generation) startDiscoverers(run, failed, attempt + 1)
        }
    }

    private fun closeDiscoverers() {
        generation++
        startJob?.cancel()
        startJob = null
        pruneJob?.cancel()
        pruneJob = null
        stale.clear()
        // libVLC's stop() also detaches the listener and releases the discoverer (and with it
        // the discoverer's own references to its items; the ones held here stay valid).
        for (discoverer in discoverers) {
            if (!discoverer.isReleased) discoverer.stop()
        }
        discoverers.clear()
    }

    private fun listenerFor(run: Int) = object : RendererDiscoverer.EventListener {
        override fun onEvent(event: RendererDiscoverer.Event?) {
            if (event == null) return
            val item = event.item ?: return
            when (event.type) {
                RendererDiscoverer.Event.ItemAdded -> {
                    // The event lets go of its item once this returns: kept, it needs a reference of its own.
                    if (item.retain()) onMainThread { onItemAdded(run, item) }
                }
                RendererDiscoverer.Event.ItemDeleted -> {
                    val name = item.name ?: return
                    onMainThread { onItemDeleted(run, name) }
                }
            }
        }
    }

    private fun onItemAdded(run: Int, item: RendererItem) {
        val id = item.name
        if (released || !isRunning || run != generation || id.isNullOrEmpty()) {
            item.release()
            return
        }
        stale.remove(id)
        // Announced again (new address, another discoverer): the newest one is the one to use.
        val previous = items.put(id, item)
        if (previous != null) {
            previous.release()
        } else {
            PlayerDiagnostics.record("cast discovery: found a ${item.type ?: "renderer"}")
        }
        publish()
    }

    private fun onItemDeleted(run: Int, id: String) {
        if (!isRunning || run != generation) return
        val item = items.remove(id) ?: return
        stale.remove(id)
        item.release()
        publish()
    }

    private fun pruneStale() {
        var changed = false
        for (id in stale) {
            val item = items.remove(id) ?: continue
            item.release()
            changed = true
        }
        stale.clear()
        if (changed) publish()
    }

    private fun publish() {
        onDevicesChanged(items.map { (id, item) -> CastDeviceUi(id = id, name = displayNameOf(item, id)) })
    }

    private fun onMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    private fun acquireMulticastLock() {
        val wifi = wifiManager ?: return
        if (multicastLock?.isHeld == true) return
        // Without the permission discovery still runs; some devices then filter the mDNS answers out.
        val granted = appContext.checkSelfPermission(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        try {
            val lock = multicastLock ?: wifi.createMulticastLock(MULTICAST_LOCK_TAG).also {
                it.setReferenceCounted(false)
                multicastLock = it
            }
            lock.acquire()
        } catch (e: SecurityException) {
            Log.w(TAG, "Multicast lock refused", e)
        }
    }

    private fun releaseMulticastLock() {
        val lock = multicastLock ?: return
        try {
            if (lock.isHeld) lock.release()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't release the multicast lock", e)
        }
    }

    private fun registerNetworkCallback() {
        val connectivity = connectivityManager ?: return
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Called on a connectivity thread.
                val self = this
                mainHandler.post { onNetworkAvailable(self, network) }
            }
        }
        try {
            knownNetwork = connectivity.activeNetwork
            connectivity.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: RuntimeException) {
            // SecurityException without the network state permission, or too many callbacks in the app.
            Log.w(TAG, "No network callback for cast discovery", e)
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        knownNetwork = null
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Network callback was not registered", e)
        }
    }

    /** Registering reports the current network too: only another one means looking again. */
    private fun onNetworkAvailable(callback: ConnectivityManager.NetworkCallback, network: Network) {
        if (callback !== networkCallback || !isRunning || network == knownNetwork) return
        knownNetwork = network
        PlayerDiagnostics.record("cast discovery: network changed, looking again")
        restartDiscoverers()
    }

    companion object {
        private const val TAG = "CastDiscovery"
        private const val MULTICAST_LOCK_TAG = "NovaFork:cast-discovery"
        private const val START_ATTEMPTS = 5
        private const val START_RETRY_MS = 1_000L
        private const val MIN_RESCAN_INTERVAL_MS = 2_000L

        /** How long a rescan waits for devices already listed to answer again before dropping them. */
        private const val RESCAN_SETTLE_MS = 6_000L

        /** What the user sees for [item]: its friendly name, else [fallback]. */
        fun displayNameOf(item: RendererItem, fallback: String): String =
            item.displayName?.takeIf { it.isNotBlank() } ?: item.name?.takeIf { it.isNotBlank() } ?: fallback
    }
}
