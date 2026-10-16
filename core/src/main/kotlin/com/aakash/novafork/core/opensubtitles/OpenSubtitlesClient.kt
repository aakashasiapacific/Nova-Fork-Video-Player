package com.aakash.novafork.core.opensubtitles

import com.aakash.novafork.core.opensubtitles.OpenSubtitlesException.Reason
import com.aakash.novafork.core.tmdb.TmdbRateLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.TreeMap
import kotlin.coroutines.resumeWithException

/**
 * Small client for the OpenSubtitles.com REST API (v1): search, optional login, download.
 * Every call is main-safe and cancellable.
 *
 * Every request carries the Api-Key header and the User-Agent "NovaFork v<version>" that
 * OpenSubtitles asks for (set by a network interceptor, so it wins over the shared client's own
 * User-Agent). Requests stay under [rateLimitRequests] per [rateLimitWindowMs] (OpenSubtitles
 * allows about 5 per second, and one login per second). A 429 is retried once after its
 * Retry-After delay; GET requests that fail on the network or with a 5xx are retried after each
 * of [retryDelaysMs]. Failures become [OpenSubtitlesException]s with a message to show.
 *
 * With a [login], downloads use the account's token (and the API server its login names in
 * base_url); the token is kept for [TOKEN_LIFETIME_MS] and renewed once when it is refused.
 */
