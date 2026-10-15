package com.aakash.novafork.core.plex

import com.aakash.novafork.core.jellyfin.ServerHttp
import kotlinx.serialization.DeserializationStrategy
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Client for one Plex Media Server at [baseUrl] ("http://192.168.1.5:32400"), signed in with
 * [token] (X-Plex-Token: the account's, or the server's own for a shared server; null for a server
 * that lets this network in without one). JSON answers (Accept: application/json). Main-safe and
 * cancellable; every failure is a [PlexException] with a message fit for the user. The token goes
 * in a header, never in the URLs of API calls; only [withToken] and [imageUrl] put it into a URL
 * (for the player and the image loader, which can't send headers).
 */
class PlexServerClient(
    private val http: OkHttpClient,
    baseUrl: String,
    private val info: PlexClientInfo,
    private val token: String?,
    /** Waits before retrying a GET that failed on the network or with a 5xx; its size is the number of retries. */
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
) {
    private val address: String = baseUrl.trim()
    private val root: HttpUrl? = ServerHttp.baseUrl(address)

    /** The server's host, for messages. */
    val host: String = root?.host ?: address

    /** GET /identity (needs no token): the server's machineIdentifier and version. */
    suspend fun identity(): PlexMediaContainer {
        val body = send(url("identity")) { code -> if (code == 404) notAServer(404) else null }
        val container = try {
            ServerHttp.json.decodeFromString(PlexResponse.serializer(), body).container
        } catch (e: IllegalArgumentException) {
            throw notAServer(0)
        }
        if (container.machineIdentifier.isNullOrBlank()) throw notAServer(0)
        return container
    }

    /** GET / : the server's name and id; fails with 401 when [token] doesn't open it. */
    suspend fun serverInfo(): PlexMediaContainer = get(url())

    /** GET /library/sections: every library, of any type. */
    suspend fun sections(): List<PlexDirectory> = get(url("library", "sections")).directories.filter { it.key.isNotBlank() }

    /**
     * Every item of [type] ([TYPE_MOVIE], [TYPE_SHOW], [TYPE_EPISODE]…) in section [sectionKey],
     * with their guids, fetched [pageSize] at a time; [onPage] gets the running count.
     */
    suspend fun sectionItems(
        sectionKey: String,
        type: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        onPage: (fetched: Int) -> Unit = {},
    ): List<PlexMetadata> {
        val query = listOf("type" to type.toString(), "includeGuids" to "1")
        return paged(pageSize, onPage) { start, size ->
            url(listOf("library", "sections", sectionKey, "all"), query + paging(start, size))
        }.metadata
    }

    /**
     * The entries below [key], a path the server handed out ("/library/sections/1/all",
     * "/library/metadata/7/children"), all pages of them. Only /library/ paths are taken.
     */
    suspend fun children(key: String, pageSize: Int = DEFAULT_PAGE_SIZE): PlexMediaContainer {
        val path = key.trim()
        if (!path.startsWith("/library/") || ".." in path) throw PlexException(0, "That isn't a folder on $host")
        return paged(pageSize, onPage = {}) { start, size -> keyUrl(path, paging(start, size)) }
    }

    /**
     * A full image URL for a server path ([PlexMetadata.thumb], art…), scaled by the server's photo
     * transcoder to [width]×[height], with the token so an image loader can fetch it as it is. A
     * path that is already an http(s) URL (some agents link outside images) is returned unchanged.
     */
    fun imageUrl(path: String?, width: Int, height: Int): String? {
        val source = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)) return source
        val query = listOf(
            "width" to width.toString(),
            "height" to height.toString(),
            "minSize" to "1",
            "upscale" to "1",
            "url" to source,
            TOKEN_PARAM to token,
        )
        return urlOrNull(listOf("photo", ":", "transcode"), query)?.toString()
    }

    /** The URL of a file ([PlexPart.key], "/library/parts/…/file.mkv") without the token (see [withToken]). */
    fun partUrl(partKey: String): String? {
        val path = partKey.trim()
        if (!path.startsWith("/")) return null
        return keyUrl(path, emptyList())?.toString()
    }

    /** [url] with the token and this app's identity, for a player that can't send headers. */
    fun withToken(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        val builder = parsed.newBuilder()
            .removeAllQueryParameters(TOKEN_PARAM)
            .removeAllQueryParameters(CLIENT_PARAM)
            .removeAllQueryParameters(PRODUCT_PARAM)
            .addQueryParameter(CLIENT_PARAM, ServerHttp.headerValue(info.clientIdentifier))
            .addQueryParameter(PRODUCT_PARAM, ServerHttp.headerValue(info.product))
        if (token != null) builder.addQueryParameter(TOKEN_PARAM, token)
        return builder.build().toString()
    }

    /** GET /:/timeline: where the player is in [ratingKey], so the server shows it playing and keeps the resume point. */
    suspend fun timeline(ratingKey: String, state: PlexPlaybackState, timeMs: Long, durationMs: Long?) {
        val query = listOf(
            "ratingKey" to ratingKey,
            "key" to "/library/metadata/$ratingKey",
            "state" to state.value,
            "time" to timeMs.coerceAtLeast(0).toString(),
            "duration" to durationMs?.takeIf { it > 0 }?.toString(),
            "identifier" to LIBRARY_IDENTIFIER,
        )
        send(url(listOf(":", "timeline"), query))
    }

    /** GET /:/scrobble (watched) or /:/unscrobble (not watched). */
    suspend fun setWatched(ratingKey: String, watched: Boolean) {
        val query = listOf("key" to ratingKey, "identifier" to LIBRARY_IDENTIFIER)
        send(url(listOf(":", if (watched) "scrobble" else "unscrobble"), query))
    }

    /** Fetches [page] until the listing ends; directories and metadata of every page together. */
    private suspend fun paged(
        pageSize: Int,
        onPage: (fetched: Int) -> Unit,
        page: (start: Int, size: Int) -> HttpUrl?,
    ): PlexMediaContainer {
        val size = pageSize.coerceAtLeast(1)
        val directories = ArrayList<PlexDirectory>()
        val metadata = ArrayList<PlexMetadata>()
        var first: PlexMediaContainer? = null
        var start = 0
        while (true) {
            val url = page(start, size) ?: throw invalidAddress()
            val container = get(url)
            if (first == null) first = container
            directories += container.directories
            metadata += container.metadata
            val count = container.directories.size + container.metadata.size
            start += count
            onPage(metadata.size)
            val total = container.totalSize
            if (count < size || (total != null && start >= total) || start >= MAX_ITEMS) break
        }
        return (first ?: PlexMediaContainer()).copy(size = start, directories = directories, metadata = metadata)
    }

    private fun paging(start: Int, size: Int) = listOf(
        "X-Plex-Container-Start" to start.toString(),
        "X-Plex-Container-Size" to size.toString(),
    )

    private suspend fun get(url: HttpUrl?): PlexMediaContainer =
        decode(PlexResponse.serializer(), send(url)).container

    private suspend fun send(url: HttpUrl?, special: (code: Int) -> PlexException? = { null }): String {
        val request = Request.Builder().url(url ?: throw invalidAddress()).headers(plexHeaders(info, token)).get().build()
        return ServerHttp.fetch(
            http = http,
            request = request,
            retryDelaysMs = retryDelaysMs,
            httpError = { code, _ -> special(code) ?: httpError(code) },
            networkError = { e -> PlexException(0, ServerHttp.unreachableMessage(e, host), e) },
        )
    }

    private fun <T> decode(deserializer: DeserializationStrategy<T>, body: String): T =
        try {
            ServerHttp.json.decodeFromString(deserializer, body)
        } catch (e: IllegalArgumentException) {
            throw PlexException(0, "$host gave an answer Nova Fork doesn't understand", e)
        }

    private fun httpError(code: Int): PlexException = when (code) {
        401 -> if (token == null) {
            PlexException(401, "$host needs you to sign in to Plex")
        } else {
            PlexException(401, "Plex didn't accept the sign-in for $host. Sign in again.")
        }
        403 -> PlexException(403, "This Plex account can't open that on $host")
        404 -> PlexException(404, "Not found on $host")
        in 500..599 -> PlexException(code, "$host had a problem (HTTP $code)")
        else -> PlexException(code, "$host refused the request (HTTP $code)")
    }

    private fun notAServer(code: Int) = PlexException(code, "$host doesn't look like a Plex server")

    private fun invalidAddress() = PlexException(0, "\"$address\" isn't a server address")

    private fun url(vararg segments: String): HttpUrl? = urlOrNull(segments.toList(), emptyList())

    private fun url(segments: List<String>, query: List<Pair<String, String?>>): HttpUrl? = urlOrNull(segments, query)

    private fun urlOrNull(segments: List<String>, query: List<Pair<String, String?>>): HttpUrl? {
        val base = root ?: return null
        val builder = base.newBuilder()
        for (segment in segments) builder.addPathSegment(segment)
        for ((name, value) in query) if (value != null) builder.addQueryParameter(name, value)
        return builder.build()
    }

    /** A server path ("/library/parts/1/2/file.mkv", already encoded) below the base URL, which may have a path of its own. */
    private fun keyUrl(key: String, query: List<Pair<String, String?>>): HttpUrl? {
        val base = root ?: return null
        val builder = base.newBuilder()
        val path = key.substringBefore('?').trim('/')
        if (path.isNotEmpty()) builder.addEncodedPathSegments(path)
        if ('?' in key) builder.encodedQuery(key.substringAfter('?').ifEmpty { null })
        for ((name, value) in query) if (value != null) builder.addQueryParameter(name, value)
        return builder.build()
    }

    companion object {
        const val TYPE_MOVIE = 1
        const val TYPE_SHOW = 2
        const val TYPE_SEASON = 3
        const val TYPE_EPISODE = 4

        const val DEFAULT_PAGE_SIZE = 300
        val DEFAULT_RETRY_DELAYS_MS: List<Long> = listOf(1_000L)

        private const val TOKEN_PARAM = "X-Plex-Token"
        private const val CLIENT_PARAM = "X-Plex-Client-Identifier"
        private const val PRODUCT_PARAM = "X-Plex-Product"
        private const val LIBRARY_IDENTIFIER = "com.plexapp.plugins.library"

        /** A runaway listing (a server ignoring the paging parameters) stops here. */
        private const val MAX_ITEMS = 200_000
    }
}

/** The X-Plex-* headers that identify this app (and [token], when there is one) to servers and plex.tv. */
internal fun plexHeaders(info: PlexClientInfo, token: String?): Headers {
    val builder = Headers.Builder()
        .add("Accept", "application/json")
        .add("X-Plex-Product", ServerHttp.headerValue(info.product))
        .add("X-Plex-Version", ServerHttp.headerValue(info.version))
        .add("X-Plex-Client-Identifier", ServerHttp.headerValue(info.clientIdentifier))
        .add("X-Plex-Platform", ServerHttp.headerValue(info.platform))
        .add("X-Plex-Device", ServerHttp.headerValue(info.device))
        .add("X-Plex-Device-Name", ServerHttp.headerValue(info.deviceName))
    info.platformVersion?.let { builder.add("X-Plex-Platform-Version", ServerHttp.headerValue(it)) }
    token?.let { builder.add("X-Plex-Token", ServerHttp.headerValue(it)) }
    return builder.build()
}
