package com.aakash.novafork.data

import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.VideoWithMeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class ScanState(
    val running: Boolean = false,
    /** Source being scanned right now ("Device storage", "NAS · Movies"). */
    val sourceName: String? = null,
    val found: Int = 0,
    /** Metadata lookups after the file scan. */
    val enriching: Boolean = false,
    val enrichDone: Int = 0,
    val enrichTotal: Int = 0,
    val lastError: String? = null,
)

enum class IdentifyType { MOVIE, TV }

data class TmdbSearchResult(
    val tmdbId: Int,
    val type: IdentifyType,
    val title: String,
    val year: Int?,
    val overview: String?,
    /** Full poster URL or null. */
    val posterUrl: String?,
)

interface LibraryRepository {
    /** Every present video with its metadata. Emits on any change. */
    fun observeVideos(): Flow<List<VideoWithMeta>>

    fun observeSources(): Flow<List<SourceEntity>>

    val scanState: StateFlow<ScanState>

    suspend fun video(id: Long): VideoWithMeta?
    suspend fun source(id: Long): SourceEntity?
    suspend fun findByUri(uri: String): VideoWithMeta?

    /**
     * Scans every enabled source (device storage only when Settings.scanDeviceStorage and the
     * permission is granted), upserts videos (keeping playback state), marks missing ones
     * present=false, then matches PENDING/stale videos against TMDB in the background.
     * Concurrent calls coalesce into the running scan.
     */
    suspend fun refreshAll()

    suspend fun scanSource(sourceId: Long)

    /** Adds a SAF tree (the caller already took the persistable permission) and scans it. */
    suspend fun addFolder(treeUri: String, displayName: String): Long

    /** Adds or updates (id != null) an SMB source, then scans it. Location built with core SmbUris. */
    suspend fun saveSmbServer(
        id: Long?,
        name: String,
        host: String,
        port: Int?,
        share: String,
        path: String,
        username: String?,
        password: String?,
        domain: String?,
    ): Long

    suspend fun removeSource(sourceId: Long)

    /** Saves a stream URL as a STREAM source plus one video; returns the video id. Title defaults to the URL's last segment. */
    suspend fun addStream(title: String?, url: String): Long

    /** Credentials of the SMB source that owns host+share (exact share match, case-insensitive host). */
    suspend fun smbCredentials(host: String, share: String): SmbCredentials?

    /**
     * Stores progress. Marks watched (and resets position to 0) when within 3% or 90 seconds of the
     * end, whichever is larger; increments playCount then. Also updates lastPlayedAt.
     */
    suspend fun savePlayback(videoId: Long, positionMs: Long, durationMs: Long)

    suspend fun saveTracks(videoId: Long, audioTrackId: Int?, subtitleTrackId: Int?)

    suspend fun setWatched(videoId: Long, watched: Boolean)

    /** The episode after this one in the same show (next episode, else first of next season). */
    suspend fun nextEpisode(videoId: Long): VideoWithMeta?

    suspend fun searchTmdb(query: String, type: IdentifyType?): List<TmdbSearchResult>

    /** Manual match. For TV the video keeps its parsed season/episode; episode data is fetched. Sets MatchState.MANUAL. */
    suspend fun identify(videoId: Long, result: TmdbSearchResult)

    /** Drops all TMDB data and match cache; marks every non-manual video PENDING and re-matches. */
    suspend fun clearMetadata()

    /** Re-runs matching for UNMATCHED videos now (e.g. after a TMDB key was added). */
    suspend fun retryMatching()
}
