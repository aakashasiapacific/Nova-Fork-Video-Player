package com.aakash.novafork.data.music

import com.aakash.novafork.data.db.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPlaylistsTest {
    private fun song(id: Long, plays: Int = 0, added: Long = 0, played: Long? = null) = SongEntity(
        id = id,
        sourceId = 1,
        uri = "content://song/$id",
        fileName = "$id.mp3",
        folder = "Music",
        title = "Song $id",
        albumKey = "album",
        artistKey = "artist",
        dateAdded = added,
        playCount = plays,
        lastPlayedAt = played,
    )

    private fun library(vararg songs: SongEntity) = MusicLibraryData(songs = songs.toList())

    private fun ids(playlist: AutoPlaylist, library: MusicLibraryData) = playlist.songs(library).map { it.id }

    @Test
    fun topPlayedIsMostPlayedFirstAndSkipsUnplayed() {
        val library = library(song(1, plays = 2), song(2, plays = 0), song(3, plays = 9), song(4, plays = 2, played = 50))
        // 4 beats 1 on the tie as it was played later.
        assertEquals(listOf(3L, 4L, 1L), ids(AutoPlaylist.TOP_PLAYED, library))
    }

    @Test
    fun topPlayedStopsAt25() {
        val library = MusicLibraryData(songs = (1L..40L).map { song(it, plays = it.toInt()) })
        val top = AutoPlaylist.TOP_PLAYED.songs(library)
        assertEquals(25, top.size)
        assertEquals(40L, top.first().id)
        assertEquals(16L, top.last().id)
    }

    @Test
    fun recentlyAddedIsNewestFirstAndStopsAt50() {
        val library = MusicLibraryData(songs = (1L..60L).map { song(it, added = it * 10) })
        val recent = AutoPlaylist.RECENTLY_ADDED.songs(library)
        assertEquals(50, recent.size)
        assertEquals(60L, recent.first().id)
        assertEquals(11L, recent.last().id)
    }

    @Test
    fun recentlyPlayedIsLatestFirstAndSkipsNeverPlayed() {
        val library = library(song(1, played = 100), song(2), song(3, played = 300), song(4, played = 200))
        assertEquals(listOf(3L, 4L, 1L), ids(AutoPlaylist.RECENTLY_PLAYED, library))
    }

    @Test
    fun onlyPlaylistsWithSongsAreShown() {
        assertTrue(AutoPlaylist.withSongs(MusicLibraryData()).isEmpty())
        // Nothing played: just Recently Added.
        assertEquals(listOf(AutoPlaylist.RECENTLY_ADDED), AutoPlaylist.withSongs(library(song(1))).keys.toList())
        assertEquals(
            listOf(AutoPlaylist.TOP_PLAYED, AutoPlaylist.RECENTLY_ADDED, AutoPlaylist.RECENTLY_PLAYED),
            AutoPlaylist.withSongs(library(song(1, plays = 1, played = 5))).keys.toList(),
        )
    }

    @Test
    fun favouritesFollowTheStarOrderAndComeFirst() {
        val library = library(song(1, plays = 1, played = 5), song(2), song(3))
        assertEquals(listOf(3L, 1L), AutoPlaylist.FAVOURITES.songs(library, listOf(3L, 99L, 1L)).map { it.id })
        assertEquals(AutoPlaylist.FAVOURITES, AutoPlaylist.withSongs(library, listOf(2L)).keys.first())
        assertTrue(AutoPlaylist.FAVOURITES !in AutoPlaylist.withSongs(library).keys)
    }
}
