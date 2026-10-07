package com.aakash.novafork.sources

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.aakash.novafork.core.artwork.ArtworkRules
import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.data.ScannedVideo
import com.aakash.novafork.data.SourceScanner
import com.aakash.novafork.data.StoragePermissions
import com.aakash.novafork.data.db.SourceEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The device library: every video MediaStore knows about. Images in the same folders (and the
 * folders above, for show art) become local artwork when image access is granted.
 */
class DeviceScanner(context: Context) : SourceScanner {
    private val context: Context = context.applicationContext
    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun scan(source: SourceEntity, onProgress: (found: Int) -> Unit): List<ScannedVideo> =
        withContext(Dispatchers.IO) {
            if (!StoragePermissions.hasAnyVideoAccess(context)) throw IOException(NO_ACCESS)
            val rows = try {
                queryVideos(onProgress)
            } catch (e: SecurityException) {
                throw IOException(NO_ACCESS, e)
            }
            val images = if (rows.isNotEmpty() && StoragePermissions.hasImageAccess(context)) imagesNear(rows) else emptyMap()
            val videosPerDir = rows.groupingBy { it.dir }.eachCount()
            val now = System.currentTimeMillis()
            val videos = rows.map { row -> row.toScannedVideo(images, videosPerDir[row.dir] ?: 1, now) }
            onProgress(videos.size)
            videos
        }

    /**
     * A MediaStore folder. On API 29+ [path] is the RELATIVE_PATH without slashes on [volume];
     * before that it is the absolute directory and [volume] is empty.
     */
    private data class Dir(val volume: String, val path: String) {
        val parent: Dir? get() = if (path.isEmpty() || path == "/") null else Dir(volume, path.substringBeforeLast('/', ""))
    }

    private class VideoRow(
        val uri: String,
        val name: String,
        val dir: Dir,
        /** Display folder relative to the storage root, "" for the root itself. */
        val folder: String,
        val sizeBytes: Long?,
        val durationMs: Long?,
        val width: Int?,
        val height: Int?,
        val dateAdded: Long?,
        val dateModified: Long?,
    )

