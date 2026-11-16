package com.aakash.novafork.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFilesTest {

    @Test
    fun recognisesAudioFiles() {
        assertTrue(AudioFiles.isAudio("01 Song.FLAC"))
        assertTrue(AudioFiles.isAudio("track.m4a"))
        assertTrue(AudioFiles.isAudio("a.opus"))
        assertFalse(AudioFiles.isAudio("movie.mkv"))
        assertFalse(AudioFiles.isAudio("._01 Song.mp3"))
        assertFalse(AudioFiles.isAudio("mp3"))
    }

    @Test
    fun picksTheCoverByName() {
        assertEquals("Cover.jpg", AudioFiles.coverOf(listOf("back.jpg", "Cover.jpg", "folder.png")))
        assertEquals("folder.jpg", AudioFiles.coverOf(listOf("folder.png", "folder.jpg", "scan1.jpg")))
        assertEquals(
            "AlbumArt_{ABC}_Large.jpg",
            AudioFiles.coverOf(listOf("AlbumArt_{ABC}_Small.jpg", "AlbumArt_{ABC}_Large.jpg")),
        )
    }

    @Test
    fun aLoneImageIsTheCover() {
        assertEquals("scan.jpg", AudioFiles.coverOf(listOf("scan.jpg", "notes.txt")))
        assertNull(AudioFiles.coverOf(listOf("scan1.jpg", "scan2.jpg")))
        assertNull(AudioFiles.coverOf(emptyList()))
    }

    @Test
    fun findsLyricsWithTheSongsName() {
        val siblings = listOf("01 Song.flac", "01 song.LRC", "02 Other.lrc")
        assertEquals("01 song.LRC", AudioFiles.lyricsFileFor("01 Song.flac", siblings))
        assertNull(AudioFiles.lyricsFileFor("03 None.flac", siblings))
    }

    @Test
    fun cleansTags() {
        assertEquals("Artist", AudioFiles.cleanTag("  Artist "))
        assertNull(AudioFiles.cleanTag("<unknown>"))
        assertNull(AudioFiles.cleanTag("   "))
        assertNull(AudioFiles.cleanTag(null))
    }

    @Test
    fun readsTrackDiscAndYear() {
        assertEquals(3, AudioFiles.leadingNumber("3/12"))
        assertEquals(3, AudioFiles.leadingNumber("03"))
        assertEquals(3, AudioFiles.leadingNumber("3 of 12"))
        assertNull(AudioFiles.leadingNumber("0"))
        assertNull(AudioFiles.leadingNumber("A1"))
        assertEquals(2004, AudioFiles.year("2004-05-01"))
        assertEquals(1999, AudioFiles.year("1999"))
        assertNull(AudioFiles.year("99"))
        assertNull(AudioFiles.year("0000"))
    }

    @Test
    fun splitsMediaStoreTrackNumbers() {
        assertEquals(5 to 2, AudioFiles.splitMediaStoreTrack(2005))
        assertEquals(12 to null, AudioFiles.splitMediaStoreTrack(12))
        assertEquals(null to 1, AudioFiles.splitMediaStoreTrack(1000))
        assertEquals(null to null, AudioFiles.splitMediaStoreTrack(0))
        assertEquals(null to null, AudioFiles.splitMediaStoreTrack(null))
    }

    @Test
    fun titlesFromFileNames() {
        assertEquals("Song", AudioFiles.titleFromFileName("01 - Song.mp3"))
        assertEquals("Song", AudioFiles.titleFromFileName("01. Song.mp3"))
        assertEquals("Song", AudioFiles.titleFromFileName("1-03 Song.flac"))
        assertEquals("Song", AudioFiles.titleFromFileName("03_Song.flac"))
        assertEquals("1999", AudioFiles.titleFromFileName("1999.mp3"))
        assertEquals("2112 Overture", AudioFiles.titleFromFileName("2112 Overture.mp3"))
        assertEquals("Plain", AudioFiles.titleFromFileName("Plain.ogg"))
    }
}
