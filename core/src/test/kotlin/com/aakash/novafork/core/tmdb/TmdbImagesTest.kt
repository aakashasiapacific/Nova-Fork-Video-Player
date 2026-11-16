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

    @Test
    fun theLogoIsInTheAppLanguageThenEnglishThenWithoutText() {
        fun logo(vararg entries: String, language: String = "hi"): String? = json.decodeFromString(
            TmdbImages.serializer(),
            """{"logos": [${entries.joinToString(",")}]}""",
        ).logo(language)
        val hindi = """{"file_path": "/hi.png", "iso_639_1": "hi", "vote_average": 3.0}"""
        val english = """{"file_path": "/en.png", "iso_639_1": "en", "vote_average": 5.0}"""
        val englishBest = """{"file_path": "/en-best.png", "iso_639_1": "en", "vote_average": 8.0}"""
        val englishSvg = """{"file_path": "/en.svg", "iso_639_1": "en", "vote_average": 9.9}"""
        val plain = """{"file_path": "/plain.png", "iso_639_1": null, "vote_average": 9.0}"""
        assertEquals("/hi.png", logo(hindi, english, plain))
        assertEquals("/en-best.png", logo(english, englishBest, englishSvg, plain))
        assertEquals("/plain.png", logo(plain, englishSvg))
        assertNull(logo(englishSvg))
        assertNull(logo())
    }
}
