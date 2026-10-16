package com.aakash.novafork.player

import java.util.Locale
import java.util.MissingResourceException

/**
 * Matches libVLC track names ("Track 2 - [Hindi]") and subtitle file names ("Movie.eng.srt")
 * against a preferred language given as an ISO 639 code or an English name.
 */
internal object TrackLanguages {
    private val SEPARATORS = Regex("[^\\p{L}\\p{M}]+")

    /** ISO 639-2/B codes that release names still use; Locale only knows the /T ones. */
    private val BIBLIOGRAPHIC = mapOf(
        "fr" to "fre", "de" to "ger", "zh" to "chi", "nl" to "dut", "cs" to "cze", "el" to "gre",
        "ro" to "rum", "sk" to "slo", "fa" to "per", "ms" to "may", "hy" to "arm", "ka" to "geo",
        "is" to "ice", "mk" to "mac", "sq" to "alb", "my" to "bur", "cy" to "wel", "eu" to "baq",
        "bo" to "tib", "mi" to "mao",
    )

    /**
     * Every lower-case single-word name the language goes by, e.g. "hin" → {hin, hi, hindi, हिन्दी}.
     * Empty when there is no preference.
     */
    fun aliases(preference: String): Set<String> {
        val wanted = preference.trim().lowercase(Locale.ROOT)
        if (wanted.isEmpty()) return emptySet()
        val aliases = mutableSetOf(wanted)
        val locale = Locale.getISOLanguages().asSequence()
            .map { Locale.forLanguageTag(it) }
            .firstOrNull { it.language == wanted || iso3(it) == wanted || englishName(it) == wanted }
        if (locale != null) {
            aliases += locale.language.lowercase(Locale.ROOT)
            iso3(locale)?.let { aliases += it }
            BIBLIOGRAPHIC[locale.language]?.let { aliases += it }
            aliases += englishName(locale)
            aliases += locale.getDisplayLanguage(locale).lowercase(locale)
        }
        return aliases.filterTo(mutableSetOf()) { it.isNotBlank() && ' ' !in it }
    }

    /** True when one of the words in [text] is one of [aliases]. */
    fun matches(text: String?, aliases: Set<String>): Boolean {
        if (text.isNullOrBlank() || aliases.isEmpty()) return false
        return text.lowercase(Locale.ROOT).split(SEPARATORS).any { it in aliases }
    }

    private fun englishName(locale: Locale): String = locale.getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT)

    private fun iso3(locale: Locale): String? =
        try {
            locale.isO3Language.lowercase(Locale.ROOT).ifEmpty { null }
        } catch (e: MissingResourceException) {
            null
        }
}
