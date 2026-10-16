package com.aakash.novafork.core.artwork

import java.util.Locale

/** File names (as given, not paths) chosen from a folder listing. Null = none found. */
data class LocalArtPick(
    val poster: String? = null,
    val backdrop: String? = null,
    /** Per-episode thumbnail / still. */
    val thumb: String? = null,
)

/**
 * Kodi/Jellyfin/Plex-style local artwork conventions. Matching is case-insensitive and accepts
 * jpg, jpeg, png and webp.
 *
 * Poster, in priority order:   "<base>-poster", "<base>.poster", "poster", "folder", "cover", "movie", "default"
 * Backdrop, in priority order: "<base>-fanart", "<base>-backdrop", "<base>-background", "fanart", "backdrop", "background", "art"
 * Thumb:                       "<base>-thumb", "<base>" (an image with exactly the video's base name)
 * where <base> is the video file name without extension.
 *
 * For episodes, [pick] only looks for per-episode art (thumb) and season posters
 * ("season02-poster", "season2-poster", "season-specials-poster" for season 0); show-level
 * poster/fanart come from [pickShowArt] on the show folder (the folder above "Season N").
 */
object ArtworkRules {
    val IMAGE_EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp")

    private val EXTENSION_ORDER = listOf("jpg", "jpeg", "png", "webp")

    fun pick(videoFileName: String, siblingNames: List<String>, isEpisode: Boolean, season: Int? = null): LocalArtPick {
        val images = imagesByStem(siblingNames)
        if (images.isEmpty()) return LocalArtPick()
        val base = baseName(videoFileName).lowercase(Locale.ROOT)
        val thumb = first(images, "$base-thumb", base)
        if (isEpisode) {
            return LocalArtPick(poster = first(images, *seasonPosterStems(season).toTypedArray()), thumb = thumb)
        }
        return LocalArtPick(
            poster = first(images, "$base-poster", "$base.poster", "poster", "folder", "cover", "movie", "default"),
            backdrop = first(images, "$base-fanart", "$base-backdrop", "$base-background", "fanart", "backdrop", "background", "art"),
            thumb = thumb,
        )
    }

    /** Show-level art in a show folder: "poster|folder|cover" and "fanart|backdrop|background". */
    fun pickShowArt(showFolderNames: List<String>): LocalArtPick {
        val images = imagesByStem(showFolderNames)
        if (images.isEmpty()) return LocalArtPick()
        return LocalArtPick(
            poster = first(images, "poster", "folder", "cover"),
            backdrop = first(images, "fanart", "backdrop", "background"),
        )
    }

    fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    private fun seasonPosterStems(season: Int?): List<String> {
        val specific = when {
            season == null || season < 0 -> emptyList()
            season == 0 -> listOf("season-specials-poster", "season00-poster", "season0-poster")
            else -> listOf("season" + season.toString().padStart(2, '0') + "-poster", "season$season-poster")
        }
        return (specific + "season-all-poster").distinct()
    }

    /** Lower-case stem → original names, best extension first ("poster.jpg" before "poster.png"). */
    private fun imagesByStem(names: List<String>): Map<String, List<String>> =
        names.asSequence()
            .filter(::isImage)
            .groupBy { baseName(it).lowercase(Locale.ROOT) }
            .mapValues { (_, group) ->
                group.sortedWith(compareBy({ EXTENSION_ORDER.indexOf(extension(it)) }, { it }))
            }

    private fun first(images: Map<String, List<String>>, vararg stems: String): String? =
        stems.firstNotNullOfOrNull { images[it]?.firstOrNull() }

    private fun baseName(name: String): String = if ('.' in name) name.substringBeforeLast('.') else name

    private fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
}
