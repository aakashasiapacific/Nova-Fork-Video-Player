package com.aakash.novafork.core.format

import kotlin.math.roundToLong

// Built by hand rather than with String.format so digits stay ASCII in every locale.
object Formats {
    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024
    private const val TB = GB * 1024

    /** Player clock: "4:05", "12:34", "1:02:03". Negative values clamp to 0. */
    fun clock(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "$hours:${two(minutes)}:${two(seconds)}" else "$minutes:${two(seconds)}"
    }

    /** Runtime label: 155 → "2h 35m", 45 → "45m", 120 → "2h". */
    fun runtime(minutes: Int): String {
        val total = minutes.coerceAtLeast(0)
        val hours = total / 60
        val rest = total % 60
        return when {
            hours == 0 -> "${rest}m"
            rest == 0 -> "${hours}h"
            else -> "${hours}h ${rest}m"
        }
    }

    /** "1.4 GB", "700 MB", "12 KB" (1024-based, one decimal for GB). */
    fun size(bytes: Long): String {
        val value = bytes.coerceAtLeast(0).toDouble()
        return when {
            value >= TB -> "${oneDecimal(value / TB)} TB"
            value >= GB -> "${oneDecimal(value / GB)} GB"
            value >= MB -> "${(value / MB).roundToLong()} MB"
            value >= KB -> "${(value / KB).roundToLong()} KB"
            else -> "${value.toLong()} B"
        }
    }

    /** "S01E03", "S01E03-E04" when [episodeEnd] is greater than [episode]. */
    fun episodeCode(season: Int, episode: Int, episodeEnd: Int? = null): String {
        val code = "S${two(season.toLong())}E${two(episode.toLong())}"
        return if (episodeEnd != null && episodeEnd > episode) "$code-E${two(episodeEnd.toLong())}" else code
    }

    /** "Time left" label: "1h 12m left", "8m left", "Under a minute left". */
    fun remaining(positionMs: Long, durationMs: Long): String {
        val leftMinutes = (durationMs - positionMs).coerceAtLeast(0) / 60_000
        if (leftMinutes == 0L) return "Under a minute left"
        val hours = leftMinutes / 60
        val minutes = leftMinutes % 60
        return when {
            hours == 0L -> "${minutes}m left"
            minutes == 0L -> "${hours}h left"
            else -> "${hours}h ${minutes}m left"
        }
    }

    private fun two(value: Long): String = value.toString().padStart(2, '0')

    private fun oneDecimal(value: Double): String {
        val tenths = (value * 10).roundToLong()
        return "${tenths / 10}.${tenths % 10}"
    }
}
