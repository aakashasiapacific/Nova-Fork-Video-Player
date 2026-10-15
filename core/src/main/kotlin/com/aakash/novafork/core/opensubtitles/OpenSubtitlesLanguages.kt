package com.aakash.novafork.core.opensubtitles

import java.util.Locale
import java.util.MissingResourceException

/**
 * OpenSubtitles language codes: ISO 639-1 in lower case, except Portuguese ("pt-pt", "pt-br")
 * and Chinese ("zh-cn", "zh-tw"), which come with their region.
 */
object OpenSubtitlesLanguages {
    private val REGIONAL: Map<String, List<String>> = mapOf(
        "pt" to listOf("pt-pt", "pt-br"),
        "zh" to listOf("zh-cn", "zh-tw"),
    )

    /** Regions whose Chinese subtitles are the traditional ones. */
    private val TRADITIONAL_REGIONS = setOf("tw", "hk", "mo")

    /** Written forms that are not ISO 639-1 / 639-2/T codes or English language names. */
    private val ALIASES: Map<String, String> = mapOf(
        "fre" to "fr", "ger" to "de", "chi" to "zh", "dut" to "nl", "gre" to "el", "cze" to "cs", "rum" to "ro",
        "slo" to "sk", "per" to "fa", "may" to "ms", "baq" to "eu", "arm" to "hy", "geo" to "ka", "ice" to "is",
        "mac" to "mk", "alb" to "sq", "bur" to "my", "wel" to "cy", "tib" to "bo", "mao" to "mi",
        "iw" to "he", "in" to "id", "ji" to "yi", "nb" to "no", "nn" to "no", "nob" to "no", "nno" to "no",
        "pob" to "pt-br", "pb" to "pt-br", "brazilian" to "pt-br", "farsi" to "fa", "persian" to "fa",
        "castilian" to "es", "flemish" to "nl", "mandarin" to "zh-cn", "cantonese" to "zh-tw",
        "chs" to "zh-cn", "cht" to "zh-tw", "zh-hans" to "zh-cn", "zh-hant" to "zh-tw", "zh-hk" to "zh-tw",
        "zh-sg" to "zh-cn", "zh-mo" to "zh-tw",
    )

    private val table: Map<String, String> by lazy { buildTable() }

    /**
     * One setting or preference ("eng", "English", "pt-BR", "en") as OpenSubtitles codes; a
     * language that comes in regional variants without a region gives all of them ("pt" →
     * pt-pt, pt-br). Empty when it names no language.
     */
    fun codesFor(value: String): List<String> {
        val wanted = value.trim().lowercase(Locale.ROOT).replace('_', '-')
        if (wanted.isEmpty()) return emptyList()
        ALIASES[wanted]?.let { return expand(it) }
        val language = wanted.substringBefore('-')
        val region = wanted.substringAfter('-', "")
        val code = ALIASES[language] ?: table[language] ?: return emptyList()
        if (region.isNotEmpty()) {
            val regional = "$code-$region"
            ALIASES[regional]?.let { return expand(it) }
            if (REGIONAL[code]?.contains(regional) == true) return listOf(regional)
            if (code == "pt") return listOf("pt-pt")
            if (code == "zh") return listOf(if (region in TRADITIONAL_REGIONS) "zh-tw" else "zh-cn")
        }
        return expand(code)
    }

    /** A comma or space separated list ("en, fr", "eng fre") as codes, in order, without repeats. */
    fun parse(setting: String): List<String> =
        normalize(setting.split(',', ';', ' ').filter { it.isNotBlank() })

    /** Codes for each value, in order, without repeats. */
    fun normalize(values: List<String>): List<String> = values.flatMap(::codesFor).distinct()

    /** A device locale's language as codes ("pt-BR" → pt-br; "zh-Hant-TW" → zh-tw). */
    fun forLocale(locale: Locale): List<String> {
        val language = locale.language.lowercase(Locale.ROOT)
        if (language.isEmpty()) return emptyList()
        if (language == "zh") {
            val traditional = locale.script.equals("Hant", ignoreCase = true) ||
                locale.country.lowercase(Locale.ROOT) in TRADITIONAL_REGIONS
            return listOf(if (traditional) "zh-tw" else "zh-cn")
        }
        val country = locale.country
        return codesFor(if (country.isNotEmpty()) "$language-$country" else language)
    }

    /** The value of [OpenSubtitlesClient]'s "languages" parameter: sorted, lower case, comma separated. */
    fun queryValue(codes: List<String>): String? =
        codes.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.distinct().sorted()
            .joinToString(",").ifEmpty { null }

    /** "English", "Portuguese (Brazil)", in [locale]; the code itself when unknown. */
    fun displayName(code: String, locale: Locale = Locale.getDefault()): String {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return "Unknown language"
        val tag = Locale.forLanguageTag(trimmed)
        val name = if (tag.country.isNotEmpty()) tag.getDisplayName(locale) else tag.getDisplayLanguage(locale)
        return name.takeIf { it.isNotBlank() && !it.equals(trimmed, ignoreCase = true) }
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            ?: trimmed
    }

    /** True when [code] (a result's language) is [preference] or one of its regional variants. */
    fun matches(code: String, preference: String): Boolean {
        val a = code.lowercase(Locale.ROOT)
        val b = preference.lowercase(Locale.ROOT)
        return a == b || a.substringBefore('-') == b || b.substringBefore('-') == a
    }

    private fun expand(code: String): List<String> = REGIONAL[code] ?: listOf(code)

    private fun buildTable(): Map<String, String> {
        val table = HashMap<String, String>()
        for (code in Locale.getISOLanguages()) {
            val locale = Locale.forLanguageTag(code)
            table[code] = code
            try {
                table.putIfAbsent(locale.isO3Language.lowercase(Locale.ROOT), code)
            } catch (e: MissingResourceException) {
                // No three-letter code known for this language on this runtime.
            }
            val name = locale.getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT)
            if (name.isNotEmpty() && name != code && ' ' !in name) table.putIfAbsent(name, code)
        }
        // Android (and older JDKs) still report the legacy codes.
        val legacy = mapOf("iw" to "he", "in" to "id", "ji" to "yi")
        table.replaceAll { _, code -> legacy[code] ?: code }
        legacy.forEach { (old, new) -> table[old] = new }
        return table
    }
}
