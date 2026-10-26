package com.aakash.novafork.player

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.aakash.novafork.NovaApp
import com.aakash.novafork.ui.adaptive.rememberDeviceLayout
import com.aakash.novafork.ui.player.PlayerScreen
import com.aakash.novafork.ui.theme.NovaPlayerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Full-screen player in its own task, with picture-in-picture. Started through [PlayerIntents].
 * Playback lives in [PlaybackService]: this screen binds to it, renders its controller's state,
 * lends it the video surface while visible and tells it when it is shown and hidden, which is
 * where background audio starts and ends.
 */
class PlayerActivity : ComponentActivity(), PlayerController.Host {

    private var service: PlaybackService? = null

    /** The last volume key down went to the player's indicator; its key up is swallowed too. */
    private var volumeKeyHandled = false
    private val connected = MutableStateFlow<PlayerController?>(null)
    private val controller: PlayerController? get() = connected.value
    private var bound = false

    private lateinit var pip: PipController
    private lateinit var subtitlePicker: ActivityResultLauncher<Array<String>>
    private var videoLayout: VLCVideoLayout? = null

    /** The intent to act on once the service is connected, with the state it was restored with. */
    private var pendingIntent: Intent? = null
    private var pendingRestore: Bundle? = null

    /** Our own document picker is opening: leaving now is not the user heading home, so no PiP. */
    private var pickingSubtitle = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val playback = (binder as? PlaybackService.LocalBinder)?.service ?: return
            onConnected(playback)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Same process: only happens when the whole process goes.
            service = null
            connected.value = null
        }
    }

    override val supportsPictureInPicture: Boolean get() = pip.isSupported

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = NovaApp.graph(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        volumeControlStream = AudioManager.STREAM_MUSIC
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // The player fades away as it closes (it is pulled down, or back), instead of sliding.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
        }
        pip = PipController(this) { controller?.togglePlay() }
        pip.register()
        subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pickingSubtitle = false
            controller?.onSubtitleFilePicked(uri)
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val current = controller
                    if (current != null) current.onBackPressed() else finish()
                }
            },
        )

        setContent {
            val controller by connected.collectAsStateWithLifecycle()
            val settings by graph.settings.settings.collectAsStateWithLifecycle()
            NovaPlayerTheme {
                // Until the service is bound (a moment) the window's black background shows.
                val current = controller
                if (current != null) {
                    val state by current.state.collectAsStateWithLifecycle()
                    PlayerScreen(
                        state = state,
                        actions = current,
                        layout = rememberDeviceLayout(settings.layoutMode),
                        copyFrame = { onCopied ->
                            val layout = videoLayout
                            if (layout == null) onCopied(null) else FrameCapture.copy(layout, onCopied)
                        },
                    ) {
                        AndroidView(
                            factory = { context -> VLCVideoLayout(context).also { onVideoLayoutCreated(it) } },
                            modifier = Modifier
                                .fillMaxSize()
                                // For a smooth shrink into the pop-up window (PipController).
                                .onGloballyPositioned { pip.videoBounds = it.boundsInWindow().toAndroidRect() },
                            onRelease = { layout -> onVideoLayoutReleased(layout) },
                        )
                    }
                }
            }
        }

        pendingIntent = intent
        pendingRestore = savedInstanceState
        bound = true
        if (!bindService(Intent(this, PlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)) {
            PlayerDiagnostics.record("playback service unavailable")
            finish()
        }
    }

    private fun onConnected(playback: PlaybackService) {
        if (isDestroyed) return
        service = playback
        val controller = playback.controller
        controller.attachHost(this)
        connected.value = controller
        observe(controller)
        attachToService()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.onHostResumed()
        val intent = pendingIntent
        val restore = pendingRestore
        pendingIntent = null
        pendingRestore = null
        if (intent != null) handleIntent(controller, intent, restore)
    }

    private fun observe(controller: PlayerController) {
        val graph = NovaApp.graph(this)
        lifecycleScope.launch {
            controller.state
                // Casting, the video is on the TV: this screen may go off as usual.
                .map { (it.isPlaying || it.isBuffering) && it.castingTo == null }
                .distinctUntilChanged()
                .collect { active -> keepScreenOn(active) }
        }
        lifecycleScope.launch {
            combine(
                controller.state.map { it.isPlaying }.distinctUntilChanged(),
                controller.videoSize,
                graph.settings.settings.map { it.backgroundAudio }.distinctUntilChanged(),
                controller.state.map { it.castingTo != null }.distinctUntilChanged(),
            ) { _, _, _, _ -> }
                .collect { refreshPip() }
        }
    }

    /**
     * Opens what [intent] asks for. A screen re-created over a session that is still going
     * ([restore] set) keeps it; one re-created after the process was gone reopens where it was.
     */
    private fun handleIntent(controller: PlayerController, intent: Intent, restore: Bundle?) {
        if (restore != null && controller.hasContent) return
        val position = restore?.getLong(KEY_POSITION, 0L)?.takeIf { it > 0 }
        val restoredVideoId = restore?.getLong(KEY_VIDEO_ID, 0L)?.takeIf { it > 0 }
        if (intent.action == PlayerIntents.ACTION_SHOW_PLAYER) {
            when {
                controller.hasContent -> Unit
                restoredVideoId != null -> controller.open(PlayRequest(videoId = restoredVideoId, startPositionMs = position))
                // Playback ended while the notification was being tapped.
                else -> finish()
            }
            return
        }
        val queue = QueueRequest.fromIntent(intent)
        if (queue != null) {
            controller.openQueue(queue.startingAt(restoredVideoId), startPositionMs = position)
            return
        }
        val request = PlayRequest.fromIntent(intent)
        when {
            // The player had moved on (next episode) before the process was gone.
            restoredVideoId != null && request?.videoId != restoredVideoId ->
                controller.open(PlayRequest(videoId = restoredVideoId, startPositionMs = position))
            request != null -> controller.open(request.copy(startPositionMs = position))
            !controller.hasContent -> finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        PlayerDiagnostics.record("new intent")
        // The notification's tap: whatever plays is shown, and the intent that opened it stays
        // the one a re-created screen falls back on.
        if (intent.action == PlayerIntents.ACTION_SHOW_PLAYER) return
        setIntent(intent)
        val current = controller
        if (current != null) {
            handleIntent(current, intent, restore = null)
        } else {
            pendingIntent = intent
            pendingRestore = null
        }
    }

    override fun onStart() {
        super.onStart()
        attachToService()
    }

    override fun onResume() {
        super.onResume()
        pickingSubtitle = false
        hideSystemBars()
        controller?.onHostResumed()
    }

    override fun onPause() {
        super.onPause()
        val controller = controller ?: return
        // Paused but still on screen: in picture-in-picture, and on Android 8-9 in split screen
        // as soon as the other app is touched. onStop pauses once it really is gone.
        if (isInPictureInPictureMode || isInMultiWindowMode) return
        // Leaving with background audio: the sound must not stop on the way out; onStop decides.
        if (!pickingSubtitle && controller.canPlayInBackground) return
        controller.onHostPaused()
    }

    override fun onStop() {
        super.onStop()
        val playback = service ?: return
        val controller = playback.controller
        if (isInPictureInPictureMode && isScreenOn()) {
            // The PiP window was dismissed.
            PlayerDiagnostics.record("picture-in-picture window closed")
            controller.close()
            return
        }
        // Left the app, or the screen went off (in PiP too): the sound plays on in the
        // background, or playback pauses and keeps the session.
        if (!playback.onPlayerHidden(allowBackground = !pickingSubtitle)) {
            controller.onHostPaused()
            controller.detachSurface()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        pip.unregister()
        service?.let { playback ->
            videoLayout?.let { playback.controller.detachSurface(it) }
            playback.controller.detachHost(this)
        }
        service = null
        connected.value = null
        // Without background playback this ends the service, and with it the player.
        if (bound) {
            bound = false
            try {
                unbindService(connection)
            } catch (e: IllegalArgumentException) {
                // The binding never got registered.
                Log.w(TAG, "Playback service was not bound", e)
            }
        }
        videoLayout = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val current = controller
        if (current == null) {
            pendingRestore?.let { outState.putAll(it) }
            return
        }
        outState.putLong(KEY_POSITION, current.positionMs)
        current.currentVideoId?.let { outState.putLong(KEY_VIDEO_ID, it) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * The volume keys change the media volume with the player's own iOS-style indicator instead
     * of the system's panel; in picture-in-picture (or before the player is connected) the
     * system handles them as usual.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode) && !isInPictureInPictureMode && controller?.onVolumeKey(keyCode) == true) {
            volumeKeyHandled = true
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // The release of a key the player took, so the system doesn't act on it (a click sound, its panel).
        if (isVolumeKey(keyCode) && volumeKeyHandled) {
            volumeKeyHandled = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun isVolumeKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        controller?.onPictureInPictureModeChanged(isInPictureInPictureMode)
        // Left PiP while stopped: the window was dismissed rather than expanded.
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED && !isFinishing) {
            PlayerDiagnostics.record("picture-in-picture window closed")
            val current = controller
            if (current != null) current.close() else finish()
        }
    }

    // PlayerController.Host

    override fun enterPictureInPicture() {
        pip.enter()
    }

    override fun captureFrame(onDone: (Boolean) -> Unit) {
        val layout = videoLayout ?: return onDone(false)
        FrameCapture.copy(layout) { bitmap ->
            if (bitmap == null) {
                onDone(false)
            } else {
                lifecycleScope.launch {
                    val saved = withContext(Dispatchers.IO) { FrameCapture.save(applicationContext, bitmap, controller?.state?.value?.title) }
                    onDone(saved)
                }
            }
        }
    }

    override fun launchSubtitlePicker(): Boolean {
        // Launching our own picker must not trigger auto-enter PiP on Android 12+.
        pip.update(playing = false, videoSize = controller?.videoSize?.value)
        pickingSubtitle = true
        return try {
            subtitlePicker.launch(arrayOf("*/*"))
            true
        } catch (e: ActivityNotFoundException) {
            pickingSubtitle = false
            false
        }
    }

    override fun finishPlayback() {
        if (!isFinishing) finish()
    }

    override fun leavePlayer() {
        // The sound goes on in the background (see onStop), not in a PiP window.
        refreshPip()
        if (!moveTaskToBack(true)) finish()
    }

    override var windowBrightness: Float
        get() = window.attributes.screenBrightness
        set(value) {
            val attributes = window.attributes
            attributes.screenBrightness = value
            window.attributes = attributes
        }

    override var screenOrientation: Int
        get() = requestedOrientation
        set(value) {
            requestedOrientation = value
        }

    override val screenConfiguration: Configuration get() = resources.configuration

    /**
     * Visible with the video surface ready: the service gets the surface, then the picture back.
     * Casting, the screen may show a placeholder instead of the surface; it counts as shown then.
     */
    private fun attachToService() {
        val playback = service ?: return
        val layout = videoLayout
        if (layout == null && !playback.controller.isCasting) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (layout != null) playback.controller.attachSurface(layout)
        playback.onPlayerShown()
        refreshPip()
    }

    /**
     * PiP parameters for the current state: auto-enter only while playing without background
     * audio, and never while casting (there is no video here to put in a window).
     */
    private fun refreshPip() {
        val current = controller ?: return
        val playing = current.state.value.isPlaying
        pip.update(
            playing = playing,
            videoSize = current.videoSize.value,
            // Leaving the player pauses (or plays on in the background when that is wanted); it
            // never turns into a picture-in-picture window by itself: the PiP button does that.
            autoEnter = false,
        )
    }

    private fun onVideoLayoutCreated(layout: VLCVideoLayout) {
        videoLayout = layout
        attachToService()
    }

    private fun onVideoLayoutReleased(layout: VLCVideoLayout) {
        controller?.detachSurface(layout)
        if (videoLayout === layout) videoLayout = null
    }

    /** Dismissing the PiP window and turning the screen off both stop the activity while in PiP. */
    private fun isScreenOn(): Boolean = getSystemService(PowerManager::class.java)?.isInteractive ?: true

    private fun keepScreenOn(on: Boolean) {
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private companion object {
        const val TAG = "PlayerActivity"
        const val KEY_POSITION = "player_position_ms"
        const val KEY_VIDEO_ID = "player_video_id"
    }
}
