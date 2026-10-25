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
    /** Portrait key art without text (TMDB's textless poster), for the tall Home hero. */
    val tallUrl: String? = null,
    /** The title drawn as transparent artwork (TMDB's logo), shown over the hero in place of text. */
    val logoUrl: String? = null,
    /** The video's width over height, for showing its frame in its own shape. */
    val frameAspect: Float? = null,
    /** Seed for the placeholder colours (usually the title). */
    val seed: String = "",
    /**
     * Continue Watching (Apple TV's Up Next): the frame where it was left, in ms, taken from
     * [frameSource] ahead of any other art. Null shows the usual art.
     */
    val frameAtMs: Long? = null,
) {
    /**
     * Only a frame of the video exists: no poster, backdrop or still (a camera-roll clip, a saved
     * YouTube video), so there is nothing to lay out as backdrop plus poster.
     */
    val frameOnly: Boolean
        get() = posterUrl == null && tallUrl == null && backdropUrl == null && stillUrl == null && frameSource != null
}

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
    /** The source the video belongs to (lets a saved stream be removed). */
    val sourceId: Long? = null,
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
    /**
     * The local file (content:// or file://) the hero plays as a preview, from [previewStartMs]:
     * the video Play plays, when it is a short clip (a minute or less). Null otherwise.
     */
    val previewUri: String? = null,
    val previewStartMs: Long = 0,
    /** On the watchlist (the "+" button shows a check). */
    val onWatchlist: Boolean = false,
    /** Starred (the hero's star is filled). */
    val favorite: Boolean = false,
    /** The video Delete removes: a single file on this phone; null hides Delete. */
    val deleteVideoId: Long? = null,
) {
    /** The library item key ("v:<id>" / "s:<showKey>"), which the watchlist stores. */
    val itemKey: String
        get() = when (val t = target) {
            is HeroTarget.Video -> "v:${t.id}"
            is HeroTarget.Show -> "s:${t.key}"
        }
}

@Immutable
data class HomeUi(
    val hero: List<HeroUi> = emptyList(),
    val continueWatching: List<VideoCardUi> = emptyList(),
    /** Movies, shows and videos saved with the hero's "+", most recently added first. */
    val watchlist: List<LibraryItemUi> = emptyList(),
    val recentlyAdded: List<VideoCardUi> = emptyList(),
    val movies: List<VideoCardUi> = emptyList(),
    val shows: List<ShowCardUi> = emptyList(),
    /** Home videos, unmatched files and streams. */
    val videos: List<VideoCardUi> = emptyList(),
    /** True when the library has no videos at all (show onboarding). */
    val isEmpty: Boolean = true,
    /** How many each Library link leads to, shown beside it. */
    val libraryCounts: LibraryCounts = LibraryCounts(),
)

/** The numbers beside Home's Library links; [bin] null while Android's bin hasn't been read. */
@Immutable
data class LibraryCounts(
    val recent: Int = 0,
    val watchlist: Int = 0,
    val favorites: Int = 0,
    val all: Int = 0,
    val movies: Int = 0,
    val shows: Int = 0,
    val bin: Int? = null,
)

enum class LibraryFilter(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    SHOWS("TV Shows"),
    VIDEOS("Videos"),

    /** The hero's "+" list and its star list: their own Library pages, not filter chips. */
    WATCHLIST("Watchlist"),
    FAVORITES("Favorites"),
    ;

    /** Shown as a chip on the Library page (Videos left the library; the lists have pages). */
    val isChip: Boolean get() = this == ALL || this == MOVIES || this == SHOWS
}

enum class LibrarySort(val label: String) { RECENT("Recently Added"), TITLE("Title"), YEAR("Year"), RATING("Rating") }

@Immutable
data class LibraryUi(
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sort: LibrarySort = LibrarySort.RECENT,
    val items: List<LibraryItemUi> = emptyList(),
    val counts: Map<LibraryFilter, Int> = emptyMap(),
    /**
     * Every video newest first, by when it reached the device, each card's second line saying
     * when ("Today · 2h 37m"): what "All Videos" shows when sorted by Recently added, as one
     * gapless grid. Empty otherwise.
     */
    val timeline: List<VideoCardUi> = emptyList(),
    /** [timeline] by the month the videos arrived ("October", "March 2024"), as Photos groups. */
    val timelineMonths: List<TimelineMonth> = emptyList(),
)

/** One month of the timeline: its heading and its videos, newest first. */
@Immutable
data class TimelineMonth(val title: String, val cards: List<VideoCardUi>)

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
    /** A file on this phone (device storage or a picked folder) that Delete can remove. */
    val deletable: Boolean = false,
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

/**
 * A folder in Browse. [path] is relative to its source root ("" = root); for a live listing of a
 * network source it is what that source's NetworkBrowser gave (a media server's item id).
 */
