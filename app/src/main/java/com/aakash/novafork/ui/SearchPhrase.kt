package com.aakash.novafork.ui

import java.util.Locale

/**
 * Plain-phrase search ("comedies under 2 hours", "unwatched thrillers", "4k sci-fi from 2019",
 * "90s action"): the words that name a genre, a length, a state, a picture size or a period become
 * filters; anything else must appear in the title, show or credits. Null when the text names no
 * filter at all, so an ordinary search stays an ordinary search.
 */
class SearchPhrase private constructor(
    val genres: Set<String>,
    val maxMinutes: Int?,
    val minMinutes: Int?,
    val unwatched: Boolean,
    val watched: Boolean,
    val minHeight: Int?,
    val yearFrom: Int?,
    val yearTo: Int?,
    /** What is left of the text: each word must be found in the title, show or credits. */
    val words: List<String>,
) {
    fun lengthMatches(durationMs: Long?): Boolean {
        if (maxMinutes == null && minMinutes == null) return true
        val minutes = (durationMs ?: return false) / MS_PER_MINUTE
        return (maxMinutes == null || minutes <= maxMinutes) && (minMinutes == null || minutes >= minMinutes)
    }

    fun yearMatches(year: Int?): Boolean {
        if (yearFrom == null && yearTo == null) return true
        val y = year ?: return false
        return (yearFrom == null || y >= yearFrom) && (yearTo == null || y <= yearTo)
    }

    fun sizeMatches(height: Int?): Boolean = minHeight == null || (height ?: 0) >= minHeight

    companion object {
        private const val MS_PER_MINUTE = 60_000L

        private val GENRES = mapOf(
            "comedy" to "Comedy", "comedies" to "Comedy", "funny" to "Comedy",
            "action" to "Action", "horror" to "Horror", "scary" to "Horror",
            "drama" to "Drama", "dramas" to "Drama",
            "thriller" to "Thriller", "thrillers" to "Thriller",
            "sci-fi" to "Science Fiction", "scifi" to "Science Fiction",
            "romance" to "Romance", "romantic" to "Romance",
            "animation" to "Animation", "animated" to "Animation", "cartoon" to "Animation", "cartoons" to "Animation",
            "documentary" to "Documentary", "documentaries" to "Documentary",
            "family" to "Family", "fantasy" to "Fantasy", "crime" to "Crime", "mystery" to "Mystery",
            "war" to "War", "western" to "Western", "westerns" to "Western", "adventure" to "Adventure",
            "history" to "History", "historical" to "History", "music" to "Music", "musical" to "Music",
        )

        private val FILLER = setOf(
            "movie", "movies", "film", "films", "show", "shows", "series", "tv", "something", "anything",
            "a", "an", "the", "of", "to", "me", "some", "with", "that", "are", "is", "for", "and", "i", "have",
            "my", "in", "from", "than", "less", "more", "over", "under", "above", "below", "longer", "shorter",
        )

        private val LENGTH = Regex(
            "(under|below|less than|shorter than|max|over|above|more than|longer than|at least)\\s+(\\d+(?:\\.\\d+)?)\\s*" +
                "(hours?|hrs?|h|minutes?|mins?|m)?",
        )
        private val YEAR_BOUND = Regex("(from|since|after|before|until|in)\\s+((?:19|20)\\d\\d)")
        private val DECADE = Regex("\\b((?:19|20)?\\d0)'?s\\b")

        fun parse(text: String): SearchPhrase? {
            var rest = " " + text.lowercase(Locale.ROOT).replace("science fiction", "sci-fi").trim() + " "
            var recognised = false

            var maxMinutes: Int? = null
            var minMinutes: Int? = null
            LENGTH.find(rest)?.let { m ->
                val number = m.groupValues[2].toDouble()
                val unit = m.groupValues[3]
                val minutes = if (unit.startsWith("h") || (unit.isEmpty() && number <= MAX_HOURS_WITHOUT_UNIT)) number * 60 else number
                if (m.groupValues[1] in setOf("under", "below", "less than", "shorter than", "max")) {
                    maxMinutes = minutes.toInt()
                } else {
                    minMinutes = minutes.toInt()
                }
                rest = rest.replace(m.value, " ")
                recognised = true
            }

            var yearFrom: Int? = null
            var yearTo: Int? = null
            YEAR_BOUND.find(rest)?.let { m ->
                val year = m.groupValues[2].toInt()
                when (m.groupValues[1]) {
                    "from", "since", "after" -> yearFrom = year
                    "before", "until" -> yearTo = year
                    else -> {
                        yearFrom = year
                        yearTo = year
                    }
                }
                rest = rest.replace(m.value, " ")
                recognised = true
            }
            DECADE.find(rest)?.let { m ->
                var start = m.groupValues[1].toInt()
                if (start < 100) start += if (start >= 30) 1900 else 2000
                yearFrom = start
                yearTo = start + 9
                rest = rest.replace(m.value, " ")
                recognised = true
            }

            var minHeight: Int? = null
            if (" 4k " in rest || " uhd " in rest) {
                minHeight = 2000
                rest = rest.replace(" 4k ", " ").replace(" uhd ", " ")
                recognised = true
            } else if (" 1080p " in rest) {
                minHeight = 1000
                rest = rest.replace(" 1080p ", " ")
                recognised = true
            }

            var unwatched = false
            var watched = false
            for (word in listOf("unwatched", "unseen")) {
                if (" $word " in rest) {
                    unwatched = true
                    recognised = true
                    rest = rest.replace(" $word ", " ")
                }
            }
            if (" watched " in rest) {
                watched = true
                recognised = true
                rest = rest.replace(" watched ", " ")
            }
            if (" short " in rest && maxMinutes == null) {
                maxMinutes = SHORT_MINUTES
                recognised = true
                rest = rest.replace(" short ", " ")
            }

            val genres = LinkedHashSet<String>()
            val words = ArrayList<String>()
            for (word in rest.split(' ').filter { it.isNotBlank() }) {
                val genre = GENRES[word]
                when {
                    genre != null -> {
                        genres += genre
                        // The plain name alone ("comedy") stays an ordinary search; "comedies" asks for the genre.
                        if (word != genre.lowercase(Locale.ROOT)) recognised = true
                    }
                    word in FILLER -> Unit
                    else -> words += word
                }
            }
            if (!recognised) return null
            return SearchPhrase(genres, maxMinutes, minMinutes, unwatched, watched, minHeight, yearFrom, yearTo, words)
        }

        private const val SHORT_MINUTES = 90
        private const val MAX_HOURS_WITHOUT_UNIT = 6.0
    }
}
