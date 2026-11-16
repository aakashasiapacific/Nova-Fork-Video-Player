package com.aakash.novafork.ui.common

import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A video's own thumbnail as the phone made it: the picture Samsung Gallery and the Files app
 * show, which Android picks as the video's representative frame (not a blind 10% in, which is
 * often a white title card). Asked of the content provider, so it works for videos in the media
 * library; anything the provider can't give fails and the caller falls back to grabbing a frame.
 */
data class SystemThumbnail(val uri: String) {
    companion object {
        /** True where Android can be asked for it: Android 10+ and a content:// video. */
        fun supports(uri: String?): Boolean = uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.startsWith("content://")
    }
}

class SystemThumbnailFetcher(
    private val data: SystemThumbnail,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bitmap = withContext(Dispatchers.IO) {
            options.context.contentResolver.loadThumbnail(Uri.parse(data.uri), Size(SIZE_PX, SIZE_PX), null)
        }
        return DrawableResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<SystemThumbnail> {
        override fun create(data: SystemThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            SystemThumbnailFetcher(data, options)
    }

    private companion object {
        const val SIZE_PX = 640
    }
}