@Immutable
data class FolderUi(
    val sourceId: Long,
    val path: String,
    val name: String,
    val videoCount: Int = 0,
    /** Shown instead of the video count when that isn't known (live listings): "Library", "Folder". */
    val note: String? = null,
)

@Immutable
data class Crumb(val label: String, val path: String)

@Immutable
data class BrowseUi(
    /** DEVICE first, then FOLDER, then the network sources (SMB, FTP, …, Plex) by type. */
    val sources: List<SourceUi> = emptyList(),
    /** Saved network streams. */
    val streams: List<VideoCardUi> = emptyList(),
    /** Most recently changed first. */
    val playlists: List<PlaylistUi> = emptyList(),
)

/** A playlist as Browse lists it. */
@Immutable
data class PlaylistUi(
    val id: Long,
    val name: String,
    /** Videos the library shows (missing files are left out until they are back). */
    val itemCount: Int = 0,
    /** Art of its first video; null while it has none. */
    val artwork: Artwork? = null,
)

/** One playlist's videos in playlist order (route Playlist). */
@Immutable
data class PlaylistDetailsUi(
    val id: Long,
    val name: String,
    val videos: List<VideoCardUi> = emptyList(),
)

/**
 * Contents of one folder of a source. Device, folder and SMB sources come from the library; the
 * other network sources are listed live by their NetworkBrowser, their videos matched to the
 * library's ([videos]) or, not scanned yet, given as [files].
 */
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
    /** Live listings only: playable files the library doesn't hold (yet); [AppActions.playRemote] plays them. */
    val files: List<RemoteFileUi> = emptyList(),
) {
    val isEmpty: Boolean get() = folders.isEmpty() && videos.isEmpty() && files.isEmpty()
}

/** A video file of a live network listing that isn't in the library. */
@Immutable
data class RemoteFileUi(
    val name: String,
    /** The URI the player opens (and a scan would store it under). */
    val uri: String,
    /** "1.4 GB". */
    val subtitle: String? = null,
    val artwork: Artwork = Artwork(),
)

@Immutable
data class SearchUi(
    val query: String = "",
    val results: List<LibraryItemUi> = emptyList(),
    /** Music that matches, one section each, best first (one search over videos and music). */
    val songs: List<MusicResultUi> = emptyList(),
    val albums: List<MusicResultUi> = emptyList(),
    val artists: List<MusicResultUi> = emptyList(),
    /** Searches the user ran, newest first, shown while the field is empty. */
    val recent: List<String> = emptyList(),
    /** The library's biggest genres, as tiles to browse by while the field is empty (Apple TV). */
    val genres: List<GenreTileUi> = emptyList(),
) {
    val hasMusic: Boolean get() = songs.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()
    val isEmpty: Boolean get() = results.isEmpty() && !hasMusic
}

/** A genre to browse by in Search: its name, how many titles, and one of their backdrops. */
@Immutable
data class GenreTileUi(val name: String, val count: Int, val artwork: Artwork)

/** A song, album or artist in search results; tapping it plays [songIds] from [startIndex]. */
@Immutable
data class MusicResultUi(
    val key: String,
    val title: String,
    /** "Song · Queen", "Album · Queen", "Artist". */
    val subtitle: String,
    /** Cover file or embedded picture; null for none. */
    val artUri: String?,
    val songIds: List<Long>,
    val startIndex: Int = 0,
    /** Artists show round art, like Apple Music. */
    val round: Boolean = false,
)

@Immutable
data class ScanUi(
    /** Files are being scanned (pull-to-refresh spinner, "Rescan" disabled). */
    val running: Boolean = false,
    /** Posters and details are being fetched after a scan; never blocks a rescan. */
    val fetchingArtwork: Boolean = false,
    /** "Scanning Device storage · 214 found", "Fetching posters 12/80" */
    val label: String? = null,
    /** 0–1, or null when indeterminate. */
    val progress: Float? = null,
)

@Immutable
data class PermissionUi(
    /** Some or all device videos are visible (the video permission, limited access or All files access). */
    val granted: Boolean = false,
    /** Android 14 "limited access" (only some videos visible). */
    val partial: Boolean = false,
    /** Settings › "Scan device storage" is on, so missing access hides videos the user expects. */
    val deviceScanning: Boolean = true,
) {
    /** Device storage can't be scanned, or only partly: the UI says so with a way to fix it. */
    val needsAttention: Boolean get() = deviceScanning && (!granted || partial)
}

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

/** Recent searches: [query] first, no repeats (ignoring case), at most [max]. */
internal fun withRecentSearch(recent: List<String>, query: String, max: Int = 8): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return recent
    return (listOf(q) + recent.filterNot { it.equals(q, ignoreCase = true) }).take(max)
}
