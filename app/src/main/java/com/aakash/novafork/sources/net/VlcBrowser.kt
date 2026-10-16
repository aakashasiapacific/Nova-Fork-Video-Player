package com.aakash.novafork.sources.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Log
import com.aakash.novafork.player.VlcEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaDiscoverer
import org.videolan.libvlc.MediaList
import org.videolan.libvlc.interfaces.IMedia
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** libVLC could not list a folder: [status] is an IMedia.ParsedStatus value (0: parsing never started). */
internal class VlcBrowseException(val status: Int, message: String) : IOException(message) {
    val timedOut: Boolean get() = status == IMedia.ParsedStatus.Timeout
}

/**
 * Lists network folders through libVLC's own access modules (ftp, sftp, nfs, upnp), the way VLC
 * for Android browses them: the folder's MRL is parsed with network access and its sub-items are
 * the entries. Also runs libVLC's service discoverers (UPnP servers on the local network).
 *
 * Every media and list taken from libVLC is released before a call returns, cancelled or not.
 */
internal class VlcBrowser(context: Context) {
    private val appContext: Context = context.applicationContext

    /**
     * The entries of the folder at [mrl]; [options] are libVLC media options (a login). Throws
     * [VlcBrowseException] when libVLC can't open or list it within [timeoutMs].
     */
    suspend fun list(mrl: String, options: List<String>, timeoutMs: Int = LIST_TIMEOUT_MS): List<VlcItem> {
        val libVLC = libVLC()
        return withContext(Dispatchers.IO) {
            val media = Media(libVLC, Uri.parse(mrl))
            try {
                for (option in options) media.addOption(option)
                val status = withTimeoutOrNull(timeoutMs.toLong() + PARSE_GRACE_MS) { parse(media, timeoutMs) }
                    ?: IMedia.ParsedStatus.Timeout
                if (status != IMedia.ParsedStatus.Done) {
                    val reason = if (status == IMedia.ParsedStatus.Timeout) "No answer in time" else "Couldn't open the folder"
                    throw VlcBrowseException(status, reason)
                }
                ensureActive()
                subItems(media)
            } finally {
                // Releasing also cancels a parse that is still running.
                media.release()
            }
        }
    }

    /**
     * Runs libVLC's [service] discoverer ("upnp") for up to [durationMs] and returns what it found,
     * sooner once [enough] accepts the items so far. Holds a Wi-Fi multicast lock meanwhile: many
     * devices drop the multicast answers without one.
     */
    suspend fun discover(service: String, durationMs: Long, enough: (List<VlcItem>) -> Boolean = { false }): List<VlcItem> {
        val libVLC = libVLC()
        return withContext(Dispatchers.IO) {
            val lock = acquireMulticastLock()
            try {
                // Releasing the discoverer stops it (when it started) and lets go of its list.
                val discoverer = MediaDiscoverer(libVLC, service)
                try {
                    val mediaList = discoverer.mediaList
                    try {
                        if (!discoverer.start()) throw IOException("Network discovery isn't available")
                        val started = System.currentTimeMillis()
                        var found: List<VlcItem>
                        do {
                            delay(DISCOVERY_POLL_MS)
                            found = readItems(mediaList)
                        } while (!enough(found) && System.currentTimeMillis() - started < durationMs)
                        found
                    } finally {
                        mediaList.release()
                    }
                } finally {
                    discoverer.release()
                }
            } finally {
                releaseMulticastLock(lock)
            }
        }
    }

    /** The process-wide instance; created on the main thread, as VlcEngine asks. */
    private suspend fun libVLC(): LibVLC = withContext(Dispatchers.Main.immediate) { VlcEngine.libVLC(appContext) }

    /** Parses [media] with network access; returns the IMedia.ParsedStatus it ended with. */
    private suspend fun parse(media: Media, timeoutMs: Int): Int = suspendCancellableCoroutine { continuation ->
        val finished = AtomicBoolean(false)
        media.setEventListener(
            object : IMedia.EventListener {
                override fun onEvent(event: IMedia.Event?) {
                    if (event == null || event.type != IMedia.Event.ParsedChanged) return
                    if (finished.compareAndSet(false, true)) continuation.resume(event.parsedStatus)
                }
            },
        )
        if (!media.parseAsync(IMedia.Parse.ParseNetwork, timeoutMs) && finished.compareAndSet(false, true)) {
            continuation.resume(IMedia.ParsedStatus.Failed)
        }
    }

    private fun subItems(media: Media): List<VlcItem> {
        val list = media.subItems()
        return try {
            readItems(list)
        } finally {
            list.release()
        }
    }

    private fun readItems(list: MediaList): List<VlcItem> {
        val count = list.count
        val items = ArrayList<VlcItem>(count)
        for (index in 0 until count) {
            val item = try {
                list.getMediaAt(index)
            } catch (e: IndexOutOfBoundsException) {
                // The list shrank while being read (a discoverer dropped a server).
                break
            }
            try {
                toItem(item)?.let { items += it }
            } finally {
                item.release()
            }
        }
        return items
    }

    private fun toItem(media: IMedia): VlcItem? {
        val uri = media.uri?.toString()?.takeIf { it.isNotEmpty() } ?: return null
        val name = media.getMeta(IMedia.Meta.Title)?.takeIf { it.isNotBlank() }
            ?: Uri.decode(uri.substringBefore('?').trimEnd('/').substringAfterLast('/'))
        val kind = when (media.type) {
            IMedia.Type.Directory -> VlcItemKind.DIRECTORY
            IMedia.Type.File -> VlcItemKind.FILE
            else -> VlcItemKind.UNKNOWN
        }
        val artwork = media.getMeta(IMedia.Meta.ArtworkURL)
            ?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        return VlcItem(
            uri = uri,
            name = name,
            kind = kind,
            artworkUrl = artwork,
            description = media.getMeta(IMedia.Meta.Description)?.takeIf { it.isNotBlank() },
            isAudio = !media.getMeta(IMedia.Meta.Album).isNullOrEmpty() || !media.getMeta(IMedia.Meta.TrackNumber).isNullOrEmpty(),
        )
    }

    private fun acquireMulticastLock(): WifiManager.MulticastLock? {
        val granted = appContext.checkSelfPermission(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val wifi = appContext.getSystemService(WifiManager::class.java) ?: return null
        return try {
            wifi.createMulticastLock(MULTICAST_LOCK_TAG).also {
                it.setReferenceCounted(false)
                it.acquire()
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Multicast lock refused", e)
            null
        }
    }

    private fun releaseMulticastLock(lock: WifiManager.MulticastLock?) {
        if (lock == null) return
        try {
            if (lock.isHeld) lock.release()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't release the multicast lock", e)
        }
    }

    companion object {
        private const val TAG = "VlcBrowser"
        private const val MULTICAST_LOCK_TAG = "NovaFork:upnp"

        /** A big folder on a slow server can take a while; libVLC gives up after this. */
        const val LIST_TIMEOUT_MS = 30_000

        /** Waits this much longer for libVLC's own timeout before giving up on its answer. */
        private const val PARSE_GRACE_MS = 5_000L
        private const val DISCOVERY_POLL_MS = 250L
    }
}
