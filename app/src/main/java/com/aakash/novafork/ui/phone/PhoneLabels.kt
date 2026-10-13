package com.aakash.novafork.ui.phone

import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.model.EpisodeUi
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.ShowCardUi
import com.aakash.novafork.ui.model.VideoCardUi

/*
 * Text the phone screens show, in the Apple TV app's wording ("S2, E3", "20m left", "2 hr 46 min").
 */

/** The player only resumes from positions past this (PlayerController.RESUME_MIN_MS). */
internal const val RESUME_MIN_MS = 10_000L

/** "S2, E3"; "E3" without a season; null without an episode number. */
internal fun episodeCode(season: Int?, episode: Int?): String? = when {
    episode == null || episode <= 0 -> season?.let { if (it == 0) "Specials" else "Season $it" }
    season == null -> "E$episode"
    else -> "S$season, E$episode"
}

/** Episodes are known by their show on cards ("Severance", "S2, E3"). */
internal fun VideoCardUi.cardTitle(): String {
    val show = showTitle
    return if (kind == MediaKind.EPISODE && !show.isNullOrBlank()) show else title
}

/** The caption under a 16:9 card: "S2, E3", "2021 · 2h 35m", "Stream". */
internal fun VideoCardUi.cardCaption(): String? = when (kind) {
    MediaKind.EPISODE -> episodeCode(season, episode) ?: subtitle
    else -> subtitle
}?.takeIf { it.isNotBlank() }

/** Continue Watching caption: "S2, E3 · 20m left" (movies: "1h 12m left"). */
internal fun VideoCardUi.continueCaption(): String? {
    val code = if (kind == MediaKind.EPISODE) episodeCode(season, episode) else null
    return metaLine(code, remainingLabel(durationMs, progress)).ifEmpty { cardCaption() }
}

/** "1h 12m left" for a partly watched item, else null. */
internal fun remainingLabel(durationMs: Long?, progress: Float?): String? {
    if (durationMs == null || progress == null || durationMs <= 0 || progress <= 0f) return null
    return Formats.remaining((durationMs * progress.coerceIn(0f, 1f)).toLong(), durationMs)
}

/** "47 min", "1 hr 2 min"; null when unknown. */
internal fun minutesLabel(durationMs: Long?): String? {
    if (durationMs == null || durationMs <= 0) return null
    if (durationMs < 60_000) return Formats.clock(durationMs)
    val minutes = ((durationMs + 30_000) / 60_000).toInt()
    return longRuntime(minutes)
}

/** 166 → "2 hr 46 min", 45 → "45 min", 120 → "2 hr". */
internal fun longRuntime(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$rest min"
        rest == 0 -> "$hours hr"
        else -> "$hours hr $rest min"
    }
}

/** The mapper's short runtime ("2h 46m", "45m") in the details wording ("2 hr 46 min"). */
internal fun longRuntimeLabel(short: String?): String? {
    if (short.isNullOrBlank()) return null
    val match = SHORT_RUNTIME.matchEntire(short.trim()) ?: return short
    val hours = match.groupValues[1].toIntOrNull() ?: 0
    val minutes = match.groupValues[2].toIntOrNull() ?: 0
    return longRuntime(hours * 60 + minutes)
}

private val SHORT_RUNTIME = Regex("""(?:(\d+)h)?\s*(?:(\d+)m)?""")

/** "1 Season", "3 Seasons". */
internal fun countLabel(count: Int, singular: String, plural: String = singular + "s"): String =
    "$count ${if (count == 1) singular else plural}"

/** Caption under a TV show card: "2019 · 2 Seasons". */
internal fun ShowCardUi.cardCaption(): String? =
    metaLine(
        year?.toString(),
        if (seasonCount > 0) countLabel(seasonCount, "Season") else null,
    ).ifEmpty { null }

/** "Movie · 2021", "TV Show", "Home Video", "Stream". */
internal fun VideoCardUi.kindLabel(): String = when (kind) {
    MediaKind.MOVIE -> metaLine("Movie", year?.toString())
    MediaKind.EPISODE -> metaLine("Episode", episodeCode(season, episode))
    MediaKind.VIDEO -> "Home Video"
    MediaKind.STREAM -> "Stream"
}

/** "EPISODE 3" (small caps are uppercased by SmallCapsText). */
internal fun EpisodeUi.smallCapsLabel(): String = when {
    episode <= 0 && code.isNotBlank() -> code
    episode <= 0 -> "Episode"
    else -> "Episode $episode"
}

/** The sort menu in the Apple TV wording. */
internal val LibrarySort.menuLabel: String
    get() = when (this) {
        LibrarySort.RECENT -> "Recently Added"
        LibrarySort.TITLE -> "Title"
        LibrarySort.YEAR -> "Release Date"
        LibrarySort.RATING -> "Rating"
    }

/** "4K" / "HD" from the mapper's "3840×2160 · 4K" resolution label; null for SD or unknown. */
internal fun qualityBadge(resolutionLabel: String?): String? = when {
    resolutionLabel == null -> null
    resolutionLabel.contains("4K") -> "4K"
    resolutionLabel.contains("HD") -> "HD"
    else -> null
}

/**
 * The hero's button label: the mapper's "Play S2 · E4" / "Resume" in the Apple TV wording, and
 * "Play" when the player would not resume (Settings › Resume playback off).
 */
internal fun heroPlayLabel(label: String, resumeEnabled: Boolean): String {
    val words = label.trim().replace(" · ", ", ")
    return if (!resumeEnabled && words.startsWith("Resume")) "Play" + words.removePrefix("Resume") else words.ifEmpty { "Play" }
}
