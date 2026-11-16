package com.aakash.novafork.data

import java.util.Locale

/** The cut of a movie as its file name gives it: "Film.2001.Directors.Cut.mkv" is a Director's Cut. */
object MovieEditions {
    private val EDITIONS = listOf(
        Regex("director'?s[ ._-]?cut") to "Director's Cut",
        Regex("extended([ ._-]?(cut|edition))?") to "Extended",
        Regex("theatrical([ ._-]?(cut|edition))?") to "Theatrical",
        Regex("unrated") to "Unrated",
        Regex("uncut") to "Uncut",
        Regex("final[ ._-]?cut") to "Final Cut",
        Regex("ultimate[ ._-]?(cut|edition)") to "Ultimate Edition",
        Regex("special[ ._-]?edition") to "Special Edition",
        Regex("imax") to "IMAX",
        Regex("remastered") to "Remastered",
        Regex("criterion") to "Criterion",
    )

    /** The edition named in [fileName], or null for none. */
    fun of(fileName: String): String? {
        val name = fileName.substringBeforeLast('.').lowercase(Locale.ROOT)
        return EDITIONS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(name) }?.second
    }
}
