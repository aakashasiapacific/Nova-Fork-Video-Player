package com.aakash.novafork.core.match

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** Fuzzy matching of a parsed title against TMDB search results. */
object TitleMatcher {
    /** Below this title similarity a candidate is never accepted. */
    private const val MIN_SIMILARITY = 0.6

    /** A candidate whose year is far off must still score at least this much in total. */
    private const val MIN_SCORE = 0.5

    private const val SAME_YEAR_BOOST = 0.15
    private const val NEAR_YEAR_BOOST = 0.08
    private const val WRONG_YEAR_PENALTY = 0.2
    private const val POPULARITY_WEIGHT = 0.005
    private const val MAX_POPULARITY_BOOST = 0.03

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val SPACES = Regex("\\s+")
    private val ARTICLES = setOf("the", "a", "an")

    /** Letters that NFKD does not split into base letter + accent. */
    private val FOLDED = mapOf(
        'ß' to "ss", 'æ' to "ae", 'œ' to "oe", 'ø' to "o", 'đ' to "d", 'ð' to "d", 'ł' to "l", 'þ' to "th", 'ı' to "i",
    )
    private val APOSTROPHES = setOf('\'', '’', '‘', '`', '´')

    /** Lower-case, strip accents/punctuation/"the|a|an" prefix, "&"→"and", collapse spaces. */
    fun normalize(title: String): String {
        val decomposed = Normalizer.normalize(title, Normalizer.Form.NFKD).replace(COMBINING_MARKS, "")
        val text = StringBuilder(decomposed.length)
        for (c in decomposed.lowercase(Locale.ROOT)) {
            when {
                c == '&' -> text.append(" and ")
                c in APOSTROPHES -> Unit // "Ocean's" and "Oceans" are the same title
                c.isLetterOrDigit() -> text.append(FOLDED[c] ?: c.toString())
                else -> text.append(' ')
            }
        }
        val words = joinInitials(text.trim().split(SPACES).filter { it.isNotEmpty() })
        val withoutArticle = if (words.size > 1 && words.first() in ARTICLES) words.drop(1) else words
        return withoutArticle.joinToString(" ")
    }

    /** "s w a t" (from "S.W.A.T.") → "swat", so it matches "SWAT". */
    private fun joinInitials(words: List<String>): List<String> {
        val result = ArrayList<String>(words.size)
        var i = 0
        while (i < words.size) {
            var j = i
            while (j < words.size && words[j].length == 1 && words[j][0].isLetter()) j++
            if (j - i >= 2) {
                result += words.subList(i, j).joinToString("")
                i = j
            } else {
                result += words[i]
                i++
            }
        }
        return result
    }

    /** 0.0 … 1.0 similarity of two titles after [normalize] (token + edit-distance based). */
    fun similarity(a: String, b: String): Double = normalizedSimilarity(normalize(a), normalize(b))

    private fun normalizedSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return max(jaccard(a, b), levenshteinRatio(a, b))
    }

    private fun jaccard(a: String, b: String): Double {
        val left = a.split(' ').toSet()
        val right = b.split(' ').toSet()
        val union = (left + right).size
        return if (union == 0) 0.0 else (left intersect right).size.toDouble() / union
    }

    private fun levenshteinRatio(a: String, b: String): Double {
        val longest = max(a.length, b.length)
        return if (longest == 0) 1.0 else 1.0 - levenshtein(a, b).toDouble() / longest
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
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
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return null
        var bestCandidate: T? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (candidate in candidates) {
            val similarity = max(
                normalizedSimilarity(normalizedQuery, normalize(title(candidate))),
                originalTitle(candidate)?.let { normalizedSimilarity(normalizedQuery, normalize(it)) } ?: 0.0,
            )
            if (similarity < MIN_SIMILARITY) continue
            val score = similarity + yearScore(year, candidateYear(candidate)) + popularityBoost(popularity(candidate))
            if (score < MIN_SCORE) continue
            if (score > bestScore) {
                bestScore = score
                bestCandidate = candidate
            }
        }
        return bestCandidate
    }

    private fun yearScore(wanted: Int?, actual: Int?): Double {
        if (wanted == null || actual == null) return 0.0
        return when (abs(wanted - actual)) {
            0 -> SAME_YEAR_BOOST
            1 -> NEAR_YEAR_BOOST
            else -> -WRONG_YEAR_PENALTY
        }
    }

    /** Only a tiebreak: ln(1 + popularity) keeps a blockbuster from beating a better title match. */
    private fun popularityBoost(popularity: Double): Double =
        if (popularity.isNaN() || popularity <= 0.0) 0.0 else min(ln(1.0 + popularity) * POPULARITY_WEIGHT, MAX_POPULARITY_BOOST)
}
