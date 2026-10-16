package com.aakash.novafork.core.tmdb

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbImagesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun detailsCarryTheTextlessPosterAndCredits() {
        val movie = json.decodeFromString(
            TmdbMovieDetails.serializer(),
            """
            {"id": 603, "title": "The Matrix",
             "images": {"posters": [
                {"file_path": "/en.jpg", "iso_639_1": "en", "vote_average": 9.0},
                {"file_path": "/plain-low.jpg", "iso_639_1": null, "vote_average": 4.0},
                {"file_path": "/plain-best.jpg", "iso_639_1": null, "vote_average": 6.5}
             ], "backdrops": []},
             "credits": {
                "cast": [{"name": "Keanu Reeves"}, {"name": "Carrie-Anne Moss"}, {"name": ""}],
                "crew": [{"name": "Lana Wachowski", "job": "Director"}, {"name": "Bill Pope", "job": "Director of Photography"}]
             }}
            """.trimIndent(),
        )
        assertEquals("/plain-best.jpg", movie.images.textlessPoster)
        assertEquals(listOf("Keanu Reeves", "Carrie-Anne Moss"), movie.credits.topCast())
        assertEquals(listOf("Lana Wachowski"), movie.credits.directors)
    }

    @Test
    fun aResponseWithoutImagesHasNoTextlessPoster() {
        val movie = json.decodeFromString(TmdbMovieDetails.serializer(), """{"id": 1, "title": "Old"}""")
        assertNull(movie.images.textlessPoster)
        assertEquals(emptyList<String>(), movie.credits.topCast())
    }
}
