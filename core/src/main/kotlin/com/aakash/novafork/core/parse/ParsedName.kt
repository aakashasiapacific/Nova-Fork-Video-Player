package com.aakash.novafork.core.parse

enum class ParsedKind { MOVIE, EPISODE, UNKNOWN }

/**
 * What a video file name says about itself. Produced by [NameParser.parse].
 *
 * MOVIE   – a title (usually with a year) and no episode marker.
 * EPISODE – a season/episode marker (S01E02, 1x02, "Season 1/02 - …"), an anime-style absolute
 *           episode ("[Group] Show - 12 [1080p]") or a daily-show air date.
 * UNKNOWN – nothing recognisable (e.g. "VID_20240501_123456.mp4"): a home video.
 */
data class ParsedName(
    val kind: ParsedKind,
    /** Clean title: the movie title or the show title. Never blank; falls back to the base name. */
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /** Last episode of a multi-episode file ("S01E01E02" or "S01E01-E03"), else null. */
    val episodeEnd: Int? = null,
    /** Episode title when the name carries one ("Breaking Bad - 1x05 - Gray Matter.avi"). */
    val episodeTitle: String? = null,
    /** Anime-style absolute episode number ("[SubsPlease] Frieren - 12 (1080p).mkv" → 12). */
    val absoluteEpisode: Int? = null,
    /** Daily shows: "yyyy-MM-dd" ("The.Daily.Show.2024.05.12.mkv"). */
    val airDate: String? = null,
    /** "2160p", "1080p", "720p", "576p", "480p" when present. */
    val resolution: String? = null,
)
