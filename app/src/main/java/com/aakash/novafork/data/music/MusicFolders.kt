package com.aakash.novafork.data.music

import com.aakash.novafork.core.library.NaturalOrder
import com.aakash.novafork.data.db.SongEntity

/** A folder holding songs, directly or in its subfolders. [path] is '/'-separated, root to leaf. */
data class MusicFolder(val path: String, val name: String, val songCount: Int)

/** What one folder shows: its subfolders and the songs directly in it. [all] adds the nested songs, for Play. */
data class FolderListing(val folders: List<MusicFolder>, val songs: List<SongEntity>, val all: List<SongEntity>)

/** The folders that hold songs, as a tree built from the songs' folder paths (unit-tested). */
object MusicFolders {
    private fun clean(folder: String): String = folder.trim().trim('/')

    /** Subfolders and songs of [path] ("" = the top level), folders alphabetically, songs by file name. */
    fun list(songs: List<SongEntity>, path: String = ""): FolderListing {
        val base = clean(path)
        val prefix = if (base.isEmpty()) "" else "$base/"
        val direct = ArrayList<SongEntity>()
        val nested = LinkedHashMap<String, MutableList<SongEntity>>()
        for (song in songs) {
            val folder = clean(song.folder)
            if (folder == base) {
                direct += song
            } else if (folder.startsWith(prefix)) {
                val child = folder.removePrefix(prefix).substringBefore('/')
                nested.getOrPut(child) { ArrayList() } += song
            }
        }
        val order = compareBy<SongEntity, String>(NaturalOrder) { it.fileName }
        val folders = nested.map { (child, inside) ->
            MusicFolder(path = prefix + child, name = child, songCount = inside.size)
        }.sortedWith(compareBy<MusicFolder, String>(String.CASE_INSENSITIVE_ORDER) { it.name })
        val sortedDirect = direct.sortedWith(order)
        val all = sortedDirect + folders.flatMap { folder -> nested.getValue(folder.name).sortedWith(order) }
        return FolderListing(folders, sortedDirect, all)
    }
}
