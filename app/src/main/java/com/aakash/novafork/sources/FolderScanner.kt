package com.aakash.novafork.sources

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.aakash.novafork.core.artwork.ArtworkRules
import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.data.ScannedVideo
import com.aakash.novafork.data.SourceScanner
import com.aakash.novafork.data.db.SourceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/** A folder the user picked with the system folder picker (SAF tree URI with a persisted grant). */
class FolderScanner(context: Context) : SourceScanner {
    private val context: Context = context.applicationContext

    override suspend fun scan(source: SourceEntity, onProgress: (found: Int) -> Unit): List<ScannedVideo> =
        withContext(Dispatchers.IO) {
            try {
                walk(Uri.parse(source.location), source.name, onProgress)
            } catch (e: SecurityException) {
                throw IOException("Folder access was revoked", e)
            }
        }

    private class Folder(
        val documentId: String,
        val name: String,
        /** Display path from the picked folder, starting with its name. */
        val path: String,
        val parent: Folder?,
        val depth: Int,
    ) {
        /** Image name → document id, kept so subfolders (seasons) can use the show art in here. */
        var images: Map<String, String> = emptyMap()
    }

    private class Doc(
        val id: String,
        val name: String,
        val isDirectory: Boolean,
        val sizeBytes: Long?,
        val lastModified: Long?,
    )

    private suspend fun walk(treeUri: Uri, fallbackName: String, onProgress: (Int) -> Unit): List<ScannedVideo> {
        val resolver = context.contentResolver
        val rootId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: IllegalArgumentException) {
            throw IOException("This isn't a folder address: $treeUri", e)
        }
        val rootName = rootQuery { displayName(resolver, treeUri, rootId) }?.takeIf { it.isNotBlank() } ?: fallbackName
        val rootDocs = rootQuery { children(resolver, treeUri, rootId) }
            ?: throw IOException("Can't open $rootName. Is the storage connected?")

        val root = Folder(documentId = rootId, name = rootName, path = rootName, parent = null, depth = 0)
        val now = System.currentTimeMillis()
        val found = ArrayList<ScannedVideo>()
        val queue = ArrayDeque<Folder>()
        queue.addLast(root)
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val folder = queue.removeFirst()
            val docs = if (folder === root) rootDocs else subfolderDocs(resolver, treeUri, folder.documentId) ?: continue

            val images = HashMap<String, String>()
            val videos = ArrayList<Doc>()
            for (doc in docs) {
                when {
                    doc.isDirectory -> {
                        if (folder.depth < MAX_FOLDER_DEPTH && !VideoFiles.isIgnoredFolder(doc.name)) {
                            queue.addLast(
                                Folder(
                                    documentId = doc.id,
                                    name = doc.name,
                                    path = "${folder.path}/${doc.name}",
                                    parent = folder,
                                    depth = folder.depth + 1,
                                ),
                            )
                        }
                    }
                    VideoFiles.isVideo(doc.name) -> {
                        if (!VideoFiles.isSample(doc.name)) videos += doc
                    }
                    ArtworkRules.isImage(doc.name) -> {
                        images[doc.name] = doc.id
                    }
                }
            }
            folder.images = images
            if (videos.isEmpty()) continue

            val imageNames = images.keys.toList()
            val parentFolders = generateSequence(folder) { it.parent }.map { it.name }.toList()
            for (video in videos) {
                val art = LocalArtwork.choose(
                    videoFileName = video.name,
                    folderName = folder.name,
                    folderImages = imageNames,
                    folderVideoCount = videos.size,
                    showFolderImages = { folder.parent?.images?.keys?.toList().orEmpty() },
                )
                found += ScannedVideo(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, video.id).toString(),
                    fileName = video.name,
                    folder = folder.path,
                    parentFolders = parentFolders,
                    sizeBytes = video.sizeBytes,
                    dateAdded = video.lastModified ?: now,
                    dateModified = video.lastModified,
                    localPoster = artUri(treeUri, folder, art.poster),
                    localBackdrop = artUri(treeUri, folder, art.backdrop),
                    localThumb = artUri(treeUri, folder, art.thumb),
                )
            }
            onProgress(found.size)
        }
        return found
    }

    private fun artUri(treeUri: Uri, folder: Folder, file: ArtFile?): String? {
        if (file == null) return null
        val documentId = when (file.place) {
            ArtPlace.VIDEO_FOLDER -> folder.images[file.name]
            ArtPlace.SHOW_FOLDER -> folder.parent?.images?.get(file.name)
        } ?: return null
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString()
    }

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
