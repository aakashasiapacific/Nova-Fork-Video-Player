package com.aakash.novafork.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.request.videoFramePercent
import coil.size.Scale
import com.aakash.novafork.ui.model.Artwork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A small bitmap of the playing video's library artwork for the playback notification and the
 * media session. Landscape art first (the system's media controls are wide): episode still,
 * backdrop, poster, then a frame of a local video. Mostly served from Coil's caches, since the
 * library showed the same art; anything slower than [TIMEOUT_MS] is skipped.
 */
internal class PlaybackArtwork(context: Context, private val imageLoader: ImageLoader) {
    private val appContext = context.applicationContext

    /** Null when nothing loads in time. Main-safe. */
    suspend fun load(artwork: Artwork): Bitmap? {
        val frameSource = artwork.frameSource
        val candidates = listOfNotNull(artwork.stillUrl, artwork.backdropUrl, artwork.posterUrl, frameSource).distinct()
        if (candidates.isEmpty()) return null
        return withTimeoutOrNull(TIMEOUT_MS) {
            for (candidate in candidates) {
                val bitmap = loadOne(candidate, isFrame = candidate == frameSource)
                if (bitmap != null) return@withTimeoutOrNull bitmap
            }
            null
        }
    }

    private suspend fun loadOne(data: String, isFrame: Boolean): Bitmap? {
        val request = ImageRequest.Builder(appContext)
            .data(data)
            .size(SIZE_PX)
            .scale(Scale.FIT)
            // Notifications and the media session parcel the bitmap; hardware bitmaps can't be
            // parcelled before Android 10.
            .allowHardware(false)
            .apply { if (isFrame) videoFramePercent(FRAME_PERCENT) }
            .build()
        val result = try {
            imageLoader.execute(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Artwork didn't load", e)
            return null
        }
        return ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
    }

    private companion object {
        const val TAG = "PlaybackArtwork"
        const val TIMEOUT_MS = 4_000L

        /** The longer side; the system scales media art down further. */
        const val SIZE_PX = 512

        /** Same frame as the library's cards (10% in). */
        const val FRAME_PERCENT = 0.1
    }
}
