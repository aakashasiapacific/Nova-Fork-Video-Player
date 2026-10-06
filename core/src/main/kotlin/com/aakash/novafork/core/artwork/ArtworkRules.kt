package com.aakash.novafork.core.artwork

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

    fun pick(videoFileName: String, siblingNames: List<String>, isEpisode: Boolean, season: Int? = null): LocalArtPick {
        TODO("core agent")
    }

    /** Show-level art in a show folder: "poster|folder|cover" and "fanart|backdrop|background". */
    fun pickShowArt(showFolderNames: List<String>): LocalArtPick {
        TODO("core agent")
    }

    fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
}
