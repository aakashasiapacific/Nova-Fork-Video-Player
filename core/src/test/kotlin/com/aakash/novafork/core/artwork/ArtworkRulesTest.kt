package com.aakash.novafork.core.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkRulesTest {

    @Test
    fun movieArtPrefersFilesNamedAfterTheVideo() {
        val siblings = listOf(
            "Movie.mkv", "Movie-poster.jpg", "poster.png", "fanart.jpg", "Movie-fanart.JPG", "Movie-thumb.jpg", "notes.txt",
        )
        assertEquals(
            LocalArtPick(poster = "Movie-poster.jpg", backdrop = "Movie-fanart.JPG", thumb = "Movie-thumb.jpg"),
            ArtworkRules.pick("Movie.mkv", siblings, isEpisode = false),
        )
    }

    @Test
    fun posterFallsBackInPriorityOrder() {
        fun poster(vararg names: String) = ArtworkRules.pick("Film.mkv", names.toList(), isEpisode = false).poster
        assertEquals("Film.poster.jpg", poster("Film.poster.jpg", "poster.jpg"))
        assertEquals("poster.jpg", poster("default.jpg", "cover.jpg", "folder.jpg", "poster.jpg"))
        assertEquals("folder.jpg", poster("default.jpg", "movie.jpg", "cover.jpg", "folder.jpg"))
        assertEquals("cover.png", poster("default.jpg", "movie.jpg", "cover.png"))
        assertEquals("movie.webp", poster("default.jpg", "movie.webp"))
        assertEquals("default.jpeg", poster("default.jpeg"))
        assertNull(poster("banner.jpg", "logo.png"))
    }

    @Test
    fun backdropFallsBackInPriorityOrder() {
        fun backdrop(vararg names: String) = ArtworkRules.pick("Film.mkv", names.toList(), isEpisode = false).backdrop
        assertEquals("Film-backdrop.jpg", backdrop("Film-backdrop.jpg", "Film-background.jpg", "fanart.jpg"))
        assertEquals("Film-background.jpg", backdrop("Film-background.jpg", "fanart.jpg"))
        assertEquals("fanart.jpg", backdrop("art.jpg", "background.jpg", "backdrop.jpg", "fanart.jpg"))
        assertEquals("backdrop.jpg", backdrop("art.jpg", "background.jpg", "backdrop.jpg"))
        assertEquals("background.jpg", backdrop("art.jpg", "background.jpg"))
        assertEquals("art.png", backdrop("art.png"))
    }

    @Test
    fun thumbIsTheImageWithTheVideoBaseName() {
        assertEquals("Film.jpg", ArtworkRules.pick("Film.mkv", listOf("Film.jpg"), isEpisode = false).thumb)
        assertEquals("Film-thumb.png", ArtworkRules.pick("Film.mkv", listOf("Film.jpg", "Film-thumb.png"), isEpisode = false).thumb)
    }

    @Test
    fun matchingIsCaseInsensitiveAndKeepsOriginalNames() {
        val pick = ArtworkRules.pick("the.movie.MKV", listOf("POSTER.JPG", "The.Movie-FanArt.PNG"), isEpisode = false)
        assertEquals("POSTER.JPG", pick.poster)
        assertEquals("The.Movie-FanArt.PNG", pick.backdrop)
    }

    @Test
    fun onlyKnownImageTypesCount() {
        val pick = ArtworkRules.pick("Film.mkv", listOf("poster.gif", "poster.txt", "fanart.bmp", "Film.nfo"), isEpisode = false)
        assertEquals(LocalArtPick(), pick)
    }

    @Test
    fun jpgWinsOverOtherExtensionsWithTheSameName() {
        assertEquals("poster.jpg", ArtworkRules.pick("Film.mkv", listOf("poster.webp", "poster.png", "poster.jpg"), false).poster)
    }

    @Test
    fun episodesUseThumbsAndSeasonPostersOnly() {
        val siblings = listOf(
            "Show.S01E01.mkv", "Show.S01E01-thumb.jpg", "poster.jpg", "fanart.jpg", "season01-poster.jpg", "season02-poster.jpg",
        )
        assertEquals(
            LocalArtPick(poster = "season01-poster.jpg", thumb = "Show.S01E01-thumb.jpg"),
            ArtworkRules.pick("Show.S01E01.mkv", siblings, isEpisode = true, season = 1),
        )
    }

    @Test
    fun seasonPosterNamingVariants() {
        fun poster(season: Int?, vararg names: String) = ArtworkRules.pick("E01.mkv", names.toList(), isEpisode = true, season = season).poster
        assertEquals("season2-poster.png", poster(2, "season2-poster.png"))
        assertEquals("Season12-Poster.jpg", poster(12, "Season12-Poster.jpg", "season-all-poster.jpg"))
        assertEquals("season-specials-poster.jpg", poster(0, "season-specials-poster.jpg", "season00-poster.jpg"))
        assertEquals("season-all-poster.jpg", poster(3, "season01-poster.jpg", "season-all-poster.jpg"))
        assertEquals("season-all-poster.jpg", poster(null, "season-all-poster.jpg"))
        assertNull(poster(1, "poster.jpg", "folder.jpg"))
    }

    @Test
    fun showArtComesFromTheShowFolder() {
        assertEquals(
            LocalArtPick(poster = "poster.jpg", backdrop = "fanart.jpg"),
            ArtworkRules.pickShowArt(listOf("Season 1", "poster.jpg", "fanart.jpg", "banner.jpg", "tvshow.nfo")),
        )
        assertEquals(
            LocalArtPick(poster = "folder.png", backdrop = "backdrop.webp"),
            ArtworkRules.pickShowArt(listOf("folder.png", "cover.jpg", "background.jpg", "backdrop.webp")),
        )
        assertEquals(LocalArtPick(), ArtworkRules.pickShowArt(emptyList()))
    }

    @Test
    fun isImage() {
        assertTrue(ArtworkRules.isImage("a.JPG"))
        assertTrue(ArtworkRules.isImage("a.jpeg"))
        assertTrue(ArtworkRules.isImage("a.webp"))
        assertFalse(ArtworkRules.isImage("a.gif"))
        assertFalse(ArtworkRules.isImage("jpg"))
    }
}
