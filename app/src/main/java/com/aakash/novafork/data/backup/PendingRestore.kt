package com.aakash.novafork.data.backup

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.aakash.novafork.core.library.LibraryKeys
import com.aakash.novafork.core.tmdb.TmdbException
import com.aakash.novafork.data.IdentifyType
import com.aakash.novafork.data.LibraryRepository
import com.aakash.novafork.data.TmdbSearchResult
import com.aakash.novafork.data.db.FavoriteSongEntity
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.NovaDatabase
import com.aakash.novafork.data.db.PlaylistSongEntity
import com.aakash.novafork.data.db.SQL_CHUNK
import com.aakash.novafork.data.db.VideoEntity
import com.aakash.novafork.data.db.VideoFavoriteEntity
import com.aakash.novafork.data.db.WatchlistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

private const val TAG = "PendingRestore"
private const val FILE_NAME = "pending_restore.json"
private const val HTTP_NOT_FOUND = 404

/** What is left of a restore 30 days after it is dropped: those files are not coming. */
private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

/** A playlist entry of a restore, waiting for its video; [playlist] is the playlist's name. */
@Serializable
internal data class PendingPlaylistItem(val playlist: String, val video: BackupVideoRef)

/** A playlist's song of a restore, waiting for its song to be scanned. */
@Serializable
internal data class PendingSongPlaylistItem(val playlist: String, val song: BackupSongRef)

/** Which list a [PendingSavedVideo] goes to. */
internal const val WATCHLIST = "watchlist"
internal const val FAVORITES = "favorites"

/** A movie or video on the watchlist ([list] = [WATCHLIST]) or starred ([FAVORITES]), waiting for its file. */
@Serializable
internal data class PendingSavedVideo(val list: String, val video: BackupVideoRef, val addedAt: Long = 0)

@Serializable
internal data class PendingRestoreData(
    /** The latest restore that added entries (epoch ms); everything left goes [MAX_AGE_MS] after it. */
    val restoredAt: Long,
    val videos: List<BackupVideoState> = emptyList(),
    val matches: List<BackupMatch> = emptyList(),
    val playlistItems: List<PendingPlaylistItem> = emptyList(),
    val songs: List<BackupSongState> = emptyList(),
    val songPlaylistItems: List<PendingSongPlaylistItem> = emptyList(),
    val savedVideos: List<PendingSavedVideo> = emptyList(),
    val songFavorites: List<BackupSongFavorite> = emptyList(),
) {
    fun isEmpty(): Boolean =
        videos.isEmpty() && matches.isEmpty() && playlistItems.isEmpty() && songs.isEmpty() && songPlaylistItems.isEmpty() &&
            savedVideos.isEmpty() && songFavorites.isEmpty()

    fun counts(): RestoreCounts =
        RestoreCounts(videos.size, matches.size, playlistItems.size + songPlaylistItems.size, songs.size, savedVideos.size + songFavorites.size)
}

/**
 * The entries of a restored backup whose videos the library doesn't have yet (sources still to
 * scan, a new device), kept in filesDir/pending_restore.json. The library applies them after each
 * scan of a source; an entry leaves the file once applied, and whatever is left goes 30 days after
 * the last restore. Every instance shares one lock, so the library's passes and a restore never
 * write the file over each other.
 */
