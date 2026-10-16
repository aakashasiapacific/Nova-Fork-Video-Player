package com.aakash.novafork.sources.media

import com.aakash.novafork.core.plex.PlexPlaybackState
import com.aakash.novafork.core.plex.PlexServerClient
import com.aakash.novafork.data.PlaybackOptions
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.VideoEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Playing Plex videos: the stored part URL gets the token (checked, and renewed when the server
 * refused it) when it is played, and playback goes to the server's timeline through
 * [MediaServerReporter].
 */
internal class PlexPlayback(private val connector: PlexConnector) : PlaybackOptions, MediaServerPlayback {
    override suspend fun mediaOptions(source: SourceEntity, uri: String): List<String> = emptyList()

    /**
     * [uri] with a working token; a URI on another server stays as it is. Never fails: when the
     * server can't be asked, the stored token (or none) goes along and libVLC reports what went wrong.
     */
    override suspend fun playUri(source: SourceEntity, uri: String): String = withContext(Dispatchers.IO) {
        if (!MediaServerUrls.onServer(source.location, uri)) return@withContext uri
        try {
            connector.withServer(source) { client, _ ->
                client.serverInfo()
                client.withToken(uri)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MediaServerLog.warn("Couldn't sign in to play a Plex video", e)
            connector.client(source, source.token).withToken(uri)
        }
    }

    override suspend fun start(source: SourceEntity, video: VideoEntity, positionMs: Long, durationMs: Long?) {
        send(source, video) { client, ratingKey -> client.timeline(ratingKey, PlexPlaybackState.PLAYING, positionMs, durationMs) }
    }

    override suspend fun progress(source: SourceEntity, video: VideoEntity, positionMs: Long, durationMs: Long?) {
        send(source, video) { client, ratingKey -> client.timeline(ratingKey, PlexPlaybackState.PLAYING, positionMs, durationMs) }
    }

    override suspend fun stop(source: SourceEntity, video: VideoEntity, positionMs: Long, durationMs: Long?) {
        send(source, video) { client, ratingKey -> client.timeline(ratingKey, PlexPlaybackState.STOPPED, positionMs, durationMs) }
    }

    override suspend fun setWatched(source: SourceEntity, video: VideoEntity, watched: Boolean) {
        send(source, video) { client, ratingKey -> client.setWatched(ratingKey, watched) }
    }

    /** Runs [call] for [video]'s ratingKey on its server; a video without one (not from a scan) is skipped. */
    private suspend fun send(source: SourceEntity, video: VideoEntity, call: suspend (client: PlexServerClient, ratingKey: String) -> Unit) {
        val ratingKey = video.remoteId?.takeIf { it.isNotBlank() } ?: return
        withContext(Dispatchers.IO) {
            connector.withServer(source) { client, _ -> call(client, ratingKey) }
        }
    }
}
