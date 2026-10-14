package com.aakash.novafork.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.aakash.novafork.NovaApp
import com.aakash.novafork.R
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns playback: the libVLC player, the media and the play queue (all in [controller]), plus a
 * [android.media.session.MediaSession] for the system's media controls. [PlayerActivity] binds
 * to it (same process, [LocalBinder]), renders the controller's state and lends it the video
 * surface while it is visible, and reports [onPlayerShown] / [onPlayerHidden].
 *
 * When the player screen goes away while something plays and background audio is wanted, the
 * service starts itself in the foreground with a MediaStyle notification, so the sound outlives
 * the screen. Paused in the background it leaves the foreground and keeps a dismissible
 * notification. It goes back to living only as long as the screen's binding once the screen
 * returns, and drops the notification when playback stops or the player is closed.
 *
 * A cast (see [PlayerController.startCasting]) keeps the service in the foreground whenever the
 * screen is gone, paused or not, with a "Casting to …" notification: the stream to the device
 * comes from this process, so the cast ends with it.
 */
class PlaybackService : Service() {

    /** Same-process binding: hands the service itself to the player screen. */
    inner class LocalBinder : Binder() {
        val service: PlaybackService get() = this@PlaybackService
    }

    private data class NotificationContent(
        val title: String,
        val text: String?,
        val playing: Boolean,
        val ongoing: Boolean,
        val artwork: Bitmap?,
        val casting: Boolean,
    )

    private val binder = LocalBinder()

    // Main.immediate like the activity's lifecycleScope the controller used to run in.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Created in onCreate; the same one for the service's whole life. */
    lateinit var controller: PlayerController
        private set

    private lateinit var session: PlaybackSession
    private lateinit var notificationManager: NotificationManager
    private lateinit var artworkLoader: PlaybackArtwork

    /** The player screen is visible (started), whether or not in picture-in-picture. */
    private var playerVisible = false

    private var foreground = false

    /** startForegroundService() was called and no startForeground() has followed yet. */
    private var foregroundOwed = false

    /** Android 12+ refused the foreground: no retry until the user does something. */
    private var foregroundRefused = false

    /** What the notification shows now; null while there is none. */
    private var posted: NotificationContent? = null

    /** The last start request; 0 while the service only lives through its binding. */
    private var lastStartId = 0
    private var destroyed = false

    private var artworkBitmap: Bitmap? = null
    private var artworkJob: Job? = null

