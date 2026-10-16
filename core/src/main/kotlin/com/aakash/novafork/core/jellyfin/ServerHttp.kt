package com.aakash.novafork.core.jellyfin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.resumeWithException

/**
 * Hides access tokens in text that goes to logs or crash reports: the values of api_key=,
 * ApiKey=, X-Plex-Token=, X-Emby-Token= and token= query parameters and of Token="…" in an
 * authorization header.
 */
object ServerSecrets {
    private val QUERY_TOKEN = Regex("""(?i)\b(api_key|apikey|x-plex-token|x-emby-token|x-mediabrowser-token|token|authToken)=([^&\s"',;#]+)""")
    private val HEADER_TOKEN = Regex("""(?i)\b(token)="[^"]*"""")
    private val JSON_TOKEN = Regex("""(?i)"(accessToken|authToken|AccessToken)"\s*:\s*"[^"]*"""")

    fun redact(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return text
            .replace(HEADER_TOKEN) { "${it.groupValues[1]}=\"***\"" }
            .replace(QUERY_TOKEN) { "${it.groupValues[1]}=***" }
            .replace(JSON_TOKEN) { "\"${it.groupValues[1]}\":\"***\"" }
    }
}

/** An HTTP answer outside 2xx, before it is turned into a message for the user. */
internal class HttpStatusException(val code: Int, val body: String) : IOException("HTTP $code")

/** What the Jellyfin, Emby and Plex clients share: the JSON settings, sending requests and readable errors. */
internal object ServerHttp {
    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
    }

    const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"

    /**
     * A server address as an API root: http(s) only, without user info, query, fragment or the
     * web client's path ("…/web/index.html" is what people copy from the browser).
     */
    fun baseUrl(address: String): HttpUrl? {
        val parsed = address.trim().toHttpUrlOrNull() ?: return null
        val segments = parsed.pathSegments.filter { it.isNotEmpty() }.toMutableList()
        if (segments.lastOrNull()?.equals("index.html", ignoreCase = true) == true) segments.removeAt(segments.lastIndex)
        if (segments.lastOrNull()?.equals("web", ignoreCase = true) == true) segments.removeAt(segments.lastIndex)
        val builder = parsed.newBuilder().encodedPath("/").query(null).fragment(null).username("").password("")
        for (segment in segments) builder.addPathSegment(segment)
        return builder.build()
    }

    /**
     * Sends [request] and returns the body of a 2xx answer. A GET that fails on the network or
     * with a 5xx is tried again after each of [retryDelaysMs]. Other answers become [httpError],
     * network failures [networkError].
     */
    suspend fun fetch(
        http: OkHttpClient,
        request: Request,
        retryDelaysMs: List<Long>,
        httpError: (code: Int, body: String) -> IOException,
        networkError: (IOException) -> IOException,
    ): String {
        var attempt = 0
        while (true) {
            try {
                val response = http.newCall(request).await()
                return withContext(Dispatchers.IO) {
                    response.use {
                        val body = it.body?.string().orEmpty()
                        if (!it.isSuccessful) throw HttpStatusException(it.code, body)
                        body
                    }
                }
            } catch (e: HttpStatusException) {
                val wait = retryDelaysMs.getOrNull(attempt)
                if (wait == null || request.method != "GET" || e.code !in 500..599) throw httpError(e.code, e.body)
                attempt++
                delay(wait)
            } catch (e: IOException) {
                val wait = retryDelaysMs.getOrNull(attempt)
                if (wait == null || request.method != "GET") throw networkError(e)
                attempt++
                delay(wait)
            }
        }
    }

    /**
     * Why [host] could not be reached, in words people can act on. A failed https connection is
     * never answered with "use http": that would send the password or token unencrypted, to
     * whoever stands in the middle when the certificate is what failed.
     */
    fun unreachableMessage(e: IOException, host: String): String {
        val causes = generateSequence<Throwable>(e) { current -> current.cause?.takeIf { it !== current } }.take(10).toList()
        return when {
            causes.any { it is UnknownHostException } -> "Can't find $host on the network"
            causes.any { it is CertificateException || it is CertPathValidatorException || it is SSLPeerUnverifiedException } ->
                "$host's certificate isn't trusted (self-signed, expired or made out to another name), so Nova Fork won't connect"
            causes.any { it is SSLException } -> "Couldn't make a secure connection to $host. Does it speak https on this port?"
            causes.any { it is ConnectException || it is NoRouteToHostException } -> "Can't connect to $host. Is the server running?"
            // SocketTimeoutException and OkHttp's call timeout are both InterruptedIOExceptions.
            causes.any { it is InterruptedIOException } -> "$host didn't answer"
            else -> "Couldn't reach $host"
        }
    }

    /**
     * A value fit for an HTTP header and the quoted fields of an authorization header: printable
     * ASCII without quotes or commas (OkHttp refuses anything else). Blank becomes [fallback].
     */
    fun headerValue(value: String?, fallback: String = "Unknown"): String {
        val clean = value.orEmpty().filter { it in ' '..'~' && it != '"' && it != ',' }.trim()
        return clean.ifEmpty { fallback }
    }

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
}
