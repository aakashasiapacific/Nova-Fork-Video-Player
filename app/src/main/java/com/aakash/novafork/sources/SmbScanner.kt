package com.aakash.novafork.sources

import com.aakash.novafork.core.artwork.ArtworkRules
import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.core.smb.SmbLocation
import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.data.ScannedVideo
import com.aakash.novafork.data.SmbBrowser
import com.aakash.novafork.data.SmbCredentials
import com.aakash.novafork.data.SmbEntry
import com.aakash.novafork.data.SourceScanner
import com.aakash.novafork.data.db.SourceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/** Walks an SMB share (from the source's folder down) through [SmbBrowser]. */
class SmbScanner(private val smb: SmbBrowser) : SourceScanner {

    private class Folder(
        /** Path inside the share, '/'-separated, "" for the share root. */
        val path: String,
        val parent: Folder?,
        val depth: Int,
    ) {
        /** Image names in here, kept so season subfolders can use the show art. */
        var images: List<String> = emptyList()
    }

    override suspend fun scan(source: SourceEntity, onProgress: (found: Int) -> Unit): List<ScannedVideo> =
        withContext(Dispatchers.IO) {
            val location = SmbUris.parse(source.location)
                ?: throw IOException("Not a valid network share address: ${source.location}")
            val credentials = SmbCredentials(source.username, source.password, source.domain)
            val now = System.currentTimeMillis()
            val found = ArrayList<ScannedVideo>()
            val root = Folder(path = location.path, parent = null, depth = 0)
            val queue = ArrayDeque<Folder>()
            queue.addLast(root)
            while (queue.isNotEmpty()) {
                ensureActive()
                val folder = queue.removeFirst()
                // The source folder itself must be readable; a subfolder that vanished or is off
                // limits is skipped, while a dropped connection fails the scan.
                val entries = if (folder === root) {
                    list(location, folder.path, credentials)
                } else {
                    try {
                        list(location, folder.path, credentials)
                    } catch (e: SmbPathException) {
                        continue
                    }
                }

                val images = ArrayList<String>()
                val videos = ArrayList<SmbEntry>()
                for (entry in entries) {
                    when {
                        entry.isDirectory -> {
                            if (folder.depth < MAX_FOLDER_DEPTH && !VideoFiles.isIgnoredFolder(entry.name)) {
                                queue.addLast(Folder(path = entry.path, parent = folder, depth = folder.depth + 1))
                            }
                        }
                        VideoFiles.isVideo(entry.name) -> {
                            if (!VideoFiles.isSample(entry.name)) videos += entry
                        }
                        ArtworkRules.isImage(entry.name) -> {
                            images += entry.name
                        }
                    }
                }
                folder.images = images
                if (videos.isEmpty()) continue

                val segments = folder.path.split('/').filter { it.isNotEmpty() }
                val parentFolders = segments.asReversed() + location.share
                for (video in videos) {
                    val art = LocalArtwork.choose(
                        videoFileName = video.name,
                        folderName = segments.lastOrNull() ?: location.share,
                        folderImages = images,
                        folderVideoCount = videos.size,
                        showFolderImages = { folder.parent?.images.orEmpty() },
                    )
                    found += ScannedVideo(
                        uri = SmbUris.build(location.copy(path = video.path)),
                        fileName = video.name,
                        folder = folder.path,
                        parentFolders = parentFolders,
                        sizeBytes = video.sizeBytes.takeIf { it >= 0 },
                        dateAdded = video.modifiedAt.takeIf { it > 0 } ?: now,
                        dateModified = video.modifiedAt.takeIf { it > 0 },
                        localPoster = artUri(location, folder, art.poster),
                        localBackdrop = artUri(location, folder, art.backdrop),
                        localThumb = artUri(location, folder, art.thumb),
                    )
                }
                onProgress(found.size)
            }
            found
        }

    private suspend fun list(location: SmbLocation, path: String, credentials: SmbCredentials): List<SmbEntry> =
        smb.list(location.host, location.port, location.share, path, credentials)

    private fun artUri(location: SmbLocation, folder: Folder, file: ArtFile?): String? {
        if (file == null) return null
        val dir = when (file.place) {
            ArtPlace.VIDEO_FOLDER -> folder
            ArtPlace.SHOW_FOLDER -> folder.parent ?: return null
        }
        return SmbUris.build(location.copy(path = dir.path).child(file.name))
    }
}
