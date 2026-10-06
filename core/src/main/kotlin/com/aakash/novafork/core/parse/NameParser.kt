package com.aakash.novafork.core.parse

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
    /**
     * @param fileName the file name including extension.
     * @param parentFolders folder names from the nearest parent outward,
     *   e.g. ["Season 02", "The Office (2005)", "TV Shows"]. May be empty.
     */
    fun parse(fileName: String, parentFolders: List<String> = emptyList()): ParsedName {
        TODO("core agent")
    }

    /** Release-junk-free, human title: dots/underscores → spaces, collapsed whitespace, Title Case kept as-is. */
    fun cleanTitle(raw: String): String {
        TODO("core agent")
    }
}
