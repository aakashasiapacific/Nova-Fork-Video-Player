package com.aakash.novafork.core.parse

import java.util.Locale

/** What a folder name contributes: a title ("The Office (2005)"), a season ("Season 2"), or both. */
internal class FolderInfo(
    val title: String?,
    val year: Int?,
    val season: Int?,
    val hasJunk: Boolean,
    /** Library roots like "TV Shows" or "Downloads": never a show or movie title. */
    val isGeneric: Boolean,
)

/** The show a file belongs to, read from its folders nearest-first. */
internal class FolderContext(val season: Int?, val show: FolderInfo?)

internal object FolderNames {
    private val I = RegexOption.IGNORE_CASE
    private const val SEASON_WORDS = "season|series|saison|staffel|temporada|stagione|seizoen|sezon"

    /** The whole folder is a season: "Season 2", "Season 02 (2006)", "S01", "Series 3". */
    private val SEASON_FOLDER = Regex("^(?:(?:$SEASON_WORDS)[ ._-]*(\\d{1,3})|s(\\d{1,3}))(?![\\p{N}])(?:$|[^\\p{L}].*)", I)
    private val SPECIALS = Regex("^specials?$", I)

    /** A season inside a longer name: "The.Office.US.S02.1080p", "Breaking Bad Season 3". */
    private val SEASON_INSIDE = Regex("(?<![\\p{L}\\p{N}])(?:s(\\d{1,2})|(?:$SEASON_WORDS)[ ._-]*(\\d{1,2}))(?![\\p{L}\\p{N}])", I)

    /** Disc structure and split folders that sit between a title folder and the video. */
    private val CONTAINER = Regex("^(?:bdmv|stream|video_ts|playlist|clipinf|(?:dis[ck]|cd|dvd)[ ._-]?\\d{1,2})$", I)

    private val MOVIE_FOLDERS = setOf("movies", "movie", "films", "film", "cinema")

    private val GENERIC = MOVIE_FOLDERS + setOf(
        "tv", "tv shows", "tv-shows", "tvshows", "tv series", "tv-series", "shows", "series", "television",
        "anime", "cartoons", "kids", "documentaries", "documentary", "music videos", "concerts",
        "videos", "video", "my videos", "media", "multimedia", "library", "movies & tv", "movies and tv",
        "downloads", "download", "complete", "completed", "incoming", "torrents", "unsorted", "new folder",
        "dcim", "camera", "pictures", "screen recordings", "screenrecorder", "screenshots", "recordings",
        "whatsapp", "whatsapp video", "telegram", "telegram video", "bluetooth",
        "public", "share", "shared", "home", "storage", "emulated", "0", "sdcard", "usb", "nas", "volume1",
        "plex", "jellyfin", "emby", "kodi", "misc", "other", "others", "temp", "tmp", "backup",
    )

    fun isMovieFolder(name: String): Boolean = name.trim().lowercase(Locale.ROOT) in MOVIE_FOLDERS

    fun isContainer(name: String): Boolean = CONTAINER.matches(name.trim())

    fun info(name: String): FolderInfo {
        val trimmed = name.trim()
        if (SPECIALS.matches(trimmed)) return FolderInfo(null, null, 0, hasJunk = false, isGeneric = false)
        SEASON_FOLDER.find(trimmed)?.let { m ->
            val season = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt()
            return FolderInfo(null, null, season, hasJunk = false, isGeneric = false)
        }
        val prepared = ReleaseTokens.prepare(trimmed)
        val seasonMatch = SEASON_INSIDE.find(prepared.text)
        val titlePart = if (seasonMatch != null) prepared.text.substring(0, seasonMatch.range.first) else prepared.text
        val analyzed = ReleaseTokens.analyze(titlePart)
        val title = analyzed.title.takeIf { t -> t.any { it.isLetterOrDigit() } }
        return FolderInfo(
            title = title,
            year = analyzed.year,
            season = seasonMatch?.let { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toIntOrNull() },
            hasJunk = analyzed.hasJunk || prepared.bracketJunk,
            isGeneric = trimmed.lowercase(Locale.ROOT) in GENERIC,
        )
    }

    /**
     * Season and show folder for an episode: the nearest season folder, and the first folder
     * with a title (normally the one above "Season N"). Stops at a library root ("TV Shows").
     */
    fun context(parentFolders: List<String>): FolderContext {
        var season: Int? = null
        for (raw in parentFolders) {
            if (isContainer(raw)) continue
            val info = info(raw)
            if (season == null) season = info.season
            if (info.isGeneric) return FolderContext(season, null)
            if (info.title != null) return FolderContext(season, info)
        }
        return FolderContext(season, null)
    }

    /** The folder that names a movie: the nearest one, skipping disc structure ("BDMV/STREAM"). */
    fun movieFolder(parentFolders: List<String>): FolderInfo? =
        parentFolders.firstOrNull { !isContainer(it) }?.let(::info)?.takeIf { it.title != null && !it.isGeneric }
}

/** Camera, phone and screen-recorder file names: always home videos. */
internal object CameraNames {
    private val I = RegexOption.IGNORE_CASE

    private val PATTERNS = listOf(
        // IMG_1234, VID_20240501_123456, PXL_20240101_101010123, MVI_1234, DSC_0001, DSCF0001, DJI_0001,
        // GOPR0001, GX010001, MAH00001, VID-20240501-WA0001 (WhatsApp), IMG_E1234 (iPhone edit)
        Regex("^_?(?:vid|img|pxl|mvi|mov|dsc[fn]?|dji|gopr|gp|gx|gh|mah|cimg|sam|wp|lrv|mvimg|imag|video)[_-]?e?\\d{4,}", I),
        Regex("^(?:p\\d{7}|c\\d{4})$", I),
        Regex("^whatsapp[ _-]?(?:video|image|gif)", I),
        Regex("^screen[ _-]?record", I),
        Regex("^screen[ _-]?\\d{8}", I),
        Regex("^screencast", I),
        Regex("^record[ _-]?\\d{4}", I),
        Regex("^rpreplay", I),
        Regex("^fullsizerender", I),
        Regex("^(?:snapchat|signal|telegram)[ _-]\\d", I),
        Regex("^(?:inshot|youcut|capcut|kinemaster|vn|lv_\\d)[ _-]\\d{6,}", I),
        Regex("^trim\\.[0-9a-f-]{8,}", I),
        // 20240501_123456, 20240501123456, 2024-05-01 12-34-56, 2024_05_01_12_34_56
        Regex("^\\d{8}[ _-]?\\d{6}"),
        Regex("^\\d{4}[-_.]\\d{2}[-_.]\\d{2}[ _-]\\d{2}[-_.:]?\\d{2}[-_.:]?\\d{2}"),
        // Epoch-millisecond names written by messaging apps.
        Regex("^\\d{10,13}$"),
    )

    fun matches(baseName: String): Boolean {
        val name = baseName.trim()
        return PATTERNS.any { it.containsMatchIn(name) }
    }
}
