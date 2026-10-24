package com.aakash.novafork.sources.music

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.aakash.novafork.core.audio.AudioFiles
import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.data.StoragePermissions
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.music.KnownSong
import com.aakash.novafork.data.music.MusicScanner
import com.aakash.novafork.data.music.ScannedSong
import com.aakash.novafork.sources.intAt
import com.aakash.novafork.sources.longAt
import com.aakash.novafork.sources.stringAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The device's music: MediaStore's audio rows marked as music (ringtones, notification sounds and
 * recordings are not). MediaStore has the tags already. Cover files and .lrc lyrics next to the
 * songs are only visible with All files access (Android hides non-media files and other apps'
 * images otherwise); without it a song shows the art embedded in it.
 */
class DeviceMusicScanner(context: Context) : MusicScanner {
    private val context: Context = context.applicationContext
    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun scan(source: SourceEntity, known: Map<String, KnownSong>): List<ScannedSong> =
        withContext(Dispatchers.IO) {
            if (!StoragePermissions.hasAudioAccess(context)) throw IOException(NO_ACCESS)
            val rows = try {
                queryMusic()
            } catch (e: SecurityException) {
                throw IOException(NO_ACCESS, e)
            }
            val canListFolders = StoragePermissions.hasAllFilesAccess(context)
            val listings = HashMap<String, List<String>>()
            val now = System.currentTimeMillis()
            val songs = ArrayList<ScannedSong>(rows.size)
            for ((i, row) in rows.withIndex()) {
                if (i % PROGRESS_EVERY == 0) currentCoroutineContext().ensureActive()
                val dir = row.dirPath?.takeIf { canListFolders }
                val names = dir?.let { path -> listings.getOrPut(path) { listFolder(path) } }.orEmpty()
                val cover = AudioFiles.coverOf(names)?.let { Uri.fromFile(File(dir, it)).toString() }
                val lyrics = AudioFiles.lyricsFileFor(row.name, names)?.let { Uri.fromFile(File(dir, it)).toString() }
                songs += ScannedSong(
                    uri = row.uri,
                    fileName = row.name,
                    folder = row.folder,
                    title = row.title,
                    artist = row.artist,
                    albumArtist = row.albumArtist,
                    album = row.album,
                    trackNumber = row.track,
                    discNumber = row.disc,
                    year = row.year,
                    genre = row.genre,
                    durationMs = row.durationMs,
                    sizeBytes = row.sizeBytes,
                    dateAdded = row.dateAdded ?: row.dateModified ?: now,
                    dateModified = row.dateModified,
                    coverUri = cover,
                    lyricsUri = lyrics,
                )
            }
            songs
        }

    private class Row(
        val uri: String,
        val name: String,
        val folder: String,
        /** The absolute folder, when MediaStore gives the file's path. */
        val dirPath: String?,
        val title: String?,
        val artist: String?,
        val albumArtist: String?,
        val album: String?,
        val track: Int?,
        val disc: Int?,
        val year: Int?,
        val genre: String?,
        val durationMs: Long?,
        val sizeBytes: Long?,
        val dateAdded: Long?,
        val dateModified: Long?,
    )

