package com.aakash.novafork.data.music

import android.app.Application
import com.aakash.novafork.core.audio.MusicKeys
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.sources.music.SongArt
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric for android.net.Uri, which album art addresses are built with. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MusicLibraryTest {
    private var nextId = 1L

    private fun song(
        title: String,
        album: String? = "Album",
        artist: String? = "Band",
        albumArtist: String? = null,
        folder: String = "Music/Band/Album",
        track: Int? = null,
        disc: Int? = null,
        added: Long = 0,
        cover: String? = null,
        plays: Int = 0,
        year: Int? = null,
    ) = SongEntity(
        id = nextId++,
        sourceId = 1,
        uri = "content://song/$title",
        fileName = "$title.mp3",
        folder = folder,
        title = title,
        artist = artist,
        albumArtist = albumArtist,
        album = album,
        albumKey = MusicKeys.albumKey(albumArtist, album, folder),
        artistKey = MusicKeys.artistKey(albumArtist, artist),
        trackNumber = track,
        discNumber = disc,
        dateAdded = added,
        coverUri = cover,
        playCount = plays,
        year = year,
    )

    @Test
    fun albumsPlayDiscThenTrackOrder() {
        val album = MusicLibrary.albums(
            listOf(
                song("D2T1", track = 1, disc = 2),
                song("NoNumber"),
                song("D1T2", track = 2, disc = 1),
                song("T1", track = 1),
            ),
        ).single()
        assertEquals(listOf("T1", "D1T2", "NoNumber", "D2T1"), album.songs.map { it.title })
    }

    @Test
    fun aCompilationWithoutAlbumArtistIsVariousArtists() {
        val album = MusicLibrary.albums(
            listOf(song("A", album = "Hits", artist = "One", folder = "Music/Hits"), song("B", album = "Hits", artist = "Two", folder = "Music/Hits")),
        ).single()
        assertEquals(MusicKeys.VARIOUS_ARTISTS, album.artist)
    }

    @Test
    fun albumArtIsTheCoverFileElseTheFirstSongsPicture() {
        val withCover = MusicLibrary.albums(listOf(song("A", track = 1), song("B", track = 2, cover = "file:///cover.jpg"))).single()
        assertEquals("file:///cover.jpg", withCover.artUri)
        val without = MusicLibrary.albums(listOf(song("B", track = 2), song("A", track = 1))).single()
        assertEquals(SongArt.embedded("content://song/A"), without.artUri)
    }

    @Test
    fun theLibraryIsSortedTheWayApplesIs() {
        val library = MusicLibrary.build(
            listOf(
                song("Yesterday", album = "Help!", artist = "The Beatles", folder = "b", added = 5, year = 1965),
                song("Angie", album = "Goats Head Soup", artist = "The Rolling Stones", folder = "r", added = 9),
                song("Bohemian Rhapsody", album = "A Night at the Opera", artist = "Queen", folder = "q", added = 1, plays = 3),
                song("Love of My Life", album = "A Night at the Opera", artist = "Queen", folder = "q", added = 2),
            ),
        )
        assertEquals(listOf("Angie", "Bohemian Rhapsody", "Love of My Life", "Yesterday"), library.songs.map { it.title })
        assertEquals(listOf("The Beatles", "Queen", "The Rolling Stones"), library.artists.map { it.name })
        // Albums by title (a leading "A" counts, only "The" is skipped); Recently Added by newest file.
        assertEquals(listOf("A Night at the Opera", "Goats Head Soup", "Help!"), library.albums.map { it.title })
        assertEquals(listOf("Goats Head Soup", "Help!", "A Night at the Opera"), library.recentlyAdded.map { it.title })
        assertEquals(listOf("Bohemian Rhapsody", "Love of My Life"), library.artists.single { it.name == "Queen" }.songs.map { it.title })
    }
}
