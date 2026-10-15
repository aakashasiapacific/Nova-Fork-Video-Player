package com.aakash.novafork.sources.net

import com.aakash.novafork.data.NetworkBrowser
import com.aakash.novafork.data.NetworkLocation
import com.aakash.novafork.data.NetworkSources
import com.aakash.novafork.data.PlaybackOptions
import com.aakash.novafork.data.RemoteEntry
import com.aakash.novafork.data.ScannedVideo
import com.aakash.novafork.data.SourceScanner
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException

/**
 * FTP (ftp, ftps, ftpes), SFTP and NFS sources: listed and played by libVLC's own modules, the
 * login going along as media options (":ftp-user"/":ftp-pwd", ":sftp-user"/":sftp-pwd"; NFS has
 * none). Videos are stored under the source's location plus their percent-encoded path.
 *
 * Artwork next to the videos is not collected: the app's image loader can't read these servers.
 */
internal class VlcShareBrowser(
    private val type: SourceType,
    private val vlc: VlcBrowser,
) : NetworkBrowser, SourceScanner, PlaybackOptions {

    init {
        require(type == SourceType.FTP || type == SourceType.SFTP || type == SourceType.NFS) { "Not a libVLC share type: $type" }
    }

    override suspend fun list(source: SourceEntity, path: String): List<RemoteEntry> =
        listAll(source, path).filter { it.isDirectory || it.playUri != null }.sortedWith(VlcBrowserRules.BROWSE_ORDER)

    override suspend fun test(source: SourceEntity): Result<Unit> =
        try {
            listAll(source, "")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(if (e is IOException) e else IOException(e.message ?: "Couldn't connect", e))
        }

    override suspend fun scan(source: SourceEntity, onProgress: (found: Int) -> Unit): List<ScannedVideo> {
        val location = location(source)
        return RemoteFolderWalk.scan(
            rootSegments = location.path.split('/').filter { it.isNotEmpty() },
            list = { path -> listAll(source, path) },
            uriOf = { path -> location.child(path).toUri() },
            withArtwork = false,
            onProgress = onProgress,
            onFolderFailure = { _, error ->
                // A folder that can't be opened is skipped; a server that stopped answering ends the scan.
                if (error is ServerUnreachableException) throw error
            },
        )
    }

    override suspend fun mediaOptions(source: SourceEntity, uri: String): List<String> = VlcBrowserRules.loginOptions(source)

    /** Folders and every file libVLC lists in [path] (its default ignore list drops images and subtitles). */
    private suspend fun listAll(source: SourceEntity, path: String): List<RemoteEntry> {
        val location = location(source)
        if (type == SourceType.SFTP && source.username.isNullOrBlank()) throw IOException("Enter the user name")
        val folder = location.child(path)
        val items = try {
            vlc.list(folder.toUri(), VlcBrowserRules.loginOptions(source))
        } catch (e: VlcBrowseException) {
            throw failure(location, folder, e)
        }
        return VlcBrowserRules.entries(location, path, items)
    }

    private fun location(source: SourceEntity): NetworkLocation {
        val location = NetworkLocation.parse(source.location)
        if (location == null || location.scheme !in NetworkSources.schemes(type)) {
            throw IOException("Not a valid ${NetworkSources.label(type)} address: ${source.location}")
        }
        return location
    }

    /** The server's address, port and folder are fine, but libVLC still couldn't list it. */
    private class ServerUnreachableException(message: String, cause: Throwable) : IOException(message, cause)

    /**
     * libVLC only says "failed" or "timed out": a connection attempt tells a server that can't be
     * reached ([ServerUnreachableException]) from a refused login or a missing folder.
     */
    private suspend fun failure(location: NetworkLocation, folder: NetworkLocation, error: VlcBrowseException): IOException {
        val server = serverName(location)
        return when (probe(location.host, port(location))) {
            Probe.UNKNOWN_HOST -> ServerUnreachableException("Can't find ${location.host} on the network", error)
            Probe.UNREACHABLE -> ServerUnreachableException("Can't connect to $server", error)
            Probe.OK -> {
                val place = if (folder.path.isEmpty()) server else folder.toUri()
                val message = when {
                    error.timedOut -> "$place took too long to answer"
                    type == SourceType.NFS -> "Couldn't open ${folder.toUri()}: check that the folder is exported to this device"
                    else -> "Couldn't open $place: check the user name, password and folder"
                }
                IOException(message, error)
            }
        }
    }

    private fun port(location: NetworkLocation): Int = location.effectivePort ?: NFS_PORT

    private fun serverName(location: NetworkLocation): String =
        if (location.port != null) "${location.host}:${location.port}" else location.host

    private enum class Probe { OK, UNKNOWN_HOST, UNREACHABLE }

    private suspend fun probe(host: String, port: Int): Probe = withContext(Dispatchers.IO) {
        try {
            Socket().use { it.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS) }
            Probe.OK
        } catch (e: UnknownHostException) {
            Probe.UNKNOWN_HOST
        } catch (e: IOException) {
            Probe.UNREACHABLE
        } catch (e: IllegalArgumentException) {
            Probe.UNREACHABLE
        }
    }

    private companion object {
        /** NFS has no URI default port; its service listens here. */
        const val NFS_PORT = 2049
        const val PROBE_TIMEOUT_MS = 5_000
    }
}
