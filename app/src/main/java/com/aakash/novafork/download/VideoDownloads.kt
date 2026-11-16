package com.aakash.novafork.download

import android.content.Context
import android.content.Intent
import android.util.Log
import com.aakash.novafork.data.db.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Copies of network videos made on the phone (owner): a video on a share or a media server is
 * downloaded into Movies/Nova Fork by [DownloadService], where the device scan finds it, to watch
 * without the network. [progress] tells the title's page how far each one is.
 */
object VideoDownloads {
    private const val TAG = "VideoDownloads"

    private val _progress = MutableStateFlow<Map<Long, Float>>(emptyMap())

    /** Videos (by id) being downloaded, with how much has arrived: 0 to 1, or -1 when the size isn't known. */
    val progress: StateFlow<Map<Long, Float>> = _progress.asStateFlow()

    /**
     * Whether a video of [type] can be downloaded: SMB shares (read with smbj) and the sources the
     * app plays over plain HTTP (WebDAV, UPnP and the media servers, which hand out the original
     * file). FTP, SFTP and NFS only play through libVLC's own modules; streams are often live.
     */
    fun canDownload(type: SourceType?): Boolean = type in DOWNLOADABLE

    /** Starts downloading [videoId] (nothing new if it is already on its way). */
    fun start(context: Context, videoId: Long) {
        val intent = Intent(context, DownloadService::class.java)
            .setAction(DownloadService.ACTION_START)
            .putExtra(DownloadService.EXTRA_VIDEO_ID, videoId)
        try {
            context.startForegroundService(intent)
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: only asked for with the app on screen.
            Log.w(TAG, "Download service refused", e)
        }
    }

    internal fun report(videoId: Long, fraction: Float) {
        _progress.update { it + (videoId to fraction) }
    }

    internal fun finished(videoId: Long) {
        _progress.update { it - videoId }
    }

    private val DOWNLOADABLE = setOf(
        SourceType.SMB,
        SourceType.WEBDAV,
        SourceType.UPNP,
        SourceType.JELLYFIN,
        SourceType.EMBY,
        SourceType.PLEX,
    )
}