    private val showPlayerIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            this,
            REQUEST_SHOW_PLAYER,
            PlayerIntents.showPlayer(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private val sessionControls = object : PlaybackSession.Controls {
        override fun play() = onUserCommand { controller.play() }

        override fun pause() = onUserCommand { controller.pause() }

        override fun stop() = onUserCommand {
            // A headset's stop with the player on screen only pauses; without it, it ends playback.
            if (playerVisible) controller.pause() else controller.close()
        }

        override fun skipToNext() = onUserCommand { controller.playNext() }

        override fun skipToPrevious() = onUserCommand { controller.playPrevious() }

        override fun seekBy(deltaMs: Long) = onUserCommand { controller.seekBy(deltaMs) }

        override fun seekTo(positionMs: Long) = onUserCommand { controller.seekTo(positionMs) }
    }

    override fun onCreate() {
        super.onCreate()
        val graph = NovaApp.graph(this)
        controller = PlayerController(this, graph, scope, onFinished = ::onPlaybackFinished)
        notificationManager = checkNotNull(getSystemService(NotificationManager::class.java))
        artworkLoader = PlaybackArtwork(this, graph.imageLoader)
        createChannel()
        // A notification a previous process left behind (paused in the background, then killed).
        notificationManager.cancel(NOTIFICATION_ID)
        session = PlaybackSession(this, showPlayerIntent, sessionControls)
        // Plain Main, not immediate: a state change inside a controller call is handled after
        // that call, never in the middle of it.
        scope.launch(Dispatchers.Main) { controller.state.collect { sync() } }
        scope.launch(Dispatchers.Main) { controller.playRequested.collect { sync() } }
        scope.launch(Dispatchers.Main) { controller.artwork.collect { loadArtwork(it) } }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // The screen just left, or the user tapped the notification: the foreground may be tried again.
        foregroundRefused = false
        val action = intent?.action
        val owed = when (action) {
            ACTION_BACKGROUND -> foregroundOwed
            // The notification's Play is started with getForegroundService().
            ACTION_PLAY -> !foreground
            else -> false
        }
        if (owed) {
            // startForeground() is owed whatever follows; sync() below leaves the foreground
            // again when it isn't wanted.
            goForeground(notificationContent(controller.state.value, ongoing = true))
        }
        when (action) {
            ACTION_PLAY -> controller.play()
            ACTION_PAUSE -> controller.pause()
            ACTION_REWIND -> controller.seekBy(-PlaybackSession.SEEK_STEP_MS)
            ACTION_FORWARD -> controller.seekBy(PlaybackSession.SEEK_STEP_MS)
            ACTION_STOP -> controller.close()
            // Swiped away while paused in the background.
            ACTION_DISMISS -> if (!playerVisible) controller.close()
        }
        sync()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        artworkJob?.cancel()
        controller.release()
        scope.cancel()
        session.release()
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        notificationManager.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    /**
     * The player screen is visible again; call after attaching its video surface. The picture
     * comes back and the notification goes.
     */
    fun onPlayerShown() {
        if (destroyed) return
        playerVisible = true
        foregroundRefused = false
        controller.exitBackground()
        controller.onScreenVisibilityChanged(true)
        sync()
    }

    /**
     * The player screen is no longer visible (left, covered, screen off). With [allowBackground]
     * and background audio wanted (setting or "Play in background") while something plays, or a
     * cast running, playback goes on without it: true. False when the screen should pause
     * playback as before.
     */
    fun onPlayerHidden(allowBackground: Boolean): Boolean {
        if (destroyed) return false
        playerVisible = false
        controller.onScreenVisibilityChanged(false)
        val background = allowBackground && controller.canPlayInBackground && startInBackground()
        if (background) controller.enterBackground()
        sync()
        return background
    }

    /**
     * Makes this a started service in the foreground, so it outlives the screen's binding. Called
     * while the screen is leaving: Android 12+ still allows a foreground start then.
     */
    private fun startInBackground(): Boolean {
        if (foreground) return true
        return try {
            startForegroundService(Intent(this, PlaybackService::class.java).setAction(ACTION_BACKGROUND))
            foregroundOwed = true
            foregroundRefused = false
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: the screen pauses playback as before.
            Log.w(TAG, "Background playback refused", e)
            PlayerDiagnostics.record("background: foreground service refused")
            false
        }
    }

    private fun onPlaybackFinished() {
        controller.stop()
        sync()
    }

    private fun onUserCommand(command: () -> Unit) {
        // Media buttons and the system's media controls may bring the service back into the foreground.
        foregroundRefused = false
        command()
        sync()
    }

    /** Brings the media session, the foreground state and the notification in line with the player. */
    private fun sync() {
        if (destroyed) return
        val state = controller.state.value
        val hasContent = controller.hasContent
        val playRequested = controller.playRequested.value
        val background = hasContent && controller.inBackground
        // A cast stays in the foreground while paused too: the device waits on this process.
        val castWaiting = controller.isCasting && state.error == null
        val wantsForeground = background && (playRequested || castWaiting)
        session.update(
            state = state,
            title = titleOf(state),
            hasContent = hasContent,
            playRequested = playRequested,
            active = hasContent && (playerVisible || controller.inBackground),
            artwork = artworkBitmap,
        )
        if (!wantsForeground) foregroundRefused = false
        when {
            wantsForeground && !foreground && !foregroundRefused ->
                goForeground(notificationContent(state, ongoing = true))
            background -> {
                if (foreground && !wantsForeground) {
                    // Paused: the notification stays and can be swiped away.
                    stopForeground(STOP_FOREGROUND_DETACH)
                    foreground = false
                    PlayerDiagnostics.record("background: paused, foreground left")
                }
                post(notificationContent(state, ongoing = foreground))
            }
            else -> {
                if (foreground) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    foreground = false
                }
                if (posted != null) {
                    notificationManager.cancel(NOTIFICATION_ID)
                    posted = null
                }
                stopIfStarted()
            }
        }
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
            foregroundOwed = false
            posted = content
            PlayerDiagnostics.record("background: foreground")
        } catch (e: IllegalStateException) {
            // Android 12+: ForegroundServiceStartNotAllowedException.
            onForegroundRefused(e, content)
        } catch (e: SecurityException) {
            onForegroundRefused(e, content)
        }
    }

