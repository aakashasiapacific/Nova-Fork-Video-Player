package com.aakash.novafork.sources.media

import com.aakash.novafork.core.plex.PlexPin
import com.aakash.novafork.core.plex.PlexResource
import com.aakash.novafork.core.plex.PlexTvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Signing in to Plex without typing a token, for the add-server screen: show [PlexPin.code] and
 * [linkUrl] (or open [approvalUrl]), wait for [awaitToken], then let the person pick one of
 * [servers] — its first connection's uri, accessToken and clientIdentifier make the
 * NetworkSourceInput (address, token, serverId). A user name and password work too ([signIn]).
 * Failures are PlexExceptions with messages fit for the user.
 */
internal class PlexSignIn(private val env: MediaServerEnvironment) {
    private val plexTv: PlexTvClient by lazy { PlexTvClient(env.http, env.plexClient) }

    /** Where people enter the code. */
    val linkUrl: String get() = PlexTvClient.LINK_URL

    /** A new 4-character code, valid for [PlexPin.expiresIn] seconds. */
    suspend fun createCode(): PlexPin = withContext(Dispatchers.IO) { plexTv.createPin(strong = false) }

    /** The page where a person already signed in to Plex in the browser approves [pin] with one tap. */
    suspend fun approvalUrl(pin: PlexPin): String = withContext(Dispatchers.IO) { plexTv.authUrl(pin) }

    /** Waits (polling every 2 s) until someone entered [pin]'s code; returns the account's token. Cancel to stop. */
    suspend fun awaitToken(pin: PlexPin): String = withContext(Dispatchers.IO) { plexTv.awaitToken(pin) }

    /** The account's token for a Plex user name (or email) and password. */
    suspend fun signIn(login: String, password: String): String = withContext(Dispatchers.IO) { plexTv.signIn(login, password) }

    /** The servers the account behind [token] can open, the ones it owns first. */
    suspend fun servers(token: String): List<PlexResource> = withContext(Dispatchers.IO) {
        plexTv.servers(token).sortedByDescending { it.owned }
    }
}