    private suspend fun queryVideos(onProgress: (Int) -> Unit): List<VideoRow> {
        val collection = videoCollection()
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            if (modern) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.MediaColumns.VOLUME_NAME)
            } else {
                add(DATA_COLUMN)
            }
        }.toTypedArray()
        val storageRoot = if (modern) "" else legacyStorageRoot()
        val rows = ArrayList<VideoRow>()
        val cursor = query(collection, projection, null, null) ?: return rows
        cursor.use { c ->
            val idCol = c.getColumnIndex(MediaStore.Video.Media._ID)
            if (idCol < 0) return rows
            val nameCol = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndex(MediaStore.Video.Media.SIZE)
            val durationCol = c.getColumnIndex(MediaStore.Video.Media.DURATION)
            val widthCol = c.getColumnIndex(MediaStore.Video.Media.WIDTH)
            val heightCol = c.getColumnIndex(MediaStore.Video.Media.HEIGHT)
            val addedCol = c.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
            val modifiedCol = c.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
            val relativeCol = if (modern) c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
            val volumeCol = if (modern) c.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME) else -1
            val dataCol = if (modern) -1 else c.getColumnIndex(DATA_COLUMN)
            var seen = 0
            while (c.moveToNext()) {
                if (++seen % 100 == 0) {
                    currentCoroutineContext().ensureActive()
                    onProgress(rows.size)
                }
                val dir: Dir
                val folder: String
                val name: String
                if (modern) {
                    name = c.stringAt(nameCol) ?: continue
                    folder = c.stringAt(relativeCol).orEmpty().trim('/')
                    dir = Dir(c.stringAt(volumeCol).orEmpty(), folder)
                } else {
                    val data = c.stringAt(dataCol) ?: continue
                    val dirPath = data.substringBeforeLast('/', "")
                    name = c.stringAt(nameCol) ?: data.substringAfterLast('/')
                    folder = legacyDisplayFolder(dirPath, storageRoot)
                    dir = Dir("", dirPath)
                }
                if (name.isBlank() || name.startsWith("._") || VideoFiles.isSample(name)) continue
                if (folder.split('/').any { it.isNotEmpty() && VideoFiles.isIgnoredFolder(it) }) continue
                rows += VideoRow(
                    uri = ContentUris.withAppendedId(collection, c.getLong(idCol)).toString(),
                    name = name,
                    dir = dir,
                    folder = folder,
                    sizeBytes = c.longAt(sizeCol)?.takeIf { it > 0 },
                    durationMs = c.longAt(durationCol)?.takeIf { it > 0 },
                    width = c.intAt(widthCol)?.takeIf { it > 0 },
                    height = c.intAt(heightCol)?.takeIf { it > 0 },
                    dateAdded = c.longAt(addedCol)?.takeIf { it > 0 }?.times(1000),
                    dateModified = c.longAt(modifiedCol)?.takeIf { it > 0 }?.times(1000),
                )
            }
        }
        return rows
    }

    /** Folder → (image name → content URI) for the video folders and the folders above them. */
    private suspend fun imagesNear(rows: List<VideoRow>): Map<Dir, Map<String, String>> {
        val dirs = HashSet<Dir>()
        for (row in rows) {
            dirs += row.dir
            row.dir.parent?.let { dirs += it }
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) imagesByRelativePath(dirs) else imagesByData(dirs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Artwork is a nicety: a provider that refuses image queries must not fail the scan.
            emptyMap()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun imagesByRelativePath(dirs: Set<Dir>): Map<Dir, Map<String, String>> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.VOLUME_NAME,
        )
        // MediaStore stores RELATIVE_PATH with a trailing slash; the volume root may be "" or "/".
        val relativePaths = dirs.map { it.path }.distinct().flatMap { path ->
            if (path.isEmpty()) listOf("", "/") else listOf("$path/")
        }
        val result = HashMap<Dir, HashMap<String, String>>()
        for (chunk in relativePaths.chunked(SQL_IN_LIMIT)) {
            currentCoroutineContext().ensureActive()
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} IN (${chunk.joinToString(",") { "?" }})"
            val cursor = query(collection, projection, selection, chunk.toTypedArray()) ?: continue
            cursor.use { c ->
                val idCol = c.getColumnIndex(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val pathCol = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val volumeCol = c.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
                if (idCol < 0) return@use
                while (c.moveToNext()) {
                    val name = c.stringAt(nameCol) ?: continue
                    if (!ArtworkRules.isImage(name)) continue
                    val dir = Dir(c.stringAt(volumeCol).orEmpty(), c.stringAt(pathCol).orEmpty().trim('/'))
                    if (dir !in dirs) continue
                    result.getOrPut(dir) { HashMap() }[name] =
                        ContentUris.withAppendedId(collection, c.getLong(idCol)).toString()
                }
            }
        }
        return result
    }

    /** API 26-28: no RELATIVE_PATH yet, so filter the image table by the directory of DATA. */
    private suspend fun imagesByData(dirs: Set<Dir>): Map<Dir, Map<String, String>> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, DATA_COLUMN)
        val result = HashMap<Dir, HashMap<String, String>>()
        val cursor = query(collection, projection, null, null) ?: return result
        cursor.use { c ->
            val idCol = c.getColumnIndex(MediaStore.Images.Media._ID)
            val dataCol = c.getColumnIndex(DATA_COLUMN)
            if (idCol < 0 || dataCol < 0) return result
            var seen = 0
            while (c.moveToNext()) {
                if (++seen % 500 == 0) currentCoroutineContext().ensureActive()
                val path = c.stringAt(dataCol) ?: continue
                val name = path.substringAfterLast('/')
                if (!ArtworkRules.isImage(name)) continue
                val dir = Dir("", path.substringBeforeLast('/', ""))
                if (dir !in dirs) continue
                result.getOrPut(dir) { HashMap() }[name] =
                    ContentUris.withAppendedId(collection, c.getLong(idCol)).toString()
            }
        }
        return result
    }

    private fun VideoRow.toScannedVideo(images: Map<Dir, Map<String, String>>, videosInDir: Int, now: Long): ScannedVideo {
        val segments = folder.split('/').filter { it.isNotEmpty() }
        val here = images[dir].orEmpty()
        val above = dir.parent?.let { images[it] }.orEmpty()
        val art = if (here.isEmpty() && above.isEmpty()) {
            LocalArtChoice()
        } else {
            LocalArtwork.choose(
                videoFileName = name,
                folderName = segments.lastOrNull(),
                folderImages = here.keys.toList(),
                folderVideoCount = videosInDir,
                showFolderImages = { above.keys.toList() },
            )
        }

        fun uriOf(file: ArtFile?): String? {
            if (file == null) return null
            return when (file.place) {
                ArtPlace.VIDEO_FOLDER -> here[file.name]
                ArtPlace.SHOW_FOLDER -> above[file.name]
            }
        }

        return ScannedVideo(
            uri = uri,
            fileName = name,
            folder = folder,
            parentFolders = segments.asReversed().toList(),
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            width = width,
            height = height,
            dateAdded = dateAdded ?: dateModified ?: now,
            dateModified = dateModified,
            localPoster = uriOf(art.poster),
            localBackdrop = uriOf(art.backdrop),
            localThumb = uriOf(art.thumb),
        )
    }

    private fun videoCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    /** Leaves out pending (still being written) and trashed rows. */
    private fun query(collection: Uri, projection: Array<String>, selection: String?, args: Array<String>?): Cursor? =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val queryArgs = Bundle().apply {
                    if (selection != null) {
                        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                    }
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE)
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_EXCLUDE)
                }
                resolver.query(collection, projection, queryArgs, null)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val notPending = "${MediaStore.MediaColumns.IS_PENDING} = 0"
                val where = if (selection == null) notPending else "($selection) AND $notPending"
                resolver.query(collection, projection, where, args, null)
            }
            else -> resolver.query(collection, projection, selection, args, null)
        }

    private fun legacyStorageRoot(): String {
        @Suppress("DEPRECATION")
        return Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
    }

    /** "/storage/emulated/0/Movies/Action" → "Movies/Action"; SD cards drop their "/storage/<id>" prefix too. */
    private fun legacyDisplayFolder(dirPath: String, storageRoot: String): String = when {
        dirPath == storageRoot -> ""
        storageRoot.isNotEmpty() && dirPath.startsWith("$storageRoot/") -> dirPath.substring(storageRoot.length + 1)
        dirPath.startsWith("/storage/") -> dirPath.removePrefix("/storage/").substringAfter('/', "")
        else -> dirPath.trim('/')
    }

    private companion object {
        const val NO_ACCESS = "Allow access to videos to scan this device"

        /** MediaColumns.DATA: deprecated on API 29+, but the only folder information before it. */
        @Suppress("DEPRECATION")
        const val DATA_COLUMN = MediaStore.MediaColumns.DATA
    }
}
