package com.aakash.novafork.sources.net

import com.aakash.novafork.AppGraph
import com.aakash.novafork.core.artwork.ArtworkRules
import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.core.webdav.WebDavClient
import com.aakash.novafork.data.RemoteEntry
import com.aakash.novafork.data.ScannedVideo
import com.aakash.novafork.data.SourceModule
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.sources.ArtFile
import com.aakash.novafork.sources.ArtPlace
import com.aakash.novafork.sources.LocalArtwork
import com.aakash.novafork.sources.MAX_FOLDER_DEPTH
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Interceptor

/**
 * FTP, SFTP, NFS, UPnP and WebDAV: their scanners, browsers and playback options, as AppGraph
 * registers them. FTP, SFTP and NFS are listed and played through libVLC's own modules, UPnP
 * servers through libVLC's UPnP access, WebDAV with OkHttp (PROPFIND) and libVLC's http access.
 */
internal object FileServerSources {
    /** [graph] gives the app context and HTTP client; read graph.library lazily, never while creating. */
    fun create(graph: AppGraph): SourceModule {
        val vlc = VlcBrowser(graph.app)
        val ftp = VlcShareBrowser(SourceType.FTP, vlc)
        val sftp = VlcShareBrowser(SourceType.SFTP, vlc)
        val nfs = VlcShareBrowser(SourceType.NFS, vlc)
        val upnp = UpnpBrowser(vlc)
        val webDav = WebDavSource(WebDavClient(graph.http))
        return SourceModule(
            scanners = mapOf(
                SourceType.FTP to ftp,
                SourceType.SFTP to sftp,
                SourceType.NFS to nfs,
                SourceType.UPNP to upnp,
                SourceType.WEBDAV to webDav,
            ),
            browsers = mapOf(
                SourceType.FTP to ftp,
                SourceType.SFTP to sftp,
                SourceType.NFS to nfs,
                SourceType.UPNP to upnp,
                SourceType.WEBDAV to webDav,
            ),
            playbackOptions = mapOf(
                SourceType.FTP to ftp,
                SourceType.SFTP to sftp,
                SourceType.WEBDAV to webDav,
            ),
        )
    }

    /**
     * The interceptor that gives image requests to a WebDAV server its source's login, for the
     * OkHttp client the image loader is built with (`http.newBuilder().addInterceptor(…)`).
     */
    fun imageAuth(graph: AppGraph): Interceptor = WebDavAuthInterceptor(graph.appScope) { graph.library.observeSources() }
}

/**
 * Walks a file server folder by folder, as SmbScanner walks a share: breadth first, at most
 * [MAX_FOLDER_DEPTH] levels below the root, skipping core VideoFiles' ignored folders and sample
 * clips. Artwork next to the videos is chosen with LocalArtwork (core ArtworkRules) when the
 * server's files can be shown as images.
 */
internal object RemoteFolderWalk {
    private class Folder(val path: String, val parent: Folder?, val depth: Int) {
        /** Image names in here, kept so season subfolders can use the show art. */
        var images: List<String> = emptyList()
    }

    /**
     * @param rootSegments the decoded path segments of the source's own location, for the names
     *   NameParser reads (a location "ftp://nas/TV/Severance" makes "Severance" a parent folder).
     * @param list lists a folder by its path below the location ("" = the location): folders
     *   and files alike. Throws when it can't.
     * @param uriOf the URI of the file at a path below the location.
     * @param withArtwork false when the app can't load the server's files as images (ftp://…).
     * @param onFolderFailure decides about a subfolder that couldn't be listed: return to skip it,
     *   throw to fail the scan (the server went away). The root folder's failure always fails it.
     */
    suspend fun scan(
        rootSegments: List<String>,
        list: suspend (path: String) -> List<RemoteEntry>,
        uriOf: (path: String) -> String,
        withArtwork: Boolean,
        onProgress: (found: Int) -> Unit,
        onFolderFailure: suspend (path: String, error: Exception) -> Unit,
    ): List<ScannedVideo> {
        val now = System.currentTimeMillis()
        val found = ArrayList<ScannedVideo>()
        val seen = HashSet<String>()
        val root = Folder(path = "", parent = null, depth = 0)
        val queue = ArrayDeque<Folder>()
        queue.addLast(root)
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val folder = queue.removeFirst()
            val entries = if (folder === root) {
                list(folder.path)
            } else {
                try {
                    list(folder.path)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onFolderFailure(folder.path, e)
                    continue
                }
            }

            val images = ArrayList<String>()
            val videos = ArrayList<RemoteEntry>()
            for (entry in entries) {
                when {
                    entry.isDirectory -> {
                        if (folder.depth < MAX_FOLDER_DEPTH && !VideoFiles.isIgnoredFolder(entry.name) && seen.add(entry.path)) {
                            queue.addLast(Folder(path = entry.path, parent = folder, depth = folder.depth + 1))
                        }
                    }
                    VideoFiles.isVideo(entry.name) -> if (!VideoFiles.isSample(entry.name)) videos += entry
                    withArtwork && ArtworkRules.isImage(entry.name) -> images += entry.name
                }
            }
            folder.images = images
            if (videos.isEmpty()) continue

            val segments = folder.path.split('/').filter { it.isNotEmpty() }
            val parentFolders = (rootSegments + segments).asReversed()
            val folderName = (rootSegments + segments).lastOrNull()
            for (video in videos) {
                val art = if (withArtwork) {
                    LocalArtwork.choose(
                        videoFileName = video.name,
                        folderName = folderName,
                        folderImages = images,
                        folderVideoCount = videos.size,
                        showFolderImages = { folder.parent?.images.orEmpty() },
                    )
                } else {
                    null
                }
                val modified = video.modifiedAt?.takeIf { it > 0 }
                found += ScannedVideo(
                    uri = video.playUri ?: uriOf(video.path),
                    fileName = video.name,
                    folder = folder.path,
                    parentFolders = parentFolders,
                    sizeBytes = video.sizeBytes?.takeIf { it >= 0 },
                    dateAdded = modified ?: now,
                    dateModified = modified,
                    localPoster = art?.poster?.let { artUri(folder, it, uriOf) },
                    localBackdrop = art?.backdrop?.let { artUri(folder, it, uriOf) },
                    localThumb = art?.thumb?.let { artUri(folder, it, uriOf) },
                )
            }
            onProgress(found.size)
        }
        return found
    }

    private fun artUri(folder: Folder, file: ArtFile, uriOf: (String) -> String): String? {
        val dir = when (file.place) {
            ArtPlace.VIDEO_FOLDER -> folder
            ArtPlace.SHOW_FOLDER -> folder.parent ?: return null
        }
        return uriOf(if (dir.path.isEmpty()) file.name else "${dir.path}/${file.name}")
    }
}
