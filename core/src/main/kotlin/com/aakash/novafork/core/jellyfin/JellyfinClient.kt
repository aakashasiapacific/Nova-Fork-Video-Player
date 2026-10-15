package com.aakash.novafork.core.jellyfin

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Client for one Jellyfin or Emby server ([flavor]) at [baseUrl] ("http://jellyfin.local:8096",
 * "https://example.com/jellyfin"). Main-safe and cancellable; every failure is a
 * [JellyfinException] with a message fit for the user. Tokens go in headers, never in the URLs of
 * API calls; only [withToken] puts one into a URL (for the player, which can't send headers).
 */
class JellyfinClient(
    private val http: OkHttpClient,
    baseUrl: String,
    val flavor: JellyfinFlavor,
    private val device: JellyfinDevice,
    /** Waits before retrying a GET that failed on the network or with a 5xx; its size is the number of retries. */
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
) {
    private val address: String = baseUrl.trim()
    private val root: HttpUrl? = ServerHttp.baseUrl(address)

    /** The server's host, for messages. */
    val host: String = root?.host ?: address

    /** Emby's API lives below /emby, unless the address already ends there (a reverse proxy). */
    private val apiPrefix: List<String> =
        if (flavor == JellyfinFlavor.EMBY && root?.pathSegments?.lastOrNull { it.isNotEmpty() }?.equals("emby", ignoreCase = true) != true) {
            listOf("emby")
        } else {
            emptyList()
        }

    /** What the server says about itself; also tells whether [baseUrl] is a server of this flavour at all. */
    suspend fun publicInfo(): JellyfinPublicInfo {
        val body = send(request(url("System", "Info", "Public"), token = null)) { code -> if (code == 404) notAServer(404) else null }
        val info = try {
            ServerHttp.json.decodeFromString(JellyfinPublicInfo.serializer(), body)
        } catch (e: IllegalArgumentException) {
            throw notAServer(0)
        }
        if (info.id.isBlank()) throw notAServer(0)
        return info
    }

    /** POST /Users/AuthenticateByName. A refused login is a [JellyfinException] with code 401, "Wrong username or password". */
    suspend fun authenticate(username: String, password: String?): JellyfinAuth {
        val name = username.trim()
        val payload = ServerHttp.json.encodeToString(JellyfinAuthRequest.serializer(), JellyfinAuthRequest(name, password.orEmpty()))
        val body = send(request(url("Users", "AuthenticateByName"), token = null, method = "POST", body = payload.toJsonBody())) { code ->
            when (code) {
                401 -> JellyfinException(401, "Wrong username or password")
                403 -> JellyfinException(403, "$name isn't allowed to sign in to $host")
                404 -> notAServer(404)
                else -> null
            }
        }
        val result = decode(JellyfinAuthResult.serializer(), body)
        val token = result.accessToken?.trim()?.takeIf { it.isNotEmpty() }
        val user = result.user
        val userId = user?.id?.trim()?.takeIf { it.isNotEmpty() }
        if (token == null || userId == null) throw JellyfinException(0, "$host didn't accept the sign-in")
        return JellyfinAuth(
            token = token,
            userId = userId,
            serverId = result.serverId?.takeIf { it.isNotBlank() } ?: user?.serverId?.takeIf { it.isNotBlank() },
            userName = user?.name,
        )
    }

    /**
     * The user [token] belongs to (GET /Users/Me). An API key belongs to nobody: then the first
     * administrator (or else the first enabled user) of /Users stands in.
     */
    suspend fun currentUser(token: String): JellyfinUser {
        val me = try {
            decode(JellyfinUser.serializer(), send(request(url("Users", "Me"), token)))
        } catch (e: JellyfinException) {
            if (e.code == 0 || e.code == 401) throw e
            null
        }
        if (me != null && me.id.isNotBlank()) return me
        val users = decode(ListSerializer(JellyfinUser.serializer()), send(request(url("Users"), token)))
            .filter { it.id.isNotBlank() && it.policy?.isDisabled != true }
        return users.firstOrNull { it.policy?.isAdministrator == true }
            ?: users.firstOrNull()
            ?: throw JellyfinException(0, "$host has no user to sign in as")
    }

    /** GET /Users/{id}: a cheap check that [auth] still works. */
    suspend fun user(auth: JellyfinAuth): JellyfinUser =
        decode(JellyfinUser.serializer(), send(request(url("Users", auth.userId), auth.token)))

    /** The user's libraries (GET /Users/{id}/Views), every kind: movies, shows, music… */
    suspend fun libraries(auth: JellyfinAuth): List<JellyfinItem> {
        val primary = request(url("Users", auth.userId, "Views"), auth.token)
        val fallback = jellyfinOnly { request(url("UserViews", query = listOf("userId" to auth.userId)), auth.token) }
        return decode(JellyfinItemsPage.serializer(), sendWithFallback(primary, fallback)).items.filter { it.id.isNotBlank() }
    }

    /** One page of GET /Users/{id}/Items. */
    suspend fun itemsPage(auth: JellyfinAuth, query: JellyfinQuery, startIndex: Int, limit: Int): JellyfinItemsPage {
        val params = listOf(
            "ParentId" to query.parentId,
            "Recursive" to query.recursive.toString(),
            "IncludeItemTypes" to query.includeItemTypes.joinToString(",").ifEmpty { null },
            "Fields" to query.fields.joinToString(",").ifEmpty { null },
            "SortBy" to query.sortBy.joinToString(",").ifEmpty { null },
            "SortOrder" to "Ascending",
            "IsMissing" to if (query.excludeMissing) "false" else null,
            "EnableImageTypes" to "Primary,Backdrop,Thumb",
            "ImageTypeLimit" to "1",
            "EnableTotalRecordCount" to "true",
            "StartIndex" to startIndex.coerceAtLeast(0).toString(),
            "Limit" to limit.coerceAtLeast(1).toString(),
        )
        val primary = request(url("Users", auth.userId, "Items", query = params), auth.token)
        val fallback = jellyfinOnly { request(url("Items", query = params + ("userId" to auth.userId)), auth.token) }
        val page = decode(JellyfinItemsPage.serializer(), sendWithFallback(primary, fallback))
        return page.copy(items = page.items.filter { it.id.isNotBlank() })
    }

    /** Every item [query] matches, fetched [pageSize] at a time; [onPage] gets the running count. */
    suspend fun items(
        auth: JellyfinAuth,
        query: JellyfinQuery,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        onPage: (fetched: Int) -> Unit = {},
    ): List<JellyfinItem> {
        val size = pageSize.coerceAtLeast(1)
        val all = ArrayList<JellyfinItem>()
        var start = 0
        while (true) {
            val page = itemsPage(auth, query, start, size)
            all += page.items
            start += page.items.size
            onPage(all.size)
            val total = page.totalRecordCount
            if (page.items.size < size || (total > 0 && start >= total) || start >= MAX_ITEMS) break
        }
        return all
    }

    /** "…/Items/{id}/Images/Primary?maxWidth=500&quality=90&tag=…": needs no token. Null without an id or a valid address. */
    fun imageUrl(itemId: String?, type: String, tag: String?, maxWidth: Int? = null, index: Int? = null): String? {
        if (itemId.isNullOrBlank()) return null
        val segments = listOf("Items", itemId, "Images", type) + listOfNotNull(index?.toString())
        val query = listOf("maxWidth" to maxWidth?.toString(), "quality" to IMAGE_QUALITY, "tag" to tag?.takeIf { it.isNotBlank() })
        return urlOrNull(segments, query)?.toString()
    }

    /**
     * "…/Videos/{id}/stream?static=true&MediaSourceId=…": the original file, as the server has it,
     * without a token (see [withToken]).
     */
    fun streamUrl(itemId: String, mediaSourceId: String?): String =
        url("Videos", itemId, "stream", query = listOf("static" to "true", "MediaSourceId" to mediaSourceId?.takeIf { it.isNotBlank() }))
            .toString()

    /** [url] with the access token as api_key (and Jellyfin's newer ApiKey), for a player that can't send headers. */
    fun withToken(url: String, token: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        val builder = parsed.newBuilder()
            .removeAllQueryParameters(LEGACY_TOKEN_PARAM)
            .removeAllQueryParameters(TOKEN_PARAM)
            .addQueryParameter(LEGACY_TOKEN_PARAM, token)
        if (flavor == JellyfinFlavor.JELLYFIN) builder.addQueryParameter(TOKEN_PARAM, token)
        return builder.build().toString()
    }

    /** POST /Sessions/Playing: playback of [itemId] started at [positionMs]. */
    suspend fun reportPlaying(auth: JellyfinAuth, itemId: String, mediaSourceId: String?, positionMs: Long) {
        postReport(auth, listOf("Sessions", "Playing"), report(itemId, mediaSourceId, positionMs, paused = false))
    }

    /** POST /Sessions/Playing/Progress. */
    suspend fun reportProgress(auth: JellyfinAuth, itemId: String, mediaSourceId: String?, positionMs: Long, paused: Boolean = false) {
        postReport(auth, listOf("Sessions", "Playing", "Progress"), report(itemId, mediaSourceId, positionMs, paused))
    }

    /** POST /Sessions/Playing/Stopped: the server keeps [positionMs] as the resume point (or marks the item played near its end). */
    suspend fun reportStopped(auth: JellyfinAuth, itemId: String, mediaSourceId: String?, positionMs: Long) {
        postReport(auth, listOf("Sessions", "Playing", "Stopped"), report(itemId, mediaSourceId, positionMs, paused = false))
    }

    /** Marks [itemId] played (POST) or unplayed (DELETE /Users/{id}/PlayedItems/{itemId}). */
    suspend fun setPlayed(auth: JellyfinAuth, itemId: String, played: Boolean) {
        val method = if (played) "POST" else "DELETE"
        val body = if (played) EMPTY_BODY else null
        val primary = request(url("Users", auth.userId, "PlayedItems", itemId), auth.token, method, body)
        val fallback = jellyfinOnly {
            request(url("UserPlayedItems", itemId, query = listOf("userId" to auth.userId)), auth.token, method, body)
        }
        sendWithFallback(primary, fallback)
    }

    private fun report(itemId: String, mediaSourceId: String?, positionMs: Long, paused: Boolean) = JellyfinPlaybackReport(
        itemId = itemId,
        mediaSourceId = mediaSourceId?.takeIf { it.isNotBlank() },
        positionTicks = msToTicks(positionMs),
        isPaused = paused,
    )

    private suspend fun postReport(auth: JellyfinAuth, segments: List<String>, report: JellyfinPlaybackReport) {
        val payload = ServerHttp.json.encodeToString(JellyfinPlaybackReport.serializer(), report)
        send(request(url(segments), auth.token, method = "POST", body = payload.toJsonBody()))
    }

    /** Jellyfin 10.9 moved the user's routes (/Users/{id}/Items → /Items?userId=); older servers only have the first. */
    private inline fun jellyfinOnly(build: () -> Request): Request? = if (flavor == JellyfinFlavor.JELLYFIN) build() else null

    private suspend fun sendWithFallback(primary: Request, fallback: Request?): String =
        try {
            send(primary)
        } catch (e: JellyfinException) {
            if (fallback == null || (e.code != 404 && e.code != 405)) throw e
            send(fallback)
        }

    private suspend fun send(request: Request, special: (code: Int) -> JellyfinException? = { null }): String =
        ServerHttp.fetch(
            http = http,
            request = request,
            retryDelaysMs = retryDelaysMs,
            httpError = { code, _ -> special(code) ?: httpError(code) },
            networkError = { e -> JellyfinException(0, ServerHttp.unreachableMessage(e, host), e) },
        )

    private fun <T> decode(deserializer: DeserializationStrategy<T>, body: String): T =
        try {
            ServerHttp.json.decodeFromString(deserializer, body)
        } catch (e: IllegalArgumentException) {
            throw JellyfinException(0, "$host gave an answer Nova Fork doesn't understand", e)
        }

    private fun httpError(code: Int): JellyfinException = when (code) {
        401 -> JellyfinException(401, "$host didn't accept the sign-in. Sign in again.")
        403 -> JellyfinException(403, "This account isn't allowed to do that on $host")
        404 -> JellyfinException(404, "Not found on $host")
        in 500..599 -> JellyfinException(code, "$host had a problem (HTTP $code)")
        else -> JellyfinException(code, "$host refused the request (HTTP $code)")
    }

    private fun notAServer(code: Int): JellyfinException {
        val what = if (flavor == JellyfinFlavor.EMBY) "an Emby" else "a Jellyfin"
        return JellyfinException(code, "$host doesn't look like $what server")
    }

    private fun authorization(token: String?): String {
        val scheme = if (flavor == JellyfinFlavor.JELLYFIN) "MediaBrowser" else "Emby"
        val fields = listOfNotNull(
            "Client" to device.client,
            "Device" to device.device,
            "DeviceId" to device.deviceId,
            "Version" to device.version,
            token?.let { "Token" to it },
        )
        return scheme + " " + fields.joinToString(", ") { (name, value) -> "$name=\"${ServerHttp.headerValue(value)}\"" }
    }

    private fun request(url: HttpUrl, token: String?, method: String = "GET", body: RequestBody? = null): Request {
        val authorization = authorization(token)
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        when (flavor) {
            JellyfinFlavor.JELLYFIN -> builder.header("Authorization", authorization).header("X-Emby-Authorization", authorization)
            JellyfinFlavor.EMBY -> {
                builder.header("X-Emby-Authorization", authorization)
                if (token != null) builder.header("X-Emby-Token", ServerHttp.headerValue(token))
            }
        }
        return builder.method(method, body).build()
    }

    private fun url(vararg segments: String, query: List<Pair<String, String?>> = emptyList()): HttpUrl =
        url(segments.toList(), query)

    private fun url(segments: List<String>, query: List<Pair<String, String?>> = emptyList()): HttpUrl =
        urlOrNull(segments, query) ?: throw JellyfinException(0, "\"$address\" isn't a server address")

    private fun urlOrNull(segments: List<String>, query: List<Pair<String, String?>>): HttpUrl? {
        val base = root ?: return null
        val builder = base.newBuilder()
        for (segment in apiPrefix + segments) builder.addPathSegment(segment)
        for ((name, value) in query) if (value != null) builder.addQueryParameter(name, value)
        return builder.build()
    }

    private fun String.toJsonBody(): RequestBody = toRequestBody(ServerHttp.JSON_MEDIA_TYPE.toMediaType())

    companion object {
        /** Jellyfin and Emby count time in ticks of 100 ns. */
        const val TICKS_PER_MS = 10_000L

        const val DEFAULT_PAGE_SIZE = 300

        /** What scans ask for besides the defaults (name, ids, images, numbering, dates). */
        val ITEM_FIELDS: List<String> = listOf(
            "Overview",
            "ProviderIds",
            "Path",
            "MediaSources",
            "DateCreated",
            "Genres",
            "Taglines",
            "OriginalTitle",
            "SortName",
            "ParentId",
        )

        val DEFAULT_RETRY_DELAYS_MS: List<Long> = listOf(1_000L)

        private const val LEGACY_TOKEN_PARAM = "api_key"
        private const val TOKEN_PARAM = "ApiKey"
        private const val IMAGE_QUALITY = "90"

        /** A runaway listing (a server ignoring Limit and StartIndex) stops here. */
        private const val MAX_ITEMS = 200_000

        private val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(null)

        fun ticksToMs(ticks: Long?): Long? = ticks?.takeIf { it > 0 }?.div(TICKS_PER_MS)

        fun msToTicks(ms: Long): Long = ms.coerceAtLeast(0) * TICKS_PER_MS

        /** The item id in a [streamUrl] ("…/Videos/{id}/stream…"), or null. */
        fun itemIdOf(streamUrl: String): String? {
            val segments = streamUrl.toHttpUrlOrNull()?.pathSegments ?: return null
            val at = segments.indexOfLast { it.equals("Videos", ignoreCase = true) }
            if (at < 0 || at + 2 >= segments.size || !segments[at + 2].startsWith("stream", ignoreCase = true)) return null
            return segments[at + 1].takeIf { it.isNotBlank() }
        }

        /** The MediaSourceId of a [streamUrl], or null. */
        fun mediaSourceIdOf(streamUrl: String): String? {
            val url = streamUrl.toHttpUrlOrNull() ?: return null
            return url.queryParameterNames.firstOrNull { it.equals("MediaSourceId", ignoreCase = true) }
                ?.let { url.queryParameter(it) }
                ?.takeIf { it.isNotBlank() }
        }
    }
}
