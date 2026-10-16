package com.aakash.novafork.data.music

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.aakash.novafork.core.audio.AudioFiles
import com.aakash.novafork.core.audio.LrcParser
import com.aakash.novafork.core.audio.Lyrics
import com.aakash.novafork.core.audio.MusicKeys
import com.aakash.novafork.data.db.MusicPlaylistRow
import com.aakash.novafork.data.db.NovaDatabase
import com.aakash.novafork.data.db.PlaylistEntity
import com.aakash.novafork.data.db.PlaylistSongEntity
import com.aakash.novafork.data.db.SQL_CHUNK
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.WatchlistEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** How the music scan is doing, for the Audio tab. */
data class MusicScanState(
    val scanning: Boolean = false,
    /** Epoch ms of the last finished scan; null before the first. */
    val lastScanAt: Long? = null,
    /** Why the last scan of a source failed ("Allow access to music …"); null when every source scanned. */
    val error: String? = null,
)

/**
 * The music library: songs of the local sources (the DEVICE row and picked FOLDERs, the ones the
 * video library has), grouped into albums and artists by [MusicLibrary]; songs in playlists;
 * synced lyrics; and the watchlist the hero's "+" button fills. Scans run when a local source is
 * added, removed or turned on or off, and on [rescan].
 */
class MusicRepository(
    private val context: Context,
    private val database: NovaDatabase,
    private val scanners: Map<SourceType, MusicScanner>,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val songDao = database.songDao()
    private val playlistSongDao = database.playlistSongDao()
    private val playlistDao = database.playlistDao()
    private val watchlistDao = database.watchlistDao()
    private val sourceDao = database.sourceDao()

    private val scanMutex = Mutex()
    private val _scanState = MutableStateFlow(MusicScanState())
    val scanState: StateFlow<MusicScanState> = _scanState.asStateFlow()

    private var sourceWatch: Job? = null

    /** Shown songs grouped for the Audio tab; regrouped off the main thread on every change. */
    fun observeLibrary(): Flow<MusicLibraryData> =
        songDao.observeShown().map { MusicLibrary.build(it) }.flowOn(Dispatchers.Default)

    /**
     * Rescans whenever the local sources change (one added, removed, turned on or off), starting
     * with a scan now. Idempotent.
     */
    fun watchSources() {
        if (sourceWatch != null) return
        sourceWatch = scope.launch {
            sourceDao.observeEnabled()
                .map { sources -> sources.filter { it.type in scanners }.map { Triple(it.id, it.type, it.location) } }
                .distinctUntilChanged()
                .collect { rescan() }
        }
    }

    fun rescanInBackground() {
        scope.launch { rescan() }
    }

    /**
     * Scans every local source that is turned on. A source that fails keeps its songs as they
     * were; its error shows in [scanState]. A song found on the device and in a picked folder of
     * the same storage is kept once, from the device.
     */
    suspend fun rescan() {
        scanMutex.withLock {
            _scanState.update { it.copy(scanning = true) }
            val errors = ArrayList<String>()
            try {
                songDao.deleteOrphans()
                val sources = sourceDao.all().filter { it.enabled && it.type in scanners }
                    .sortedBy { if (it.type == SourceType.DEVICE) 0 else 1 }
                var deviceFiles: Set<String> = emptySet()
                for (source in sources) {
                    val scanned = scanSource(source, errors) ?: continue
                    if (source.type == SourceType.DEVICE) {
                        deviceFiles = scanned.mapTo(HashSet()) { sameFileKey(it.fileName, it.sizeBytes) }
                        store(source, scanned)
                    } else {
                        store(source, scanned.filter { sameFileKey(it.fileName, it.sizeBytes) !in deviceFiles })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Music scan failed", e)
                errors += e.message ?: e.javaClass.simpleName
            } finally {
                _scanState.value = MusicScanState(scanning = false, lastScanAt = now(), error = errors.firstOrNull())
            }
        }
    }

    private suspend fun scanSource(source: SourceEntity, errors: MutableList<String>): List<ScannedSong>? {
        val scanner = scanners[source.type] ?: return null
        val known = songDao.bySource(source.id).associate { it.uri to KnownSong(it.sizeBytes, it.dateModified) }
        val timeout = if (source.type == SourceType.DEVICE) DEVICE_TIMEOUT_MS else FOLDER_TIMEOUT_MS
        return try {
            withTimeoutOrNull(timeout) { scanner.scan(source, known) }
                ?: run {
                    errors += "Scanning ${source.name} took too long"
                    null
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            errors += e.message ?: "Couldn't read ${source.name}"
            null
        } catch (e: Exception) {
            Log.w(TAG, "Music scan of a ${source.type} source failed", e)
            errors += "Couldn't read ${source.name}"
            null
        }
    }

    /** Stores what one source's scan found: new songs, changed tags, and files no longer there. */
    internal suspend fun store(source: SourceEntity, scanned: List<ScannedSong>) {
        val existing = songDao.bySource(source.id).associateBy { it.uri }
        val seen = HashSet<String>(scanned.size)
        val inserts = ArrayList<SongEntity>()
        val updates = ArrayList<SongEntity>()
        for (file in scanned) {
            if (!seen.add(file.uri)) continue
            val old = existing[file.uri]
            if (old == null) {
                inserts += songOf(source.id, file)
            } else {
                val updated = refreshed(old, file)
                if (updated != old) updates += updated
            }
        }
        val missing = existing.values.filter { it.present && it.uri !in seen }.map { it.id }
        database.withTransaction {
            inserts.chunked(SQL_CHUNK).forEach { songDao.insertAll(it) }
            updates.chunked(SQL_CHUNK).forEach { songDao.updateAll(it) }
            missing.chunked(SQL_CHUNK).forEach { songDao.markMissing(it) }
        }
    }

    private fun songOf(sourceId: Long, file: ScannedSong): SongEntity = SongEntity(
        sourceId = sourceId,
        uri = file.uri,
        fileName = file.fileName,
        folder = file.folder,
        title = file.title ?: AudioFiles.titleFromFileName(file.fileName),
        artist = file.artist,
        albumArtist = file.albumArtist,
        album = file.album,
        albumKey = MusicKeys.albumKey(file.albumArtist, file.album, file.folder),
        artistKey = MusicKeys.artistKey(file.albumArtist, file.artist),
        trackNumber = file.trackNumber,
        discNumber = file.discNumber,
        year = file.year,
        genre = file.genre,
        durationMs = file.durationMs,
        sizeBytes = file.sizeBytes,
        dateAdded = file.dateAdded,
        dateModified = file.dateModified,
        coverUri = file.coverUri,
        lyricsUri = file.lyricsUri,
    )

    /** The stored song brought up to date; play counts stay, and so do its tags when the file is unchanged. */
    private fun refreshed(old: SongEntity, file: ScannedSong): SongEntity {
        val base = if (file.tagsUnchanged) {
            old.copy(fileName = file.fileName, folder = file.folder)
        } else {
            songOf(old.sourceId, file).copy(
                id = old.id,
                dateAdded = old.dateAdded,
                // A duration the player measured stays when the tags have none.
                durationMs = file.durationMs ?: old.durationMs,
            )
        }
        return base.copy(
            coverUri = file.coverUri,
            lyricsUri = file.lyricsUri,
            sizeBytes = file.sizeBytes ?: old.sizeBytes,
            dateModified = file.dateModified ?: old.dateModified,
            playCount = old.playCount,
            lastPlayedAt = old.lastPlayedAt,
            present = true,
        )
    }

    private fun sameFileKey(fileName: String, sizeBytes: Long?): String = "${fileName.lowercase()}|${sizeBytes ?: -1}"

    // Songs

    suspend fun song(id: Long): SongEntity? = songDao.byId(id)

    /** The songs of [ids] that still exist, in the order given. */
    suspend fun songs(ids: List<Long>): List<SongEntity> {
        val byId = ids.distinct().chunked(SQL_CHUNK).flatMap { songDao.byIds(it) }.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    suspend fun markPlayed(songId: Long) = songDao.markPlayed(songId, now())

    suspend fun fillDuration(songId: Long, durationMs: Long) {
        if (durationMs > 0) songDao.fillDuration(songId, durationMs)
    }

    /** The song's .lrc lyrics; null when it has none or the file can't be read. */
    suspend fun lyrics(song: SongEntity): Lyrics? {
        val uri = song.lyricsUri ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    val buffer = ByteArray(MAX_LYRICS_BYTES + 1)
                    var total = 0
                    while (total < buffer.size) {
                        val read = input.read(buffer, total, buffer.size - total)
                        if (read < 0) break
                        total += read
                    }
                    if (total > MAX_LYRICS_BYTES) null else buffer.copyOf(total)
                } ?: return@withContext null
                LrcParser.parse(String(bytes, Charsets.UTF_8)).takeUnless { it.isEmpty }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read lyrics", e)
                null
            }
        }
    }

    // Playlists

    fun observePlaylists(): Flow<List<MusicPlaylistRow>> = playlistSongDao.observePlaylists()

    suspend fun playlistSongs(playlistId: Long): List<SongEntity> = playlistSongDao.shownSongs(playlistId)

    /** A new, empty playlist; its id. */
    suspend fun createPlaylist(name: String): Long {
        val time = now()
        return playlistDao.insert(PlaylistEntity(name = name.trim().ifEmpty { "Playlist" }, createdAt = time, updatedAt = time))
    }

    /** Appends [songIds] to the playlist; songs already in it stay where they are. */
    suspend fun addToPlaylist(playlistId: Long, songIds: List<Long>) {
        database.withTransaction {
            if (playlistDao.byId(playlistId) == null) return@withTransaction
            val items = playlistSongDao.items(playlistId)
            val already = items.mapTo(HashSet()) { it.songId }
            var position = (items.maxOfOrNull { it.position } ?: -1) + 1
            val added = songIds.distinct().filter { it !in already }.map { PlaylistSongEntity(playlistId = playlistId, songId = it, position = position++) }
            if (added.isEmpty()) return@withTransaction
            playlistSongDao.insertItems(added)
            playlistDao.touch(playlistId, now())
        }
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) {
        database.withTransaction {
            playlistSongDao.removeSong(playlistId, songId)
            playlistDao.touch(playlistId, now())
        }
    }

    suspend fun deletePlaylist(playlistId: Long) = playlistDao.delete(playlistId)

    // Watchlist (the hero's "+" button)

    fun observeWatchlist(): Flow<List<String>> = watchlistDao.observe().map { rows -> rows.map { it.itemKey } }

    suspend fun addToWatchlist(itemKey: String) = watchlistDao.add(WatchlistEntity(itemKey = itemKey, addedAt = now()))

    suspend fun removeFromWatchlist(itemKey: String) = watchlistDao.remove(itemKey)

    suspend fun isOnWatchlist(itemKey: String): Boolean = watchlistDao.count(itemKey) > 0

    private companion object {
        const val TAG = "MusicRepository"
        const val DEVICE_TIMEOUT_MS = 5 * 60_000L
        const val FOLDER_TIMEOUT_MS = 30 * 60_000L
        const val MAX_LYRICS_BYTES = 512 * 1024
    }
}
