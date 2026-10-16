package com.aakash.novafork.sources

import com.aakash.novafork.core.artwork.ArtworkRules
import com.aakash.novafork.core.library.VideoFiles
import java.util.Locale

/** Which folder listing a chosen artwork file belongs to. */
internal enum class ArtPlace {
    /** The folder that holds the video. */
    VIDEO_FOLDER,

    /** The show folder one level above a season folder. */
    SHOW_FOLDER,
}

internal data class ArtFile(val place: ArtPlace, val name: String)

internal data class LocalArtChoice(
    val poster: ArtFile? = null,
    val backdrop: ArtFile? = null,
    val thumb: ArtFile? = null,
)

/**
 * One folder's image names, indexed once for every video in the folder. A camera folder holds
 * tens of thousands of photos; matching each of them against each video's name made a device
 * scan cost videos × photos string work.
 */
internal class FolderImages(names: Collection<String>) {
    val all: List<String> = names.toList()

    /** Indices into [all], ordered by their lower-case base name. */
    private val order: IntArray
    private val bases: Array<String>

    init {
        val lowered = Array(all.size) { i -> VideoFiles.baseName(all[i]).lowercase(Locale.ROOT) }
        order = all.indices.sortedBy { lowered[it] }.toIntArray()
        bases = Array(order.size) { i -> lowered[order[i]] }
    }

    /** The names whose lower-case base name starts with [prefix] (lower case), in [all]'s order. */
    fun startingWith(prefix: String): List<String> {
        // Names sharing a prefix sit next to each other in sorted order, from the first one not below it.
        var low = 0
        var high = bases.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (bases[mid] < prefix) low = mid + 1 else high = mid
        }
        var end = low
        while (end < bases.size && bases[end].startsWith(prefix)) end++
        if (end == low) return emptyList()
        return (low until end).map { order[it] }.sorted().map { all[it] }
    }
}

/**
 * Applies core [ArtworkRules] to the folder listings a scanner already has. Scanners turn the
 * chosen names into URIs of their own kind.
 *
 * Movies: generic names (poster.jpg, fanart.jpg, folder.jpg…) only count when the folder holds a
 * single video, otherwise every movie in a shared "Movies" folder would get the same poster;
 * "<base>-poster" style names always count.
 *
 * Episodes (the folder is "Season N" / "S01" / "Specials", or the name has an SxxEyy marker):
 * thumb from the episode's own art, poster = season poster or else the show poster, backdrop =
 * show fanart. The show folder is the season folder's parent, or the folder itself when there is
 * no season folder.
 */
internal object LocalArtwork {
    private val SEASON_FOLDER = Regex(
        "^(?:(?:season|series|saison|staffel|temporada|stagione)[ ._-]*(\\d{1,3})|s(\\d{1,3}))(?!\\d)(?:$|[^\\p{L}].*)",
        RegexOption.IGNORE_CASE,
    )
    private val SPECIALS = Regex("^specials?$", RegexOption.IGNORE_CASE)
    private val EPISODE_MARKER = Regex("(?<![\\p{L}\\p{N}])s(\\d{1,2})[ ._-]?e\\d{1,4}", RegexOption.IGNORE_CASE)

    /** Season number of a season folder name ("Season 2" → 2, "Specials" → 0), null for other folders. */
    fun seasonOfFolder(folderName: String): Int? {
        val name = folderName.trim()
        if (SPECIALS.matches(name)) return 0
        val match = SEASON_FOLDER.matchEntire(name) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }.toIntOrNull()
    }

    /**
     * @param folderName name of the folder holding the video (null when it has none, e.g. a share root).
     * @param folderImages image names in that folder (other names are ignored).
     * @param folderVideoCount videos in that folder, samples not counted.
     * @param showFolderImages image names in the folder above; only called for videos in a season folder.
     */
    fun choose(
        videoFileName: String,
        folderName: String?,
        folderImages: List<String>,
        folderVideoCount: Int,
        showFolderImages: () -> List<String>,
    ): LocalArtChoice = choose(
        videoFileName = videoFileName,
        folderName = folderName,
        folderImages = folderImages,
        imagesStartingWith = { base -> folderImages.filter { VideoFiles.baseName(it).lowercase(Locale.ROOT).startsWith(base) } },
        folderVideoCount = folderVideoCount,
        showFolderImages = showFolderImages,
    )

    /** The same with the folder's images indexed once for all its videos (device scans). */
    fun choose(
        videoFileName: String,
        folderName: String?,
        folderImages: FolderImages,
        folderVideoCount: Int,
        showFolderImages: () -> List<String>,
    ): LocalArtChoice = choose(
        videoFileName = videoFileName,
        folderName = folderName,
        folderImages = folderImages.all,
        imagesStartingWith = folderImages::startingWith,
        folderVideoCount = folderVideoCount,
        showFolderImages = showFolderImages,
    )

    private fun choose(
        videoFileName: String,
        folderName: String?,
        folderImages: List<String>,
        imagesStartingWith: (String) -> List<String>,
        folderVideoCount: Int,
        showFolderImages: () -> List<String>,
    ): LocalArtChoice {
        val base = VideoFiles.baseName(videoFileName).lowercase(Locale.ROOT)
        val markerSeason = EPISODE_MARKER.find(base)?.groupValues?.get(1)?.toIntOrNull()
        val folderSeason = folderName?.let(::seasonOfFolder)

        if (markerSeason == null && folderSeason == null) {
            val candidates = if (folderVideoCount <= 1) folderImages else imagesStartingWith(base)
            val pick = ArtworkRules.pick(videoFileName, candidates, isEpisode = false)
            return LocalArtChoice(
                poster = pick.poster.inVideoFolder(),
                backdrop = pick.backdrop.inVideoFolder(),
                thumb = pick.thumb.inVideoFolder(),
            )
        }

        val season = markerSeason ?: folderSeason
        val own = ArtworkRules.pick(videoFileName, folderImages, isEpisode = true, season = season)
        val thumb = own.thumb.inVideoFolder()
        if (folderSeason == null) {
            val show = ArtworkRules.pickShowArt(folderImages)
            return LocalArtChoice(
                poster = (own.poster ?: show.poster).inVideoFolder(),
                backdrop = show.backdrop.inVideoFolder(),
                thumb = thumb,
            )
        }

        val showImages = showFolderImages()
        val show = ArtworkRules.pickShowArt(showImages)
        // Jellyfin keeps a season's poster as "Season 1/poster.jpg" (or folder.jpg).
        val seasonFolderArt = ArtworkRules.pickShowArt(folderImages)
        val seasonPoster = own.poster.inVideoFolder()
            ?: ArtworkRules.pick(videoFileName, showImages, isEpisode = true, season = season).poster.inShowFolder()
            ?: seasonFolderArt.poster.inVideoFolder()
        return LocalArtChoice(
            poster = seasonPoster ?: show.poster.inShowFolder(),
            backdrop = show.backdrop.inShowFolder() ?: seasonFolderArt.backdrop.inVideoFolder(),
            thumb = thumb,
        )
    }

    private fun String?.inVideoFolder(): ArtFile? = this?.let { ArtFile(ArtPlace.VIDEO_FOLDER, it) }

    private fun String?.inShowFolder(): ArtFile? = this?.let { ArtFile(ArtPlace.SHOW_FOLDER, it) }
}
