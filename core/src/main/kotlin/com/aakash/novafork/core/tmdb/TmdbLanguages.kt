package com.aakash.novafork.core.tmdb

import java.util.Locale

/** The TMDB `language` parameter ("hi-IN", "en-GB") for the device's languages. */
object TmdbLanguages {
    /** Used when none of the device's languages is one TMDB has translations in. */
    const val DEFAULT = "en-US"

    /** Languages TMDB has translations in, with the tag used when the device's country isn't one of [REGIONAL]. */
    private val LANGUAGE_TAGS: Map<String, String> = mapOf(
        "af" to "af-ZA", "ar" to "ar-SA", "be" to "be-BY", "bg" to "bg-BG", "bn" to "bn-IN",
        "ca" to "ca-ES", "cs" to "cs-CZ", "cy" to "cy-GB", "da" to "da-DK", "de" to "de-DE",
        "el" to "el-GR", "en" to "en-US", "es" to "es-ES", "et" to "et-EE", "eu" to "eu-ES",
        "fa" to "fa-IR", "fi" to "fi-FI", "fr" to "fr-FR", "ga" to "ga-IE", "gl" to "gl-ES",
        "he" to "he-IL", "hi" to "hi-IN", "hr" to "hr-HR", "hu" to "hu-HU", "id" to "id-ID",
        "it" to "it-IT", "ja" to "ja-JP", "ka" to "ka-GE", "kk" to "kk-KZ", "kn" to "kn-IN",
        "ko" to "ko-KR", "lt" to "lt-LT", "lv" to "lv-LV", "ml" to "ml-IN", "mr" to "mr-IN",
        "ms" to "ms-MY", "nb" to "nb-NO", "nl" to "nl-NL", "no" to "no-NO", "pa" to "pa-IN",
        "pl" to "pl-PL", "pt" to "pt-BR", "ro" to "ro-RO", "ru" to "ru-RU", "si" to "si-LK",
        "sk" to "sk-SK", "sl" to "sl-SI", "sq" to "sq-AL", "sr" to "sr-RS", "sv" to "sv-SE",
        "ta" to "ta-IN", "te" to "te-IN", "th" to "th-TH", "tl" to "tl-PH", "tr" to "tr-TR",
        "uk" to "uk-UA", "ur" to "ur-PK", "vi" to "vi-VN", "zh" to "zh-CN", "zu" to "zu-ZA",
    )

    /** Regional variants kept as the device has them instead of becoming the language's tag. */
    private val REGIONAL: Set<String> = setOf(
        "en-AU", "en-CA", "en-GB", "en-IE", "en-IN", "en-NZ",
        "es-MX", "fr-CA", "de-AT", "de-CH", "pt-PT", "nl-BE", "ms-SG", "ar-AE", "bn-BD",
    )

    /** The tag for the first of [locales] (most preferred first) TMDB has, else [DEFAULT]. */
    fun forLocales(locales: List<Locale>): String = locales.firstNotNullOfOrNull { forLocale(it) } ?: DEFAULT

    /**
     * The device's own language-country pair when TMDB knows it ("hi-IN", "en-GB", "en-IN"),
     * else the language's usual tag ("en-DE" → "en-US"); null when TMDB lacks the language.
     */
    fun forLocale(locale: Locale): String? {
        val language = language(locale)
        val languageTag = LANGUAGE_TAGS[language] ?: return null
        val country = locale.country.uppercase(Locale.ROOT)
        if (language == "zh") return chinese(locale.script, country)
        // Latin American Spanish (es-419) reads best as TMDB's Mexican Spanish.
        if (language == "es" && country == "419") return "es-MX"
        val exact = "$language-$country"
        return if (country.length == 2 && (exact == languageTag || exact in REGIONAL)) exact else languageTag
    }

    /** Lower-case ISO 639-1 code; older Java and Android report some languages by retired codes. */
    private fun language(locale: Locale): String = when (val code = locale.language.lowercase(Locale.ROOT)) {
        "in" -> "id"
        "iw" -> "he"
        "fil" -> "tl"
        // Norwegian Nynorsk: TMDB has Norwegian ("no") and Bokmål ("nb").
        "nn" -> "no"
        else -> code
    }

    /** Traditional script (or Taiwan, Hong Kong, Macau without a script) → zh-TW / zh-HK, else simplified. */
    private fun chinese(script: String, country: String): String {
        val traditional = script == "Hant" || (script.isEmpty() && country in setOf("TW", "HK", "MO"))
        return when {
            traditional && (country == "HK" || country == "MO") -> "zh-HK"
            traditional -> "zh-TW"
            country == "SG" -> "zh-SG"
            else -> "zh-CN"
        }
    }
}
