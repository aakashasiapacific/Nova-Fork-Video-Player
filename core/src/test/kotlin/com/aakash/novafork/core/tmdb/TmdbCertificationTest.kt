package com.aakash.novafork.core.tmdb

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class TmdbCertificationTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val releases = json.decodeFromString(
        TmdbReleaseDates.serializer(),
        """
        {"id": 603, "results": [
          {"iso_3166_1": "US", "release_dates": [
            {"certification": "", "type": 1, "release_date": "1999-03-24"},
            {"certification": "R", "type": 3, "release_date": "1999-03-31"},
            {"certification": "NR", "type": 5}
          ]},
          {"iso_3166_1": "GB", "release_dates": [{"certification": "15", "type": 4}]},
          {"iso_3166_1": "IN", "release_dates": [{"certification": "", "type": 3}]}
        ]}
        """.trimIndent(),
    )

    @Test
    fun movieUsesTheDeviceCountryThenTheUs() {
        assertEquals("15", releases.certification("GB"))
        assertEquals("R", releases.certification("US"))
        // India lists none, Japan is not listed: the US one.
        assertEquals("R", releases.certification("IN"))
        assertEquals("R", releases.certification("JP"))
        assertEquals("15", releases.certification("gb"))
    }

    @Test
    fun movieWithoutAnyCertificationIsNull() {
        assertNull(TmdbReleaseDates().certification("US"))
        assertNull(json.decodeFromString(TmdbReleaseDates.serializer(), """{"results": [{"iso_3166_1": "US", "release_dates": []}]}""").certification("US"))
    }

    @Test
    fun theatricalReleaseWinsInsideACountry() {
        val dates = json.decodeFromString(
            TmdbReleaseDates.serializer(),
            """{"results": [{"iso_3166_1": "US", "release_dates": [{"certification": "PG", "type": 4}, {"certification": "PG-13", "type": 3}]}]}""",
        )
        assertEquals("PG-13", dates.certification("US"))
    }

    @Test
    fun showUsesTheDeviceCountryThenTheUs() {
        val ratings = json.decodeFromString(
            TmdbContentRatings.serializer(),
            """{"results": [{"iso_3166_1": "US", "rating": "TV-MA"}, {"iso_3166_1": "DE", "rating": "16"}, {"iso_3166_1": "FR", "rating": ""}]}""",
        )
        assertEquals("16", ratings.certification("DE"))
        assertEquals("TV-MA", ratings.certification("FR"))
        assertEquals("TV-MA", ratings.certification("US"))
        assertNull(TmdbContentRatings().certification("US"))
    }

    @Test
    fun nextEpisodeDecodesWhenPresentOrNull() {
        val with = json.decodeFromString(
            TmdbTvNextEpisode.serializer(),
            """{"id": 1, "name": "X", "next_episode_to_air": {"air_date": "2025-11-07", "season_number": 2, "episode_number": 4}}""",
        )
        assertEquals("2025-11-07", with.nextEpisodeToAir?.airDate)
        assertEquals(4, with.nextEpisodeToAir?.episodeNumber)
        val without = json.decodeFromString(TmdbTvNextEpisode.serializer(), """{"id": 1, "next_episode_to_air": null}""")
        assertNull(without.nextEpisodeToAir)
    }

    @Test
    fun nextEpisodeLabelOmitsTheYearThisYear() {
        val today = LocalDate.of(2025, 11, 3)
        assertEquals("New episode Fri, 7 Nov", NextEpisodeLabel.of("2025-11-07", today, Locale.ENGLISH))
        assertEquals("New episode Mon, 3 Nov", NextEpisodeLabel.of("2025-11-03", today, Locale.ENGLISH))
        assertEquals("New episode Thu, 1 Jan 2026", NextEpisodeLabel.of("2026-01-01", today, Locale.ENGLISH))
    }

    @Test
    fun nextEpisodeLabelIsNullWhenPastMissingOrUnreadable() {
        val today = LocalDate.of(2025, 11, 3)
        assertNull(NextEpisodeLabel.of("2025-11-02", today, Locale.ENGLISH))
        assertNull(NextEpisodeLabel.of(null, today, Locale.ENGLISH))
        assertNull(NextEpisodeLabel.of("", today, Locale.ENGLISH))
        assertNull(NextEpisodeLabel.of("soon", today, Locale.ENGLISH))
        assertNull(NextEpisodeLabel.of("2025-13-45", today, Locale.ENGLISH))
    }

    @Test
    fun nextEpisodeLabelUsesTheLocalePattern() {
        val today = LocalDate.of(2025, 11, 3)
        // The skeleton asked for is passed on, and its pattern used: day before month here.
        val asked = mutableListOf<String>()
        val label = NextEpisodeLabel.of("2025-11-07", today, Locale.ENGLISH) { skeleton ->
            asked += skeleton
            "d/M"
        }
        assertEquals("New episode 7/11", label)
        assertEquals(listOf(NextEpisodeLabel.SKELETON), asked)
    }
}
