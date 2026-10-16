package com.aakash.novafork.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {

    @Test
    fun keys() {
        assertEquals("movie:603", LibraryKeys.movieKey(603))
        assertEquals("tv:1396", LibraryKeys.tvKey(1396))
        assertEquals("tv:1396", LibraryKeys.showKey(1396, "Breaking Bad"))
        assertEquals("name:office us", LibraryKeys.showKey(null, "The Office US"))
        assertEquals(LibraryKeys.showKey(null, "The.Office"), LibraryKeys.showKey(null, "the office"))
        assertEquals(603, LibraryKeys.tmdbId("movie:603"))
        assertEquals(1396, LibraryKeys.tmdbId("tv:1396"))
        assertNull(LibraryKeys.tmdbId("name:office"))
        assertNull(LibraryKeys.tmdbId("movie:abc"))
        assertEquals("movie|amelie|2001", LibraryKeys.matchQueryKey("movie", "Amélie", 2001))
        assertEquals("tv|office|", LibraryKeys.matchQueryKey("tv", "The Office", null))
    }

    @Test
    fun videoFiles() {
        assertTrue(VideoFiles.isVideo("Movie.MKV"))
        assertFalse(VideoFiles.isVideo("._Movie.mkv"))
        assertFalse(VideoFiles.isVideo("Movie.srt"))
        assertEquals("Movie.2019", VideoFiles.baseName("Movie.2019.mkv"))
        assertTrue(VideoFiles.isSample("movie-sample.mkv"))
        assertTrue(VideoFiles.isIgnoredFolder(".thumbnails"))
        assertFalse(VideoFiles.isIgnoredFolder("Movies"))
    }
}
