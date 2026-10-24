package com.aakash.novafork.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.aakash.novafork.NovaApp
import com.aakash.novafork.R
import com.aakash.novafork.player.PlaybackArtwork
import com.aakash.novafork.player.PlaybackSession
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps music playing while the app is in the background: a foreground service with a
 * MediaStyle notification (previous, play/pause, next, close) and a media session for the lock
 * screen, headsets and cars. It follows [MusicPlayer]: in the foreground while a song plays, a
 * dismissible notification while paused, gone once the queue is closed.
 */
class MusicService : Service() {

    private data class NotificationContent(
        val title: String,
        val text: String?,
        val playing: Boolean,
        val ongoing: Boolean,
        val hasPrevious: Boolean,
        val hasNext: Boolean,
        val artwork: Bitmap?,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: MusicPlayer
    private lateinit var session: PlaybackSession
    private lateinit var notificationManager: NotificationManager
    private lateinit var artworkLoader: PlaybackArtwork

    private var foreground = false

    /** A start was handled (startForeground called): only then may sync() stop the service. */
    private var started = false
    private var posted: NotificationContent? = null
    private var destroyed = false
    private var artUri: String? = null
    private var artwork: Bitmap? = null
    private var artworkJob: Job? = null

    private val openAppIntent: PendingIntent by lazy {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        PendingIntent.getActivity(this, REQUEST_OPEN, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private val controls = object : PlaybackSession.Controls {
        override fun play() = player.play()

        override fun pause() = player.pause()

        override fun stop() = player.close()

        override fun skipToNext() = player.next()

        override fun skipToPrevious() = player.previous()

        override fun seekBy(deltaMs: Long) = player.seekTo(player.state.value.positionMs + deltaMs)

        override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    }

    override fun onCreate() {
        super.onCreate()
        val graph = NovaApp.graph(this)
        player = graph.musicPlayer
        notificationManager = checkNotNull(getSystemService(NotificationManager::class.java))
        artworkLoader = PlaybackArtwork(this, graph.imageLoader)
        createChannel()
        session = PlaybackSession(this, openAppIntent, controls, seekButtons = false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start comes through startForegroundService(): startForeground() is owed now,
        // and sync() below leaves the foreground again when nothing plays.
        goForeground(content(ongoing = true))
        if (!started) {
            started = true
            // Only now: a sync before the first start could stop the service while Android still
            // waits for startForeground(), which it punishes with a crash.
            // Plain Main: a state change inside a player call is handled after that call.
            scope.launch(Dispatchers.Main) { player.state.collect { sync() } }
        }
        when (intent?.action) {
            ACTION_PLAY -> player.play()
            ACTION_PAUSE -> player.pause()
            ACTION_NEXT -> player.next()
            ACTION_PREVIOUS -> player.previous()
            ACTION_STOP -> player.close()
        }
        sync()
        return START_NOT_STICKY
    }

    /** Music plays on in the background, until the app is cleared from recents (owner's choice). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Stops, but keeps the queue: the next start shows it paused on the Audio tab.
        player.close(forget = false)
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        notificationManager.cancel(NOTIFICATION_ID)
        posted = null
        stopSelf()
    }

    override fun onDestroy() {
        destroyed = true
        artworkJob?.cancel()
        scope.cancel()
        session.release()
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        notificationManager.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    /** Media session, foreground state and notification in line with the player. */
    private fun sync() {
        if (destroyed) return
        val state = player.state.value
        val now = state.nowPlaying
        loadArtwork(now?.artUri)
        session.update(
            state = sessionState(state),
            title = now?.title ?: DEFAULT_TITLE,
            hasContent = now != null,
            playRequested = state.playRequested,
            active = state.active,
            artwork = artwork,
        )
        when {
            !state.active -> {
                if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
                foreground = false
                notificationManager.cancel(NOTIFICATION_ID)
                posted = null
                stopSelf()
            }
            // Between songs and while a failed one is skipped too, not only while sound plays:
            // a service that left the foreground can't come back to it from the background.
            state.playRequested -> {
                if (!foreground) goForeground(content(ongoing = true)) else post(content(ongoing = true))
            }
            else -> {
                if (foreground) {
                    // Paused: the notification stays and can be swiped away.
                    stopForeground(STOP_FOREGROUND_DETACH)
                    foreground = false
                }
                post(content(ongoing = false))
            }
        }
    }

    private fun sessionState(state: MusicPlayerState) = PlayerUiState(
        title = state.nowPlaying?.title.orEmpty(),
        subtitle = state.nowPlaying?.artist,
        isPlaying = state.isPlaying,
        isBuffering = state.isBuffering,
        positionMs = state.positionMs,
        durationMs = state.durationMs,
        seekable = state.durationMs > 0,
        hasPrevious = true,
        hasNext = state.hasNext,
        error = state.error,
    )

    private fun content(ongoing: Boolean): NotificationContent {
        val state = player.state.value
        return NotificationContent(
            title = state.nowPlaying?.title ?: DEFAULT_TITLE,
            text = state.error ?: state.nowPlaying?.artist,
            playing = state.playRequested,
            ongoing = ongoing,
            hasPrevious = state.nowPlaying != null,
            hasNext = state.hasNext,
            artwork = artwork,
        )
    }

    private fun goForeground(content: NotificationContent) {
        val notification = buildNotification(content)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foreground = true
            posted = content
        } catch (e: IllegalStateException) {
            // Android 12+: ForegroundServiceStartNotAllowedException. The sound goes on while the
            // system lets it; the controls are still shown.
            Log.w(TAG, "Foreground refused", e)
            post(content.copy(ongoing = false))
        } catch (e: SecurityException) {
            Log.w(TAG, "Foreground refused", e)
            post(content.copy(ongoing = false))
        }
    }

    private fun post(content: NotificationContent) {
        if (content == posted) return
        notificationManager.notify(NOTIFICATION_ID, buildNotification(content))
        posted = content
    }

    private fun loadArtwork(uri: String?) {
        if (uri == artUri) return
        artUri = uri
        artworkJob?.cancel()
        artwork = null
        if (uri == null) return
        artworkJob = scope.launch(Dispatchers.Main) {
            val bitmap = artworkLoader.load(Artwork(posterUrl = uri)) ?: return@launch
            if (artUri != uri) return@launch
            artwork = bitmap
            sync()
        }
    }

    private fun buildNotification(content: NotificationContent): Notification {
        val playPause = if (content.playing) {
            action(R.drawable.ic_pause, "Pause", ACTION_PAUSE)
        } else {
            action(R.drawable.ic_play_arrow, "Play", ACTION_PLAY)
        }
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_audiotrack)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(openAppIntent)
            .setDeleteIntent(serviceIntent(ACTION_STOP))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(content.ongoing)
            .addAction(action(R.drawable.ic_skip_previous, "Previous", ACTION_PREVIOUS))
            .addAction(playPause)
            .addAction(action(R.drawable.ic_skip_next, "Next", ACTION_NEXT))
            .addAction(action(R.drawable.ic_close, "Close", ACTION_STOP))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.token)
                    .setShowActionsInCompactView(0, 1, 2),
            )
        content.artwork?.let { builder.setLargeIcon(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun action(icon: Int, title: String, action: String): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(this, icon), title, serviceIntent(action)).build()

    /** Every action starts the service as a foreground one: it may have stopped since the notification was posted. */
    private fun serviceIntent(action: String): PendingIntent =
        PendingIntent.getForegroundService(
            this,
            REQUEST_ACTION,
            Intent(this, MusicService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Music", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Controls for the music playing"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "MusicService"
        private const val CHANNEL_ID = "music"
        private const val NOTIFICATION_ID = 1002
        private const val DEFAULT_TITLE = "Nova Fork"
        private const val REQUEST_OPEN = 1
        private const val REQUEST_ACTION = 2

        private const val ACTION_PLAY = "com.aakash.novafork.music.PLAY"
        private const val ACTION_PAUSE = "com.aakash.novafork.music.PAUSE"
        private const val ACTION_NEXT = "com.aakash.novafork.music.NEXT"
        private const val ACTION_PREVIOUS = "com.aakash.novafork.music.PREVIOUS"
        private const val ACTION_STOP = "com.aakash.novafork.music.STOP"

        /** Called when a queue starts, with the app on screen (a foreground-service start is allowed then). */
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, MusicService::class.java))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException: the music plays while the app is open.
                Log.w(TAG, "Music service refused", e)
            }
        }
    }
}
