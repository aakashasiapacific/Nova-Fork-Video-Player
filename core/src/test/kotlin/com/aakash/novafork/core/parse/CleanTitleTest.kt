package com.aakash.novafork.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CleanTitleTest {

    @Test
    fun replacesDotsAndUnderscores() {
        assertEquals("Some Movie Name", NameParser.cleanTitle("Some_Movie_Name"))
        assertEquals("Movie Name", NameParser.cleanTitle("Movie.Name"))
        assertEquals("Movie Name", NameParser.cleanTitle("  Movie   Name  "))
    }

    @Test
    fun dropsReleaseJunkAndDecorationYears() {
        assertEquals("Inception", NameParser.cleanTitle("Inception.2010.1080p.BluRay.x264-SPARKS"))
        assertEquals("The Matrix", NameParser.cleanTitle("The.Matrix.(1999)"))
        assertEquals("Movie", NameParser.cleanTitle("[YTS] Movie (2019) [1080p]"))
        assertEquals("Movie Name", NameParser.cleanTitle("Movie.Name.mkv"))
    }

    @Test
    fun keepsYearsThatArePartOfTheTitle() {
        assertEquals("Wonder Woman 1984", NameParser.cleanTitle("Wonder Woman 1984"))
        assertEquals("Blade Runner 2049", NameParser.cleanTitle("Blade.Runner.2049"))
        assertEquals("1917", NameParser.cleanTitle("1917"))
    }

    @Test
    fun keepsAbbreviationsAndCapitalisation() {
        assertEquals("Mr. Robot", NameParser.cleanTitle("Mr.Robot"))
        assertEquals("Kill Bill Vol. 2", NameParser.cleanTitle("Kill.Bill.Vol.2"))
        assertEquals("S.H.I.E.L.D.", NameParser.cleanTitle("S.H.I.E.L.D."))
        assertEquals("the office", NameParser.cleanTitle("the.office"))
        assertEquals("Spider-Man", NameParser.cleanTitle("Spider-Man"))
    }

    @Test
    fun stripsSceneGroupSuffix() {
        assertEquals("Some Movie", NameParser.cleanTitle("Some.Movie-GROUP"))
    }

    @Test
    fun fallsBackToTheInputWhenNothingIsLeft() {
        assertEquals("[1080p]", NameParser.cleanTitle("[1080p]"))
    }

    @Test
    fun resolutionTokens() {
        assertEquals("2160p", ReleaseTokens.resolution("Movie.2019.UHD.BluRay"))
        assertEquals("2160p", ReleaseTokens.resolution("Movie 4K HDR"))
        assertEquals("1080p", ReleaseTokens.resolution("Movie.1080i.HDTV"))
        assertEquals("1080p", ReleaseTokens.resolution("Movie 1920x1080"))
        assertEquals("576p", ReleaseTokens.resolution("Movie.576p.DVDRip"))
        assertNull(ReleaseTokens.resolution("Movie.2019.x264"))
        assertNull(ReleaseTokens.resolution("4Kids Show"))
    }
}
