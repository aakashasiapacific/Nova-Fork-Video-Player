package com.aakash.novafork.ui.model

import androidx.compose.runtime.Immutable
import com.aakash.novafork.data.db.SourceType

/**
 * Image candidates for one item, best first. Every field is a full URL/URI or null.
 * Components try them in order and fall back to a generated gradient placeholder (see ui.common).
 */
@Immutable
data class Artwork(
    /** 2:3 poster (TMDB w500 or a local poster.jpg). */
    val posterUrl: String? = null,
    /** 16:9 backdrop (TMDB w1280 or a local fanart.jpg). */
    val backdropUrl: String? = null,
    /** 16:9 episode still / local thumb. */
    val stillUrl: String? = null,
    /** A local video URI (content:// or file://) to grab a frame from when nothing else exists. Null for network files. */
    val frameSource: String? = null,
    /** Seed for the placeholder colours (usually the title). */
    val seed: String = "",
)

enum class MediaKind { MOVIE, EPISODE, VIDEO, STREAM }

@Immutable
data class VideoCardUi(
    val id: Long,
    /** Movie title, episode title ("Pilot"), or file title for home videos. */
    val title: String,
    /** Second line: "2021 · 2h 35m", "S1 · E3", "Stream", "Movies/Action". */
    val subtitle: String? = null,
    val artwork: Artwork = Artwork(),
    val kind: MediaKind = MediaKind.VIDEO,
    val year: Int? = null,
    /** TMDB vote average 0–10, null when unknown. */
    val rating: Float? = null,
    val durationMs: Long? = null,
    /** 0–1 when partly watched, else null. */
    val progress: Float? = null,
    val watched: Boolean = false,
    /** Episodes: show title, season, episode. */
    val showTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /** "Device", "Folder name", "NAS", "Stream". */
    val sourceLabel: String? = null,
)

@Immutable
data class ShowCardUi(
    /** core LibraryKeys.showKey */
    val key: String,
    val title: String,
    val artwork: Artwork = Artwork(),
    val year: Int? = null,
    val rating: Float? = null,
    val seasonCount: Int = 0,
    val episodeCount: Int = 0,
    val unwatchedCount: Int = 0,
)

@Immutable
sealed interface LibraryItemUi {
    val key: String
    val title: String

    data class Video(val card: VideoCardUi) : LibraryItemUi {
        override val key: String get() = "v:${card.id}"
        override val title: String get() = card.title
    }

    data class Show(val card: ShowCardUi) : LibraryItemUi {
        override val key: String get() = "s:${card.key}"
        override val title: String get() = card.title
    }
}

@Immutable
sealed interface HeroTarget {
    data class Video(val id: Long) : HeroTarget
    data class Show(val key: String) : HeroTarget
}

/** A featured item for the big backdrop banner on Home. */
@Immutable
data class HeroUi(
    val target: HeroTarget,
    val title: String,
    val overview: String? = null,
    val artwork: Artwork = Artwork(),
    /** "2024 · 2h 46m · Science Fiction, Adventure" */
    val metaLine: String? = null,
    val rating: Float? = null,
    val genres: List<String> = emptyList(),
    val progress: Float? = null,
    /** What the Play button plays (the movie, or the next-up episode of a show). */
    val playVideoId: Long? = null,
    /** "Play", "Resume", "Play S2 · E4". */
    val playLabel: String = "Play",
)

@Immutable
data class HomeUi(
    val hero: List<HeroUi> = emptyList(),
    val continueWatching: List<VideoCardUi> = emptyList(),
    val recentlyAdded: List<VideoCardUi> = emptyList(),
    val movies: List<VideoCardUi> = emptyList(),
    val shows: List<ShowCardUi> = emptyList(),
    /** Home videos, unmatched files and streams. */
    val videos: List<VideoCardUi> = emptyList(),
    /** True when the library has no videos at all (show onboarding). */
    val isEmpty: Boolean = true,
)

enum class LibraryFilter(val label: String) { ALL("All"), MOVIES("Movies"), SHOWS("TV shows"), VIDEOS("Videos") }

enum class LibrarySort(val label: String) { RECENT("Recently added"), TITLE("Title"), YEAR("Year"), RATING("Rating") }

@Immutable
data class LibraryUi(
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sort: LibrarySort = LibrarySort.RECENT,
    val items: List<LibraryItemUi> = emptyList(),
    val counts: Map<LibraryFilter, Int> = emptyMap(),
)

