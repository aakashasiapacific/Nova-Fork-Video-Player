package com.aakash.novafork.sources

import com.aakash.novafork.core.library.VideoFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * [FolderImages] indexes a folder's images once for all its videos (a camera folder holds
 * thousands of photos); it must pick exactly what matching every image against every video did.
 */
class LocalArtworkTest {
    private val names = listOf(
        "20240601_101500.jpg",
        "20240601_101500(1).JPG",
        "20240601_1015.jpg",
        "IMG_0001.jpg",
        "poster.jpg",
        "fanart.jpg",
        "Heat (1995)-poster.jpg",
        "heat (1995)-fanart.png",
        "Heat (1995).jpg",
        "Inception (2010)-poster.jpg",
        "zz.png",
    )

    @Test
    fun startingWithFindsWhatAFullFilterFinds() {
        val index = FolderImages(names)
        for (prefix in listOf("20240601_101500", "20240601", "heat (1995)", "inception", "img", "nothing", "")) {
            val expected = names.filter { VideoFiles.baseName(it).lowercase(Locale.ROOT).startsWith(prefix) }
            assertEquals("Prefix \"$prefix\"", expected, index.startingWith(prefix))
        }
        assertEquals(names, index.all)
        assertTrue(FolderImages(emptyList()).startingWith("a").isEmpty())
    }

    @Test
    fun choosingWithTheIndexPicksTheSameArtwork() {
        val cases = listOf(
            // A shared Movies folder: only "<name>-poster" style art counts.
            Triple("Heat (1995).mkv", "Movies", 3),
            Triple("Inception (2010).mkv", "Movies", 3),
            // A camera folder with photos taken at the same moment.
            Triple("20240601_101500.mp4", "Camera", 40),
            // A movie alone in its folder: generic names count.
            Triple("Heat (1995).mkv", "Heat (1995)", 1),
            // Episodes: season folder and SxxEyy marker.
            Triple("Show.S01E02.mkv", "Season 1", 10),
            Triple("Show.S01E02.mkv", "Show", 10),
        )
        val above = listOf("poster.jpg", "fanart.jpg", "season01-poster.jpg")
        for ((video, folder, count) in cases) {
            val fromList = LocalArtwork.choose(video, folder, names, count) { above }
            val fromIndex = LocalArtwork.choose(video, folder, FolderImages(names), count) { above }
            assertEquals("$video in $folder", fromList, fromIndex)
        }
    }
}
