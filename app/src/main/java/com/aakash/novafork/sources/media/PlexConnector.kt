package com.aakash.novafork.sources.media

import com.aakash.novafork.core.plex.PlexException
import com.aakash.novafork.core.plex.PlexServerClient
import com.aakash.novafork.core.plex.PlexTvClient
import com.aakash.novafork.data.db.SourceEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Opens Plex servers with the right token: the one stored with the source (pasted, or from a
 * plex.tv sign-in), else one signed in with the stored user name and password, else none (a
 * server can let its own network in without). When the server refuses an account's token (a
 * server shared by a friend wants its own), plex.tv is asked for the server's token.
 */
internal class PlexConnector(
    private val env: MediaServerEnvironment,
    private val plexTvUrl: String = PlexTvClient.DEFAULT_BASE_URL,
) {
    /** Latest working token per server and user, also for sources not saved yet (a connection test). */
    private val tokens = ConcurrentHashMap<String, String>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    private val plexTv: PlexTvClient by lazy { PlexTvClient(env.http, env.plexClient, plexTvUrl) }

    fun client(source: SourceEntity, token: String?): PlexServerClient =
        PlexServerClient(env.http, source.location, env.plexClient, token)

    /**
     * Runs [block] with a client for [source]'s server and the token it uses. A refused token (401)
     * is replaced once (see the class) and [block] runs again.
     */
    suspend fun <T> withServer(source: SourceEntity, block: suspend (client: PlexServerClient, token: String?) -> T): T {
        val token = source.token ?: tokens[key(source)] ?: renew(source, refused = null)
        try {
            return block(client(source, token), token)
        } catch (e: PlexException) {
            if (e.code != HTTP_UNAUTHORIZED) throw e
            val renewed = renew(source, refused = token) ?: throw e
            return block(client(source, renewed), renewed)
        }
    }

    /** Remembers the server's machineIdentifier with a saved source. */
    suspend fun rememberServer(source: SourceEntity, token: String?, machineIdentifier: String?) {
        val id = machineIdentifier?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (id != source.serverId) env.storeAuth(source, token, source.userId, id)
    }

    /**
     * A token to use instead of [refused] (null: [source] has none yet): the latest one another
     * caller found, the server's own token from plex.tv, or a new sign-in with the stored user name
     * and password. Null when there is nothing better.
     */
    private suspend fun renew(source: SourceEntity, refused: String?): String? {
        val key = key(source)
        return locks.getOrPut(key) { Mutex() }.withLock {
            tokens[key]?.takeIf { it != refused }?.let { return@withLock it }
            val shared = refused?.let { serverToken(source, it) }?.takeIf { it != refused }
            val renewed = shared ?: signIn(source)?.let { account -> serverToken(source, account) ?: account }
            if (renewed != null) {
                tokens[key] = renewed
                env.storeAuth(source, renewed, source.userId, source.serverId)
            }
            renewed
        }
    }

    /** The server's own token for [accountToken]'s account, from plex.tv's list of its servers; null when it isn't listed. */
    private suspend fun serverToken(source: SourceEntity, accountToken: String): String? =
        try {
            val machine = source.serverId ?: client(source, null).identity().machineIdentifier
            plexTv.servers(accountToken)
                .firstOrNull { it.clientIdentifier.equals(machine, ignoreCase = true) }
                ?.accessToken
                ?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MediaServerLog.warn("Couldn't look the Plex server up on plex.tv", e)
            null
        }

    /** The account's token from a sign-in with the stored user name and password; null without them. */
    private suspend fun signIn(source: SourceEntity): String? {
        val login = source.username ?: return null
        val password = source.password ?: return null
        return plexTv.signIn(login, password)
    }

    private fun key(source: SourceEntity): String = "${source.location}|${source.username.orEmpty()}"

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
