package com.aakash.novafork.core.tmdb

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** The "New episode Fri, 7 Nov" line of a show's page. */
object NextEpisodeLabel {
    /** Skeletons for [of]'s `pattern`: weekday, day and month, and the same with the year. */
    const val SKELETON = "EEEdMMM"
    const val SKELETON_WITH_YEAR = "EEEdMMMy"

    /**
     * "New episode Fri, 7 Nov" for TMDB's [airDate] ("2025-11-07"), with the year when it isn't
     * [today]'s year. Null when the date is missing, unreadable or before [today].
     * [pattern] turns a skeleton into the locale's date pattern (Android's
     * DateFormat.getBestDateTimePattern); the default is English.
     */
    fun of(
        airDate: String?,
        today: LocalDate,
        locale: Locale = Locale.getDefault(),
        pattern: (String) -> String = ::englishPattern,
    ): String? {
        val date = parse(airDate) ?: return null
        if (date.isBefore(today)) return null
        val skeleton = if (date.year == today.year) SKELETON else SKELETON_WITH_YEAR
        return "New episode " + DateTimeFormatter.ofPattern(pattern(skeleton), locale).format(date)
    }

    /** The date of "2025-11-07" (anything after the first 10 characters is ignored), or null. */
    fun parse(airDate: String?): LocalDate? =
        try {
            airDate?.trim()?.takeIf { it.length >= 10 }?.let { LocalDate.parse(it.take(10)) }
        } catch (e: DateTimeParseException) {
            null
        }

    private fun englishPattern(skeleton: String): String =
        if (skeleton == SKELETON) "EEE, d MMM" else "EEE, d MMM y"
}
