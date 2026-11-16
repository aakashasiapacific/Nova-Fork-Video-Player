package com.aakash.novafork.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.aakash.novafork.NovaApp
import com.aakash.novafork.R
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import kotlin.coroutines.coroutineContext

/**
 * Downloads network videos into Movies/Nova Fork (owner), one after another, in the foreground
 * with a progress notification that can cancel them. The file goes in through MediaStore (pending
 * until it is whole, so no half file shows up in other apps); when it is done the library looks
 * the device over again quietly and the copy appears with the rest. Reads an SMB share with the
 * app's smbj browser and everything else over HTTP with the login or token playback uses.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifications: NotificationManager

    /** Waiting to start, in order; the one running is [current]. */
    private val waiting = ArrayDeque<Long>()
    private var current: Long? = null
    private var currentTitle: String = ""
    private var job: Job? = null
    private var lastPostAt = 0L

    override fun onCreate() {
        super.onCreate()
        notifications = checkNotNull(getSystemService(NotificationManager::class.java))
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Videos copying to this phone"
                setShowBadge(false)
            },
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start is a startForegroundService(): startForeground() is owed now.
        goForeground(progressNotification(fraction = null))
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getLongExtra(EXTRA_VIDEO_ID, -1L)
                if (id >= 0 && id != current && id !in waiting) {
                    waiting.addLast(id)
                    VideoDownloads.report(id, 0f)
                }
            }
            ACTION_CANCEL -> {
                waiting.forEach(VideoDownloads::finished)
                waiting.clear()
                job?.cancel()
            }
        }
        next()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        waiting.forEach(VideoDownloads::finished)
        current?.let(VideoDownloads::finished)
        super.onDestroy()
    }

    /** Starts the next waiting download, or stops once nothing is left. */
    private fun next() {
        if (job?.isActive == true) return
        val id = waiting.removeFirstOrNull()
        if (id == null) {
            current = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        current = id
        currentTitle = ""
        job = scope.launch {
            val outcome = try {
                download(id)
            } catch (e: CancellationException) {
                null
            } catch (e: Exception) {
                Log.w(TAG, "Download failed", e)
                Outcome.Failed(e.message ?: e.javaClass.simpleName)
            }
            VideoDownloads.finished(id)
            when (outcome) {
                is Outcome.Done -> {
                    notifyResult(id, "Downloaded", outcome.title)
                    // The device scan picks the new file up; the library refreshes quietly.
                    val graph = NovaApp.graph(this@DownloadService)
                    graph.appScope.launch { graph.library.refreshAll() }
                }
                is Outcome.Failed -> notifyResult(id, "Download failed", outcome.message)
                null -> Unit
            }
            current = null
            next()
        }
    }

    private sealed interface Outcome {
        class Done(val title: String) : Outcome
        class Failed(val message: String) : Outcome
    }

    private suspend fun download(videoId: Long): Outcome = withContext(Dispatchers.IO) {
        val graph = NovaApp.graph(this@DownloadService)
        val item = graph.library.video(videoId) ?: return@withContext Outcome.Failed("The video is no longer in the library")
        val video = item.video
        val source = graph.library.sourceForVideo(videoId) ?: return@withContext Outcome.Failed("Its source was removed")
        if (!VideoDownloads.canDownload(source.type)) return@withContext Outcome.Failed("Videos from this source can't be downloaded")
        val title = item.meta?.title?.takeIf { it.isNotBlank() && video.season == null } ?: video.fileName
        withContext(Dispatchers.Main) {
            currentTitle = title
            post(progressNotification(fraction = null))
        }
        val input = open(source, video)
        input.stream.use { stream -> save(videoId, video, stream, input.length) }
        Outcome.Done(title)
    }

    private class Input(val stream: InputStream, val length: Long)

    /** The original file: from the share, or over HTTP with what playback signs in with. */
    private suspend fun open(source: SourceEntity, video: VideoEntity): Input {
        val graph = NovaApp.graph(this)
        if (source.type == SourceType.SMB) {
            val handle = graph.smb.open(video.uri)
            return Input(handle.stream, handle.length)
        }
        val hook = graph.playbackOptions[source.type]
        val playUri = hook?.playUri(source, video.uri) ?: video.uri
        val options = hook?.mediaOptions(source, video.uri).orEmpty()
        val url = playUri.toHttpUrlOrNull() ?: throw IOException("Not a web address: $playUri")
        val request = Request.Builder().url(url.newBuilder().username("").password("").build())
        // libVLC takes a WebDAV login inside the address; HTTP wants it as a header.
        if (url.username.isNotEmpty()) request.header("Authorization", Credentials.basic(url.username, url.password))
        for (option in options) {
            when {
                option.startsWith(":http-user-agent=") -> request.header("User-Agent", option.substringAfter('='))
                option.startsWith(":http-referrer=") -> request.header("Referer", option.substringAfter('='))
            }
        }
        // No cache (a film would fill it) and patience with slow servers.
        val client = graph.http.newBuilder()
            .cache(null)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
        val response = client.newCall(request.build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("The server answered ${response.code}")
        }
        val body = response.body ?: throw IOException("The server sent nothing")
        return Input(body.byteStream(), body.contentLength())
    }

    /** Writes [stream] into Movies/Nova Fork, pending until whole; removed again on failure or cancel. */
    private suspend fun save(videoId: Long, video: VideoEntity, stream: InputStream, length: Long) {
        val resolver = contentResolver
        val name = fileName(video)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType(name))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + FOLDER)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        // Android 9 and older want storage access for this, which the app doesn't ask for: the
        // insert then fails and the notification says so.
        val target: Uri = try {
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        } catch (e: SecurityException) {
            throw IOException("No access to the phone's Movies folder", e)
        } ?: throw IOException("Couldn't create the file in Movies")
        var complete = false
        try {
            val output = resolver.openOutputStream(target) ?: throw IOException("Couldn't write to Movies")
            output.use { out ->
                val buffer = ByteArray(BUFFER_BYTES)
                var copied = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    copied += read
                    reportProgress(videoId, if (length > 0) (copied.toFloat() / length).coerceIn(0f, 1f) else -1f)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(target, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            }
            complete = true
        } finally {
            if (!complete) {
                withContext(NonCancellable) {
                    try {
                        resolver.delete(target, null, null)
                    } catch (e: Exception) {
                        Log.w(TAG, "Couldn't remove a partial download", e)
                    }
                }
            }
        }
    }

    /** The page's figure on every step; the notification at most every [POST_INTERVAL_MS]. */
    private suspend fun reportProgress(videoId: Long, fraction: Float) {
        VideoDownloads.report(videoId, fraction)
        val now = SystemClock.elapsedRealtime()
        if (now - lastPostAt < POST_INTERVAL_MS) return
        lastPostAt = now
        withContext(Dispatchers.Main) { post(progressNotification(fraction.takeIf { it >= 0f })) }
    }

    private fun fileName(video: VideoEntity): String {
        val clean = video.fileName.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "Video" }
        return if (clean.substringAfterLast('.', "").isEmpty()) "$clean.mkv" else clean
    }

    private fun mimeType(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.takeIf { it.startsWith("video/") } ?: "video/x-matroska"
    }

    private fun goForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Foreground refused", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Foreground refused", e)
        }
    }

    private fun post(notification: Notification) {
        if (current != null) notifications.notify(NOTIFICATION_ID, notification)
    }

    /** "Downloading <title>", the bar (moving while the size is unknown), "and 2 more", Cancel. */
    private fun progressNotification(fraction: Float?): Notification {
        val more = waiting.size
        val text = buildString {
            if (fraction != null) append("${(fraction * 100).toInt()}%")
            if (more > 0) {
                if (isNotEmpty()) append(" · ")
                append("$more more waiting")
            }
        }
        val cancel = PendingIntent.getService(
            this,
            REQUEST_CANCEL,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(if (currentTitle.isEmpty()) "Downloading" else "Downloading $currentTitle")
            .setContentText(text.ifEmpty { null })
            .setContentIntent(openApp())
            .setProgress(PROGRESS_MAX, ((fraction ?: 0f) * PROGRESS_MAX).toInt(), fraction == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_close), "Cancel", cancel).build())
            .build()
    }

    private fun notifyResult(videoId: Long, title: String, text: String) {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        notifications.notify(RESULT_ID_BASE + (videoId % RESULT_IDS).toInt(), notification)
    }

    private fun openApp(): PendingIntent {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        return PendingIntent.getActivity(this, REQUEST_OPEN, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        private const val TAG = "DownloadService"
        internal const val ACTION_START = "com.aakash.novafork.download.START"
        internal const val ACTION_CANCEL = "com.aakash.novafork.download.CANCEL"
        internal const val EXTRA_VIDEO_ID = "video_id"

        /** Movies/<this>, as the owner asked. */
        private const val FOLDER = "Nova Fork"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1003
        private const val RESULT_ID_BASE = 2000
        private const val RESULT_IDS = 1000L
        private const val REQUEST_CANCEL = 1
        private const val REQUEST_OPEN = 2
        private const val BUFFER_BYTES = 256 * 1024
        private const val PROGRESS_MAX = 1000
        private const val POST_INTERVAL_MS = 1_000L
    }
}