    /** The sound goes on as long as the system lets it; the controls are still shown. */
    private fun onForegroundRefused(error: Exception, content: NotificationContent) {
        Log.w(TAG, "Foreground refused", error)
        PlayerDiagnostics.record("background: foreground refused")
        foregroundRefused = true
        post(content.copy(ongoing = false))
    }

    private fun post(content: NotificationContent) {
        if (content == posted) return
        notificationManager.notify(NOTIFICATION_ID, buildNotification(content))
        posted = content
    }

    /** Back to living through the screen's binding only (or ending, without one). */
    private fun stopIfStarted() {
        val startId = lastStartId
        // False when a newer start is on its way; its onStartCommand ends up here again.
        if (startId != 0 && stopSelfResult(startId)) lastStartId = 0
    }

    private fun loadArtwork(artwork: Artwork?) {
        artworkJob?.cancel()
        if (artworkBitmap != null) {
            artworkBitmap = null
            sync()
        }
        if (artwork == null) return
        artworkJob = scope.launch(Dispatchers.Main) {
            val bitmap = artworkLoader.load(artwork) ?: return@launch
            artworkBitmap = bitmap
            sync()
        }
    }

    private fun notificationContent(state: PlayerUiState, ongoing: Boolean) = NotificationContent(
        title = titleOf(state),
        text = state.error ?: state.castingTo?.let { "Casting to $it" } ?: state.subtitle,
        // As the media session reports it: loading after a play counts as playing.
        playing = state.isPlaying || (state.isBuffering && controller.playRequested.value),
        ongoing = ongoing,
        artwork = artworkBitmap,
        casting = state.castingTo != null,
    )

    private fun buildNotification(content: NotificationContent): Notification {
        val playPause = if (content.playing) {
            action(R.drawable.ic_pause, "Pause", ACTION_PAUSE)
        } else {
            action(R.drawable.ic_play_arrow, "Play", ACTION_PLAY)
        }
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (content.casting) R.drawable.ic_cast_connected else R.drawable.ic_play_arrow)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(showPlayerIntent)
            .setDeleteIntent(serviceIntent(ACTION_DISMISS))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(content.ongoing)
            .addAction(action(R.drawable.ic_replay_10, "Back 10 seconds", ACTION_REWIND))
            .addAction(playPause)
            .addAction(action(R.drawable.ic_forward_10, "Forward 10 seconds", ACTION_FORWARD))
            .addAction(action(R.drawable.ic_close, "Stop", ACTION_STOP))
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

    private fun serviceIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        // Play may have to bring the service back into the foreground, so it is started as one.
        return if (action == ACTION_PLAY) {
            PendingIntent.getForegroundService(this, REQUEST_ACTION, intent, flags)
        } else {
            PendingIntent.getService(this, REQUEST_ACTION, intent, flags)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Controls for videos playing in the background"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun titleOf(state: PlayerUiState): String = state.title.ifBlank { DEFAULT_TITLE }

    private companion object {
        const val TAG = "PlaybackService"
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1001
        const val DEFAULT_TITLE = "Nova Fork"

        const val REQUEST_SHOW_PLAYER = 1
        const val REQUEST_ACTION = 2

        const val ACTION_BACKGROUND = "com.aakash.novafork.playback.BACKGROUND"
        const val ACTION_PLAY = "com.aakash.novafork.playback.PLAY"
        const val ACTION_PAUSE = "com.aakash.novafork.playback.PAUSE"
        const val ACTION_REWIND = "com.aakash.novafork.playback.REWIND"
        const val ACTION_FORWARD = "com.aakash.novafork.playback.FORWARD"
        const val ACTION_STOP = "com.aakash.novafork.playback.STOP"
        const val ACTION_DISMISS = "com.aakash.novafork.playback.DISMISS"
    }
}
