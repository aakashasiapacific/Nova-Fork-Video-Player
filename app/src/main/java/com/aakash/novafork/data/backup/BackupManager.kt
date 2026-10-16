package com.aakash.novafork.data.backup

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import com.aakash.novafork.core.library.LibraryKeys
import com.aakash.novafork.data.LibraryRepository
import com.aakash.novafork.data.PrefsSettingsStore
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.SettingsStore
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.NovaDatabase
import com.aakash.novafork.data.db.SQL_CHUNK
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "BackupManager"

/**
 * Backs up and restores what the user put into the app: settings (the whole settings file),
 * sources, each video's playback state, each song's plays, Identify's manual matches and
 * playlists of videos and songs.
 *
 * A restore replaces the settings, adds the sources this device doesn't have and applies the
 * rest to the videos it finds (see [VideoMatcher]); entries whose videos are not in the library
 * yet wait in [PendingRestore] and are applied after each scan of a source.
 *
 * [reloadSettings] reads the settings file into [Settings] after a restore wrote it, for
 * [settings] to take over (by default through a fresh PrefsSettingsStore).
 */
class BackupManager(
    context: Context,
    private val database: NovaDatabase,
    private val settings: SettingsStore,
    private val library: LibraryRepository,
    private val appVersion: String,
    reloadSettings: (() -> Settings)? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val appContext: Context = context.applicationContext
    private val reload: () -> Settings = reloadSettings ?: { PrefsSettingsStore(appContext).settings.value }
    private val pending = PendingRestore(appContext, database, clock)
    private val sourceDao = database.sourceDao()
    private val videoDao = database.videoDao()
    private val playlistDao = database.playlistDao()
    private val playlistSongDao = database.playlistSongDao()
    private val songDao = database.songDao()

    private val settingsPrefs: SharedPreferences
        get() = appContext.getSharedPreferences(SettingsBackup.PREFS_NAME, Context.MODE_PRIVATE)

    private val statePrefs: SharedPreferences
        get() = appContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

    // Backing up

    /**
     * The backup of everything as it is now. Without [includeSecrets], passwords, sign-in tokens,
     * API keys and the secrets in saved stream URLs stay out (a stream that needs one is left out).
     */
    suspend fun create(includeSecrets: Boolean = false): NovaBackup = withContext(Dispatchers.IO) {
        val backupSettings = SettingsBackup.export(settingsPrefs, includeSecrets)
        database.withTransaction {
            val sources = sourceDao.all()
            val sourcesById = sources.associateBy { it.id }
            val withState = videoDao.withUserState()
            NovaBackup(
                appVersion = appVersion,
                createdAt = clock(),
                includesSecrets = includeSecrets,
                settings = backupSettings,
                sources = sources.mapNotNull { it.toBackup(includeSecrets) },
                videos = withState.filter { it.hasPlaybackState() }.map { video ->
                    BackupVideoState(
                        video = VideoMatcher.refOf(video, sourcesById[video.sourceId], includeSecrets),
                        positionMs = video.positionMs,
                        watched = video.watched,
                        lastPlayedAt = video.lastPlayedAt,
                        playCount = video.playCount,
                        audioTrackId = video.audioTrackId,
                        subtitleTrackId = video.subtitleTrackId,
                    )
                },
                matches = withState.mapNotNull { video -> video.manualMatch(sourcesById[video.sourceId], includeSecrets) },
                playlists = playlistDao.all().map { playlist ->
                    val items = playlistDao.items(playlist.id)
                    val videos = items.map { it.videoId }.distinct().chunked(SQL_CHUNK)
                        .flatMap { videoDao.byIds(it) }
                        .associateBy { it.id }
                    val songItems = playlistSongDao.items(playlist.id)
                    val songs = songItems.map { it.songId }.distinct().chunked(SQL_CHUNK)
                        .flatMap { songDao.byIds(it) }
                        .associateBy { it.id }
                    BackupPlaylist(
                        name = playlist.name,
                        createdAt = playlist.createdAt,
                        items = items.mapNotNull { item ->
                            videos[item.videoId]?.let { VideoMatcher.refOf(it, sourcesById[it.sourceId], includeSecrets) }
                        },
                        songs = songItems.mapNotNull { item ->
                            songs[item.songId]?.let { SongMatcher.refOf(it, sourcesById[it.sourceId]) }
                        },
                    )
                },
                songs = songDao.withPlays().map { song ->
                    BackupSongState(
                        song = SongMatcher.refOf(song, sourcesById[song.sourceId]),
                        playCount = song.playCount,
                        lastPlayedAt = song.lastPlayedAt,
                    )
                },
            )
        }
    }

    /**
     * Writes [create]'s backup as JSON to [output] and closes it (a document provider may only
     * store the file then); the time is remembered once that worked.
     */
    suspend fun export(output: OutputStream, includeSecrets: Boolean = false): BackupSummary = withContext(Dispatchers.IO) {
        val backup = output.use { stream ->
            create(includeSecrets).also { stream.write(BackupCodec.encode(it).toByteArray(Charsets.UTF_8)) }
        }
        statePrefs.edit { putLong(KEY_LAST_BACKUP_AT, backup.createdAt) }
        backup.summary()
    }

    /** When the last backup was written on this device (epoch ms), null if never. */
    suspend fun lastBackupAt(): Long? = withContext(Dispatchers.IO) {
        statePrefs.getLong(KEY_LAST_BACKUP_AT, 0L).takeIf { it > 0L }
    }

    // Restoring

    /**
     * Reads and checks a backup file; BackupFormatException says what is wrong with it, for people.
     * The picker offers any file, so one that can't be a backup is refused as early as possible:
     * by the size it announces, by its first character (a backup is a JSON object), and once it
     * grows past [MAX_BACKUP_BYTES] (backups are a few MB at most).
     */
    suspend fun read(input: InputStream): NovaBackup = withContext(Dispatchers.IO) {
        val announced = try {
            input.available()
        } catch (e: IOException) {
            0
        }
        if (announced > MAX_BACKUP_BYTES) throw BackupFormatException(TOO_LARGE)
        // A file's stream announces what is left of it; one more byte of room finds its end without growing.
        var bytes = ByteArray(announced.coerceIn(MIN_BUFFER_BYTES, MAX_BACKUP_BYTES - 1) + 1)
        var size = 0
        var start = -1
        while (true) {
            if (size == bytes.size) {
                if (bytes.size >= MAX_BACKUP_BYTES) {
                    if (input.read() < 0) break
                    throw BackupFormatException(TOO_LARGE)
                }
                bytes = bytes.copyOf(minOf(bytes.size.toLong() * 2, MAX_BACKUP_BYTES.toLong()).toInt())
            }
            val read = input.read(bytes, size, bytes.size - size)
            if (read < 0) break
            size += read
            if (start < 0) {
                start = jsonStart(bytes, size)
                if (start >= 0 && bytes[start] != '{'.code.toByte()) throw BackupFormatException(BackupCodec.NOT_A_BACKUP)
            }
        }
        if (start < 0) throw BackupFormatException(BackupCodec.NOT_A_BACKUP)
        BackupCodec.decode(bytes, start, size - start)
    }

    /**
     * Restores [backup]: settings replaced entry by entry, sources added where none of the same
     * type and location exists (with their secrets only if the backup has them), playlists
     * created or merged by name (missing videos appended), then playback state, manual matches
     * and playlist items applied to the videos the library has; the others wait for later scans.
     * Folders picked with the system picker come back only where this device still grants access
     * to them: the rest are reported in [RestoreReport.foldersToPick]. The caller scans
     * [RestoreReport.addedSourceIds].
     */
    suspend fun restore(backup: NovaBackup): RestoreReport {
        val settingsWritten = withContext(Dispatchers.IO) { SettingsBackup.restore(settingsPrefs, backup.settings) }
        if (settingsWritten > 0) {
            val restored = withContext(Dispatchers.IO) { reload() }
            settings.update { restored }
        }

        var existing = 0
        var skipped = 0
        val added = ArrayList<Long>()
        var addedStreams = 0
        val foldersToPick = ArrayList<String>()
        for (source in backup.sources) {
            val type = SourceType.entries.firstOrNull { it.name == source.type }
            val location = source.location.trim()
            if (type == null || type == SourceType.DEVICE || location.isEmpty()) {
                skipped++
                continue
            }
            if (withContext(Dispatchers.IO) { sourceDao.byLocation(type.name, location) } != null) {
                existing++
                continue
            }
            // One entry going wrong costs that source, not the rest of the restore.
            try {
                when (type) {
                    SourceType.STREAM -> {
                        library.addStream(source.name, location)
                        addedStreams++
                    }
                    SourceType.FOLDER -> if (canReadFolder(location)) {
                        added += insertSource(type, location, source)
                    } else {
                        foldersToPick += source.name.ifBlank { location }
                    }
                    SourceType.DEVICE -> Unit
                    SourceType.SMB, SourceType.FTP, SourceType.SFTP, SourceType.NFS, SourceType.UPNP, SourceType.WEBDAV,
                    SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX,
                    -> added += insertSource(type, location, source)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Restoring a ${type.name} source failed", e)
                skipped++
            }
        }

        val playlistNames = backup.playlists.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()
        val known = withContext(Dispatchers.IO) { playlistDao.all().mapTo(HashSet()) { it.name } }
        var playlistsCreated = 0
        for (name in playlistNames) {
            if (name in known) continue
            try {
                library.createPlaylist(name)
                playlistsCreated++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Restoring a playlist failed", e)
            }
        }

        pending.add(
            videos = backup.videos,
            matches = backup.matches,
            playlistItems = backup.playlists.flatMap { playlist ->
                val name = playlist.name.trim()
                if (name.isEmpty()) emptyList() else playlist.items.map { PendingPlaylistItem(name, it) }
            },
            songs = backup.songs,
            songPlaylistItems = backup.playlists.flatMap { playlist ->
                val name = playlist.name.trim()
                if (name.isEmpty()) emptyList() else playlist.songs.map { PendingSongPlaylistItem(name, it) }
            },
        )
        val result = pending.apply(library, identify = true)
        return RestoreReport(
            settings = settingsWritten,
            sourcesAdded = added.size + addedStreams,
            sourcesExisting = existing,
            sourcesSkipped = skipped,
            foldersToPick = foldersToPick,
            addedSourceIds = added,
            playlistsCreated = playlistsCreated,
            applied = result.applied,
            waiting = result.waiting,
            matchesFailed = result.matchesFailed,
        )
    }

    /** Applies what waits of earlier restores to the library as it is now, manual matches included. */
    suspend fun applyPending(): PendingApplyResult = pending.apply(library, identify = true)

    /** What of earlier restores still waits for its videos. */
    suspend fun waiting(): RestoreCounts = pending.waiting()

    private suspend fun insertSource(type: SourceType, location: String, source: BackupSource): Long =
        withContext(Dispatchers.IO) {
            sourceDao.insert(
                SourceEntity(
                    type = type,
                    name = source.name.trim().ifEmpty { location },
                    location = location,
                    username = source.username?.takeIf { it.isNotEmpty() },
                    password = source.password?.takeIf { it.isNotEmpty() },
                    domain = source.domain?.takeIf { it.isNotEmpty() },
                    enabled = source.enabled,
                    addedAt = clock(),
                    port = source.port?.takeIf { it in 1..MAX_PORT },
                    token = source.token?.takeIf { it.isNotEmpty() },
                    userId = source.userId?.takeIf { it.isNotEmpty() },
                    serverId = source.serverId?.takeIf { it.isNotEmpty() },
                    libraryIds = source.libraryIds?.takeIf { it.isNotEmpty() },
                ),
            )
        }

    /**
     * Where the JSON in the first [size] bytes starts, past a UTF-8 byte order mark and white
     * space; -1 while only those have been read.
     */
    private fun jsonStart(bytes: ByteArray, size: Int): Int {
        var i = 0
        if (size >= UTF8_BOM.size && UTF8_BOM.indices.all { bytes[it] == UTF8_BOM[it] }) {
            i = UTF8_BOM.size
        } else if (size < UTF8_BOM.size && (0 until size).all { bytes[it] == UTF8_BOM[it] }) {
            return -1
        }
        while (i < size) {
            val byte = bytes[i].toInt()
            if (byte != ' '.code && byte != '\t'.code && byte != '\n'.code && byte != '\r'.code) return i
            i++
        }
        return -1
    }

    /** A picked folder is only readable with the persisted permission the system picker gave. */
    private fun canReadFolder(treeUri: String): Boolean =
        try {
            appContext.contentResolver.persistedUriPermissions.any { it.isReadPermission && it.uri.toString() == treeUri }
        } catch (e: SecurityException) {
            false
        }

    companion object {
        const val MIME_TYPE = "application/json"

        /** Types the restore picker offers: some file managers don't know .json files as JSON. */
        val OPEN_MIME_TYPES: Array<String> = arrayOf(MIME_TYPE, "text/plain", "application/octet-stream")

        /** "NovaFork-backup-2026-10-15.json" */
        fun fileName(date: LocalDate): String = "NovaFork-backup-$date.json"

        private const val STATE_PREFS = "backup"
        private const val KEY_LAST_BACKUP_AT = "last_backup_at"
        private const val TOO_LARGE = "This file is too large to be a Nova Fork backup"

        /** Far above any real backup (some 400 bytes a video), low enough to read on any phone. */
        private const val MAX_BACKUP_BYTES = 16 * 1024 * 1024
        private const val MIN_BUFFER_BYTES = 64 * 1024
        private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        private const val MAX_PORT = 65535
    }
}

private fun VideoEntity.hasPlaybackState(): Boolean =
    positionMs > 0 || watched || lastPlayedAt != null || playCount > 0 || audioTrackId != null || subtitleTrackId != null

private fun VideoEntity.manualMatch(source: SourceEntity?, includeSecrets: Boolean): BackupMatch? {
    if (matchState != MatchState.MANUAL) return null
    val key = metadataKey ?: return null
    val tmdbId = LibraryKeys.tmdbId(key) ?: return null
    return BackupMatch(
        video = VideoMatcher.refOf(this, source, includeSecrets),
        tmdbId = tmdbId,
        kind = key.substringBefore(':'),
        season = season,
        episode = episode,
    )
}

/** Device storage comes with the device; a stream whose URL holds a secret needs secrets to come along. */
private fun SourceEntity.toBackup(includeSecrets: Boolean): BackupSource? {
    if (type == SourceType.DEVICE) return null
    if (type == SourceType.STREAM && !includeSecrets && UriSecrets.hasSecret(location)) return null
    return BackupSource(
        type = type.name,
        name = name,
        location = location,
        port = port,
        username = username,
        domain = domain,
        enabled = enabled,
        serverId = serverId,
        libraryIds = libraryIds,
        password = password.takeIf { includeSecrets },
        token = token.takeIf { includeSecrets },
        userId = userId.takeIf { includeSecrets },
    )
}
