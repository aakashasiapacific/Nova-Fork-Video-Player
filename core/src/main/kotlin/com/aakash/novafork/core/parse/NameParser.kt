package com.aakash.novafork.core.parse

import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.core.match.TitleMatcher
import java.util.Locale

/**
 * Turns release-style file names into a [ParsedName].
 *
 * Must handle at least (see NameParserTest):
 *  - "Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-FLUX.mkv" → MOVIE "Dune Part Two" 2024
 *  - "The Matrix (1999).mp4", "Blade Runner 2049 (2017) [1080p].mkv" (2049 is part of the title)
 *  - "The.Office.US.S02E03.720p.mkv" → EPISODE "The Office US" S2E3
 *  - "breaking bad - 1x05 - Gray Matter.avi", "Show S01E01E02.mkv", "Show.S01E01-E03.mkv"
 *  - "[SubsPlease] Sousou no Frieren - 12 (1080p) [A1B2C3D4].mkv" → EPISODE absoluteEpisode 12
 *  - "Season 2/03 - Title.mkv" with parentFolders ["Season 2", "The Office (2005)"] → EPISODE
 *    "The Office" 2005 S2E3 (show title and year come from the folder above the season folder)
 *  - "E05.mkv" / "05.mkv" inside "Season 1" folders, "Specials" folders → season 0
 *  - "The.Daily.Show.2024.05.12.mkv" → EPISODE airDate "2024-05-12"
 *  - "VID_20240501_123456.mp4", "IMG_1234.MOV", "PXL_20240101_101010.mp4" → UNKNOWN
 *  - Movie folder fallback: "movie.mkv" inside "Inception (2010)" → MOVIE "Inception" 2010
 *  - Strips release junk: resolution, source (WEB-DL, BluRay, HDTV…), codecs (x264, HEVC…), audio
 *    (DDP5.1, AAC…), HDR flags, group names ("-FLUX", "[YTS.MX]"), "PROPER", "REPACK", "EXTENDED"…
 */
object NameParser {
    /** File names that say nothing about the movie: the folder names it instead. */
    private val GENERIC_FILE = Regex(
        "^(?:movie|video|film|main|feature|title(?: ?t?\\d{1,3})?|vts \\d{2} \\d|\\d{5})$",
        RegexOption.IGNORE_CASE,
    )

    /** An unknown extension still looks like one ("ogm", "rmvb"), but not like "E05" or "x264". */
    private val EXTENSION_LIKE = Regex("^[A-Za-z][A-Za-z0-9]{1,3}$")
    private val LETTER_DIGITS = Regex("^[A-Za-z]\\d+$")

    /** "Spy x Family S2 - 05", "Title Season 2 - 05", "Title 2nd Season - 05". */
    private val SEASON_SUFFIX = Regex(
        "^(.*?)\\s+(?:s(\\d{1,2})|season\\s*(\\d{1,2})|(\\d{1,2})(?:st|nd|rd|th)\\s+season)$",
        RegexOption.IGNORE_CASE,
    )

    /**
     * @param fileName the file name including extension.
     * @param parentFolders folder names from the nearest parent outward,
     *   e.g. ["Season 02", "The Office (2005)", "TV Shows"]. May be empty.
     */
    fun parse(fileName: String, parentFolders: List<String> = emptyList()): ParsedName {
        val name = fileName.trim().substringAfterLast('/')
        val base = stripExtension(name).trim()
        val fallbackTitle = base.ifBlank { name }.ifBlank { "Video" }
        val resolution = ReleaseTokens.resolution(base)
        if (CameraNames.matches(base)) {
            return ParsedName(ParsedKind.UNKNOWN, title = fallbackTitle, resolution = resolution)
        }

        val prepared = ReleaseTokens.prepare(base)
        val folders = FolderNames.context(parentFolders)
        val parsed = EpisodeMarkers.find(prepared.text, hasSeasonContext = folders.season != null)
            ?.let { marker -> episode(prepared.text, marker, folders) }
            ?: movie(prepared, parentFolders)
        return parsed.copy(title = parsed.title.ifBlank { fallbackTitle }, resolution = resolution)
    }

