package com.aakash.novafork.core.opensubtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class OpenSubtitlesLanguagesTest {
    @Test
    fun codesAndNamesBecomeIso6391() {
        assertEquals(listOf("en"), OpenSubtitlesLanguages.codesFor("eng"))
        assertEquals(listOf("en"), OpenSubtitlesLanguages.codesFor("English"))
        assertEquals(listOf("en"), OpenSubtitlesLanguages.codesFor("EN"))
        assertEquals(listOf("fr"), OpenSubtitlesLanguages.codesFor("fre"))
        assertEquals(listOf("de"), OpenSubtitlesLanguages.codesFor("ger"))
        assertEquals(listOf("hi"), OpenSubtitlesLanguages.codesFor("hin"))
        assertEquals(listOf("he"), OpenSubtitlesLanguages.codesFor("iw"))
        assertEquals(listOf("no"), OpenSubtitlesLanguages.codesFor("nb"))
        assertEquals(listOf("en"), OpenSubtitlesLanguages.codesFor("en-US"))
        assertEquals(emptyList<String>(), OpenSubtitlesLanguages.codesFor(""))
        assertEquals(emptyList<String>(), OpenSubtitlesLanguages.codesFor("klingonish"))
    }

    @Test
    fun portugueseAndChineseKeepTheirRegion() {
        assertEquals(listOf("pt-pt", "pt-br"), OpenSubtitlesLanguages.codesFor("por"))
        assertEquals(listOf("pt-br"), OpenSubtitlesLanguages.codesFor("pt-BR"))
        assertEquals(listOf("pt-br"), OpenSubtitlesLanguages.codesFor("pob"))
        assertEquals(listOf("pt-pt"), OpenSubtitlesLanguages.codesFor("pt_PT"))
        assertEquals(listOf("zh-cn", "zh-tw"), OpenSubtitlesLanguages.codesFor("chi"))
        assertEquals(listOf("zh-tw"), OpenSubtitlesLanguages.codesFor("zh-HK"))
        assertEquals(listOf("zh-cn"), OpenSubtitlesLanguages.codesFor("zh-Hans"))
    }

    @Test
    fun settingIsParsedInOrderWithoutRepeats() {
        assertEquals(listOf("en", "fr", "pt-br"), OpenSubtitlesLanguages.parse(" en, fre ;eng pt-br,, "))
        assertEquals(emptyList<String>(), OpenSubtitlesLanguages.parse(""))
    }

    @Test
    fun deviceLocales() {
        assertEquals(listOf("pt-br"), OpenSubtitlesLanguages.forLocale(Locale.forLanguageTag("pt-BR")))
        assertEquals(listOf("zh-tw"), OpenSubtitlesLanguages.forLocale(Locale.forLanguageTag("zh-Hant-TW")))
        assertEquals(listOf("zh-cn"), OpenSubtitlesLanguages.forLocale(Locale.forLanguageTag("zh-CN")))
        assertEquals(listOf("hi"), OpenSubtitlesLanguages.forLocale(Locale.forLanguageTag("hi-IN")))
    }

    @Test
    fun queryValueIsSortedLowerCaseAndCommaSeparated() {
        assertEquals("en,fr,pt-br", OpenSubtitlesLanguages.queryValue(listOf("pt-BR", "fr", "en", "fr")))
        assertNull(OpenSubtitlesLanguages.queryValue(emptyList()))
    }

    @Test
    fun displayNames() {
        assertEquals("English", OpenSubtitlesLanguages.displayName("en", Locale.ENGLISH))
        assertEquals("Portuguese (Brazil)", OpenSubtitlesLanguages.displayName("pt-br", Locale.ENGLISH))
        assertEquals("Unknown language", OpenSubtitlesLanguages.displayName("", Locale.ENGLISH))
    }

    @Test
    fun regionalCodesMatchTheirLanguage() {
        assertTrue(OpenSubtitlesLanguages.matches("pt-br", "pt-br"))
        assertTrue(OpenSubtitlesLanguages.matches("pt-br", "pt"))
        assertFalse(OpenSubtitlesLanguages.matches("pt-br", "pt-pt"))
        assertFalse(OpenSubtitlesLanguages.matches("en", "fr"))
    }
}
