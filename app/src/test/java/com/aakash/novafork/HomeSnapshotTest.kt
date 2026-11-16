package com.aakash.novafork

import com.aakash.novafork.ui.HomeSnapshot
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.HeroTarget
import com.aakash.novafork.ui.model.HeroUi
import com.aakash.novafork.ui.model.HomeUi
import com.aakash.novafork.ui.model.LibraryCounts
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.ShowCardUi
import com.aakash.novafork.ui.model.VideoCardUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSnapshotTest {
    private val home = HomeUi(
        hero = listOf(
            HeroUi(
                target = HeroTarget.Video(7),
                title = "Dune",
                overview = "Sand.",
                artwork = Artwork(posterUrl = "https://x/p.jpg", backdropUrl = "https://x/b.jpg", seed = "Dune", frameAtMs = 5_000),
                metaLine = "2021 · 2h 35m",
                rating = 8.1f,
                genres = listOf("Science Fiction", "Adventure"),
                progress = 0.4f,
                playVideoId = 7,
                playLabel = "Resume · 1h 30m left",
            ),
            HeroUi(target = HeroTarget.Show("tv:severance"), title = "Severance"),
        ),
        continueWatching = listOf(
            VideoCardUi(id = 7, title = "Dune", subtitle = "2021", kind = MediaKind.MOVIE, progress = 0.4f, durationMs = 9_300_000, newEpisode = true),
        ),
        movies = listOf(VideoCardUi(id = 9, title = "Heat", year = 1995, rating = 8.3f, sourceId = 2)),
        shows = listOf(ShowCardUi(key = "tv:severance", title = "Severance", seasonCount = 2, episodeCount = 19, unwatchedCount = 9)),
        isEmpty = false,
        libraryCounts = LibraryCounts(favorites = 3, all = 42),
    )

    @Test
    fun aHomePageSurvivesTheRoundTrip() {
        val back = requireNotNull(HomeSnapshot.decode(HomeSnapshot.encode(home)))
        assertEquals(home.hero, back.hero)
        assertEquals(home.continueWatching, back.continueWatching)
        assertEquals(home.movies, back.movies)
        assertEquals(home.shows, back.shows)
        assertEquals(home.libraryCounts, back.libraryCounts)
        assertTrue(!back.isEmpty)
    }

    @Test
    fun garbageOrAnOtherVersionIsIgnored() {
        assertNull(HomeSnapshot.decode("not json"))
        assertNull(HomeSnapshot.decode("{\"v\":99}"))
        assertNull(HomeSnapshot.decode(HomeSnapshot.encode(HomeUi())))
    }
}
