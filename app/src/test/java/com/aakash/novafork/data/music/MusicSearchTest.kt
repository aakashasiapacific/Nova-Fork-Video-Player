package com.aakash.novafork.data.music

import android.app.Application
import com.aakash.novafork.core.audio.MusicKeys
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.ui.LibraryIndex
import com.aakash.novafork.ui.UiMappers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric for android.net.Uri, which album art addresses are built with. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MusicSearchTest {
    private var nextId = 1L

    private fun song(title: String, artist: String, album: String) = SongEntity(
        id = nextId++,
        sourceId = 1,
        uri = "content://song/${nextId}",
        fileName = "$title.mp3",
        folder = "Music/$album",
        title = title,
        artist = artist,
        album = album,
        albumKey = MusicKeys.albumKey(null, album, "Music/$album"),
        artistKey = MusicKeys.artistKey(null, artist),
    )

    private val library = MusicLibrary.build(
        listOf(
            song("Halo", "Beyoncé", "I Am... Sasha Fierce"),
            song("Single Ladies", "Beyoncé", "I Am... Sasha Fierce"),
            song("Love On Top", "Beyoncé", "4"),
            song("Crazy in Love", "Beyoncé", "Dangerously in Love"),
            song("Love of My Life", "Queen", "A Night at the Opera"),
        ),
    )

    @Test
    fun findsSongsAlbumsAndArtistsIgnoringAccents() {
        val found = MusicSearch.search(library, "beyonce")
        assertEquals(4, found.songs.size)
        assertEquals(listOf("Beyoncé"), found.artists.map { it.name })
        assertEquals(3, found.albums.size)
    }

    @Test
    fun namesStartingWithTheQueryComeFirst() {
        val found = MusicSearch.search(library, "love")
        assertEquals(listOf("Love of My Life", "Love On Top", "Crazy in Love"), found.songs.map { it.title })
        assertEquals(listOf("Dangerously in Love"), found.albums.map { it.title })
    }

    @Test
    fun aBlankQueryFindsNothing() {
        assertTrue(MusicSearch.search(library, "  ").isEmpty)
    }

    @Test
    fun searchResultsPlayFromTheSongTapped() {
        val search = UiMappers.search(LibraryIndex.build(emptyList(), emptyList(), preferLocalArtwork = false), "love", library)
        val second = search.songs[1]
        assertEquals("Love On Top", second.title)
        assertEquals("Song · Beyoncé", second.subtitle)
        assertEquals(1, second.startIndex)
        assertEquals(3, second.songIds.size)
        val album = search.albums.single()
        assertEquals(library.albums.single { it.title == "Dangerously in Love" }.songs.map { it.id }, album.songIds)
        assertTrue(search.results.isEmpty())
        assertTrue(!search.isEmpty)
    }

    @Test
    fun aSungLineFindsItsSong() {
        val halo = library.songs.single { it.title == "Halo" }
        val withLyrics = library.copy(lyrics = mapOf(halo.id to listOf("Remember those walls I built", "Everywhere I'm looking now")))
        val index = LibraryIndex.build(emptyList(), emptyList(), preferLocalArtwork = false)
        val search = UiMappers.search(index, "walls i built", withLyrics)
        val hit = search.lyrics.single()
        assertEquals("Halo", hit.title)
        assertEquals("\u201CRemember those walls I built\u201D · Beyoncé", hit.subtitle)
        assertTrue(search.hasMusic)
        // Short queries don't search lyrics; a title hit isn't listed twice.
        assertTrue(UiMappers.search(index, "wal", withLyrics).lyrics.isEmpty())
        assertTrue(UiMappers.search(index, "halo", withLyrics.copy(lyrics = mapOf(halo.id to listOf("halo halo")))).lyrics.isEmpty())
    }

    @Test
    fun postersLoadAtTheSizeTheirCardNeeds() {
        val url = "https://image.tmdb.org/t/p/w500/abc.jpg"
        assertEquals("https://image.tmdb.org/t/p/w185/abc.jpg", com.aakash.novafork.ui.common.rightSizedPoster(url, 150))
        assertEquals("https://image.tmdb.org/t/p/w342/abc.jpg", com.aakash.novafork.ui.common.rightSizedPoster(url, 330))
        assertEquals(url, com.aakash.novafork.ui.common.rightSizedPoster(url, 480))
        assertEquals(url, com.aakash.novafork.ui.common.rightSizedPoster(url, 900))
        assertEquals("file:///poster.jpg", com.aakash.novafork.ui.common.rightSizedPoster("file:///poster.jpg", 100))
    }
}
