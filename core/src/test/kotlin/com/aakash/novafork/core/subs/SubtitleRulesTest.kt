package com.aakash.novafork.core.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleRulesTest {

    @Test
    fun ordersExactThenPreferredLanguageThenAlphabetical() {
        val siblings = listOf(
            "Movie.mkv", "Movie.fr.srt", "Movie.de.ass", "Movie.eng.forced.srt", "Movie.English.SDH.srt",
            "Movie.en.srt", "Movie.srt", "Other.srt", "Movie.nfo",
        )
        assertEquals(
            listOf("Movie.srt", "Movie.en.srt", "Movie.English.SDH.srt", "Movie.eng.forced.srt", "Movie.de.ass", "Movie.fr.srt"),
            SubtitleRules.match("Movie.mkv", siblings),
        )
    }

    @Test
    fun followsThePreferredLanguageOrder() {
        val siblings = listOf("Movie.mkv", "Movie.en.srt", "Movie.fr.srt", "Movie.es.srt")
        assertEquals(
            listOf("Movie.fr.srt", "Movie.en.srt", "Movie.es.srt"),
            SubtitleRules.match("Movie.mkv", siblings, preferredLanguages = listOf("fr", "en")),
        )
    }

    @Test
    fun acceptsIso6392AndLanguageNames() {
        val siblings = listOf("Movie.mkv", "Movie.de.srt", "Movie.fre.srt", "Movie.Spanish.srt", "Movie.ger.srt")
        assertEquals(
            listOf("Movie.fre.srt", "Movie.Spanish.srt", "Movie.de.srt", "Movie.ger.srt"),
            SubtitleRules.match("Movie.mkv", siblings, preferredLanguages = listOf("fra", "spa")),
        )
    }

    @Test
    fun understandsRegionTags() {
        val siblings = listOf("Movie.en.srt", "Movie.pt-BR.srt")
        assertEquals(listOf("Movie.pt-BR.srt", "Movie.en.srt"), SubtitleRules.match("Movie.mkv", siblings, listOf("pt")))
    }

    @Test
    fun isCaseInsensitive() {
        assertEquals(listOf("MOVIE.EN.SRT"), SubtitleRules.match("movie.mkv", listOf("MOVIE.EN.SRT")))
        assertEquals(listOf("the.office.s01e01.srt"), SubtitleRules.match("The.Office.S01E01.mkv", listOf("the.office.s01e01.srt")))
    }

    @Test
    fun neverReturnsSubtitlesOfAnotherVideo() {
        val siblings = listOf("Movie.mkv", "Movie.Part.2.mkv", "Movie.Part.2.srt", "Movie.Part.2.en.srt", "Movie.en.srt")
        assertEquals(listOf("Movie.en.srt"), SubtitleRules.match("Movie.mkv", siblings))
        assertEquals(listOf("Movie.Part.2.srt", "Movie.Part.2.en.srt"), SubtitleRules.match("Movie.Part.2.mkv", siblings))
    }

    @Test
    fun ignoresSuffixesThatAreNotLanguagesOrFlags() {
        val siblings = listOf("Show.S01E01.srt", "Show.S01E01.extended.cut.srt", "Show.S01E011.srt", "Show.S01E01x.srt")
        assertEquals(listOf("Show.S01E01.srt"), SubtitleRules.match("Show.S01E01.mkv", siblings))
    }

    @Test
    fun acceptsNumberedTracks() {
        val siblings = listOf("Movie.en.2.srt", "Movie.en.srt", "Movie.2.srt")
        assertEquals(listOf("Movie.en.2.srt", "Movie.en.srt", "Movie.2.srt"), SubtitleRules.match("Movie.mkv", siblings))
    }

    @Test
    fun vobSubIdxReplacesItsSubSibling() {
        assertEquals(listOf("Movie.idx", "Movie.srt"), SubtitleRules.match("Movie.mkv", listOf("Movie.sub", "Movie.idx", "Movie.srt")))
        assertEquals(listOf("Movie.en.idx"), SubtitleRules.match("Movie.mkv", listOf("Movie.en.idx", "Movie.en.sub")))
        // A text .sub (MicroDVD) has no .idx and stays; a lone .idx has nothing to show.
        assertEquals(listOf("Movie.sub"), SubtitleRules.match("Movie.mkv", listOf("Movie.sub")))
        assertEquals(emptyList<String>(), SubtitleRules.match("Movie.mkv", listOf("Movie.idx")))
    }

    @Test
    fun ignoresNonSubtitlesAndDuplicates() {
        val siblings = listOf("Movie.mkv", "Movie.jpg", "Movie.nfo", "Movie.srt", "Movie.srt", "Movie.vtt", "Movie.smi", "Movie.ssa")
        assertEquals(listOf("Movie.smi", "Movie.srt", "Movie.ssa", "Movie.vtt"), SubtitleRules.match("Movie.mkv", siblings))
    }

    @Test
    fun noSiblingsNoSubtitles() {
        assertEquals(emptyList<String>(), SubtitleRules.match("Movie.mkv", emptyList()))
        assertEquals(emptyList<String>(), SubtitleRules.match("Movie.mkv", listOf("Movies.srt", "Movie2.srt", "AMovie.srt")))
    }

    @Test
    fun isSubtitle() {
        assertTrue(SubtitleRules.isSubtitle("a.SRT"))
        assertTrue(SubtitleRules.isSubtitle("a.ass"))
        assertFalse(SubtitleRules.isSubtitle("a.txt"))
        assertFalse(SubtitleRules.isSubtitle("srt"))
    }
}
