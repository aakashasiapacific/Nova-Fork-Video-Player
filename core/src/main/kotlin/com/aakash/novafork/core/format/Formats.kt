package com.aakash.novafork.core.format

object Formats {
    /** Player clock: "4:05", "12:34", "1:02:03". Negative values clamp to 0. */
    fun clock(ms: Long): String {
        TODO("core agent")
    }

    /** Runtime label: 155 → "2h 35m", 45 → "45m", 120 → "2h". */
    fun runtime(minutes: Int): String {
        TODO("core agent")
    }

    /** "1.4 GB", "700 MB", "12 KB" (1024-based, one decimal for GB). */
    fun size(bytes: Long): String {
        TODO("core agent")
    }

    /** "S01E03", "S01E03-E04" when [episodeEnd] is greater than [episode]. */
    fun episodeCode(season: Int, episode: Int, episodeEnd: Int? = null): String {
        TODO("core agent")
    }

    /** "Time left" label: "1h 12m left", "8m left", "Under a minute left". */
    fun remaining(positionMs: Long, durationMs: Long): String {
        TODO("core agent")
    }
}