internal class PendingRestore(
    context: Context,
    private val database: NovaDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val appContext: Context = context.applicationContext
    private val file: File by lazy { File(appContext.filesDir, FILE_NAME) }
    private val sourceDao = database.sourceDao()
    private val videoDao = database.videoDao()
    private val playlistDao = database.playlistDao()
    private val songDao = database.songDao()
    private val playlistSongDao = database.playlistSongDao()
    private val watchlistDao = database.watchlistDao()
    private val videoFavoriteDao = database.videoFavoriteDao()
    private val favoriteSongDao = database.favoriteSongDao()

    /** Cheap check before a pass: false when nothing waits. Call off the main thread. */
    fun exists(): Boolean = file.exists()

    suspend fun waiting(): RestoreCounts = withContext(Dispatchers.IO) {
        fileLock.withLock { load()?.counts() ?: RestoreCounts() }
    }

    /** Adds a restore's entries; one for a video or song that already waits replaces the older one. */
    suspend fun add(
        videos: List<BackupVideoState>,
        matches: List<BackupMatch>,
        playlistItems: List<PendingPlaylistItem>,
        songs: List<BackupSongState> = emptyList(),
        songPlaylistItems: List<PendingSongPlaylistItem> = emptyList(),
        savedVideos: List<PendingSavedVideo> = emptyList(),
        songFavorites: List<BackupSongFavorite> = emptyList(),
    ) {
        withContext(Dispatchers.IO) {
            fileLock.withLock {
                val old = load()
                val newVideos = videos.mapTo(HashSet()) { it.video }
                val newMatches = matches.mapTo(HashSet()) { it.video }
                val newSongs = songs.mapTo(HashSet()) { it.song }
                save(
                    PendingRestoreData(
                        restoredAt = clock(),
                        videos = old?.videos.orEmpty().filter { it.video !in newVideos } + videos,
                        matches = old?.matches.orEmpty().filter { it.video !in newMatches } + matches,
                        playlistItems = (old?.playlistItems.orEmpty() + playlistItems).distinct(),
                        songs = old?.songs.orEmpty().filter { it.song !in newSongs } + songs,
                        songPlaylistItems = (old?.songPlaylistItems.orEmpty() + songPlaylistItems).distinct(),
                        savedVideos = (old?.savedVideos.orEmpty() + savedVideos).distinctBy { it.list to it.video },
                        songFavorites = (old?.songFavorites.orEmpty() + songFavorites).distinctBy { it.song },
                    ),
                )
            }
        }
    }

    /**
     * Applies the entries whose videos the library now has: playback state, playlist items (to
     * the playlist of that name, if it still exists) and, with [identify], manual matches through
     * [LibraryRepository.identify], so their artwork and details come along. Without [identify]
     * the matches that could be applied are only counted ([PendingApplyResult.matchesReady]):
     * they ask TMDB, which a scan should not wait for. A match TMDB can't be asked for now (no
     * connection, no key) waits for the next pass.
     */
    suspend fun apply(library: LibraryRepository, identify: Boolean): PendingApplyResult = withContext(Dispatchers.IO) {
        val pass = fileLock.withLock { applyLocal(library) } ?: return@withContext PendingApplyResult()
        if (!identify || pass.ready.isEmpty()) return@withContext pass.result
        val outcome = identifyLock.withLock { identifyAll(library, pass.ready) }
        val waiting = fileLock.withLock {
            val data = load() ?: return@withLock RestoreCounts()
            val left = data.copy(matches = data.matches.filterNot { it in outcome.done })
            save(left)
            left.counts()
        }
        PendingApplyResult(
            applied = pass.result.applied + RestoreCounts(matches = outcome.applied),
            waiting = waiting,
            matchesReady = (pass.ready.size - outcome.done.size).coerceAtLeast(0),
            matchesFailed = outcome.failed,
        )
    }

    private class LocalPass(val result: PendingApplyResult, val ready: List<Pair<BackupMatch, Long>>)

    private class IdentifyOutcome(val done: Set<BackupMatch>, val applied: Int, val failed: Int)

    /** Playback state and playlist items; called with [fileLock] held. */
    private suspend fun applyLocal(library: LibraryRepository): LocalPass? {
        val data = load() ?: return null
        val matcher = VideoMatcher(videoDao.identities(), sourceDao.all())

        val found = ArrayList<Pair<BackupVideoState, Long>>()
        val missing = ArrayList<BackupVideoState>()
        for (state in data.videos) {
            val id = matcher.match(state.video)
            if (id != null) found += state to id else missing += state
        }
        if (found.isNotEmpty()) applyStates(found)

        var itemsApplied = 0
        val itemsLeft = ArrayList<PendingPlaylistItem>()
        if (data.playlistItems.isNotEmpty()) {
            val playlists = playlistDao.all()
            // groupBy keeps the playlists' item order.
            for ((name, items) in data.playlistItems.groupBy { it.playlist }) {
                // A playlist deleted since the restore takes its waiting items with it.
                val playlist = playlists.firstOrNull { it.name == name } ?: continue
                val ids = ArrayList<Long>()
                for (item in items) {
                    val id = matcher.match(item.video)
                    if (id != null) ids += id else itemsLeft += item
                }
                if (ids.isNotEmpty()) {
                    library.addToPlaylist(playlist.id, ids)
                    itemsApplied += ids.size
                }
            }
        }

        val songPass = applySongs(data)

        // Watchlist and favorites: the key names this device's id for the video.
        var savedApplied = 0
        val savedLeft = ArrayList<PendingSavedVideo>()
        if (data.savedVideos.isNotEmpty()) {
            database.withTransaction {
                for (item in data.savedVideos) {
                    val id = matcher.match(item.video)
                    if (id == null) {
                        savedLeft += item
                        continue
                    }
                    val addedAt = item.addedAt.takeIf { it > 0 } ?: clock()
                    // Both lists keep an entry that is already there (and its date).
                    if (item.list == WATCHLIST) {
                        watchlistDao.add(WatchlistEntity("v:$id", addedAt))
                    } else {
                        videoFavoriteDao.add(VideoFavoriteEntity("v:$id", addedAt))
                    }
                    savedApplied++
                }
            }
        }

        val ready = data.matches.mapNotNull { match -> matcher.match(match.video)?.let { match to it } }
        val left = data.copy(
            videos = missing,
            playlistItems = itemsLeft,
            songs = songPass.songsLeft,
            songPlaylistItems = songPass.itemsLeft,
            savedVideos = savedLeft,
            songFavorites = songPass.favoritesLeft,
        )
        save(left)
        return LocalPass(
            result = PendingApplyResult(
                applied = RestoreCounts(
                    videos = found.size,
                    playlistItems = itemsApplied + songPass.itemsApplied,
                    songs = songPass.songsApplied,
                    saved = savedApplied + songPass.favoritesApplied,
                ),
                waiting = left.counts(),
                matchesReady = ready.size,
            ),
            ready = ready,
        )
    }

    private class SongPass(
        val songsApplied: Int,
        val songsLeft: List<BackupSongState>,
        val itemsApplied: Int,
        val itemsLeft: List<PendingSongPlaylistItem>,
        val favoritesApplied: Int = 0,
        val favoritesLeft: List<BackupSongFavorite> = emptyList(),
    )

    /** Song plays, the songs of playlists and starred songs; called with [fileLock] held. */
    private suspend fun applySongs(data: PendingRestoreData): SongPass {
        if (data.songs.isEmpty() && data.songPlaylistItems.isEmpty() && data.songFavorites.isEmpty()) {
            return SongPass(0, emptyList(), 0, emptyList())
        }
        val matcher = SongMatcher(songDao.identities(), sourceDao.all())
        val found = ArrayList<Pair<BackupSongState, Long>>()
        val missing = ArrayList<BackupSongState>()
        for (state in data.songs) {
            val id = matcher.match(state.song)
            if (id != null) found += state to id else missing += state
        }
        var itemsApplied = 0
        val itemsLeft = ArrayList<PendingSongPlaylistItem>()
        var favoritesApplied = 0
        val favoritesLeft = ArrayList<BackupSongFavorite>()
        database.withTransaction {
            for (favorite in data.songFavorites) {
                val id = matcher.match(favorite.song)
                if (id == null) {
                    favoritesLeft += favorite
                } else {
                    favoriteSongDao.add(FavoriteSongEntity(songId = id, addedAt = favorite.addedAt.takeIf { it > 0 } ?: clock()))
                    favoritesApplied++
                }
            }
            if (found.isNotEmpty()) {
                val current = found.map { it.second }.distinct().chunked(SQL_CHUNK)
                    .flatMap { songDao.byIds(it) }
                    .associateBy { it.id }
                for ((state, id) in found) {
                    val song = current[id] ?: continue
                    val playCount = max(song.playCount, state.playCount.coerceAtLeast(0))
                    val lastPlayedAt = listOfNotNull(song.lastPlayedAt, state.lastPlayedAt).maxOrNull()
                    if (playCount != song.playCount || lastPlayedAt != song.lastPlayedAt) {
                        songDao.restorePlays(id, playCount, lastPlayedAt)
                    }
                }
            }
            if (data.songPlaylistItems.isNotEmpty()) {
                val playlists = playlistDao.all()
                for ((name, items) in data.songPlaylistItems.groupBy { it.playlist }) {
                    // A playlist deleted since the restore takes its waiting songs with it.
                    val playlist = playlists.firstOrNull { it.name == name } ?: continue
                    val existing = playlistSongDao.items(playlist.id)
                    val already = existing.mapTo(HashSet()) { it.songId }
                    var position = (existing.maxOfOrNull { it.position } ?: -1) + 1
                    val added = ArrayList<PlaylistSongEntity>()
                    var waiting = 0
                    for (item in items) {
                        val id = matcher.match(item.song)
                        when {
                            id == null -> {
                                itemsLeft += item
                                waiting++
                            }
                            already.add(id) -> added += PlaylistSongEntity(playlistId = playlist.id, songId = id, position = position++)
                            // Already in the playlist: nothing to add, nothing left to wait for.
                            else -> Unit
                        }
                    }
                    if (added.isNotEmpty()) {
                        playlistSongDao.insertItems(added)
                        playlistDao.touch(playlist.id, clock())
                    }
                    itemsApplied += items.size - waiting
                }
            }
        }
        return SongPass(found.size, missing, itemsApplied, itemsLeft, favoritesApplied, favoritesLeft)
    }

    private suspend fun applyStates(found: List<Pair<BackupVideoState, Long>>) {
        database.withTransaction {
            val current = found.map { it.second }.distinct().chunked(SQL_CHUNK)
                .flatMap { videoDao.byIds(it) }
                .associateByTo(HashMap()) { it.id }
            for ((state, id) in found) {
                val video = current[id] ?: continue
                val restored = withRestoredPlayback(video, state)
                if (restored == video) continue
                videoDao.restorePlayback(
                    id = id,
                    positionMs = restored.positionMs,
                    watched = restored.watched,
                    lastPlayedAt = restored.lastPlayedAt,
                    playCount = restored.playCount,
                    audioTrackId = restored.audioTrackId,
                    subtitleTrackId = restored.subtitleTrackId,
                )
                current[id] = restored
            }
        }
    }

    /** Called with [identifyLock] held, not [fileLock]: TMDB may take a while. */
    private suspend fun identifyAll(library: LibraryRepository, ready: List<Pair<BackupMatch, Long>>): IdentifyOutcome {
        val done = HashSet<BackupMatch>()
        var applied = 0
        var failed = 0
        for ((match, videoId) in ready) {
            val type = when (match.kind) {
                "movie" -> IdentifyType.MOVIE
                "tv" -> IdentifyType.TV
                else -> null
            }
            if (type == null || match.tmdbId <= 0) {
                done += match
                failed++
                continue
            }
            val key = if (type == IdentifyType.MOVIE) LibraryKeys.movieKey(match.tmdbId) else LibraryKeys.tvKey(match.tmdbId)
            val video = videoDao.byId(videoId) ?: continue
            // Identifying one episode moves its whole show: the show's other episodes are done already.
            if (video.matchState == MatchState.MANUAL && video.metadataKey == key) {
                done += match
                applied++
                continue
            }
            try {
                library.identify(videoId, TmdbSearchResult(match.tmdbId, type, title = "", year = null, overview = null, posterUrl = null))
                done += match
                applied++
            } catch (e: CancellationException) {
                throw e
            } catch (e: TmdbException) {
                if (e.code == HTTP_NOT_FOUND) {
                    Log.w(TAG, "TMDB no longer has ${match.kind} ${match.tmdbId}; dropping the match")
                    done += match
                    failed++
                } else {
                    Log.w(TAG, "Restoring matches stopped; trying again after the next scan", e)
                    break
                }
            } catch (e: Exception) {
                // No connection or no TMDB key: the rest would fail the same way.
                Log.w(TAG, "Restoring matches stopped; trying again after the next scan", e)
                break
            }
        }
        return IdentifyOutcome(done, applied, failed)
    }

    /** The waiting entries, or null when there are none (an expired or unreadable file is deleted). */
    private fun load(): PendingRestoreData? {
        if (!file.exists()) return null
        val data = try {
            fileJson.decodeFromString(PendingRestoreData.serializer(), file.readText())
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't read $FILE_NAME", e)
            return null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Dropping an unreadable $FILE_NAME", e)
            file.delete()
            return null
        }
        if (data.isEmpty() || clock() - data.restoredAt > MAX_AGE_MS) {
            file.delete()
            return null
        }
        return data
    }

    private fun save(data: PendingRestoreData) {
        if (data.isEmpty()) {
            file.delete()
            return
        }
        // Written aside and renamed over the old file, so a crash midway never leaves half a file.
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeText(fileJson.encodeToString(PendingRestoreData.serializer(), data))
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) throw IOException("Couldn't save $FILE_NAME")
        }
    }

    private companion object {
        val fileLock = Mutex()

        /** One identify pass at a time; a second one finds the first one's work done. */
        val identifyLock = Mutex()

        val fileJson = Json { ignoreUnknownKeys = true }
    }
}

/**
 * [video] with a backup's playback state: taken whole when the video has none of its own or the
 * backup's was played more recently, else the video's own stays. Plays add up to the larger
 * count; track choices fill in where the video has none.
 */
internal fun withRestoredPlayback(video: VideoEntity, state: BackupVideoState): VideoEntity {
    val untouched = video.positionMs == 0L && !video.watched && video.lastPlayedAt == null && video.playCount == 0
    val backupNewer = (state.lastPlayedAt ?: Long.MIN_VALUE) > (video.lastPlayedAt ?: Long.MIN_VALUE)
    val takeBackup = untouched || backupNewer
    return video.copy(
        positionMs = if (takeBackup) state.positionMs.coerceAtLeast(0) else video.positionMs,
        watched = if (takeBackup) state.watched else video.watched,
        lastPlayedAt = listOfNotNull(video.lastPlayedAt, state.lastPlayedAt).maxOrNull(),
        playCount = max(video.playCount, state.playCount.coerceAtLeast(0)),
        audioTrackId = video.audioTrackId ?: state.audioTrackId,
        subtitleTrackId = video.subtitleTrackId ?: state.subtitleTrackId,
    )
}
