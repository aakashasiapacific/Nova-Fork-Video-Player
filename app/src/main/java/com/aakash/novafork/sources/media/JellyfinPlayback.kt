package com.aakash.novafork.sources.media

import com.aakash.novafork.core.jellyfin.JellyfinAuth
import com.aakash.novafork.core.jellyfin.JellyfinClient
import com.aakash.novafork.data.PlaybackOptions
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.VideoEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Playing Jellyfin and Emby videos: the stored stream URL gets the access token (checked, and
 * renewed when the server refused it) when it is played, and playback is reported to the server
 * through [MediaServerReporter].
 */
internal class JellyfinPlayback(private val connector: JellyfinConnector) : PlaybackOptions, MediaServerPlayback {
    override suspend fun mediaOptions(source: SourceEntity, uri: String): List<String> = emptyList()

    /**
     * [uri] with a working token; a URI on another server stays as it is. Never fails: when the
     * server can't be asked, the stored token (or none) goes along and libVLC reports what went wrong.
     */
    override suspend fun playUri(source: SourceEntity, uri: String): String = withContext(Dispatchers.IO) {
        if (JellyfinClient.itemIdOf(uri) == null || !MediaServerUrls.onServer(source.location, uri)) return@withContext uri
        try {
            connector.withAuth(source) { client, auth ->
                client.user(auth)
                client.withToken(uri, auth.token)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MediaServerLog.warn("Couldn't sign in to play a ${source.type.name} video", e)
            source.token?.let { connector.client(source).withToken(uri, it) } ?: uri
        }
    }

    override suspend fun start(source: SourceEntity, video: VideoEntity, positionMs: Long, durationMs: Long?) {
        send(source, video) { client, auth, itemId, mediaSourceId -> client.reportPlaying(auth, itemId, mediaSourceId, positionMs) }
    }

    override suspend fun progress(source: SourceEntity, video: VideoEntity, positionMs: Long, durationMs: Long?) {
        send(source, video) { client, auth, itemId, mediaSourceId -> client.reportProgress(auth, itemId, mediaSourceId, positionMs) }
    }

    override suspend fun stop(source: SourceEntity, video: VideoEntity, positionMs: Long, durationMs: Long?) {
        send(source, video) { client, auth, itemId, mediaSourceId -> client.reportStopped(auth, itemId, mediaSourceId, positionMs) }
    }

    override suspend fun setWatched(source: SourceEntity, video: VideoEntity, watched: Boolean) {
        send(source, video) { client, auth, itemId, _ -> client.setPlayed(auth, itemId, watched) }
    }

    /** Runs [call] for [video]'s item on its server; a video without an item id (not from a scan) is skipped. */
    private suspend fun send(
        source: SourceEntity,
        video: VideoEntity,
        call: suspend (client: JellyfinClient, auth: JellyfinAuth, itemId: String, mediaSourceId: String?) -> Unit,
    ) {
        val itemId = video.remoteId?.takeIf { it.isNotBlank() } ?: JellyfinClient.itemIdOf(video.uri) ?: return
        val mediaSourceId = JellyfinClient.mediaSourceIdOf(video.uri)
        withContext(Dispatchers.IO) {
            connector.withAuth(source) { client, auth -> call(client, auth, itemId, mediaSourceId) }
        }
    }
}
