package com.aakash.novafork.sources.net

import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.core.webdav.DavEntry
import com.aakash.novafork.core.webdav.WebDavClient
import com.aakash.novafork.core.webdav.WebDavException
import com.aakash.novafork.data.NetworkBrowser
import com.aakash.novafork.data.NetworkLocation
import com.aakash.novafork.data.PlaybackOptions
import com.aakash.novafork.data.RemoteEntry
import com.aakash.novafork.data.ScannedVideo
import com.aakash.novafork.data.SourceScanner
import com.aakash.novafork.data.db.SourceEntity
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * WebDAV sources (Nextcloud, ownCloud, Apache, NAS servers, rclone…): folders are listed with
 * PROPFIND through core [WebDavClient] (HTTP Basic login), libVLC plays the files over http(s)
 * with the login as the URI's user info, added only at playback ([playUri]) so it is never stored.
 * Posters and fanart next to the videos are picked with core ArtworkRules; loading them needs the
 * login too (see [WebDavAuthInterceptor]).
 */
internal class WebDavSource(private val client: WebDavClient) : NetworkBrowser, SourceScanner, PlaybackOptions {

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
        val location = WebDavRules.location(source)
        return RemoteFolderWalk.scan(
            rootSegments = location.path.split('/').filter { it.isNotEmpty() },
            list = { path -> listAll(source, path) },
            uriOf = { path -> location.child(path).toUri() },
            withArtwork = true,
            onProgress = onProgress,
            onFolderFailure = { _, error ->
                // A folder gone or off limits is skipped; a login refused or a server gone ends the scan.
                val status = (error as? WebDavException)?.status
                if (status == null || status == 401 || status >= 500) throw error
            },
        )
    }

    override suspend fun mediaOptions(source: SourceEntity, uri: String): List<String> = emptyList()

    override suspend fun playUri(source: SourceEntity, uri: String): String =
        WebDavRules.withLogin(source, uri)

    private suspend fun listAll(source: SourceEntity, path: String): List<RemoteEntry> {
        val location = WebDavRules.location(source)
        val folder = location.child(path)
        val entries = try {
            client.list(folder.toUri() + "/", source.username, source.password)
        } catch (e: CancellationException) {
            throw e
        } catch (e: WebDavException) {
            throw e
        } catch (e: IOException) {
            throw IOException(WebDavRules.networkMessage(location, e), e)
        } catch (e: IllegalArgumentException) {
            throw IOException("Not a valid WebDAV address: ${source.location}", e)
        }
        return WebDavRules.entries(location, path, entries)
    }
}

/** The pure part of [WebDavSource]: addresses, logins in URIs, listings, messages. */
internal object WebDavRules {
    fun location(source: SourceEntity): NetworkLocation {
        val location = NetworkLocation.parse(source.location)
        if (location == null || (location.scheme != "https" && location.scheme != "http")) {
            throw IOException("Not a valid WebDAV address: ${source.location}")
        }
        return location
    }

    /** The entries of the folder at [folderPath] below [location]; videos carry their URI. */
    fun entries(location: NetworkLocation, folderPath: String, entries: List<DavEntry>): List<RemoteEntry> =
        entries.mapNotNull { entry ->
            val name = entry.name.trim()
            if (name.isEmpty() || '/' in name) return@mapNotNull null
            val path = if (folderPath.isEmpty()) name else "$folderPath/$name"
            RemoteEntry(
                name = name,
                path = path,
                isDirectory = entry.isCollection,
                sizeBytes = entry.sizeBytes,
                modifiedAt = entry.modifiedAt,
                playUri = if (!entry.isCollection && VideoFiles.isVideo(name)) location.child(path).toUri() else null,
            )
        }

    /**
     * [uri] with the source's login as user info ("https://bob:s%40cret@host/…"), which libVLC's
     * http access sends as Basic authentication. Only for a URI on the source's own server; a
     * source without login, or a URI that already has user info, is left as it is.
     */
    fun withLogin(source: SourceEntity, uri: String): String {
        val user = source.username?.takeIf { it.isNotEmpty() } ?: return uri
        val location = NetworkLocation.parse(source.location) ?: return uri
        val target = NetworkLocation.parse(uri) ?: return uri
        if (!location.covers(target)) return uri
        val separator = uri.indexOf("://")
        val authorityEnd = uri.indexOfAny(charArrayOf('/', '?', '#'), separator + 3).let { if (it < 0) uri.length else it }
        if (uri.substring(separator + 3, authorityEnd).contains('@')) return uri
        val password = source.password?.takeIf { it.isNotEmpty() }
        val userInfo = NetworkLocation.encodeSegment(user) + (password?.let { ":" + NetworkLocation.encodeSegment(it) } ?: "")
        return uri.substring(0, separator + 3) + userInfo + "@" + uri.substring(separator + 3)
    }

    /** A connection failure in words: the server can't be found, reached, trusted, or answered too slowly. */
    fun networkMessage(location: NetworkLocation, error: IOException): String {
        val server = if (location.port != null) "${location.host}:${location.port}" else location.host
        return when (error) {
            is UnknownHostException -> "Can't find ${location.host} on the network"
            is ConnectException, is NoRouteToHostException -> "Can't connect to $server"
            is SocketTimeoutException, is InterruptedIOException -> "$server took too long to answer"
            is SSLException -> "Couldn't make a secure connection to $server (is its certificate trusted, and does it speak https on this port?)"
            else -> error.message?.takeIf { it.isNotBlank() } ?: "Couldn't connect to $server"
        }
    }
}
