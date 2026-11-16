package com.aakash.novafork.ui.dialogs

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aakash.novafork.data.db.SourceType
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A server seen on the local network: what to show and what to fill in. */
data class NearbyServer(val name: String, val host: String, val port: Int?)

/**
 * "On your Wi-Fi": servers of [type] the phone can see, as chips; tapping one fills in its address
 * (and port). File servers and Plex announce themselves over mDNS; Jellyfin and Emby answer a
 * broadcast question on UDP 7359. Nothing shows while none is found.
 */
@Composable
internal fun NearbyServers(type: SourceType, onPick: (NearbyServer) -> Unit) {
    val context = LocalContext.current
    val found = remember(type) { mutableStateListOf<NearbyServer>() }
    fun add(server: NearbyServer) {
        if (found.none { it.host == server.host && it.port == server.port }) found += server
    }
    val serviceType = mdnsType(type)
    if (serviceType != null) {
        DisposableEffect(type) {
            val discovery = MdnsDiscovery(context, serviceType) { server -> add(server) }
            discovery.start()
            onDispose { discovery.stop() }
        }
    }
    val probe = udpProbe(type)
    if (probe != null) {
        LaunchedEffect(type) { askOverUdp(probe).forEach(::add) }
    }
    if (found.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("On your Wi-Fi", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            found.forEach { server ->
                AssistChip(onClick = { onPick(server) }, label = { Text("${server.name} · ${server.host}") })
            }
        }
    }
}

private fun mdnsType(type: SourceType): String? = when (type) {
    SourceType.SMB -> "_smb._tcp."
    SourceType.FTP -> "_ftp._tcp."
    SourceType.SFTP -> "_sftp-ssh._tcp."
    SourceType.WEBDAV -> "_webdav._tcp."
    SourceType.NFS -> "_nfs._tcp."
    SourceType.PLEX -> "_plexmediasvr._tcp."
    else -> null
}

private fun udpProbe(type: SourceType): String? = when (type) {
    SourceType.JELLYFIN -> "Who is JellyfinServer?"
    SourceType.EMBY -> "who is EmbyServer?"
    else -> null
}

/** Broadcasts [question] on UDP 7359 and collects the JSON answers ({"Address": "http://…", "Name": "…"}) for two seconds. */
private suspend fun askOverUdp(question: String): List<NearbyServer> = withContext(Dispatchers.IO) {
    val servers = ArrayList<NearbyServer>()
    try {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 2_000
            val data = question.toByteArray()
            socket.send(DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), 7359))
            val buffer = ByteArray(4_096)
            while (true) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    break
                }
                val json = JSONObject(String(packet.data, 0, packet.length))
                val address = json.optString("Address").ifBlank { "http://${packet.address.hostAddress}:8096" }
                val uri = android.net.Uri.parse(address)
                val host = uri.host ?: packet.address.hostAddress ?: continue
                servers += NearbyServer(json.optString("Name").ifBlank { host }, host, uri.port.takeIf { it > 0 })
            }
        }
    } catch (e: Exception) {
        // No network or broadcast refused: nothing found.
    }
    servers
}

/** mDNS discovery of one service type, resolving each service it sees (one at a time, as Android wants). */
private class MdnsDiscovery(context: Context, private val serviceType: String, private val onFound: (NearbyServer) -> Unit) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var started = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            main.post {
                pending.addLast(serviceInfo)
                next()
            }
        }
    }

    fun start() {
        val manager = nsd ?: return
        try {
            manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            started = true
        } catch (e: Exception) {
            started = false
        }
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            nsd?.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            // Already stopped.
        }
    }

    @Suppress("DEPRECATION")
    private fun next() {
        val manager = nsd ?: return
        if (resolving || !started) return
        val info = pending.removeFirstOrNull() ?: return
        resolving = true
        try {
            manager.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        main.post {
                            resolving = false
                            next()
                        }
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.host?.hostAddress
                        main.post {
                            resolving = false
                            if (host != null && started) onFound(NearbyServer(serviceInfo.serviceName, host, serviceInfo.port.takeIf { it > 0 }))
                            next()
                        }
                    }
                },
            )
        } catch (e: Exception) {
            resolving = false
        }
    }
}
