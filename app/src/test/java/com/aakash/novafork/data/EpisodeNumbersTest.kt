package com.aakash.novafork.data

import com.aakash.novafork.core.tmdb.TmdbSeasonSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/** [resolveEpisodeNumbers]: absolute anime numbering placed into a show's TMDB seasons. */
class EpisodeNumbersTest {
    private val seasons = listOf(
        // Out of order on purpose; specials (season 0) and empty seasons never count.
        TmdbSeasonSummary(seasonNumber = 2, episodeCount = 10),
        TmdbSeasonSummary(seasonNumber = 0, episodeCount = 4),
        TmdbSeasonSummary(seasonNumber = 1, episodeCount = 28),
        TmdbSeasonSummary(seasonNumber = 3, episodeCount = 0),
    )

    @Test
    fun absoluteNumbersFallIntoSeasonsByEpisodeCount() {
        assertEquals(1 to 1, resolveEpisodeNumbers(null, 1, seasons))
        assertEquals(1 to 28, resolveEpisodeNumbers(null, 28, seasons))
        assertEquals(2 to 1, resolveEpisodeNumbers(null, 29, seasons))
        assertEquals(2 to 2, resolveEpisodeNumbers(null, 30, seasons))
        assertEquals(2 to 10, resolveEpisodeNumbers(null, 38, seasons))
    }

    @Test
    fun numbersPastTheListedEpisodesStayAbsolute() {
        assertEquals(null to 39, resolveEpisodeNumbers(null, 39, seasons))
        assertEquals(null to 12, resolveEpisodeNumbers(null, 12, emptyList()))
    }

    @Test
    fun explicitSeasonsAndMissingNumbersAreLeftAlone() {
        assertEquals(1 to 30, resolveEpisodeNumbers(1, 30, seasons))
        assertEquals(3 to 2, resolveEpisodeNumbers(3, 2, seasons))
        assertEquals(null to null, resolveEpisodeNumbers(null, null, seasons))
        assertEquals(null to 0, resolveEpisodeNumbers(null, 0, seasons))
    }
}
