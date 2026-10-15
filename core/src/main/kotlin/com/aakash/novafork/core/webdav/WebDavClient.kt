package com.aakash.novafork.core.webdav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resumeWithException

/** One file or folder inside a listed WebDAV collection. */
data class DavEntry(
    /** The decoded file or folder name (the last segment of its href). */
    val name: String,
    val isCollection: Boolean,
    val sizeBytes: Long? = null,
    /** Epoch ms. */
    val modifiedAt: Long? = null,
    val contentType: String? = null,
    /** The server's display name, when it sends one; [name] is the real file name. */
    val displayName: String? = null,
)

/** A WebDAV request failed with HTTP [status] (0: the answer was not a usable multistatus). */
class WebDavException(val status: Int, message: String) : IOException(message)

/** Hrefs and paths of WebDAV answers. */
object DavPaths {
    private const val HEX = "0123456789ABCDEF"

    /**
     * The decoded path segments of an href: a full URL ("https://host/dav/a%20b/") or a path
     * ("/dav/a%20b/"); query and fragment are dropped, empty segments skipped.
     */
    fun segments(href: String): List<String> {
        var path = href.trim()
        val scheme = path.indexOf("://")
        if (scheme > 0 && path.substring(0, scheme).all { it.isLetterOrDigit() || it in "+-." }) {
            val slash = path.indexOf('/', scheme + 3)
            path = if (slash < 0) "" else path.substring(slash)
        }
        path = path.substringBefore('?').substringBefore('#')
        return path.split('/').filter { it.isNotEmpty() }.map { decode(it) }.filter { it.isNotEmpty() && it != "." }
    }

    /** Decodes valid %XX escapes as UTF-8; a stray '%' and anything unescaped stay as they are ('+' too). */
    fun decode(text: String): String {
        if ('%' !in text) return text
        val out = StringBuilder(text.length)
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val high = if (c == '%' && i + 2 < text.length) hexValue(text[i + 1]) else -1
            val low = if (high >= 0) hexValue(text[i + 2]) else -1
            if (high >= 0 && low >= 0) {
                bytes.write(high * 16 + low)
                i += 3
                continue
            }
            flush(bytes, out)
            out.append(c)
            i++
        }
        flush(bytes, out)
        return out.toString()
    }

    /** Percent-encodes one path segment as UTF-8; RFC 3986 unreserved characters stay. */
    fun encodeSegment(segment: String): String {
        val out = StringBuilder(segment.length)
        for (byte in segment.toByteArray(StandardCharsets.UTF_8)) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            if (b < 0x80 && (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-._~")) {
                out.append(c)
            } else {
                out.append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
            }
        }
        return out.toString()
    }

    /**
     * The entries of a Depth 1 answer inside the collection at [requestSegments] (its decoded path):
     * the collection's own response, failed responses, hidden names and duplicates are left out.
     */
    fun children(responses: List<DavResponse>, requestSegments: List<String>): List<DavEntry> {
        val seen = HashSet<String>()
        val out = ArrayList<DavEntry>()
        responses.forEachIndexed { index, response ->
            val status = response.status
            if (status != null && status !in 200..299) return@forEachIndexed
            val segments = segments(response.href)
            if (isSelf(segments, requestSegments, index, response)) return@forEachIndexed
            val name = segments.lastOrNull() ?: return@forEachIndexed
            if (name.startsWith(".") || !seen.add(name)) return@forEachIndexed
            out += DavEntry(
                name = name,
                isCollection = response.isCollection,
                sizeBytes = response.contentLength.takeUnless { response.isCollection },
                modifiedAt = response.lastModified,
                contentType = response.contentType,
                displayName = response.displayName,
            )
        }
        return out
    }

    /**
     * The listed collection itself: its own path (case aside, as IIS answers), or, as servers list
     * it first, a first response that is a collection of the same name behind a rewritten prefix.
     */
    private fun isSelf(segments: List<String>, request: List<String>, index: Int, response: DavResponse): Boolean {
        if (segments.size == request.size && segments.indices.all { segments[it].equals(request[it], ignoreCase = true) }) {
            return true
        }
        if (segments.isEmpty()) return true
        return index == 0 && response.isCollection && request.isNotEmpty() &&
            segments.last().equals(request.last(), ignoreCase = true) &&
            segments.size != request.size + 1
    }

    private fun flush(bytes: ByteArrayOutputStream, out: StringBuilder) {
        if (bytes.size() == 0) return
        out.append(String(bytes.toByteArray(), StandardCharsets.UTF_8))
        bytes.reset()
    }

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }
}

/**
 * Lists WebDAV collections with PROPFIND (Depth: 1). Logins go as HTTP Basic on every request
 * (there is no Digest support), so they should only travel over https.
 */
class WebDavClient(private val http: OkHttpClient) {

    /**
     * The files and folders in the collection at [url] (http or https; a missing trailing slash
     * is added). Throws [WebDavException] with a message fit for the user when the server
     * refuses, IOException when it can't be reached.
     */
    suspend fun list(url: String, username: String?, password: String?): List<DavEntry> {
        val target = collectionUrl(url)
        val request = propfind(target, username, password)
        val (body, answered) = withContext(Dispatchers.IO) {
            http.newCall(request).await().use { response -> read(response) to response.request.url }
        }
        val responses = withContext(Dispatchers.Default) { WebDavMultistatus.parse(body) }
        if (responses.isEmpty() && !body.contains("multistatus", ignoreCase = true)) {
            throw WebDavException(0, "The server didn't answer like a WebDAV server")
        }
        // After a redirect the listing describes the collection it ended at.
        return DavPaths.children(responses, answered.pathSegments.filter { it.isNotEmpty() })
    }

    private fun collectionUrl(url: String): HttpUrl {
        val parsed = url.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("Not a valid http(s) address: $url")
        return if (parsed.encodedPath.endsWith("/")) parsed else parsed.newBuilder().addPathSegment("").build()
    }

    private fun propfind(url: HttpUrl, username: String?, password: String?): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Depth", "1")
            .header("Accept", "application/xml, text/xml")
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML))
        if (!username.isNullOrEmpty()) {
            try {
                builder.header("Authorization", Credentials.basic(username, password.orEmpty(), StandardCharsets.UTF_8))
            } catch (e: IllegalArgumentException) {
                throw WebDavException(0, "The user name or password contains characters that can't be sent")
            }
        }
        return builder.build()
    }

    private fun read(response: Response): String {
        val code = response.code
        if (code != HTTP_MULTISTATUS && code !in 200..299) throw WebDavException(code, message(code))
        return response.body?.string().orEmpty()
    }

    private fun message(code: Int): String = when (code) {
        401 -> "The user name or password is wrong"
        403 -> "The server doesn't allow access to this folder"
        404, 410 -> "This folder doesn't exist on the server"
        405, 501 -> "The server doesn't offer WebDAV at this address"
        in 300..399 -> "The server sent this address elsewhere (HTTP $code)"
        in 500..599 -> "The server failed (HTTP $code)"
        else -> "The server answered HTTP $code"
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
                    continuation.resume(response) { _, unread, _ -> unread.close() }
                }
            },
        )
    }

    companion object {
        const val HTTP_MULTISTATUS = 207

        private val XML = "application/xml; charset=utf-8".toMediaType()

        /** The properties a listing needs; servers answer the ones they lack in a 404 propstat. */
        private val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:"><D:prop><D:resourcetype/><D:getcontentlength/><D:getlastmodified/><D:getcontenttype/><D:displayname/></D:prop></D:propfind>
        """.trimIndent()
    }
}
