package com.aakash.novafork.data.backup

import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.data.db.SongIdentity
import com.aakash.novafork.data.db.SourceEntity
import java.util.Locale

/**
 * Finds the song a backup entry is about in this library: the same URI with the same file name
 * (the same device), else the same place in a source of the same type and location (a picked
 * folder, a song moved between scans), else one song with the same file name and size (a new
 * device, where MediaStore ids differ). Null when none or several fit.
 */
internal class SongMatcher(songs: List<SongIdentity>, sources: List<SourceEntity>) {
    private val byUri = songs.associateBy { it.uri }
    private val sourceIds = sources.groupBy({ it.type.name to it.location }, { it.id })
    private val byPlace = songs.groupBy { Triple(it.sourceId, it.folder, it.fileName) }
    private val byNameAndSize = songs.filter { it.sizeBytes != null }
        .groupBy { it.fileName.lowercase(Locale.ROOT) to it.sizeBytes }

    fun match(ref: BackupSongRef): Long? {
        byUri[ref.uri]?.takeIf { it.fileName == ref.fileName }?.let { return it.id }
        val type = ref.sourceType
        val location = ref.sourceLocation
        if (type != null && location != null) {
            val found = sourceIds[type to location].orEmpty().flatMap { byPlace[Triple(it, ref.folder, ref.fileName)].orEmpty() }
            if (found.size == 1) return found.single().id
        }
        val size = ref.sizeBytes ?: return null
        return byNameAndSize[ref.fileName.lowercase(Locale.ROOT) to size]?.singleOrNull()?.id
    }

    companion object {
        fun refOf(song: SongEntity, source: SourceEntity?): BackupSongRef = BackupSongRef(
            uri = song.uri,
            sourceType = source?.type?.name,
            sourceLocation = source?.location,
            folder = song.folder,
            fileName = song.fileName,
            sizeBytes = song.sizeBytes,
        )
    }
}
