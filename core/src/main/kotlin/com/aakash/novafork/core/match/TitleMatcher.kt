package com.aakash.novafork.core.match

/** Fuzzy matching of a parsed title against TMDB search results. */
object TitleMatcher {
    /** Lower-case, strip accents/punctuation/"the|a|an" prefix, "&"→"and", collapse spaces. */
    fun normalize(title: String): String {
        TODO("core agent")
    }

    /** 0.0 … 1.0 similarity of two titles after [normalize] (token + edit-distance based). */
    fun similarity(a: String, b: String): Double {
        TODO("core agent")
    }

    /**
     * Picks the best candidate for [query] or null when nothing is convincing.
     * Score = title similarity (best of title/original title), boosted when the year matches
     * (±1 allowed), lightly boosted by popularity; reject below ~0.6 similarity.
     */
    fun <T> best(
        query: String,
        year: Int?,
        candidates: List<T>,
        title: (T) -> String,
        originalTitle: (T) -> String?,
        candidateYear: (T) -> Int?,
        popularity: (T) -> Double,
    ): T? {
        TODO("core agent")
    }
}
