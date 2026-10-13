package com.aakash.novafork.core.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class TmdbLanguagesTest {
    private fun tag(languageTag: String): String? = TmdbLanguages.forLocale(Locale.forLanguageTag(languageTag))

    @Test
    fun deviceTagIsKeptWhenTmdbHasIt() {
        assertEquals("en-US", tag("en-US"))
        assertEquals("en-GB", tag("en-GB"))
        assertEquals("hi-IN", tag("hi-IN"))
        assertEquals("ta-IN", tag("ta-IN"))
        assertEquals("te-IN", tag("te-IN"))
        assertEquals("de-AT", tag("de-AT"))
        assertEquals("fr-CA", tag("fr-CA"))
        assertEquals("pt-PT", tag("pt-PT"))
    }

    @Test
    fun indianEnglishStaysIndianEnglish() {
        assertEquals("en-IN", tag("en-IN"))
    }

    @Test
    fun otherCountriesUseTheLanguagesTag() {
        assertEquals("en-US", tag("en-DE"))
        assertEquals("fr-FR", tag("fr-BE"))
        assertEquals("ja-JP", tag("ja"))
        assertEquals("pt-BR", tag("pt"))
        assertEquals("es-ES", tag("es-AR"))
        assertEquals("es-MX", tag("es-419"))
        assertEquals("hi-IN", tag("hi"))
    }

    @Test
    fun chineseFollowsScriptAndRegion() {
        assertEquals("zh-CN", tag("zh-Hans-CN"))
        assertEquals("zh-CN", tag("zh"))
        assertEquals("zh-TW", tag("zh-Hant-TW"))
        assertEquals("zh-TW", tag("zh-TW"))
        assertEquals("zh-TW", tag("zh-Hant"))
        assertEquals("zh-HK", tag("zh-Hant-HK"))
        assertEquals("zh-HK", tag("zh-HK"))
        assertEquals("zh-SG", tag("zh-Hans-SG"))
    }

    @Test
    fun retiredAndAlternativeCodesAreUnderstood() {
        assertEquals("id-ID", TmdbLanguages.forLocale(Locale("in", "ID")))
        assertEquals("he-IL", TmdbLanguages.forLocale(Locale("iw", "IL")))
        assertEquals("tl-PH", tag("fil-PH"))
        assertEquals("nb-NO", tag("nb-NO"))
        assertEquals("no-NO", tag("nn-NO"))
    }

    @Test
    fun unknownLanguagesGiveNull() {
        assertNull(tag("gu-IN"))
        assertNull(tag("xx"))
        assertNull(TmdbLanguages.forLocale(Locale.ROOT))
    }

    @Test
    fun firstLanguageTmdbHasWins() {
        val gujarati = Locale.forLanguageTag("gu-IN")
        val hindi = Locale.forLanguageTag("hi-IN")
        val english = Locale.forLanguageTag("en-IN")
        assertEquals("hi-IN", TmdbLanguages.forLocales(listOf(gujarati, hindi, english)))
        assertEquals("en-IN", TmdbLanguages.forLocales(listOf(english, hindi)))
    }

    @Test
    fun noKnownLanguageFallsBackToUsEnglish() {
        assertEquals("en-US", TmdbLanguages.forLocales(listOf(Locale.forLanguageTag("gu-IN"))))
        assertEquals("en-US", TmdbLanguages.forLocales(emptyList()))
        assertEquals(TmdbLanguages.DEFAULT, TmdbLanguages.forLocales(listOf(Locale.ROOT)))
    }
}
