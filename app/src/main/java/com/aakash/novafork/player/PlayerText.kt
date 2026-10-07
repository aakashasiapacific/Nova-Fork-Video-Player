package com.aakash.novafork.player

import android.net.Uri
import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.core.tmdb.TmdbClient
import com.aakash.novafork.data.db.VideoKind
import com.aakash.novafork.data.db.VideoWithMeta
import com.aakash.novafork.ui.model.Artwork
import kotlin.math.roundToInt

/** The two lines the player shows for what is playing. */
internal class Titles(val title: String, val subtitle: String?)

/** Titles, labels and artwork the player derives from library entries and URIs. */
internal object PlayerText {

    /**
     * Movie: "Dune: Part Two" / "2024 · 4K". Episode: show title / "S1 · E3 · Pilot".
     * Anything else: file name / folder (or host). [size] fills in a resolution the scan didn't know.
     */
    fun titlesFor(entry: VideoWithMeta, size: VideoSize?): Titles {
        val video = entry.video
        val mainTitle = entry.meta?.title?.takeIf { it.isNotBlank() } ?: video.parsedTitle
        return when (video.kind) {
            VideoKind.MOVIE -> Titles(
                title = mainTitle,
                subtitle = joinDots(
                    (entry.meta?.year ?: video.parsedYear)?.toString(),
                    resolutionLabel(video.width ?: size?.width, video.height ?: size?.height),
                ),
            )
            VideoKind.EPISODE -> Titles(
                title = mainTitle,
                subtitle = joinDots(
                    video.season?.let { "S$it" },
                    video.episode?.let { episodeLabel(it, video.episodeEnd) },
                    video.episodeTitle,
                ),
            )
            VideoKind.VIDEO -> Titles(
                title = displayName(video.fileName),
                subtitle = video.folder.takeIf { it.isNotBlank() } ?: hostOf(video.uri),
            )
        }
    }

    /** TMDB first, local files as fallback (or the other way round with [preferLocal]). */
    fun artworkFor(entry: VideoWithMeta, seed: String, preferLocal: Boolean): Artwork {
        val video = entry.video
        val meta = entry.meta
        fun pick(tmdb: String?, local: String?): String? = if (preferLocal) local ?: tmdb else tmdb ?: local
        return Artwork(
            posterUrl = pick(TmdbClient.posterUrl(meta?.posterPath, "w342"), video.localPoster),
            backdropUrl = pick(TmdbClient.backdropUrl(meta?.backdropPath), video.localBackdrop),
            stillUrl = pick(TmdbClient.stillUrl(video.episodeStillPath), video.localThumb),
            frameSource = video.uri.takeIf { UriKind.of(it).isLocal },
            seed = seed,
        )
    }

    /** "Holiday.mp4" → "Holiday"; names that aren't video files ("News 24.7") stay whole. */
    fun displayName(fileName: String): String =
        if (VideoFiles.isVideo(fileName)) VideoFiles.baseName(fileName) else fileName

    /** Server name for SMB and network URIs, null for local ones. */
    fun hostOf(uri: String): String? = when (UriKind.of(uri)) {
        UriKind.SMB -> SmbUris.parse(uri)?.host
        UriKind.NETWORK -> Uri.parse(uri).host
        UriKind.CONTENT, UriKind.FILE -> null
    }

    /** "1×", "1.5×", "1.25×". */
    fun rateLabel(rate: Float): String {
        val hundredths = (rate * 100).roundToInt()
        val whole = hundredths / 100
        val fraction = hundredths % 100
        val text = when {
            fraction == 0 -> "$whole"
            fraction % 10 == 0 -> "$whole.${fraction / 10}"
            else -> "$whole.${fraction.toString().padStart(2, '0')}"
        }
        return "$text×"
    }

    private fun joinDots(vararg parts: String?): String? =
        parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }

    private fun episodeLabel(episode: Int, episodeEnd: Int?): String =
        if (episodeEnd != null && episodeEnd > episode) "E$episode–E$episodeEnd" else "E$episode"

    private fun resolutionLabel(width: Int?, height: Int?): String? {
        if (width == null || height == null || width <= 0 || height <= 0) return null
        val long = maxOf(width, height)
        val short = minOf(width, height)
        return when {
            long >= 3200 || short >= 2000 -> "4K"
            long >= 2400 || short >= 1400 -> "1440p"
            long >= 1800 || short >= 1000 -> "1080p"
            long >= 1200 || short >= 700 -> "720p"
            short >= 560 -> "576p"
            short >= 420 -> "480p"
            else -> "SD"
        }
    }
}