    private suspend fun queryMusic(): List<Row> {
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val collection = if (modern) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val tagged = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(DATA_COLUMN)
            if (modern) add(MediaStore.MediaColumns.RELATIVE_PATH)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            if (tagged) {
                add(ALBUM_ARTIST_COLUMN)
                add(GENRE_COLUMN)
                add(DISC_NUMBER_COLUMN)
            }
        }.toTypedArray()
        val essentials = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(DATA_COLUMN)
            if (modern) add(MediaStore.MediaColumns.RELATIVE_PATH)
            add(MediaStore.Audio.Media.TITLE)
        }.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        // No cursor means MediaProvider isn't available right now, not a device without music.
        val cursor = try {
            query(collection, projection, selection)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "MediaStore refused the full projection, retrying with the essentials", e)
            query(collection, essentials, selection)
        } ?: throw IOException(MEDIA_UNAVAILABLE)
        val storageRoot = legacyStorageRoot()
        val rows = ArrayList<Row>()
        cursor.use { c ->
            val idCol = c.getColumnIndex(MediaStore.Audio.Media._ID)
            if (idCol < 0) throw IOException(MEDIA_UNAVAILABLE)
            val nameCol = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = c.getColumnIndex(DATA_COLUMN)
            val relativeCol = if (modern) c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
            val titleCol = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val albumArtistCol = if (tagged) c.getColumnIndex(ALBUM_ARTIST_COLUMN) else -1
            val trackCol = c.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val durationCol = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val sizeCol = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val addedCol = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val modifiedCol = c.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val genreCol = if (tagged) c.getColumnIndex(GENRE_COLUMN) else -1
            val discCol = if (tagged) c.getColumnIndex(DISC_NUMBER_COLUMN) else -1
            var seen = 0
            while (c.moveToNext()) {
                if (++seen % PROGRESS_EVERY == 0) currentCoroutineContext().ensureActive()
                val data = c.stringAt(dataCol)
                val name = c.stringAt(nameCol)?.takeIf { it.isNotBlank() } ?: data?.substringAfterLast('/') ?: continue
                if (name.startsWith("._")) continue
                val dirPath = data?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() }
                val folder = if (relativeCol >= 0) {
                    c.stringAt(relativeCol).orEmpty().trim('/')
                } else {
                    dirPath?.let { legacyDisplayFolder(it, storageRoot) }.orEmpty()
                }
                if (VideoFiles.isIgnoredPath(folder.split('/'))) continue
                val (track, packedDisc) = AudioFiles.splitMediaStoreTrack(c.intAt(trackCol))
                rows += Row(
                    uri = ContentUris.withAppendedId(collection, c.getLong(idCol)).toString(),
                    name = name,
                    folder = folder,
                    dirPath = dirPath,
                    title = AudioFiles.cleanTag(c.stringAt(titleCol)),
                    artist = AudioFiles.cleanTag(c.stringAt(artistCol)),
                    albumArtist = AudioFiles.cleanTag(c.stringAt(albumArtistCol)),
                    album = AudioFiles.cleanTag(c.stringAt(albumCol)),
                    track = track,
                    disc = AudioFiles.leadingNumber(c.stringAt(discCol)) ?: packedDisc,
                    year = c.intAt(yearCol)?.takeIf { it in 1000..2999 },
                    genre = AudioFiles.cleanTag(c.stringAt(genreCol)),
                    durationMs = c.longAt(durationCol)?.takeIf { it > 0 },
                    sizeBytes = c.longAt(sizeCol)?.takeIf { it > 0 },
                    dateAdded = c.longAt(addedCol)?.takeIf { it > 0 }?.times(1000),
                    dateModified = c.longAt(modifiedCol)?.takeIf { it > 0 }?.times(1000),
                )
            }
        }
        return rows
    }

    /** Leaves out pending (still being written) and trashed rows. */
    private fun query(collection: Uri, projection: Array<String>, selection: String): Cursor? =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val queryArgs = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE)
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_EXCLUDE)
                }
                resolver.query(collection, projection, queryArgs, null)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                resolver.query(collection, projection, "($selection) AND ${MediaStore.MediaColumns.IS_PENDING} = 0", null, null)
            else -> resolver.query(collection, projection, selection, null, null)
        }

    /** A folder's file names; empty when it can't be listed. */
    private fun listFolder(path: String): List<String> =
        try {
            File(path).list()?.toList().orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }

    private fun legacyStorageRoot(): String {
        @Suppress("DEPRECATION")
        return Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
    }

    /** "/storage/emulated/0/Music/Album" → "Music/Album"; SD cards drop their "/storage/<id>" prefix too. */
    private fun legacyDisplayFolder(dirPath: String, storageRoot: String): String = when {
        dirPath == storageRoot -> ""
        storageRoot.isNotEmpty() && dirPath.startsWith("$storageRoot/") -> dirPath.substring(storageRoot.length + 1)
        dirPath.startsWith("/storage/") -> dirPath.removePrefix("/storage/").substringAfter('/', "")
        else -> dirPath.trim('/')
    }

    private companion object {
        const val TAG = "DeviceMusicScanner"
        const val NO_ACCESS = "Allow access to music to scan this device"
        const val MEDIA_UNAVAILABLE = "Media storage isn't available right now"
        const val PROGRESS_EVERY = 200

        /** MediaColumns.DATA: deprecated on API 29+, but still the file's path, and the only one before. */
        @Suppress("DEPRECATION")
        const val DATA_COLUMN = MediaStore.MediaColumns.DATA

        // Spelled out (the constants are API 30+) and only asked for from API 30 on, where every
        // MediaProvider has them: a refused column would cost the whole projection.
        const val ALBUM_ARTIST_COLUMN = "album_artist"
        const val GENRE_COLUMN = "genre"
        const val DISC_NUMBER_COLUMN = "disc_number"
    }
}
