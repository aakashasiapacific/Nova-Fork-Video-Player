package com.aakash.novafork.core.subs

/** Picks external subtitle files that belong to a video. */
object SubtitleRules {
    val EXTENSIONS: Set<String> = setOf("srt", "ass", "ssa", "vtt", "sub", "smi", "idx")

    fun isSubtitle(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /**
     * Sibling subtitle names for [videoFileName]: same base name, optionally followed by language
     * and flag parts ("Movie.srt", "Movie.en.srt", "Movie.eng.forced.srt", "Movie.English.SDH.srt").
     * Case-insensitive. ".idx" is returned only with its ".sub" sibling dropped (VLC opens the pair via .idx).
     * Ordered: exact base name first, then languages in [preferredLanguages] order (ISO 639-1 or -2,
     * e.g. "en"/"eng"), then the rest alphabetically. Never returns names for another video.
     */
    fun match(videoFileName: String, siblingNames: List<String>, preferredLanguages: List<String> = listOf("en")): List<String> {
        TODO("core agent")
    }
}
