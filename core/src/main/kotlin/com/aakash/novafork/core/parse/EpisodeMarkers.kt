package com.aakash.novafork.core.parse

import java.time.DateTimeException
import java.time.LocalDate

/** Where an episode marker sits in a prepared name and what it says. */
internal class EpisodeMarker(
    val start: Int,
    val end: Int,
    /** Season written next to the episode ("S01E02", "1x02", "Season 1 Episode 2"); null otherwise. */
    val season: Int? = null,
    val episode: Int? = null,
    val episodeEnd: Int? = null,
    /** "yyyy-MM-dd" of a daily show. */
    val airDate: String? = null,
    /** A bare number ("Ep 05", "Title - 12", "05 - Pilot"): the season comes from elsewhere. */
    val numberOnly: Boolean = false,
)

/**
 * Finds the episode marker of a prepared file name. Markers are tried strongest first, so a word
 * like "Ep" or a " - 12" only counts when nothing more explicit is present.
 */
internal object EpisodeMarkers {
    private const val B = "(?<![\\p{L}\\p{N}])"
    private const val A = "(?![\\p{L}\\p{N}])"
    private val I = RegexOption.IGNORE_CASE

    /** S01E02, s1e2, S01.E02, S01E01E02, S01E01-E02, S01E01-02, and year-numbered seasons (S2024E05). */
    private val SXXEYY = Regex("${B}s(\\d{1,2}|(?:19|20)\\d{2})[ ._-]?e(\\d{1,4})(?!\\d)((?:(?:[ ._-]?e|-e?)\\d{1,4}(?=$|[^\\p{L}\\p{N}]|e\\d))*)", I)
    private val CHAIN_PART = Regex("(?:[ ._-]?e|-e?)(\\d{1,4})", I)

    /** "Season 1 Episode 2", "Series 1 Ep 2". */
    private val WORDS = Regex(
        "${B}(?:season|series|saison|staffel|temporada)[ ._-]*(\\d{1,2})[ ._,-]*" +
            "(?:episode|episodio|[ée]pisode|folge|ep)[ ._-]*(\\d{1,4})(?!\\d)",
        I,
    )

    /** 1x02, 1x02-03. */
    private val NXNN = Regex("${B}(\\d{1,2})x(\\d{2,3})(?:[-x](\\d{2,3}))?$A", I)

    private val DAILY_YMD = Regex("$B(\\d{4})[ ._-](\\d{2})[ ._-](\\d{2})(?!\\d)")
    private val DAILY_DMY = Regex("$B(\\d{2})[ ._-](\\d{2})[ ._-](\\d{4})(?!\\d)")

    /** "Episode 5", "Ep 05", "Ep.05", "EP05", "E05", "E05-E06". */
    private val EPISODE_ONLY = Regex("$B(?:(?:episode|ep)\\.?[ ._-]?|e)(\\d{1,4})(?:v\\d)?(?:-e?(\\d{1,4}))?$A", I)

    /** Anime absolute numbering: "Title - 12", "Title - 12v2". Needs the spaced dash. */
    private val DASH_NUMBER = Regex("(?<=\\s)[-–—]\\s*(\\d{1,4})(?:v\\d{1,2})?(?=\\s|$)")

    /** "05", "05 - Pilot", "05. Pilot", "05 Pilot" at the start of the name. */
    private val LEADING_NUMBER = Regex("^(\\d{1,3})(?:v\\d)?(?=$|[\\s._-])")
    private val SPACED_DASH_START = Regex("^\\s+[-–—]\\s")

    /**
     * @param hasSeasonContext a "Season N" / "Specials" folder holds the file, which lets a bare
     *   leading number ("05.mkv") count as the episode.
     */
    fun find(text: String, hasSeasonContext: Boolean): EpisodeMarker? =
        explicit(text)
            ?: daily(text)
            ?: episodeOnly(text)
            ?: dashNumber(text)
            ?: leadingNumber(text, hasSeasonContext)

