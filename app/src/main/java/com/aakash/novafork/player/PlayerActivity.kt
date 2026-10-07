package com.aakash.novafork.player

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout

/** Full-screen libVLC player in its own task, with picture-in-picture. Started through [PlayerIntents]. */
class PlayerActivity : ComponentActivity(), PlayerController.Host {

    private lateinit var controller: PlayerController
    private lateinit var pip: PipController
    private lateinit var subtitlePicker: ActivityResultLauncher<Array<String>>
    private var videoLayout: VLCVideoLayout? = null

    /** Our own document picker is opening: leaving now is not the user heading home, so no PiP. */
    private var pickingSubtitle = false

    override val supportsPictureInPicture: Boolean get() = pip.isSupported

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = NovaApp.graph(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        volumeControlStream = AudioManager.STREAM_MUSIC
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        pip = PipController(this) { controller.togglePlay() }
        pip.register()
        controller = PlayerController(this, graph, lifecycleScope, this)
        subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pickingSubtitle = false
            controller.onSubtitleFilePicked(uri)
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    controller.onBackPressed()
                }
            },
        )

        setContent {
            val state by controller.state.collectAsStateWithLifecycle()
            val settings by graph.settings.settings.collectAsStateWithLifecycle()
            NovaPlayerTheme {
                PlayerScreen(
                    state = state,
                    actions = controller,
                    layout = rememberDeviceLayout(settings.layoutMode),
                ) {
                    AndroidView(
                        factory = { context -> VLCVideoLayout(context).also { onVideoLayoutCreated(it) } },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { layout -> onVideoLayoutReleased(layout) },
                    )
                }
            }
        }

        lifecycleScope.launch {
            controller.state
                .map { it.isPlaying || it.isBuffering }
                .distinctUntilChanged()
                .collect { active -> keepScreenOn(active) }
        }
        lifecycleScope.launch {
            combine(
                controller.state.map { it.isPlaying }.distinctUntilChanged(),
                controller.videoSize,
            ) { playing, size -> playing to size }
                .collect { pair -> pip.update(playing = pair.first, videoSize = pair.second) }
        }

        val request = PlayRequest.fromIntent(intent)
        if (request == null) {
            finish()
            return
        }
        val restoredPosition = savedInstanceState?.getLong(KEY_POSITION, 0L)?.takeIf { it > 0 }
        controller.open(request.copy(startPositionMs = restoredPosition))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PlayRequest.fromIntent(intent)?.let { controller.open(it) }
    }

    override fun onStart() {
        super.onStart()
        videoLayout?.let { controller.attachSurface(it) }
    }

    override fun onResume() {
        super.onResume()
        pickingSubtitle = false
        hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        // In picture-in-picture the activity is paused but still on screen: keep playing.
        if (!isInPictureInPictureMode) controller.onHostPaused()
    }

    override fun onStop() {
        super.onStop()
        if (isInPictureInPictureMode) {
            // The PiP window was dismissed.
            controller.saveProgress()
            finish()
        } else {
            controller.detachSurface()
        }
    }

    override fun onDestroy() {
        controller.release()
        pip.unregister()
        videoLayout = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_POSITION, controller.positionMs)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters PiP by itself (auto-enter is on while playing).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !pickingSubtitle && controller.state.value.isPlaying) {
            pip.enter()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        controller.onPictureInPictureModeChanged(isInPictureInPictureMode)
        // Left PiP while stopped: the window was dismissed rather than expanded.
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED && !isFinishing) {
            controller.saveProgress()
            finish()
        }
    }

    // PlayerController.Host

    override fun enterPictureInPicture() {
        pip.enter()
    }

    override fun launchSubtitlePicker(): Boolean {
        // Launching our own picker must not trigger auto-enter PiP on Android 12+.
        pip.update(playing = false, videoSize = controller.videoSize.value)
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

    private fun onVideoLayoutCreated(layout: VLCVideoLayout) {
        videoLayout = layout
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.attachSurface(layout)
    }

    private fun onVideoLayoutReleased(layout: VLCVideoLayout) {
        controller.detachSurface(layout)
        if (videoLayout === layout) videoLayout = null
    }

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
        const val KEY_POSITION = "player_position_ms"
    }
}
