package com.aakash.novafork.data.backup

import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import com.aakash.novafork.data.db.VideoIdentity

/**
 * Finds the library's video for a backup's [BackupVideoRef]: the same URI holding the same file
 * (name, and size when both are known); else the same place (source type and location, folder,
 * file name), which survives a file's new MediaStore or document id; else the same file name and
 * size, which also finds it on another share or device. Where several videos fit, one the library
 * shows wins, then the oldest.
 *
 * A URI alone proves nothing on another device: MediaStore and document ids are counters of the
 * device, so there content://media/external/video/media/1000034 may well be another file.
 */
internal class VideoMatcher(videos: List<VideoIdentity>, sources: List<SourceEntity>) {
    private data class Place(val sourceType: String, val sourceLocation: String, val folder: String, val fileName: String)

    private data class NameAndSize(val fileName: String, val sizeBytes: Long)

    /** The video at a URI, with what tells whether it is the file a backup means. */
    private class AtUri(val id: Long, val fileName: String, val sizeBytes: Long?) {
        fun holds(ref: BackupVideoRef): Boolean {
            if (fileName != ref.fileName) return false
            val size = sizeBytes?.takeIf { it > 0 } ?: return true
            val refSize = ref.sizeBytes?.takeIf { it > 0 } ?: return true
            return size == refSize
        }
    }

    private val byUri = HashMap<String, AtUri>()
    private val byPlace = HashMap<Place, Long>()
    private val byNameAndSize = HashMap<NameAndSize, Long>()

    init {
        val sourcesById = sources.associateBy { it.id }
        for (video in videos.sortedWith(compareByDescending<VideoIdentity> { it.present }.thenBy { it.id })) {
            byUri.putIfAbsent(video.uri, AtUri(video.id, video.fileName, video.sizeBytes))
            val source = sourcesById[video.sourceId]
            if (source != null) {
                byPlace.putIfAbsent(Place(source.type.name, source.location, cleanFolder(video.folder), video.fileName), video.id)
            }
            val size = video.sizeBytes
            if (size != null && size > 0) byNameAndSize.putIfAbsent(NameAndSize(video.fileName, size), video.id)
        }
    }

    /** The id of [ref]'s video, or null while the library has none that fits. */
    fun match(ref: BackupVideoRef): Long? {
        byUri[ref.uri]?.takeIf { it.holds(ref) }?.let { return it.id }
        val type = ref.sourceType
        val location = ref.sourceLocation
        if (type != null && location != null) {
            byPlace[Place(type, location, cleanFolder(ref.folder), ref.fileName)]?.let { return it }
        }
        val size = ref.sizeBytes
        if (size != null && size > 0) byNameAndSize[NameAndSize(ref.fileName, size)]?.let { return it }
        return null
    }

    companion object {
        private fun cleanFolder(folder: String): String = folder.trim('/')

        /**
         * [video] as a backup refers to it. Without secrets, the URL of a saved stream loses its
         * password and tokens (no other source keeps them in its URIs).
         */
        fun refOf(video: VideoEntity, source: SourceEntity?, includeSecrets: Boolean): BackupVideoRef {
            val redact = !includeSecrets && source?.type == SourceType.STREAM
            return BackupVideoRef(
                uri = if (redact) UriSecrets.redact(video.uri) else video.uri,
                sourceType = source?.type?.name,
                sourceLocation = source?.location?.let { if (redact) UriSecrets.redact(it) else it },
                folder = video.folder,
                fileName = video.fileName,
                sizeBytes = video.sizeBytes,
            )
        }
    }
}
