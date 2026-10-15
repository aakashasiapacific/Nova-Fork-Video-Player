package com.aakash.novafork.core.plex

import com.aakash.novafork.core.jellyfin.ServerHttp
import kotlinx.coroutines.delay
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * plex.tv, for signing in without typing a token: the PIN flow (show [PlexPin.code], the person
 * enters it at [LINK_URL] or approves it at [authUrl], [awaitToken] picks up the token), a
 * user name and password sign-in, and the servers an account can open. Main-safe and cancellable;
 * failures are [PlexException]s with messages fit for the user.
 */
class PlexTvClient(
    private val http: OkHttpClient,
    private val info: PlexClientInfo,
    baseUrl: String = DEFAULT_BASE_URL,
    private val retryDelaysMs: List<Long> = PlexServerClient.DEFAULT_RETRY_DELAYS_MS,
) {
    private val root: HttpUrl? = baseUrl.trim().toHttpUrlOrNull()

    /**
     * POST /api/v2/pins. A normal PIN has a 4-character [PlexPin.code] for [LINK_URL]; a [strong]
     * one a long code that only works through [authUrl].
     */
    suspend fun createPin(strong: Boolean = false): PlexPin {
        val url = url(listOf("api", "v2", "pins"), listOf("strong" to strong.toString()))
        val pin = decode(PlexPin.serializer(), send(post(url, FormBody.Builder().build())))
        if (pin.id <= 0 || pin.code.isBlank()) throw PlexException(0, "plex.tv didn't hand out a code. Try again.")
        return pin
    }

    /** GET /api/v2/pins/{id}: [pin] as it is now; its authToken is set once someone linked it. */
    suspend fun checkPin(pin: PlexPin): PlexPin {
        val url = url(listOf("api", "v2", "pins", pin.id.toString()), listOf("code" to pin.code))
        return decode(PlexPin.serializer(), send(get(url)) { code -> if (code == 404 || code == 410) expired() else null })
    }

    /**
     * Polls [pin] every [pollIntervalMs] until someone links it, and returns the account's token.
     * The person is in a browser meanwhile, often with this app in the background, so a check that
     * fails on the network or with 408, 429 or a 5xx only goes to [onCheckFailed]: polling goes
     * on, further apart (up to 30 s) while plex.tv answers with errors. Only an expired or refused
     * code ends the wait early. Gives up after [timeoutMs], by default when the PIN expires: with
     * "The code expired…", or with the last check's failure when that one failed. Cancel the
     * coroutine to stop waiting.
     */
    suspend fun awaitToken(
        pin: PlexPin,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
        timeoutMs: Long? = null,
        onCheckFailed: (PlexException) -> Unit = {},
    ): String {
        val limit = timeoutMs ?: ((pin.expiresIn?.takeIf { it > 0 }?.toLong() ?: DEFAULT_PIN_LIFETIME_S) * 1000L)
        val deadline = System.nanoTime() + limit * 1_000_000L
        val interval = pollIntervalMs.coerceAtLeast(1L)
        var busyChecks = 0
        while (true) {
            var failure: PlexException? = null
            val token = try {
                checkPin(pin).authToken?.trim()
            } catch (e: PlexException) {
                if (!isPassing(e)) throw e
                onCheckFailed(e)
                failure = e
                null
            }
            if (!token.isNullOrEmpty()) return token
            val left = deadline - System.nanoTime()
            if (left <= 0) throw failure ?: expired()
            busyChecks = when {
                failure == null -> 0
                // No network says nothing about how busy plex.tv is.
                failure.code == 0 -> busyChecks
                else -> busyChecks + 1
            }
            // Never past the deadline: the last check comes right at it.
            delay(pollWait(interval, busyChecks).coerceAtMost(left / 1_000_000L).coerceAtLeast(1L))
        }
    }

    /** A check that failed on the way (no network, plex.tv busy or down), not because of the code. */
    private fun isPassing(e: PlexException): Boolean =
        e.code == 0 || e.code == HTTP_REQUEST_TIMEOUT || e.code == HTTP_TOO_MANY_REQUESTS || e.code in 500..599

    /** [interval], doubled for each check in a row that plex.tv answered with an error, up to [MAX_POLL_WAIT_MS]. */
    private fun pollWait(interval: Long, busyChecks: Int): Long {
        if (busyChecks == 0 || interval >= MAX_POLL_WAIT_MS) return interval
        return (interval shl minOf(busyChecks, MAX_BACKOFF_DOUBLINGS)).coerceAtMost(MAX_POLL_WAIT_MS)
    }

    /** The page where a signed-in person approves [pin] (works for normal and strong PINs). */
    fun authUrl(pin: PlexPin): String =
        AUTH_APP_URL + "#?clientID=" + encode(info.clientIdentifier) +
            "&code=" + encode(pin.code) +
            "&context%5Bdevice%5D%5Bproduct%5D=" + encode(info.product)

    /**
     * POST /api/v2/users/signin with a Plex account's user name (or email) and password; returns
     * the account's token. A refused login is a PlexException with code 401, "Wrong username or password".
     */
    suspend fun signIn(login: String, password: String): String {
        val form = FormBody.Builder()
            .add("login", login.trim())
            .add("password", password)
            .add("rememberMe", "true")
            .build()
        val body = send(post(url(listOf("api", "v2", "users", "signin"), emptyList()), form)) { code ->
            when (code) {
                400, 401, 422 -> PlexException(401, "Wrong username or password")
                429 -> PlexException(429, "Too many sign-in attempts. Wait a few minutes and try again.")
                else -> null
            }
        }
        return decode(PlexUser.serializer(), body).authToken?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw PlexException(0, "plex.tv didn't accept the sign-in")
    }

    /** The servers the account behind [token] can open (GET /api/v2/resources), with their addresses and tokens. */
    suspend fun servers(token: String): List<PlexResource> {
        val url = url(listOf("api", "v2", "resources"), listOf("includeHttps" to "1", "includeRelay" to "1"))
        return decode(ListSerializer(PlexResource.serializer()), send(get(url, token))).filter { it.isServer && it.clientIdentifier.isNotBlank() }
    }

    private fun get(url: HttpUrl, token: String? = null): Request =
        Request.Builder().url(url).headers(plexHeaders(info, token)).get().build()

    private fun post(url: HttpUrl, body: RequestBody): Request =
        Request.Builder().url(url).headers(plexHeaders(info, null)).post(body).build()

    private suspend fun send(request: Request, special: (code: Int) -> PlexException? = { null }): String =
        ServerHttp.fetch(
            http = http,
            request = request,
            retryDelaysMs = retryDelaysMs,
            httpError = { code, _ -> special(code) ?: httpError(code) },
            networkError = { e -> PlexException(0, ServerHttp.unreachableMessage(e, HOST), e) },
        )

    private fun <T> decode(deserializer: DeserializationStrategy<T>, body: String): T =
        try {
            ServerHttp.json.decodeFromString(deserializer, body)
        } catch (e: IllegalArgumentException) {
            throw PlexException(0, "plex.tv gave an answer Nova Fork doesn't understand", e)
        }

    private fun httpError(code: Int): PlexException = when (code) {
        401 -> PlexException(401, "plex.tv didn't accept the sign-in. Sign in again.")
        429 -> PlexException(429, "plex.tv is busy. Try again in a minute.")
        in 500..599 -> PlexException(code, "plex.tv had a problem (HTTP $code)")
        else -> PlexException(code, "plex.tv refused the request (HTTP $code)")
    }

    private fun expired() = PlexException(410, "The code expired. Get a new one.")

    private fun url(segments: List<String>, query: List<Pair<String, String?>>): HttpUrl {
        val base = root ?: throw PlexException(0, "plex.tv's address isn't valid")
        val builder = base.newBuilder()
        for (segment in segments) builder.addPathSegment(segment)
        for ((name, value) in query) if (value != null) builder.addQueryParameter(name, value)
        return builder.build()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    companion object {
        const val DEFAULT_BASE_URL = "https://plex.tv/"

        /** Where people enter a normal PIN's code. */
        const val LINK_URL = "https://plex.tv/link"

        const val AUTH_APP_URL = "https://app.plex.tv/auth"

        const val DEFAULT_POLL_INTERVAL_MS = 2_000L

        /** How long a PIN lives when plex.tv doesn't say: 15 minutes. */
        private const val DEFAULT_PIN_LIFETIME_S = 900L

        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MAX_POLL_WAIT_MS = 30_000L
        private const val MAX_BACKOFF_DOUBLINGS = 4

        private const val HOST = "plex.tv"
    }
}
