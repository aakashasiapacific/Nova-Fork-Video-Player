package com.aakash.novafork.core.opensubtitles

/** Orders OpenSubtitles results the way they are offered, and picks one to fetch unasked. */
object SubtitleRanking {
    /**
     * The wanted [languages] first, in their order (other languages after them), then within a
     * language: timed for this file, made by people (not AI or machine translated), most downloaded.
     */
    fun sort(results: List<SubtitleResult>, languages: List<String>): List<SubtitleResult> =
        results.sortedWith(
            compareBy<SubtitleResult>(
                { languageRank(it.language, languages) },
                { if (it.movieHashMatch) 0 else 1 },
                { if (it.aiTranslated || it.machineTranslated) 1 else 0 },
                { -it.downloadCount.toLong() },
                { it.id },
            ),
        )

    /**
     * What to download without asking: in the first of [languages] that has any result, the one
     * timed for this file, else the most downloaded (people's translations before machines').
     * Null when nothing is in a wanted language.
     */
    fun best(results: List<SubtitleResult>, languages: List<String>): SubtitleResult? {
        if (languages.isEmpty()) return sort(results, languages).firstOrNull()
        return sort(results, languages).firstOrNull { languageRank(it.language, languages) < languages.size }
    }

    private fun languageRank(language: String, languages: List<String>): Int {
        val exact = languages.indexOfFirst { it.equals(language, ignoreCase = true) }
        if (exact >= 0) return exact
        val related = languages.indexOfFirst { OpenSubtitlesLanguages.matches(language, it) }
        return if (related >= 0) related else languages.size
    }
}