class OpenSubtitlesClient(
    http: OkHttpClient,
    /** Read on every call; null or blank = no key ([Reason.NO_KEY]). */
    private val apiKey: () -> String?,
    /** Read on every call; null (or a blank username or password) = anonymous downloads. */
    private val login: () -> OpenSubtitlesLogin? = { null },
    /** The app's version, for the User-Agent ("NovaFork v1.0.42"). */
    appVersion: String,
    /** The API root; must end with "/". */
    private val baseUrl: String = DEFAULT_BASE_URL,
    /** Waits before each retry of a failed GET; its size is the number of retries. */
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
    /** At most this many requests start in any [rateLimitWindowMs]; 0 turns every limit off. */
    rateLimitRequests: Int = RATE_LIMIT_REQUESTS,
    rateLimitWindowMs: Long = RATE_LIMIT_WINDOW_MS,
    /** Wall clock in milliseconds, for the token's age. */
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** "NovaFork v1.0.42". */
    val userAgent: String = "NovaFork v" + appVersion.filter { it in '!'..'~' }.ifEmpty { "1.0" }

    private val http: OkHttpClient = http.newBuilder()
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        .build()

    private val limiter = TmdbRateLimiter(rateLimitRequests, rateLimitWindowMs)
    private val loginLimiter =
        if (rateLimitRequests > 0) TmdbRateLimiter(1, LOGIN_INTERVAL_MS) else TmdbRateLimiter(0, 0)

    private val sessionLock = Mutex()

    @Volatile
    private var session: Session? = null

    /** Credentials the server refused: not sent again until they change. */
    @Volatile
    private var refused: Identity? = null

    /** True when an API key is set. */
    val hasKey: Boolean get() = !apiKey().isNullOrBlank()

    /**
     * Subtitles for the video [request] describes, in the order OpenSubtitles ranks them (see
     * [SubtitleRanking] to order them for people). Tries, until one finds something: its TMDB id,
     * its IMDb id, the movie hash alone, then the title (and year); see [searchAttempts]. Tries
     * by id send the movie hash too, so results made for this very file come marked
     * [SubtitleResult.movieHashMatch].
     */
    suspend fun search(request: SubtitleSearchRequest): List<SubtitleResult> {
        val key = requireKey()
        val attempts = searchAttempts(request)
        if (attempts.isEmpty()) return emptyList()
        val base = validSession(key)?.base ?: defaultBase()
        for (params in attempts) {
            val url = base.newBuilder().addPathSegment("subtitles").apply {
                for ((name, value) in params) addQueryParameter(name, value)
            }.build()
            val answer = exchange(apiRequest(url, key, token = null).get().build(), limiter)
            if (!answer.isSuccessful) throw failure(Endpoint.SEARCH, answer, withToken = false)
            val results = parseResults(answer.text)
            if (results.isNotEmpty()) return results
        }
        return emptyList()
    }

    /**
     * Asks for [fileId]'s download link (POST /download), logged in when a [login] is set. Each
     * call counts against the daily quota.
     */
    suspend fun requestDownload(fileId: Long): SubtitleDownloadLink {
        val key = requireKey()
        var current = validSession(key) ?: signIn(key)
        var renewed = false
        while (true) {
            val base = current?.base ?: defaultBase()
            val url = base.newBuilder().addPathSegment("download").build()
            val body = json.encodeToString(DownloadBody.serializer(), DownloadBody(fileId)).toRequestBody(JSON_MEDIA_TYPE)
            val answer = exchange(apiRequest(url, key, current?.token).post(body).build(), limiter)
            if (answer.code == HTTP_UNAUTHORIZED && current != null && !renewed) {
                // The token expired early or was revoked: one fresh login, then the answer stands.
                renewed = true
                forget(current)
                current = signIn(key)
                continue
            }
            if (!answer.isSuccessful) throw failure(Endpoint.DOWNLOAD, answer, withToken = current != null)
            return parseDownload(answer.text)
        }
    }

    /** [requestDownload], then fetches the file (at most [maxBytes]). */
    suspend fun download(fileId: Long, maxBytes: Long = MAX_FILE_BYTES): DownloadedSubtitle {
        val link = requestDownload(fileId)
        val url = link.link.toHttpUrlOrNull() ?: throw badResponse(null)
        val answer = exchange(Request.Builder().url(url).get().build(), limiter, maxBytes)
        if (!answer.isSuccessful) throw failure(Endpoint.FILE, answer, withToken = false)
        if (answer.bytes.isEmpty()) throw badResponse(null)
        val name = link.fileName?.trim()?.takeIf { it.isNotEmpty() }
            ?: url.pathSegments.lastOrNull { it.isNotBlank() }
            ?: DEFAULT_FILE_NAME
        return DownloadedSubtitle(name, answer.bytes, link.remaining, link.resetAt)
    }

    /** Forgets the login token; the next download logs in again. */
    fun signOut() {
        session = null
        refused = null
    }

    /**
     * The search parameters of each try, alphabetically ordered as OpenSubtitles asks: the ids
     * with the movie hash (results timed for the file come marked), the hash alone, the first id
     * again without the hash (in case a hash narrows the answer down to exact matches), then the
     * title without it.
     */
    internal fun searchAttempts(request: SubtitleSearchRequest): List<Map<String, String>> {
        val common = TreeMap<String, String>()
        OpenSubtitlesLanguages.queryValue(request.languages)?.let { common["languages"] = it }
        common["order_by"] = "download_count"
        val hash = request.movieHash?.trim()?.lowercase(Locale.ROOT)?.takeIf { HASH.matches(it) }
        val episode = request.episode?.takeIf { it >= 0 }
        val season = request.season?.takeIf { it >= 0 }
        val episodeParams = TreeMap<String, String>()
        if (episode != null) {
            episodeParams["episode_number"] = episode.toString()
            season?.let { episodeParams["season_number"] = it.toString() }
        }
        val imdb = request.imdbId?.trim()?.lowercase(Locale.ROOT)?.removePrefix("tt")?.trimStart('0')
            ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        val tmdb = request.tmdbId?.takeIf { it > 0 }
        val ids = listOfNotNull(
            tmdb?.let { (if (episode != null) "parent_tmdb_id" else "tmdb_id") to it.toString() },
            imdb?.let { (if (episode != null) "parent_imdb_id" else "imdb_id") to it },
        )
        val query = cleanQuery(request.title)

        val attempts = ArrayList<Map<String, String>>()
        fun attempt(extra: List<Pair<String, String>>, withHash: Boolean, withEpisode: Boolean = true) {
            val params = TreeMap(common)
            if (withHash && hash != null) params["moviehash"] = hash
            if (withEpisode) params.putAll(episodeParams)
            params.putAll(extra)
            if (params !in attempts) attempts += params
        }
        for (id in ids) attempt(listOf(id), withHash = true)
        if (hash != null) {
            attempt(emptyList(), withHash = true, withEpisode = false)
            ids.firstOrNull()?.let { attempt(listOf(it), withHash = false) }
        }
        if (query != null) {
            val year = request.year?.takeIf { episode == null && it in 1870..2999 }
            val title = if (year != null) listOf("query" to query, "year" to year.toString()) else listOf("query" to query)
            attempt(title, withHash = false)
        }
        return attempts
    }

    private fun cleanQuery(title: String?): String? =
        title?.replace(QUERY_SEPARATORS, " ")?.replace(SPACES, " ")?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

    private fun requireKey(): String {
        val key = apiKey()?.trim()
        if (key.isNullOrEmpty()) throw OpenSubtitlesException(Reason.NO_KEY, OpenSubtitlesException.NO_KEY_MESSAGE)
        return key
    }

    private fun defaultBase(): HttpUrl =
        baseUrl.toHttpUrlOrNull() ?: throw OpenSubtitlesException(Reason.BAD_RESPONSE, "Invalid OpenSubtitles address: $baseUrl")

    private class Identity(val apiKey: String, val username: String, val password: String) {
        override fun equals(other: Any?): Boolean =
            other is Identity && other.apiKey == apiKey && other.username == username && other.password == password

        override fun hashCode(): Int = (apiKey.hashCode() * 31 + username.hashCode()) * 31 + password.hashCode()
    }

    private class Session(val identity: Identity, val token: String, val base: HttpUrl, val createdAt: Long)

    private fun identity(key: String): Identity? {
        val credentials = login() ?: return null
        val username = credentials.username.trim()
        if (username.isEmpty() || credentials.password.isEmpty()) return null
        return Identity(key, username, credentials.password)
    }

    /** The token for the current credentials while it is fresh; never logs in. */
    private fun validSession(key: String): Session? {
        val identity = identity(key) ?: return null
        return session?.takeIf { it.identity == identity && clock() - it.createdAt in 0 until TOKEN_LIFETIME_MS }
    }

    private fun forget(stale: Session) {
        if (session === stale) session = null
    }

    /** Logs in with the current credentials; null without any. Callers share one login. */
    private suspend fun signIn(key: String): Session? {
        val identity = identity(key) ?: return null
        return sessionLock.withLock {
            validSession(key)?.let { return@withLock it }
            if (refused == identity) {
                throw OpenSubtitlesException(Reason.LOGIN_FAILED, OpenSubtitlesException.LOGIN_FAILED_MESSAGE, HTTP_UNAUTHORIZED)
            }
            val url = defaultBase().newBuilder().addPathSegment("login").build()
            val body = json.encodeToString(LoginBody.serializer(), LoginBody(identity.username, identity.password))
                .toRequestBody(JSON_MEDIA_TYPE)
            val answer = exchange(apiRequest(url, key, token = null).post(body).build(), loginLimiter)
            if (!answer.isSuccessful) {
                val error = failure(Endpoint.LOGIN, answer, withToken = false)
                if (error.reason == Reason.LOGIN_FAILED) refused = identity
                throw error
            }
            val parsed = decode(LoginResponse.serializer(), answer.text)
            val token = parsed.token?.trim()?.takeIf { it.isNotEmpty() } ?: throw badResponse(null)
            val fresh = Session(identity, token, apiBase(parsed.baseUrl) ?: defaultBase(), clock())
            session = fresh
            fresh
        }
    }

    /**
     * The API root a login names: "vip-api.opensubtitles.com" → https://vip-api.opensubtitles.com/api/v1/.
     * A full URL is taken as it is (with "/api/v1/" added when it has no path).
     */
    private fun apiBase(value: String?): HttpUrl? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val url = (if ("://" in text) text else "https://$text").toHttpUrlOrNull() ?: return null
        // Never from https down to plain http: the token would travel unencrypted.
        if (url.scheme == "http" && defaultBase().scheme == "https") return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val builder = url.newBuilder().query(null).fragment(null).encodedPath("/")
        if (segments.isEmpty()) {
            builder.addPathSegments("api/v1")
        } else {
            for (segment in segments) builder.addPathSegment(segment)
        }
        return builder.addPathSegment("").build()
    }

    private fun apiRequest(url: HttpUrl, key: String, token: String?): Request.Builder {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        try {
            builder.header("Api-Key", key)
        } catch (e: IllegalArgumentException) {
            // OkHttp refuses control and non-ASCII characters in header values.
            throw OpenSubtitlesException(Reason.BAD_KEY, OpenSubtitlesException.BAD_KEY_MESSAGE, cause = e)
        }
        if (token != null) {
            try {
                builder.header("Authorization", "Bearer $token")
            } catch (e: IllegalArgumentException) {
                throw badResponse(e)
            }
        }
        return builder
    }

    private class Answer(val code: Int, val bytes: ByteArray, val retryAfter: String?) {
        val isSuccessful: Boolean get() = code in 200..299
        val text: String get() = String(bytes, Charsets.UTF_8)
    }

    /**
     * Sends [request] under [rateLimiter] and reads the whole answer (at most [maxBytes]). A 429
     * is tried again once after Retry-After; a GET that fails on the network or with a 5xx after
     * each of [retryDelaysMs]. Other answers are returned as they are.
     */
    private suspend fun exchange(request: Request, rateLimiter: TmdbRateLimiter, maxBytes: Long = MAX_ANSWER_BYTES): Answer =
        withContext(Dispatchers.IO) { exchangeWithRetries(request, rateLimiter, maxBytes) }

    private suspend fun exchangeWithRetries(request: Request, rateLimiter: TmdbRateLimiter, maxBytes: Long): Answer {
        var retries = 0
        var waitedForLimit = false
        while (true) {
            val answer = try {
                rateLimiter.acquire()
                http.newCall(request).await().use { read(it, maxBytes) }
            } catch (e: OpenSubtitlesException) {
                throw e
            } catch (e: IOException) {
                val wait = retryDelaysMs.getOrNull(retries)
                if (wait == null || request.method != "GET") {
                    throw OpenSubtitlesException(Reason.NETWORK, OpenSubtitlesException.NETWORK_MESSAGE, cause = e)
                }
                retries++
                delay(wait)
                continue
            }
            if (answer.code == HTTP_TOO_MANY_REQUESTS && !waitedForLimit) {
                waitedForLimit = true
                delay(retryAfterMs(answer.retryAfter))
                continue
            }
            if (answer.code in 500..599 && request.method == "GET") {
                val wait = retryDelaysMs.getOrNull(retries)
                if (wait != null) {
                    retries++
                    delay(wait)
                    continue
                }
            }
            return answer
        }
    }

    private fun read(response: Response, maxBytes: Long): Answer {
        val output = ByteArrayOutputStream()
        response.body?.byteStream()?.use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                if (output.size() > maxBytes) {
                    throw OpenSubtitlesException(Reason.BAD_RESPONSE, "That subtitle file is too large", response.code)
                }
            }
        }
        return Answer(response.code, output.toByteArray(), response.header("Retry-After"))
    }

    private fun retryAfterMs(header: String?): Long =
        (header?.trim()?.toLongOrNull() ?: DEFAULT_RETRY_AFTER_SECONDS).coerceIn(0L, MAX_RETRY_AFTER_SECONDS) * 1_000L

    private enum class Endpoint { LOGIN, SEARCH, DOWNLOAD, FILE }

    private fun failure(endpoint: Endpoint, answer: Answer, withToken: Boolean): OpenSubtitlesException {
        val code = answer.code
        return when {
            endpoint == Endpoint.FILE && code in FILE_GONE_CODES ->
                OpenSubtitlesException(Reason.NOT_FOUND, OpenSubtitlesException.NOT_FOUND_MESSAGE, code)
            code == HTTP_UNAUTHORIZED && (endpoint == Endpoint.LOGIN || withToken) ->
                OpenSubtitlesException(Reason.LOGIN_FAILED, OpenSubtitlesException.LOGIN_FAILED_MESSAGE, code)
            code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN ->
                OpenSubtitlesException(Reason.BAD_KEY, OpenSubtitlesException.BAD_KEY_MESSAGE, code)
            code == HTTP_NOT_ACCEPTABLE && endpoint == Endpoint.DOWNLOAD -> quotaFailure(answer)
            code == HTTP_TOO_MANY_REQUESTS && endpoint == Endpoint.DOWNLOAD && looksLikeQuota(answer) -> quotaFailure(answer)
            code == HTTP_TOO_MANY_REQUESTS ->
                OpenSubtitlesException(Reason.RATE_LIMITED, OpenSubtitlesException.RATE_LIMITED_MESSAGE, code)
            code == HTTP_NOT_FOUND || code == HTTP_GONE ->
                OpenSubtitlesException(Reason.NOT_FOUND, OpenSubtitlesException.NOT_FOUND_MESSAGE, code)
            code in 500..599 ->
                OpenSubtitlesException(Reason.SERVER, "OpenSubtitles isn't working right now (HTTP $code). Try again later.", code)
            else -> {
                val detail = serverMessage(answer.text)
                val message = if (detail != null) "OpenSubtitles: $detail" else "OpenSubtitles refused the request (HTTP $code)"
                OpenSubtitlesException(Reason.HTTP, message, code)
            }
        }
    }

    private fun looksLikeQuota(answer: Answer): Boolean {
        val quota = quotaOf(answer.text) ?: return false
        return quota.resetTimeUtc != null || quota.resetTime != null || (quota.remaining != null && quota.remaining <= 0)
    }

    private fun quotaFailure(answer: Answer): OpenSubtitlesException {
        val quota = quotaOf(answer.text)
        val resetAt = parseInstant(quota?.resetTimeUtc)
        val resetIn = quota?.resetTime?.trim()?.takeIf { it.isNotEmpty() }
        return OpenSubtitlesException(
            reason = Reason.QUOTA,
            message = OpenSubtitlesException.quotaMessage(resetAt, resetIn, now = Instant.ofEpochMilli(clock())),
            code = answer.code,
            resetAt = resetAt,
            resetIn = resetIn,
        )
    }

    private fun quotaOf(body: String): DownloadResponse? =
        try {
            json.decodeFromString(DownloadResponse.serializer(), body)
        } catch (e: IllegalArgumentException) {
            null
        }

    /** {"message": "…"} or {"errors": ["…"]}. */
    private fun serverMessage(body: String): String? {
        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: return null
        val message = (root["message"] as? JsonPrimitive)?.contentOrNull
            ?: (root["errors"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.joinToString(" ")
        return message?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_SERVER_MESSAGE)
    }

    private fun parseResults(body: String): List<SubtitleResult> {
        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (e: IllegalArgumentException) {
            throw badResponse(e)
        } ?: throw badResponse(null)
        val data = root["data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            // One odd entry should not hide the others. (SerializationException is an IllegalArgumentException.)
            val item = try {
                json.decodeFromJsonElement(SubtitleItem.serializer(), element)
            } catch (e: IllegalArgumentException) {
                return@mapNotNull null
            }
            toResult(item)
        }.distinctBy { it.id }
    }

    private fun toResult(item: SubtitleItem): SubtitleResult? {
        val attributes = item.attributes ?: return null
        val file = attributes.files
            .filter { it.fileId != null && it.fileId > 0 }
            .minByOrNull { it.cdNumber ?: 1 }
            ?: return null
        val fileId = file.fileId ?: return null
        val id = item.id?.trim()?.takeIf { it.isNotEmpty() }
            ?: attributes.subtitleId?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileId.toString()
        return SubtitleResult(
            id = id,
            fileId = fileId,
            fileName = file.fileName?.trim()?.takeIf { it.isNotEmpty() },
            language = attributes.language?.trim()?.lowercase(Locale.ROOT).orEmpty(),
            release = attributes.release?.trim()?.takeIf { it.isNotEmpty() } ?: file.fileName?.trim().orEmpty(),
            downloadCount = attributes.downloadCount.coerceAtLeast(0),
            hearingImpaired = attributes.hearingImpaired,
            movieHashMatch = attributes.movieHashMatch,
            fps = attributes.fps?.takeIf { it > 0.0 },
            aiTranslated = attributes.aiTranslated,
            machineTranslated = attributes.machineTranslated,
        )
    }

    private fun parseDownload(body: String): SubtitleDownloadLink {
        val parsed = decode(DownloadResponse.serializer(), body)
        val link = parsed.link?.trim()?.takeIf { it.toHttpUrlOrNull() != null }
        if (link == null) {
            // Some quota answers come as 200 without a link.
            if (parsed.remaining != null && parsed.remaining < 0) {
                val resetAt = parseInstant(parsed.resetTimeUtc)
                throw OpenSubtitlesException(
                    reason = Reason.QUOTA,
                    message = OpenSubtitlesException.quotaMessage(resetAt, parsed.resetTime, now = Instant.ofEpochMilli(clock())),
                    resetAt = resetAt,
                    resetIn = parsed.resetTime,
                )
            }
            throw badResponse(null)
        }
        return SubtitleDownloadLink(
            link = link,
            fileName = parsed.fileName?.trim()?.takeIf { it.isNotEmpty() },
            remaining = parsed.remaining,
            resetAt = parseInstant(parsed.resetTimeUtc),
        )
    }

    private fun <T> decode(deserializer: DeserializationStrategy<T>, body: String): T =
        try {
            json.decodeFromString(deserializer, body)
        } catch (e: IllegalArgumentException) {
            throw badResponse(e)
        }

    private fun parseInstant(value: String?): Instant? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            Instant.parse(text)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun badResponse(cause: Throwable?) =
        OpenSubtitlesException(Reason.BAD_RESPONSE, OpenSubtitlesException.BAD_RESPONSE_MESSAGE, cause = cause)

    /** Enqueues the call and cancels it when the coroutine is cancelled. */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    // Arrived after cancellation: nobody reads it, so release the connection.
                    continuation.resume(response) { _, unread, _ -> unread.close() }
                }
            },
        )
    }

    @Serializable
    private class LoginBody(val username: String, val password: String)

    @Serializable
    private class LoginResponse(
        val token: String? = null,
        @SerialName("base_url") val baseUrl: String? = null,
    )

    @Serializable
    private class DownloadBody(@SerialName("file_id") val fileId: Long)

    @Serializable
    private class DownloadResponse(
        val link: String? = null,
        @SerialName("file_name") val fileName: String? = null,
        val remaining: Int? = null,
        @SerialName("reset_time") val resetTime: String? = null,
        @SerialName("reset_time_utc") val resetTimeUtc: String? = null,
    )

    @Serializable
    private class SubtitleItem(
        val id: String? = null,
        val attributes: SubtitleAttributes? = null,
    )

    @Serializable
    private class SubtitleAttributes(
        @SerialName("subtitle_id") val subtitleId: String? = null,
        val language: String? = null,
        @SerialName("download_count") val downloadCount: Int = 0,
        @SerialName("hearing_impaired") val hearingImpaired: Boolean = false,
        val fps: Double? = null,
        @SerialName("ai_translated") val aiTranslated: Boolean = false,
        @SerialName("machine_translated") val machineTranslated: Boolean = false,
        val release: String? = null,
        val files: List<SubtitleFile> = emptyList(),
        @SerialName("moviehash_match") val movieHashMatch: Boolean = false,
    )

    @Serializable
    private class SubtitleFile(
        @SerialName("file_id") val fileId: Long? = null,
        @SerialName("cd_number") val cdNumber: Int? = null,
        @SerialName("file_name") val fileName: String? = null,
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://api.opensubtitles.com/api/v1/"

        /** Waits before the first and second retry of a failed GET. */
        val DEFAULT_RETRY_DELAYS_MS: List<Long> = listOf(1_000L, 3_000L)

        /** OpenSubtitles allows about 5 requests per second. */
        const val RATE_LIMIT_REQUESTS = 5
        const val RATE_LIMIT_WINDOW_MS = 1_000L

        /** Login tokens last 24 hours; renewed a little before. */
        const val TOKEN_LIFETIME_MS = 23L * 60 * 60 * 1_000

        /** Text subtitles are small; ASS files with embedded fonts are the large ones. */
        const val MAX_FILE_BYTES = 10L * 1024 * 1024

        private const val LOGIN_INTERVAL_MS = 1_000L
        private const val MAX_ANSWER_BYTES = 8L * 1024 * 1024
        private const val BUFFER_SIZE = 8 * 1024
        private const val MAX_SERVER_MESSAGE = 200
        private const val DEFAULT_FILE_NAME = "subtitle.srt"

        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_NOT_ACCEPTABLE = 406
        private const val HTTP_GONE = 410
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val FILE_GONE_CODES = setOf(HTTP_FORBIDDEN, HTTP_NOT_FOUND, HTTP_GONE)

        private const val DEFAULT_RETRY_AFTER_SECONDS = 1L
        private const val MAX_RETRY_AFTER_SECONDS = 10L

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val HASH = Regex("[0-9a-f]{16}")
        private val QUERY_SEPARATORS = Regex("[._]")
        private val SPACES = Regex("\\s+")

        @OptIn(ExperimentalSerializationApi::class)
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            isLenient = true
        }
    }
}
