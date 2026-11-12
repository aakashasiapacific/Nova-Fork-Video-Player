package com.aakash.novafork.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MusicKeysTest {

    @Test
    fun artistIsTheAlbumArtistFirst() {
        assertEquals("Band", MusicKeys.artistOf("Band", "Band feat. Guest"))
        assertEquals("Solo", MusicKeys.artistOf(null, "Solo"))
        assertEquals(MusicKeys.UNKNOWN_ARTIST, MusicKeys.artistOf("<unknown>", " "))
    }

    @Test
    fun taggedAlbumsStayTogetherAcrossFolders() {
        assertEquals(
            MusicKeys.albumKey("Band", "Album", "Music/Band/Album/CD1"),
            MusicKeys.albumKey("band ", "ALBUM", "Music/Band/Album/CD2"),
        )
    }

    @Test
    fun untaggedAlbumsAreSplitByFolder() {
        assertEquals(
            MusicKeys.albumKey(null, "Hits", "Music/Hits"),
            MusicKeys.albumKey(null, "hits", "music/hits"),
        )
        assertNotEquals(
            MusicKeys.albumKey(null, "Hits", "Music/Hits 1"),
            MusicKeys.albumKey(null, "Hits", "Music/Hits 2"),
        )
        assertNotEquals(MusicKeys.albumKey("A", "Hits", "x"), MusicKeys.albumKey(null, "Hits", "x"))
    }

    @Test
    fun albumArtistOfManyArtistsIsVarious() {
        assertEquals("Band", MusicKeys.albumArtistOf(listOf(null, "Band"), listOf("A", "B")))
        assertEquals("Solo", MusicKeys.albumArtistOf(listOf(null), listOf("Solo", "solo")))
        assertEquals(MusicKeys.VARIOUS_ARTISTS, MusicKeys.albumArtistOf(emptyList(), listOf("A", "B")))
        assertEquals(MusicKeys.UNKNOWN_ARTIST, MusicKeys.albumArtistOf(emptyList(), listOf(null)))
    }

    @Test
    fun normalizeFoldsCaseAccentsAndSpaces() {
        assertEquals("beyonce", MusicKeys.normalize("  Beyoncé "))
        assertEquals("sigur ros", MusicKeys.normalize("Sigur   Rós"))
    }

    @Test
    fun sortKeyIgnoresALeadingThe() {
        val sorted = listOf("The Beatles", "ABBA", "Coldplay", "The The").sortedBy(MusicKeys::sortKey)
        assertEquals(listOf("ABBA", "The Beatles", "Coldplay", "The The"), sorted)
        assertEquals("the", MusicKeys.sortKey("The"))
    }
}
