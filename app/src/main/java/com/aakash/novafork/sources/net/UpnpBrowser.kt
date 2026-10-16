package com.aakash.novafork.sources.net

import com.aakash.novafork.core.parse.NameParser
import com.aakash.novafork.core.parse.ParsedKind
import com.aakash.novafork.data.NetworkBrowser
import com.aakash.novafork.data.RemoteEntry
import com.aakash.novafork.data.ScannedVideo
import com.aakash.novafork.data.SourceScanner
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.sources.MAX_FOLDER_DEPTH
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * UPnP/DLNA media servers through libVLC's UPnP module: [discoverUpnpServers] finds them on the
 * local network, folders are listed by ContentDirectory object id ([RemoteEntry.path] is the
 * container's ObjectID, "" the root) and videos are stored under the stream URL the server gives.
 *
 * A source's location is the server's description URL or its UDN ("uuid:…", also kept as
 * [SourceEntity.serverId]). A UDN is looked up on the network (and remembered while the app
 * runs), so a server whose address changed is found again; a URL that stops answering is looked
 * up again by the UDN when the source has one.
 */
internal class UpnpBrowser(private val vlc: VlcBrowser) : NetworkBrowser, SourceScanner {

    /** UDN (lower case) → description URL of the servers seen while the app runs. */
    private val known = ConcurrentHashMap<String, String>()

    /**
     * The media servers that answer on the local network within [timeoutMs]: name = the server's
     * name, path = its UDN ("uuid:…", or the description URL when it has none) to pass as the
     * address of a NetworkSourceInput of type UPNP, imageUrl = its icon. Empty when none answers.
     */
    suspend fun discoverUpnpServers(timeoutMs: Long = DISCOVERY_MS): List<RemoteEntry> {
        val items = discover(timeoutMs) { false }
        return items.mapNotNull { item ->
            val description = UpnpRules.descriptionUrl(item.uri) ?: return@mapNotNull null
            val udn = item.description?.trim()?.takeIf { UpnpRules.isUdn(it) }
            RemoteEntry(
                name = item.name,
                path = udn ?: description,
                isDirectory = true,
                imageUrl = item.artworkUrl,
            )
        }.distinctBy { it.path }.sortedWith(VlcBrowserRules.BROWSE_ORDER)
    }

    override suspend fun list(source: SourceEntity, path: String): List<RemoteEntry> =
        browse(source, path).mapNotNull { item ->
            when {
                item.kind == VlcItemKind.DIRECTORY -> UpnpRules.objectId(item.uri)?.let { id ->
                    RemoteEntry(name = item.name, path = id, isDirectory = true, imageUrl = item.artworkUrl)
                }
                UpnpRules.isVideoItem(item.name, item.uri, item.isAudio) -> RemoteEntry(
                    name = item.name,
                    path = item.uri,
                    isDirectory = false,
                    playUri = item.uri,
                    imageUrl = item.artworkUrl,
                )
                else -> null
            }
        }

    override suspend fun test(source: SourceEntity): Result<Unit> =
        try {
            browse(source, "")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(if (e is IOException) e else IOException(e.message ?: "Couldn't reach the server", e))
        }

    private class Container(val objectId: String, val titles: List<String>, val depth: Int)

    /**
     * Walks the server's containers breadth first, at most MAX_FOLDER_DEPTH levels deep. Music
     * and photo branches are skipped, and where a container offers a folder view ("Folders",
     * "By Folder") only that view is walked: the other views list the same videos again. A video
     * listed twice keeps its first place.
     */
    override suspend fun scan(source: SourceEntity, onProgress: (found: Int) -> Unit): List<ScannedVideo> {
        val now = System.currentTimeMillis()
        val found = ArrayList<ScannedVideo>()
        val seenVideos = HashSet<String>()
        val seenContainers = HashSet<String>()
        val queue = ArrayDeque<Container>()
        queue.addLast(Container(UpnpRules.ROOT_OBJECT_ID, emptyList(), 0))
        seenContainers += UpnpRules.ROOT_OBJECT_ID
        var opened = 0
        while (queue.isNotEmpty() && opened < MAX_CONTAINERS) {
            currentCoroutineContext().ensureActive()
            val container = queue.removeFirst()
            opened++
            val items = if (container.depth == 0) {
                browse(source, "")
            } else {
                try {
                    browse(source, container.objectId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    // One container failing (a server timing out on a huge view) doesn't end the scan.
                    continue
                }
            }

            val children = items.filter { it.kind == VlcItemKind.DIRECTORY }
                .mapNotNull { item -> UpnpRules.objectId(item.uri)?.let { it to item } }
                .filterNot { (_, item) -> UpnpRules.isSkippedContainer(item.name) }
            val folderView = UpnpRules.folderViewIndex(children.map { it.second.name })
            val walked = if (folderView != null) listOf(children[folderView]) else children
            if (container.depth < MAX_FOLDER_DEPTH) {
                for ((id, item) in walked) {
                    if (seenContainers.add(id)) {
                        queue.addLast(Container(id, container.titles + UpnpRules.folderName(item.name), container.depth + 1))
                    }
                }
            }

            val parentFolders = container.titles.asReversed()
            for (item in items) {
                if (item.kind == VlcItemKind.DIRECTORY || !UpnpRules.isVideoItem(item.name, item.uri, item.isAudio)) continue
                if (!seenVideos.add(item.uri)) continue
                val fileName = UpnpRules.fileName(item.name, item.uri)
                val art = item.artworkUrl
                val isEpisode = art != null && NameParser.parse(fileName, parentFolders).kind == ParsedKind.EPISODE
                found += ScannedVideo(
                    uri = item.uri,
                    fileName = fileName,
                    folder = container.titles.joinToString("/"),
                    parentFolders = parentFolders,
                    dateAdded = now,
                    // The server's art is a poster for films, an episode's own picture for episodes.
                    localPoster = art?.takeUnless { isEpisode },
                    localThumb = art,
                )
            }
            onProgress(found.size)
        }
        return found
    }

    /** Lists container [objectId] ("" = the root), finding the server again by its UDN when needed. */
    private suspend fun browse(source: SourceEntity, objectId: String): List<VlcItem> {
        val id = objectId.ifEmpty { UpnpRules.ROOT_OBJECT_ID }
        val udn = udnOf(source)
        val description = descriptionUrl(source, udn, refresh = false)
        return try {
            listContainer(description, id)
        } catch (e: VlcBrowseException) {
            // The server may have moved to another address or port: look for it by its UDN.
            val moved = udn?.let { descriptionUrl(source, it, refresh = true) }
            if (moved == null || moved == description) throw IOException(failure(source, e), e)
            try {
                listContainer(moved, id)
            } catch (again: VlcBrowseException) {
                throw IOException(failure(source, again), again)
            }
        }
    }

    private suspend fun listContainer(descriptionUrl: String, objectId: String): List<VlcItem> {
        val mrl = UpnpRules.browseMrl(descriptionUrl, objectId)
            ?: throw IOException("Not a UPnP server address: $descriptionUrl")
        return vlc.list(mrl, emptyList())
    }

    private fun udnOf(source: SourceEntity): String? =
        source.serverId?.trim()?.takeIf { UpnpRules.isUdn(it) } ?: source.location.trim().takeIf { UpnpRules.isUdn(it) }

    /** The description URL to browse: the location itself unless that is a UDN or [refresh] asks to look again. */
    private suspend fun descriptionUrl(source: SourceEntity, udn: String?, refresh: Boolean): String {
        val location = source.location.trim()
        if (!UpnpRules.isUdn(location) && !refresh) return location
        val key = (udn ?: return location).lowercase(Locale.ROOT)
        if (refresh) known.remove(key) else known[key]?.let { return it }
        discover(LOOKUP_MS) { found -> found.any { it.description?.trim().equals(key, ignoreCase = true) } }
        return known[key] ?: throw IOException("${source.name} isn't answering on this network")
    }

    /** Runs the UPnP discoverer and remembers the description URL of every server it lists. */
    private suspend fun discover(timeoutMs: Long, enough: (List<VlcItem>) -> Boolean): List<VlcItem> {
        val items = try {
            vlc.discover(UPNP_SERVICE, timeoutMs, enough)
        } catch (e: IllegalStateException) {
            throw IOException("Network discovery isn't available", e)
        }
        for (item in items) {
            val udn = item.description?.trim()?.takeIf { UpnpRules.isUdn(it) } ?: continue
            UpnpRules.descriptionUrl(item.uri)?.let { known[udn.lowercase(Locale.ROOT)] = it }
        }
        return items
    }

    private fun failure(source: SourceEntity, error: VlcBrowseException): String =
        if (error.timedOut) "${source.name} took too long to answer" else "Couldn't browse ${source.name}"

    companion object {
        private const val UPNP_SERVICE = "upnp"

        /** How long the "Add DLNA server" picker listens by default. */
        const val DISCOVERY_MS = 5_000L

        /** How long a source's server is looked for by its UDN. */
        private const val LOOKUP_MS = 8_000L

        /** Bounds a scan of a server with endless virtual views. */
        private const val MAX_CONTAINERS = 2_000
    }
}
