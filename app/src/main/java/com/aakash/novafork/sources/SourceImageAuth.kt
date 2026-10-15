package com.aakash.novafork.sources

import com.aakash.novafork.core.plex.PlexServerClient
import com.aakash.novafork.data.NetworkLocation
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/** A header image requests to a source's server carry: its login or token. */
internal class ImageAuth(
    /** The requests it goes with: same scheme, host and port, at or below this path. */
    val location: NetworkLocation,
    val header: String,
    val value: String,
)

/** Which sources' artwork needs a sign-in to load, and how it is sent. */
internal object SourceImageAuth {
    private const val AUTHORIZATION = "Authorization"

    /**
     * What image requests to [source]'s server need: WebDAV its login as HTTP Basic (posters and
     * fanart sit next to the videos, below its location), Plex its token as an X-Plex-Token header
     * (artwork comes from the server's photo transcoder; stored artwork URLs never carry the
     * token). Null when nothing is needed: the other types' images load without a sign-in.
     */
    fun of(source: SourceEntity): ImageAuth? = when (source.type) {
        SourceType.WEBDAV -> webDavLogin(source)
        SourceType.PLEX -> plexToken(source)
        SourceType.DEVICE, SourceType.FOLDER, SourceType.SMB, SourceType.STREAM, SourceType.FTP, SourceType.SFTP,
        SourceType.NFS, SourceType.UPNP, SourceType.JELLYFIN, SourceType.EMBY,
        -> null
    }

    private fun webDavLogin(source: SourceEntity): ImageAuth? {
        val user = source.username?.takeIf { it.isNotEmpty() } ?: return null
        val location = NetworkLocation.parse(source.location) ?: return null
        return ImageAuth(location, AUTHORIZATION, Credentials.basic(user, source.password.orEmpty(), StandardCharsets.UTF_8))
    }

    private fun plexToken(source: SourceEntity): ImageAuth? {
        // OkHttp refuses header values outside printable ASCII; a real token never has any.
        val token = source.token?.trim()?.takeIf { value -> value.isNotEmpty() && value.all { it in ' '..'~' } } ?: return null
        // The whole server, like the token given to playback: a base path in the address isn't where the artwork lives.
        val server = NetworkLocation.parse(source.location)?.copy(path = "") ?: return null
        return ImageAuth(server, PlexServerClient.TOKEN_HEADER, token)
    }
}

/**
 * Lets the image loader read artwork that needs a source's sign-in (see [SourceImageAuth]): a
 * request to a server one of [sources] has an [ImageAuth] for gets that header, unless it carries
 * one of that name already. Requests elsewhere pass untouched.
 *
 * Add it as a *network* interceptor of the image loader's OkHttp client: it then sees each hop of
 * a redirect on its own, and a hop that leaves the source's server goes without the header (OkHttp
 * drops only Authorization itself; it would carry X-Plex-Token along). [sources] is read on the
 * first request, then followed in [scope], so creating this never touches the library.
 */
internal class SourceImageAuthInterceptor(
    private val scope: CoroutineScope,
    private val authOf: (SourceEntity) -> ImageAuth? = SourceImageAuth::of,
    private val sources: () -> Flow<List<SourceEntity>>,
) : Interceptor {

    @Volatile
    private var auths: List<ImageAuth>? = null
    private val following = AtomicBoolean(false)

    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(authorize(chain.request()))

    /** [request] with the headers its server needs, the most specific source's first. */
    fun authorize(request: Request): Request {
        val target = NetworkLocation.parse(request.url.toString()) ?: return request
        var builder: Request.Builder? = null
        val given = HashSet<String>()
        for (auth in currentAuths()) {
            if (!auth.location.covers(target) || request.header(auth.header) != null || !given.add(auth.header)) continue
            builder = (builder ?: request.newBuilder()).header(auth.header, auth.value)
        }
        return builder?.build() ?: request
    }

    private fun currentAuths(): List<ImageAuth> {
        auths?.let { return it }
        if (following.compareAndSet(false, true)) {
            scope.launch { sources().collect { auths = authsOf(it) } }
        }
        // OkHttp calls interceptors on its own threads, where waiting once for the list is fine.
        val first = try {
            runBlocking { authsOf(sources().first()) }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            emptyList()
        } catch (e: Exception) {
            // Anything but an IOException thrown here would take OkHttp's thread down: load without a sign-in.
            emptyList()
        }
        return auths ?: first.also { auths = it }
    }

    private fun authsOf(list: List<SourceEntity>): List<ImageAuth> =
        list.mapNotNull(authOf).sortedByDescending { it.location.path.length }
}
