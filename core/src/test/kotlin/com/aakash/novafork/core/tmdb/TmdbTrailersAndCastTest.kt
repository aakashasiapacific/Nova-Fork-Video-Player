package com.aakash.novafork.core.tmdb

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbTrailersAndCastTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun video(
        key: String,
        type: String = "Trailer",
        site: String = "YouTube",
        official: Boolean = true,
        language: String? = "en",
        published: String? = "2024-01-01T00:00:00.000Z",
    ) = TmdbVideo(key = key, site = site, type = type, official = official, language = language, publishedAt = published)

    @Test
    fun onlyYouTubeTrailersCount() {
        val videos = listOf(
            video("teaser", type = "Teaser"),
            video("vimeo", site = "Vimeo"),
            video("clip", type = "Clip"),
            video("", type = "Trailer"),
        )
        assertNull(Trailers.pick(videos, listOf("en")))
        assertNull(Trailers.pick(emptyList(), listOf("en")))
    }

    @Test
    fun officialBeatsUnofficialEvenWhenOlder() {
        val videos = listOf(
            video("fan", official = false, published = "2025-01-01T00:00:00.000Z"),
            video("studio", official = true, published = "2023-01-01T00:00:00.000Z"),
        )
        assertEquals("studio", Trailers.pick(videos, listOf("en")))
    }

    @Test
    fun newestOfficialTrailerWins() {
        val videos = listOf(
            video("old", published = "2023-05-01T00:00:00.000Z"),
            video("new", published = "2024-05-01T00:00:00.000Z"),
            video("mid", published = "2023-12-01T00:00:00.000Z"),
        )
        assertEquals("new", Trailers.pick(videos, listOf("en")))
    }

    @Test
    fun deviceLanguageThenEnglishThenAnyOther() {
        val videos = listOf(
            video("fr", language = "fr"),
            video("en", language = "en"),
            video("de", language = "de"),
            video("none", language = null),
        )
        assertEquals("de", Trailers.pick(videos, listOf("de", "en")))
        assertEquals("en", Trailers.pick(videos.filter { it.key != "de" }, listOf("de", "en")))
        assertEquals("fr", Trailers.pick(videos.filter { it.key == "fr" || it.key == "none" }, listOf("de", "en")))
        // A regional device language ("pt-BR") matches its base language.
        assertEquals("pt", Trailers.pick(listOf(video("en"), video("pt", language = "pt")), listOf("pt-BR", "en")))
    }

    @Test
    fun videosDecodeFromTmdbJson() {
        val videos = json.decodeFromString(
            TmdbVideos.serializer(),
            """{"id": 603, "results": [
              {"iso_639_1": "en", "iso_3166_1": "US", "name": "Official Trailer", "key": "abc123", "site": "YouTube", "size": 1080,
               "type": "Trailer", "official": true, "published_at": "2021-09-09T15:00:11.000Z", "id": "x"},
              {"name": "Behind the scenes", "key": "zzz", "site": "YouTube", "type": "Featurette"}
            ]}""",
        )
        assertEquals(2, videos.results.size)
        assertEquals("abc123", videos.trailerKey(listOf("en")))
    }

    @Test
    fun movieCastKeepsBillingOrderAndPhotos() {
        val credits = json.decodeFromString(
            TmdbCastCredits.serializer(),
            """{"id": 603, "cast": [
              {"name": "Carrie-Anne Moss", "character": "Trinity", "profile_path": "/c.jpg", "order": 1},
              {"name": "Keanu Reeves", "character": "Neo", "profile_path": "/k.jpg", "order": 0},
              {"name": "", "character": "Nobody", "order": 2},
              {"name": "Extra Person", "character": "", "profile_path": null, "order": 3}
            ], "crew": []}""",
        )
        val cast = credits.topCast()
        assertEquals(listOf("Keanu Reeves", "Carrie-Anne Moss", "Extra Person"), cast.map { it.name })
        assertEquals(CastMember("Keanu Reeves", "Neo", "/k.jpg"), cast.first())
        assertNull(cast.last().character)
        assertNull(cast.last().profilePath)
    }

    @Test
    fun showCastUsesTheRoleWithMostEpisodes() {
        val credits = json.decodeFromString(
            TmdbCastCredits.serializer(),
            """{"cast": [
              {"name": "Bryan Cranston", "profile_path": "/b.jpg", "order": 0, "total_episode_count": 62,
               "roles": [{"character": "Walter White", "episode_count": 62}, {"character": "Cameo", "episode_count": 1}]}
            ]}""",
        )
        assertEquals("Walter White", credits.topCast().single().character)
    }

    @Test
    fun castIsLimitedAndHasNoRepeats() {
        val entries = (0 until 30).map { TmdbCastEntry(name = "Actor ${it % 20}", character = "C$it", order = it) }
        val cast = TmdbCastCredits(entries).topCast()
        assertEquals(15, cast.size)
        assertEquals(cast.map { it.name }.distinct(), cast.map { it.name })
        assertEquals(3, TmdbCastCredits(entries).topCast(limit = 3).size)
    }
}
