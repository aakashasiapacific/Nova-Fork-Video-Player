package com.aakash.novafork.sources.media

import com.aakash.novafork.core.plex.PlexConnection
import com.aakash.novafork.core.plex.PlexPin
import com.aakash.novafork.core.plex.PlexResource
import com.aakash.novafork.core.plex.PlexTvClient
import com.aakash.novafork.data.NetworkSourceInput
import com.aakash.novafork.data.db.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Signing in to Plex without typing a token, for the add-server screen: show [PlexPin.code] and
 * [linkUrl] (or open [approvalUrl]), wait for [awaitToken], then let the person pick one of
 * [servers]; [sourceInput] makes what LibraryRepository.saveNetworkSource takes (address, the
 * server's token, its id). A user name and password work too ([signIn]). Failures are
 * PlexExceptions with messages fit for the user.
 */
internal class PlexSignIn(private val env: MediaServerEnvironment) {
    private val plexTv: PlexTvClient by lazy { PlexTvClient(env.http, env.plexClient) }

    /** Where people enter the code. */
    val linkUrl: String get() = PlexTvClient.LINK_URL

    /**
     * A new code, valid for [PlexPin.expiresIn] seconds: 4 characters to enter at [linkUrl], or
     * with [strong] a long one that only works through [approvalUrl].
     */
    suspend fun createCode(strong: Boolean = false): PlexPin = withContext(Dispatchers.IO) { plexTv.createPin(strong) }

    /** The page where a person already signed in to Plex in the browser approves [pin] with one tap. */
    suspend fun approvalUrl(pin: PlexPin): String = withContext(Dispatchers.IO) { plexTv.authUrl(pin) }

    /**
     * Waits (polling every 2 s) until someone entered [pin]'s code; returns the account's token.
     * Checks that fail on the way (no network while the person is in the browser, plex.tv busy)
     * are logged and the wait goes on until the code expires. Cancel to stop.
     */
    suspend fun awaitToken(pin: PlexPin): String = withContext(Dispatchers.IO) {
        var logged: String? = null
        plexTv.awaitToken(pin) { failure ->
            // Once per kind of trouble, not once per poll: the crash report's trail is short.
            if (failure.message != logged) {
                logged = failure.message
                MediaServerLog.warn("Checking the Plex code failed; still waiting", failure)
            }
        }
    }

    /** The account's token for a Plex user name (or email) and password. */
    suspend fun signIn(login: String, password: String): String = withContext(Dispatchers.IO) { plexTv.signIn(login, password) }

    /** The servers the account behind [token] can open, the ones it owns first. */
    suspend fun servers(token: String): List<PlexResource> = withContext(Dispatchers.IO) {
        plexTv.servers(token).sortedByDescending { it.owned }
    }

    /**
     * What to save for [server] (one of [servers]) signed in with [accountToken]: its best address
     * (see [address]), its own token (a shared server has one of its own) and its id. Null when
     * plex.tv lists no address for it.
     */
    fun sourceInput(server: PlexResource, accountToken: String, name: String = ""): NetworkSourceInput? {
        val address = address(server) ?: return null
        return NetworkSourceInput(
            type = SourceType.PLEX,
            name = name.ifBlank { server.name.orEmpty() },
            address = address,
            token = server.accessToken?.takeIf { it.isNotBlank() } ?: accountToken,
            serverId = server.clientIdentifier,
        )
    }

    companion object {
        /**
         * The connection to use: on this network before the internet, direct before Plex's relay,
         * and over the internet https before http (the token goes along with every request).
         */
        fun address(server: PlexResource): String? =
            server.connections
                .filter { it.uri.startsWith("http://") || it.uri.startsWith("https://") }
                .sortedWith(compareBy<PlexConnection>({ it.relay }, { !it.local }, { !it.local && !it.uri.startsWith("https://") }))
                .firstOrNull()
                ?.uri
    }
}
