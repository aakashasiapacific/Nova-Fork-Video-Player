package com.aakash.novafork.sources.music

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.aakash.novafork.core.audio.AudioFiles
import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.music.KnownSong
import com.aakash.novafork.data.music.MusicScanner
import com.aakash.novafork.data.music.ScannedSong
import com.aakash.novafork.sources.MAX_FOLDER_DEPTH
import com.aakash.novafork.sources.longAt
import com.aakash.novafork.sources.stringAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The music in a folder the user picked (the same SAF trees the video library scans). Nothing
 * indexes these files, so each new or changed song's tags are read from the file; unchanged
 * ones (same size and date) keep the tags the library has. The folder's cover image and the
 * songs' .lrc files come from the listing.
 */
class FolderMusicScanner(context: Context) : MusicScanner {
    private val context: Context = context.applicationContext

    override suspend fun scan(source: SourceEntity, known: Map<String, KnownSong>): List<ScannedSong> =
        withContext(Dispatchers.IO) {
            try {
                walk(Uri.parse(source.location), source.name, known)
            } catch (e: SecurityException) {
                throw IOException("Folder access was revoked", e)
            }
        }

    private class Doc(
        val id: String,
        val name: String,
        val isDirectory: Boolean,
        val sizeBytes: Long?,
        val lastModified: Long?,
    )

    private class Folder(val documentId: String, val path: String, val segments: List<String>, val depth: Int)

    private suspend fun walk(treeUri: Uri, fallbackName: String, known: Map<String, KnownSong>): List<ScannedSong> {
        val resolver = context.contentResolver
        val rootId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: IllegalArgumentException) {
            throw IOException("This isn't a folder address: $treeUri", e)
        }
        val rootName = rootQuery { displayName(resolver, treeUri, rootId) }?.takeIf { it.isNotBlank() } ?: fallbackName
        val rootDocs = rootQuery { children(resolver, treeUri, rootId) }
            ?: throw IOException("Can't open $rootName. Is the storage connected?")

        val now = System.currentTimeMillis()
        val found = ArrayList<ScannedSong>()
        val queue = ArrayDeque<Folder>()
        val root = Folder(rootId, rootName, emptyList(), 0)
        queue.addLast(root)
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val folder = queue.removeFirst()
            val docs = if (folder === root) rootDocs else subfolderDocs(resolver, treeUri, folder.documentId) ?: continue
            val songs = ArrayList<Doc>()
            val sidecars = HashMap<String, String>()
            for (doc in docs) {
                when {
                    doc.isDirectory -> {
                        val segments = folder.segments + doc.name
                        if (folder.depth < MAX_FOLDER_DEPTH && !VideoFiles.isIgnoredPath(segments)) {
                            queue.addLast(Folder(doc.id, "${folder.path}/${doc.name}", segments, folder.depth + 1))
                        }
                    }
                    AudioFiles.isAudio(doc.name) -> songs += doc
                    AudioFiles.isImage(doc.name) || AudioFiles.isLyrics(doc.name) -> sidecars[doc.name] = doc.id
                }
            }
            if (songs.isEmpty()) continue
            val sidecarNames = sidecars.keys.toList()
            val cover = AudioFiles.coverOf(sidecarNames)?.let { documentUri(treeUri, sidecars.getValue(it)) }
            for (song in songs) {
                currentCoroutineContext().ensureActive()
                val uri = documentUri(treeUri, song.id)
                val lyrics = AudioFiles.lyricsFileFor(song.name, sidecarNames)?.let { documentUri(treeUri, sidecars.getValue(it)) }
                val previous = known[uri]
                val unchanged = previous != null && previous.sizeBytes == song.sizeBytes &&
                    previous.dateModified == song.lastModified && song.lastModified != null
                val tags = if (unchanged) null else AudioTags.read(context, Uri.parse(uri))
                found += ScannedSong(
                    uri = uri,
                    fileName = song.name,
                    folder = folder.path,
                    title = tags?.title,
                    artist = tags?.artist,
                    albumArtist = tags?.albumArtist,
                    album = tags?.album,
                    trackNumber = tags?.trackNumber,
                    discNumber = tags?.discNumber,
                    year = tags?.year,
                    genre = tags?.genre,
                    durationMs = tags?.durationMs,
                    sizeBytes = song.sizeBytes,
                    dateAdded = song.lastModified ?: now,
                    dateModified = song.lastModified,
                    coverUri = cover,
                    lyricsUri = lyrics,
                    tagsUnchanged = unchanged,
                )
            }
        }
        return found
    }

    private fun documentUri(treeUri: Uri, documentId: String): String =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString()

    /** Providers report a deleted or unmounted picked folder with IllegalArgumentException. */
    private inline fun <T> rootQuery(block: () -> T): T =
        try {
            block()
        } catch (e: IllegalArgumentException) {
            throw IOException(MISSING_FOLDER, e)
        }

    private fun displayName(resolver: ContentResolver, treeUri: Uri, documentId: String): String? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val cursor = resolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null) ?: return null
        return cursor.use { c -> if (c.moveToFirst()) c.stringAt(0) else null }
    }

    /** A subfolder that vanished or that the provider refuses to list is skipped, not fatal. */
    private fun subfolderDocs(resolver: ContentResolver, treeUri: Uri, documentId: String): List<Doc>? =
        try {
            children(resolver, treeUri, documentId)
        } catch (e: SecurityException) {
            throw e
        } catch (e: RuntimeException) {
            null
        }

    private fun children(resolver: ContentResolver, treeUri: Uri, parentId: String): List<Doc>? {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val cursor = resolver.query(uri, CHILD_COLUMNS, null, null, null) ?: return null
        return cursor.use { c ->
            val idCol = c.getColumnIndex(Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
            val mimeCol = c.getColumnIndex(Document.COLUMN_MIME_TYPE)
            val sizeCol = c.getColumnIndex(Document.COLUMN_SIZE)
            val modifiedCol = c.getColumnIndex(Document.COLUMN_LAST_MODIFIED)
            val docs = ArrayList<Doc>()
            while (c.moveToNext()) {
                val id = c.stringAt(idCol) ?: continue
                val name = c.stringAt(nameCol)?.takeIf { it.isNotBlank() } ?: continue
                docs += Doc(
                    id = id,
                    name = name,
                    isDirectory = c.stringAt(mimeCol) == Document.MIME_TYPE_DIR,
                    sizeBytes = c.longAt(sizeCol)?.takeIf { it >= 0 },
                    lastModified = c.longAt(modifiedCol)?.takeIf { it > 0 },
                )
            }
            docs
        }
    }

    private companion object {
        const val MISSING_FOLDER = "Folder not found. It may have been moved or deleted."

        val CHILD_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
