package com.aakash.novafork.sources.media

import com.aakash.novafork.core.jellyfin.JellyfinAuth
import com.aakash.novafork.core.jellyfin.JellyfinClient
import com.aakash.novafork.core.jellyfin.JellyfinException
import com.aakash.novafork.core.jellyfin.JellyfinFlavor
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Signs in to Jellyfin and Emby servers and keeps the sign-in: a source's stored token first, then
 * this process's latest sign-in for the same server and user, then a new sign-in with the stored
 * user name and password (stored with the source through [MediaServerEnvironment.storeAuth]).
 */
internal class JellyfinConnector(private val env: MediaServerEnvironment) {
    /** Latest sign-in per server and user, also for sources not saved yet (a connection test). */
    private val signedIn = ConcurrentHashMap<String, JellyfinAuth>()
    private val signInLocks = ConcurrentHashMap<String, Mutex>()

    fun client(source: SourceEntity): JellyfinClient =
        JellyfinClient(env.http, source.location, flavorOf(source.type), env.jellyfinDevice)

    /**
     * Runs [block] signed in to [source]'s server. When the server refuses the token (401) and a
     * user name is stored, signs in again and runs [block] once more.
     */
    suspend fun <T> withAuth(source: SourceEntity, block: suspend (JellyfinClient, JellyfinAuth) -> T): T {
        val client = client(source)
        val known = knownAuth(source, client)
        if (known != null) {
            try {
                return block(client, known)
            } catch (e: JellyfinException) {
                if (e.code != HTTP_UNAUTHORIZED || source.username == null) throw e
            }
        }
        return block(client, signIn(source, client, refused = known))
    }

    /** The sign-in to try first; null when there is none (or the stored token is known to be refused). */
    private suspend fun knownAuth(source: SourceEntity, client: JellyfinClient): JellyfinAuth? {
        val token = source.token ?: return signedIn[key(source)]
        val userId = source.userId ?: try {
            // A pasted token (or API key) comes without its user.
            client.currentUser(token).id.also { env.storeAuth(source, token, it, source.serverId) }
        } catch (e: JellyfinException) {
            if (e.code == HTTP_UNAUTHORIZED && source.username != null) return null
            throw e
        }
        return JellyfinAuth(token = token, userId = userId, serverId = source.serverId)
    }

    private suspend fun signIn(source: SourceEntity, client: JellyfinClient, refused: JellyfinAuth?): JellyfinAuth {
        val key = key(source)
        return signInLocks.getOrPut(key) { Mutex() }.withLock {
            // Someone else may have signed in while this caller waited; another sign-in from the same
            // device would end that session again.
            val latest = signedIn[key]
            if (latest != null && latest.token != refused?.token) return@withLock latest
            val username = source.username
                ?: throw JellyfinException(HTTP_UNAUTHORIZED, "Enter the user name for ${client.host}")
            val auth = client.authenticate(username, source.password)
            signedIn[key] = auth
            env.storeAuth(source, auth.token, auth.userId, auth.serverId ?: source.serverId)
            auth
        }
    }

    private fun key(source: SourceEntity): String = "${source.type.name}|${source.location}|${source.username.orEmpty()}"

    companion object {
        private const val HTTP_UNAUTHORIZED = 401

        fun flavorOf(type: SourceType): JellyfinFlavor = if (type == SourceType.EMBY) JellyfinFlavor.EMBY else JellyfinFlavor.JELLYFIN
    }
}
