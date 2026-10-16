package com.aakash.novafork.core.subs

import com.aakash.novafork.core.library.VideoFiles
import java.util.Locale
import java.util.MissingResourceException

/** Picks external subtitle files that belong to a video. */
object SubtitleRules {
    val EXTENSIONS: Set<String> = setOf("srt", "ass", "ssa", "vtt", "sub", "smi", "idx")

    fun isSubtitle(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /** Name parts that describe a subtitle rather than name a different video. */
    private val FLAGS = setOf(
        "forced", "foreign", "sdh", "cc", "hi", "default", "full", "signs", "songs", "commentary", "complete", "dialogue",
    )
    private val NUMBER = Regex("\\d{1,2}")
    private val REGION = Regex("([a-z]{2,3})[-_]([a-z]{2,4})")

    /** ISO 639-1, ISO 639-2 (T and B) and English language names → ISO 639-1. */
    private val LANGUAGES: Map<String, String> by lazy { buildLanguageTable() }

    /**
     * Sibling subtitle names for [videoFileName]: same base name, optionally followed by language
     * and flag parts ("Movie.srt", "Movie.en.srt", "Movie.eng.forced.srt", "Movie.English.SDH.srt").
     * Case-insensitive. ".idx" is returned only with its ".sub" sibling dropped (VLC opens the pair via .idx).
     * Ordered: exact base name first, then languages in [preferredLanguages] order (ISO 639-1 or -2,
     * e.g. "en"/"eng"), then the rest alphabetically. Never returns names for another video.
     */
    fun match(videoFileName: String, siblingNames: List<String>, preferredLanguages: List<String> = listOf("en")): List<String> {
        val base = VideoFiles.baseName(videoFileName).lowercase(Locale.ROOT)
        if (base.isEmpty()) return emptyList()
        val otherVideos = siblingNames.asSequence()
            .filter { VideoFiles.isVideo(it) && !it.equals(videoFileName, ignoreCase = true) }
            .map { VideoFiles.baseName(it).lowercase(Locale.ROOT) }
            .filter { it.length > base.length && it.startsWith("$base.") }
            .toList()

        val candidates = siblingNames.distinct().mapNotNull { name ->
            if (!isSubtitle(name)) return@mapNotNull null
            val stem = VideoFiles.baseName(name).lowercase(Locale.ROOT)
            val parts = when {
                stem == base -> emptyList()
                stem.startsWith("$base.") -> stem.substring(base.length + 1).split('.')
                else -> return@mapNotNull null
            }
            // "Movie.Part.2.srt" belongs to "Movie.Part.2.mkv", not to "Movie.mkv".
            if (otherVideos.any { stem == it || stem.startsWith("$it.") }) return@mapNotNull null
            if (!parts.all(::isDescriptor)) return@mapNotNull null
            Candidate(name, stem, VideoFiles.extension(name), parts)
        }

        val pairedIdx = candidates.filter { it.extension == "idx" }.map { it.stem }.toSet()
        val subtitles = candidates.filter { candidate ->
            when (candidate.extension) {
                // VobSub: the .idx opens the pair; a lone .idx has no picture data.
                "idx" -> candidates.any { it.extension == "sub" && it.stem == candidate.stem }
                "sub" -> candidate.stem !in pairedIdx
                else -> true
            }
        }

        val preferred = preferredLanguages.mapNotNull { languageOf(it.lowercase(Locale.ROOT)) }.distinct()
        return subtitles.sortedWith(
            compareBy<Candidate>(
                { if (it.parts.isEmpty()) 0 else 1 },
                { candidate -> preferred.indexOf(candidate.language).let { if (it < 0) Int.MAX_VALUE else it } },
                { if ("forced" in it.parts || "foreign" in it.parts) 1 else 0 },
                { it.name.lowercase(Locale.ROOT) },
            ),
        ).map { it.name }
    }

    private class Candidate(val name: String, val stem: String, val extension: String, val parts: List<String>) {
        val language: String? = parts.firstNotNullOfOrNull(::languageOf)
    }

    private fun isDescriptor(part: String): Boolean =
        part in FLAGS || NUMBER.matches(part) || languageOf(part) != null

    private fun languageOf(part: String): String? {
        LANGUAGES[part]?.let { return it }
        val region = REGION.matchEntire(part) ?: return null
        return LANGUAGES[region.groupValues[1]]
    }

    private fun buildLanguageTable(): Map<String, String> {
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
            if (name.isNotEmpty() && name != code && name.none { it == ' ' || it == '(' }) table.putIfAbsent(name, code)
        }
        // ISO 639-2/B codes and names that release groups use but Locale does not list.
        table += mapOf(
            "fre" to "fr", "ger" to "de", "chi" to "zh", "dut" to "nl", "gre" to "el", "cze" to "cs", "rum" to "ro",
            "slo" to "sk", "per" to "fa", "may" to "ms", "baq" to "eu", "arm" to "hy", "geo" to "ka", "ice" to "is",
            "mac" to "mk", "alb" to "sq", "bur" to "my", "wel" to "cy", "tib" to "bo", "mao" to "mi",
            "pob" to "pt", "pb" to "pt", "brazilian" to "pt", "portuguese" to "pt", "spa" to "es", "esp" to "es",
            "castilian" to "es", "latino" to "es", "farsi" to "fa", "flemish" to "nl", "mandarin" to "zh",
            "cantonese" to "zh", "chs" to "zh", "cht" to "zh", "jap" to "ja", "nob" to "nb", "nno" to "nn",
            "norwegian" to "no", "greek" to "el", "romanian" to "ro", "slovenian" to "sl", "persian" to "fa",
            "he" to "he", "iw" to "he", "heb" to "he", "hebrew" to "he",
            "id" to "id", "in" to "id", "ind" to "id", "indonesian" to "id",
            "yi" to "yi", "ji" to "yi", "yid" to "yi", "yiddish" to "yi",
        )
        // Some runtimes (Android) still report the legacy codes iw/in/ji.
        val legacy = mapOf("iw" to "he", "in" to "id", "ji" to "yi")
        table.replaceAll { _, code -> legacy[code] ?: code }
        return table
    }
}