    private fun explicit(text: String): EpisodeMarker? {
        val candidates = listOfNotNull(
            SXXEYY.find(text)?.let { m ->
                val episode = m.groupValues[2].toInt()
                val last = CHAIN_PART.findAll(m.groupValues[3]).lastOrNull()?.groupValues?.get(1)?.toInt()
                EpisodeMarker(m.range.first, m.range.last + 1, m.groupValues[1].toInt(), episode, endOf(episode, last))
            },
            WORDS.find(text)?.let { m ->
                EpisodeMarker(m.range.first, m.range.last + 1, m.groupValues[1].toInt(), m.groupValues[2].toInt())
            },
            NXNN.find(text)?.let { m ->
                val episode = m.groupValues[2].toInt()
                val last = m.groupValues[3].toIntOrNull()
                EpisodeMarker(m.range.first, m.range.last + 1, m.groupValues[1].toInt(), episode, endOf(episode, last))
            },
        )
        return candidates.minByOrNull { it.start }
    }

    private fun daily(text: String): EpisodeMarker? {
        // A date at the very start is a home video ("2024-05-01 Birthday"), not a show's air date.
        val ymd = DAILY_YMD.findAll(text).filter { it.range.first > 0 }.firstNotNullOfOrNull { m ->
            dateOf(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { EpisodeMarker(m.range.first, m.range.last + 1, airDate = it) }
        }
        val dmy = DAILY_DMY.findAll(text).filter { it.range.first > 0 }.firstNotNullOfOrNull { m ->
            dateOf(m.groupValues[3], m.groupValues[2], m.groupValues[1])?.let { EpisodeMarker(m.range.first, m.range.last + 1, airDate = it) }
        }
        return listOfNotNull(ymd, dmy).minByOrNull { it.start }
    }

    private fun episodeOnly(text: String): EpisodeMarker? =
        EPISODE_ONLY.findAll(text).firstNotNullOfOrNull { m ->
            val number = m.groupValues[1]
            val episode = number.toInt()
            val after = text.substring(m.range.last + 1)
            // "Star.Wars.Episode.4.A.New.Hope.1977" is a movie: a release year follows the number.
            if (ReleaseTokens.isYearText(number) || containsYear(after)) return@firstNotNullOfOrNull null
            EpisodeMarker(
                m.range.first,
                m.range.last + 1,
                episode = episode,
                episodeEnd = endOf(episode, m.groupValues[2].toIntOrNull()),
                numberOnly = true,
            )
        }

    private fun dashNumber(text: String): EpisodeMarker? =
        DASH_NUMBER.findAll(text).firstNotNullOfOrNull { m ->
            val number = m.groupValues[1]
            val after = text.substring(m.range.last + 1)
            if (ReleaseTokens.isYearText(number) || ReleaseTokens.startsWithYear(after)) return@firstNotNullOfOrNull null
            EpisodeMarker(m.range.first, m.range.last + 1, episode = number.toInt(), numberOnly = true)
        }

    private fun leadingNumber(text: String, hasSeasonContext: Boolean): EpisodeMarker? {
        val m = LEADING_NUMBER.find(text) ?: return null
        val after = text.substring(m.range.last + 1)
        // Outside a season folder only "05 - Title" reads as an episode; "300" or "21 Jump Street" do not.
        if (!hasSeasonContext && !(SPACED_DASH_START.containsMatchIn(after) && after.any { it.isLetter() })) return null
        return EpisodeMarker(start = 0, end = m.range.last + 1, episode = m.groupValues[1].toInt(), numberOnly = true)
    }

    private fun containsYear(text: String): Boolean =
        ReleaseTokens.tokenize(text).any { it.type == TokenType.YEAR }

    private fun endOf(episode: Int, last: Int?): Int? =
        last?.takeIf { it > episode && it - episode < 100 }

    private fun dateOf(year: String, month: String, day: String): String? {
        val y = year.toIntOrNull()?.takeIf { ReleaseTokens.isPlausibleYear(it) } ?: return null
        val m = month.toIntOrNull() ?: return null
        val d = day.toIntOrNull() ?: return null
        return try {
            LocalDate.of(y, m, d).toString()
        } catch (e: DateTimeException) {
            null
        }
    }
}
