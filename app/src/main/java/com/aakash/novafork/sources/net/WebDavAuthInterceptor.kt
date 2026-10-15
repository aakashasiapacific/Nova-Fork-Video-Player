package com.aakash.novafork.sources.net

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
import okhttp3.Response
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lets the image loader read posters and fanart off WebDAV servers: a request to a WebDAV
 * source's server (same scheme, host and port, at or below its location) gets that source's
 * login as HTTP Basic. Requests elsewhere, and requests with an Authorization of their own, pass
 * untouched; OkHttp drops the header itself when a redirect leaves the host.
 *
 * Add it to the OkHttp client Coil uses (an application interceptor). [sources] is read on the
 * first request, then followed in [scope], so creating this never touches the library.
 */
internal class WebDavAuthInterceptor(
    private val scope: CoroutineScope,
    private val sources: () -> Flow<List<SourceEntity>>,
) : Interceptor {

    private class Login(val location: NetworkLocation, val header: String)

    @Volatile
    private var logins: List<Login>? = null
    private val following = AtomicBoolean(false)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) != null) return chain.proceed(request)
        val target = NetworkLocation.parse(request.url.toString()) ?: return chain.proceed(request)
        val login = currentLogins().firstOrNull { it.location.covers(target) } ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, login.header).build())
    }

    private fun currentLogins(): List<Login> {
        logins?.let { return it }
        if (following.compareAndSet(false, true)) {
            scope.launch { sources().collect { logins = loginsOf(it) } }
        }
        // OkHttp calls interceptors on its own threads, where waiting once for the list is fine.
        val first = try {
            runBlocking { loginsOf(sources().first()) }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            emptyList()
        } catch (e: Exception) {
            // Anything but an IOException thrown here would take OkHttp's thread down: load without a login.
            emptyList()
        }
        return logins ?: first.also { logins = it }
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"

        fun loginsOf(sources: List<SourceEntity>): List<Login> = sources.mapNotNull { source ->
            if (source.type != SourceType.WEBDAV) return@mapNotNull null
            val user = source.username?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val location = NetworkLocation.parse(source.location) ?: return@mapNotNull null
            val header = try {
                Credentials.basic(user, source.password.orEmpty(), StandardCharsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                return@mapNotNull null
            }
            Login(location, header)
        }.sortedByDescending { it.location.path.length }
    }
}
