package com.aakash.novafork.core.library

import com.aakash.novafork.core.match.TitleMatcher

/** Stable keys shared by the database and the UI. */
object LibraryKeys {
    fun movieKey(tmdbId: Int): String = "movie:$tmdbId"
    fun tvKey(tmdbId: Int): String = "tv:$tmdbId"

    /** Groups episodes into a show: the TMDB show when matched, else the normalised parsed title. */
    fun showKey(tmdbTvId: Int?, parsedTitle: String): String =
        if (tmdbTvId != null) tvKey(tmdbTvId) else "name:" + TitleMatcher.normalize(parsedTitle)

    /** "movie:603" → 603, "tv:1396" → 1396, anything else → null. */
    fun tmdbId(key: String): Int? = key.substringAfter(':', "").toIntOrNull()
        ?.takeIf { key.startsWith("movie:") || key.startsWith("tv:") }

    /** Cache key for a TMDB lookup so a whole season costs one search. */
    fun matchQueryKey(type: String, title: String, year: Int?): String =
        "$type|${TitleMatcher.normalize(title)}|${year ?: ""}"
}