@Immutable
data class VideoDetailsUi(
    val id: Long,
    val kind: MediaKind,
    val title: String,
    val originalTitle: String? = null,
    val tagline: String? = null,
    val overview: String? = null,
    val year: Int? = null,
    val runtimeLabel: String? = null,
    val rating: Float? = null,
    val genres: List<String> = emptyList(),
    val artwork: Artwork = Artwork(),
    val progress: Float? = null,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    /** "Resume from 42:10" / "Play" */
    val playLabel: String = "Play",
    val watched: Boolean = false,
    // Episode context
    val showKey: String? = null,
    val showTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeCode: String? = null,
    val airDate: String? = null,
    val nextEpisode: VideoCardUi? = null,
    // File facts
    val fileName: String = "",
    /** "Movies/Action" or "NAS › Movies/Action" */
    val location: String = "",
    val sizeLabel: String? = null,
    /** "3840×2160 · 4K" */
    val resolutionLabel: String? = null,
    val sourceLabel: String = "",
    /** True when the info came from TMDB. */
    val isMatched: Boolean = false,
)

@Immutable
data class EpisodeUi(
    val videoId: Long,
    val season: Int,
    val episode: Int,
    /** Episode title or the file name when unknown. */
    val title: String,
    val overview: String? = null,
    val artwork: Artwork = Artwork(),
    val durationMs: Long? = null,
    val progress: Float? = null,
    val watched: Boolean = false,
    val airDate: String? = null,
    /** "E3" or "E3–E4" */
    val code: String = "",
)

@Immutable
data class SeasonUi(
    val number: Int,
    /** "Season 1", "Specials" */
    val title: String,
    val posterUrl: String? = null,
    val episodes: List<EpisodeUi> = emptyList(),
)

@Immutable
data class ShowDetailsUi(
    val key: String,
    val title: String,
    val overview: String? = null,
    val tagline: String? = null,
    val year: Int? = null,
    val rating: Float? = null,
    val genres: List<String> = emptyList(),
    val artwork: Artwork = Artwork(),
    val seasons: List<SeasonUi> = emptyList(),
    /** First unwatched / in-progress episode. */
    val nextUp: EpisodeUi? = null,
    /** "Resume S1 · E3", "Play S1 · E1" */
    val playLabel: String = "Play",
    val isMatched: Boolean = false,
)

@Immutable
data class SourceUi(
    val id: Long,
    val type: SourceType,
    val name: String,
    /** "Internal storage", "content tree path", "nas.local/Movies", URL */
    val detail: String,
    val videoCount: Int = 0,
    val isScanning: Boolean = false,
    val error: String? = null,
)

/** A folder in Browse. [path] is relative to its source root ("" = root). */
@Immutable
data class FolderUi(
    val sourceId: Long,
    val path: String,
    val name: String,
    val videoCount: Int = 0,
)

@Immutable
data class Crumb(val label: String, val path: String)

@Immutable
data class BrowseUi(
    /** DEVICE first, then FOLDER, then SMB sources. */
    val sources: List<SourceUi> = emptyList(),
    /** Saved network streams. */
    val streams: List<VideoCardUi> = emptyList(),
)

/** Contents of one folder of a source (device/folder sources come from the library, SMB is listed live). */
@Immutable
data class BrowseDirUi(
    val sourceId: Long,
    val sourceType: SourceType,
    val path: String,
    val title: String,
    val crumbs: List<Crumb> = emptyList(),
    val folders: List<FolderUi> = emptyList(),
    val videos: List<VideoCardUi> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@Immutable
data class SearchUi(
    val query: String = "",
    val results: List<LibraryItemUi> = emptyList(),
)

@Immutable
data class ScanUi(
    val running: Boolean = false,
    /** "Scanning Device storage · 214 found", "Fetching posters 12/80" */
    val label: String? = null,
    /** 0–1, or null when indeterminate. */
    val progress: Float? = null,
)

@Immutable
data class PermissionUi(
    val granted: Boolean = false,
    /** Android 14 "limited access" (only some videos visible). */
    val partial: Boolean = false,
)

enum class KeyStatus { MISSING, UNKNOWN, CHECKING, VALID, INVALID, UNREACHABLE }

@Immutable
data class SettingsUi(
    val settings: com.aakash.novafork.data.Settings = com.aakash.novafork.data.Settings(),
    val hasBuiltInKey: Boolean = false,
    val keyStatus: KeyStatus = KeyStatus.UNKNOWN,
    val versionName: String = "",
    val sources: List<SourceUi> = emptyList(),
    /** "All files access" (subtitles and artwork next to device videos). */
    val allFilesAccess: Boolean = false,
    /** Android 11+: the toggle opens the system screen. Older: covered by the storage permission. */
    val canRequestAllFilesAccess: Boolean = false,
)

@Immutable
data class IdentifyResultUi(
    val tmdbId: Int,
    val isTv: Boolean,
    val title: String,
    val year: Int? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
)