    /** Release-junk-free, human title: dots/underscores → spaces, collapsed whitespace, Title Case kept as-is. */
    fun cleanTitle(raw: String): String {
        val trimmed = raw.trim()
        val withoutExtension =
            if (VideoFiles.extension(trimmed) in VideoFiles.EXTENSIONS) VideoFiles.baseName(trimmed) else trimmed
        return ReleaseTokens.clean(withoutExtension).ifBlank { trimmed }
    }

    private fun episode(text: String, marker: EpisodeMarker, folders: FolderContext): ParsedName? {
        val before = ReleaseTokens.analyze(text.substring(0, marker.start))
        var title = before.title
        var nameSeason = marker.season
        if (marker.numberOnly && nameSeason == null) {
            SEASON_SUFFIX.matchEntire(title)?.let { m ->
                title = m.groupValues[1].trim()
                nameSeason = (2..4).firstNotNullOfOrNull { m.groupValues[it].toIntOrNull() }
            }
        }

        var year = before.year
        val show = folders.show
        if (!title.hasText()) {
            // A bare date names no show; let the movie/home-video rules handle the file.
            if (marker.airDate != null) return null
            title = show?.title.orEmpty()
            if (year == null) year = show?.year
        } else if (year == null && show?.title != null && sameTitle(title, show.title)) {
            year = show.year
        }

        val episodeTitle = ReleaseTokens.episodeTitle(text.substring(marker.end))
        if (marker.airDate != null) {
            return ParsedName(ParsedKind.EPISODE, title = title, year = year, episodeTitle = episodeTitle, airDate = marker.airDate)
        }
        val season = nameSeason ?: folders.season
        return ParsedName(
            kind = ParsedKind.EPISODE,
            title = title,
            year = year,
            season = season ?: 1,
            episode = marker.episode,
            episodeEnd = marker.episodeEnd,
            episodeTitle = episodeTitle,
            // Without any season the number counts from the first episode of the show.
            absoluteEpisode = if (marker.numberOnly && season == null) marker.episode else null,
        )
    }

    private fun movie(prepared: Prepared, parentFolders: List<String>): ParsedName {
        val info = ReleaseTokens.analyze(prepared.text)
        var title = info.title
        var year = info.year
        var folderJunk = false
        val folder = FolderNames.movieFolder(parentFolders)
        val folderTitle = folder?.title
        if (folder != null && folderTitle != null) {
            val generic = !title.hasText() || GENERIC_FILE.matches(title)
            if (generic || sameTitle(title, folderTitle)) {
                if (generic) title = folderTitle
                if (year == null) year = folder.year
                folderJunk = folder.hasJunk
            }
        }
        val isMovie = year != null || info.hasJunk || prepared.bracketJunk || folderJunk ||
            parentFolders.any(FolderNames::isMovieFolder)
        return ParsedName(kind = if (isMovie) ParsedKind.MOVIE else ParsedKind.UNKNOWN, title = title, year = year)
    }

    private fun stripExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return name
        val extension = name.substring(dot + 1)
        val known = extension.lowercase(Locale.ROOT) in VideoFiles.EXTENSIONS
        val looksLikeOne = EXTENSION_LIKE.matches(extension) && !LETTER_DIGITS.matches(extension)
        return if (known || looksLikeOne) name.substring(0, dot) else name
    }

    private fun sameTitle(a: String, b: String): Boolean {
        val normalized = TitleMatcher.normalize(a)
        return normalized.isNotEmpty() && normalized == TitleMatcher.normalize(b)
    }

    private fun String.hasText(): Boolean = any { it.isLetterOrDigit() }
}
