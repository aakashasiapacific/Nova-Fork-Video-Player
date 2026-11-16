package com.aakash.novafork.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraFilesTest {

    private fun movie(id: Long, folder: String, name: String, source: Long = 1) =
        ExtraFiles.Entry(id, source, folder, name, isMovie = true)

    private fun file(id: Long, folder: String, name: String, source: Long = 1) =
        ExtraFiles.Entry(id, source, folder, name, isMovie = false)

    @Test
    fun extrasFolderBelongsToTheMovieAbove() {
        val matches = ExtraFiles.match(
            listOf(
                movie(1, "Movies/Dune (2021)", "Dune (2021).mkv"),
                file(2, "Movies/Dune (2021)/Featurettes", "Making of.mkv"),
                file(3, "Movies/Dune (2021)/Extras/Deleted Scenes", "Cut 1.mkv"),
            ),
        )
        assertEquals(mapOf(2L to 1L, 3L to 1L), matches)
    }

    @Test
    fun plexSuffixSharesTheMoviesBaseName() {
        val matches = ExtraFiles.match(
            listOf(
                movie(1, "Movies", "Dune (2021).mkv"),
                movie(2, "Movies", "Arrival (2016).mkv"),
                file(3, "Movies", "Arrival (2016)-trailer.mp4"),
                file(4, "Movies", "Dune (2021)-behindthescenes.mkv"),
            ),
        )
        assertEquals(mapOf(3L to 2L, 4L to 1L), matches)
    }

    @Test
    fun bareTrailerNeedsAnUnambiguousMovie() {
        assertEquals(
            mapOf(2L to 1L),
            ExtraFiles.match(listOf(movie(1, "Dune", "Dune.mkv"), file(2, "Dune", "trailer.mkv"))),
        )
        assertTrue(
            ExtraFiles.match(listOf(movie(1, "M", "A.mkv"), movie(3, "M", "B.mkv"), file(2, "M", "trailer.mkv"))).isEmpty(),
        )
    }

    @Test
    fun unmatchedExtrasStayOut() {
        // Several movies share the folder above Extras, another source, and a suffix without its movie.
        val matches = ExtraFiles.match(
            listOf(
                movie(1, "Movies", "A.mkv"),
                movie(2, "Movies", "B.mkv"),
                file(3, "Movies/Extras", "Clip.mkv"),
                movie(4, "Other", "C.mkv", source = 2),
                file(5, "Other/Extras", "Clip2.mkv", source = 1),
                file(6, "Movies", "Zed-trailer.mkv"),
            ),
        )
        assertTrue(matches.isEmpty())
    }

    @Test
    fun ordinaryMoviesAreNotExtras() {
        val matches = ExtraFiles.match(listOf(movie(1, "Movies", "Short Term 12.mkv"), movie(2, "Movies", "Scene Stealer.mkv")))
        assertTrue(matches.isEmpty())
        assertNull(ExtraFiles.suffixedMovieName("Short Term 12"))
        assertEquals("Dune (2021)", ExtraFiles.suffixedMovieName("Dune (2021)-Trailer"))
    }

    @Test
    fun folderMatchIsCaseInsensitive() {
        assertEquals(
            mapOf(2L to 1L),
            ExtraFiles.match(listOf(movie(1, "movies/dune", "Dune.mkv"), file(2, "Movies/Dune/EXTRAS", "x.mkv"))),
        )
    }
}
