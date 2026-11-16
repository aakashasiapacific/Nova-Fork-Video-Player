package com.aakash.novafork.data.music

import com.aakash.novafork.data.db.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicFoldersTest {
    private fun song(id: Long, folder: String, file: String = "$id.mp3") = SongEntity(
        id = id,
        sourceId = 1,
        uri = "content://song/$id",
        fileName = file,
        folder = folder,
        title = "Song $id",
        albumKey = "album",
        artistKey = "artist",
    )

    private val songs = listOf(
        song(1, "Music/Rock/Live", "b.mp3"),
        song(2, "Music/Rock", "02.mp3"),
        song(3, "Music/Rock", "01.mp3"),
        song(4, "Music/Jazz"),
        song(5, "downloads"),
    )

    @Test
    fun topLevelFoldersAreAlphabetical() {
        val top = MusicFolders.list(songs)
        assertEquals(listOf("downloads", "Music"), top.folders.map { it.name })
        assertEquals(listOf(1, 4), top.folders.map { it.songCount })
        assertEquals(emptyList<Long>(), top.songs.map { it.id })
    }

    @Test
    fun aFolderListsItsSubfoldersAndOwnSongs() {
        val rock = MusicFolders.list(songs, "Music/Rock")
        assertEquals(listOf("Music/Rock/Live"), rock.folders.map { it.path })
        assertEquals(listOf(3L, 2L), rock.songs.map { it.id })
        assertEquals(listOf(3L, 2L, 1L), rock.all.map { it.id })
    }

    @Test
    fun aFolderNameIsNotMatchedByAPrefix() {
        val listing = MusicFolders.list(songs + song(6, "Music/Rockabilly"), "Music/Rock")
        assertEquals(listOf("Live"), listing.folders.map { it.name })
    }
}
