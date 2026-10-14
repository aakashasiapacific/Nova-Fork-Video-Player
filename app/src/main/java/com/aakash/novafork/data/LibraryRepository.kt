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

/**
 * A playlist as lists show it. [itemCount] and [coverVideoId] only take the videos the library
 * shows (present, from a source that is turned on); [LibraryRepository.playlistVideos] lists the same.
 */
data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val itemCount: Int,
    /** Last change to the name or the items, epoch ms. */
    val updatedAt: Long,
    /** First shown video, for the playlist's artwork; null while it shows none. */
    val coverVideoId: Long?,
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

    /**
     * Adds or updates (id != null) an FTP, SFTP, NFS, UPnP, WebDAV, Jellyfin, Emby or Plex source,
     * then scans it (once a scanner for its type is registered). [input] goes through
     * [NetworkSources.toSource]: IllegalArgumentException carries a message for the user. Adding
     * what exists already (same type, location and user name) updates that source instead. An edit
     * that keeps the location and user name keeps the stored token, user id and server id unless
     * [input] brings new ones; changing either forgets them (the next scan signs in again).
     */
    suspend fun saveNetworkSource(id: Long?, input: NetworkSourceInput): Long

    /**
     * Stores what signing in to a media server gave (null clears a value); the media-server
     * scanners and browsers call it. An unknown id is ignored.
     */
    suspend fun updateSourceAuth(sourceId: Long, token: String?, userId: String?, serverId: String?)

    /**
     * Removes a folder, share, server or stream with its videos. Videos another share (SMB, FTP,
     * SFTP, NFS, WebDAV) of the same type also covers move to that share instead. The DEVICE
     * source can't be removed (IllegalArgumentException): turn off Settings.scanDeviceStorage to hide it.
     */
    suspend fun removeSource(sourceId: Long)

    /** Saves a stream URL as a STREAM source plus one video; returns the video id. Title defaults to the URL's last segment. */
    suspend fun addStream(title: String?, url: String): Long

    /** Credentials of the SMB source that owns host+share (exact share match, case-insensitive host). */
    suspend fun smbCredentials(host: String, share: String): SmbCredentials?

    /**
     * The source a video belongs to, with its credentials and token: what [PlaybackOptions] and
     * [RemoteProgressReporter] are given. Null for an unknown id.
     */
    suspend fun sourceForVideo(videoId: Long): SourceEntity?

    /**
     * The source [uri] belongs to: the one of the video stored under [uri], else the network
     * source whose location contains it (artwork or subtitles next to a video on an FTP server),
     * an enabled one and then the deepest location first. Null when none does.
     */
    suspend fun sourceForUri(uri: String): SourceEntity?

    /**
     * Stores progress. Marks watched (and resets position to 0) when within 3% or 90 seconds of the
     * end, whichever is larger; increments playCount then. Also updates lastPlayedAt. A video of a
     * source with a [RemoteProgressReporter] then has the progress reported to its server, in the
     * background.
     */
    suspend fun savePlayback(videoId: Long, positionMs: Long, durationMs: Long)

    suspend fun saveTracks(videoId: Long, audioTrackId: Int?, subtitleTrackId: Int?)

    /** Also reported to the video's server, as [savePlayback] does. */
    suspend fun setWatched(videoId: Long, watched: Boolean)

    /** The episode after this one in the same show (next episode, else first of next season). */
    suspend fun nextEpisode(videoId: Long): VideoWithMeta?

    suspend fun searchTmdb(query: String, type: IdentifyType?): List<TmdbSearchResult>

    /**
     * Manual match; sets MatchState.MANUAL. For TV every episode sharing the video's showKey is
     * matched to the show too (the whole show moves to the new showKey "tv:<id>"); each keeps its
     * parsed season/episode (absolute numbers placed into the show's seasons) and gets episode data.
     */
    suspend fun identify(videoId: Long, result: TmdbSearchResult)

    /**
     * Drops all TMDB data and match cache; marks every non-manual video PENDING and re-matches.
     * What media servers described (ScannedVideo.serverMetadata) stays as it is.
     */
    suspend fun clearMetadata()

    /** Re-runs matching for UNMATCHED videos now (e.g. after a TMDB key was added). */
    suspend fun retryMatching()

    // Playlists. A video is in a playlist at most once. Items whose file is missing (or whose source
    // is turned off) are kept but left out of counts, lists and positions until the file is back;
    // removing a source removes its videos from every playlist.

    /** Most recently changed first. Emits on any change. */
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    /** The playlist's shown videos in playlist order (empty for an unknown id). */
    suspend fun playlistVideos(playlistId: Long): List<VideoWithMeta>

    /** Returns the new playlist's id. The name is trimmed and must not be blank (IllegalArgumentException). */
    suspend fun createPlaylist(name: String): Long

    /** Same name rules as [createPlaylist]; an unknown id is ignored. */
    suspend fun renamePlaylist(id: Long, name: String)

    suspend fun deletePlaylist(id: Long)

    /**
     * Appends [videoIds] in the given order, skipping videos already in the playlist, repeats and
     * ids without a video. An unknown playlist is ignored.
     */
    suspend fun addToPlaylist(playlistId: Long, videoIds: List<Long>)

    suspend fun removeFromPlaylist(playlistId: Long, videoId: Long)

    /**
     * Moves the item at index [from] of [playlistVideos] so it ends up at index [to]; indices out
     * of range are ignored (the list may have changed under a drag).
     */
    suspend fun movePlaylistItem(playlistId: Long, from: Int, to: Int)

    // Queues: ordered video ids to play, from what the library shows.

    /**
     * The videos directly in one folder of a source (not its subfolders), by file name with
     * numbers in order ("Episode 2" before "Episode 10"). [path] is the folder as Browse lists it:
     * relative to the source's root, '/'-separated, "" for the root.
     */
    suspend fun folderQueue(sourceId: Long, path: String): List<Long>

    /**
     * A show's episodes in watching order: by season (specials, season 0, last), then episode.
     * [season] null = the whole show; season 1 includes episodes without a season. Of two copies
     * of an episode (720p and 1080p) the first by file name plays, and a double episode
     * (S01E01E02) stands in for the episodes it contains.
     */
    suspend fun seasonQueue(showKey: String, season: Int?): List<Long>

    /**
     * What to play from [videoId] on: for an episode, it and the rest of its show after it (as in
     * [seasonQueue]); for anything else just the video itself. Empty for an unknown id.
     */
    suspend fun nextUpQueue(videoId: Long): List<Long>
}
