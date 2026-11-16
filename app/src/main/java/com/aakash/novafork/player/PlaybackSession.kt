package com.aakash.novafork.player

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.aakash.novafork.R
import com.aakash.novafork.ui.model.PlayerUiState
import kotlin.math.abs

/**
 * The player as Android's media controls see it: lock screen, the media card in the notification
 * shade, Bluetooth and wired headset buttons, car head units. Their commands go to [Controls];
 * [update] keeps what they show in step with the player. Main thread only.
 */
internal class PlaybackSession(
    context: Context,
    sessionActivity: PendingIntent,
    private val controls: Controls,
    /** Back/forward 10 s buttons; music has previous and next instead. */
    private val seekButtons: Boolean = true,
) {

    /** What the media controls can ask for. */
    interface Controls {
        fun play()
        fun pause()

        /** "Stop" from a headset, a car or the media card. */
        fun stop()
        fun skipToNext()
        fun skipToPrevious()
        fun seekBy(deltaMs: Long)
        fun seekTo(positionMs: Long)
    }

    private data class MetadataKey(val title: String, val subtitle: String?, val durationMs: Long, val artwork: Bitmap?)

    private data class StateKey(val code: Int, val actions: Long, val speed: Float, val seekable: Boolean, val error: String?)

    private val session = MediaSession(context, TAG)
    private var released = false

    private var metadataKey: MetadataKey? = null
    private var metadataPublished = false
    private var stateKey: StateKey? = null
    private var statePositionMs = 0L
    private var stateUpdatedAt = 0L

    val token: MediaSession.Token get() = session.sessionToken

    init {
        session.setCallback(
            object : MediaSession.Callback() {
                override fun onPlay() = controls.play()

                override fun onPause() = controls.pause()

                override fun onStop() = controls.stop()

                override fun onSkipToNext() = controls.skipToNext()

                override fun onSkipToPrevious() = controls.skipToPrevious()

                override fun onFastForward() = controls.seekBy(SEEK_STEP_MS)

                override fun onRewind() = controls.seekBy(-SEEK_STEP_MS)

                override fun onSeekTo(pos: Long) = controls.seekTo(pos)

                override fun onCustomAction(action: String, extras: Bundle?) {
                    when (action) {
                        CUSTOM_REWIND -> controls.seekBy(-SEEK_STEP_MS)
                        CUSTOM_FORWARD -> controls.seekBy(SEEK_STEP_MS)
                        CUSTOM_STOP -> controls.stop()
                    }
                }
            },
            Handler(Looper.getMainLooper()),
        )
        session.setSessionActivity(sessionActivity)
    }

    /**
     * Publishes the player's state. [playRequested]: see [PlayerController.playRequested].
     * [active] = the media controls may act on it (something is open and the player is on screen
     * or playing in the background). Cheap when nothing they show changed: the position goes out
     * only when it jumps, the system extrapolates the rest.
     */
    fun update(
        state: PlayerUiState,
        title: String,
        hasContent: Boolean,
        playRequested: Boolean,
        active: Boolean,
        artwork: Bitmap?,
    ) {
        if (released) return
        updateMetadata(if (hasContent) MetadataKey(title, state.subtitle, state.durationMs, artwork) else null)
        updatePlaybackState(state, hasContent, playRequested)
        if (session.isActive != active) session.isActive = active
    }

    fun release() {
        if (released) return
        released = true
        session.isActive = false
        session.release()
    }

    private fun updateMetadata(key: MetadataKey?) {
        if (metadataPublished && key == metadataKey) return
        metadataKey = key
        metadataPublished = true
        if (key == null) {
            session.setMetadata(null)
            return
        }
        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, key.title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, key.title)
        if (key.subtitle != null) {
            // Most media controls show the artist line under the title.
            builder.putString(MediaMetadata.METADATA_KEY_ARTIST, key.subtitle)
            builder.putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, key.subtitle)
        }
        // Without a duration (live streams) the media controls show no seek bar.
        if (key.durationMs > 0) builder.putLong(MediaMetadata.METADATA_KEY_DURATION, key.durationMs)
        if (key.artwork != null) builder.putBitmap(MediaMetadata.METADATA_KEY_ART, key.artwork)
        session.setMetadata(builder.build())
    }

    private fun updatePlaybackState(state: PlayerUiState, hasContent: Boolean, playRequested: Boolean) {
        val code = when {
            !hasContent -> PlaybackState.STATE_NONE
            state.error != null -> PlaybackState.STATE_ERROR
            state.isBuffering && (state.isPlaying || playRequested) -> PlaybackState.STATE_BUFFERING
            state.isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        val actions = if (hasContent) actionsFor(state) else 0L
        val speed = if (code == PlaybackState.STATE_PLAYING) state.rate else 0f
        val key = StateKey(code, actions, speed, state.seekable, state.error)
        val now = SystemClock.elapsedRealtime()
        if (key == stateKey) {
            val expectedMs = statePositionMs + ((now - stateUpdatedAt) * speed).toLong()
            if (abs(state.positionMs - expectedMs) < POSITION_DRIFT_MS) return
        }
        stateKey = key
        statePositionMs = state.positionMs
        stateUpdatedAt = now
        val builder = PlaybackState.Builder()
            .setActions(actions)
            .setState(code, state.positionMs, speed, now)
        if (hasContent) {
            // Android 13+ builds its media controls from these: they fill the previous/next
            // slots when there is no previous/next item, then the extra ones.
            if (state.seekable && seekButtons) {
                builder.addCustomAction(customAction(CUSTOM_REWIND, "Back 10 seconds", R.drawable.ic_replay_10))
                builder.addCustomAction(customAction(CUSTOM_FORWARD, "Forward 10 seconds", R.drawable.ic_forward_10))
            }
            builder.addCustomAction(customAction(CUSTOM_STOP, "Stop", R.drawable.ic_close))
        }
        if (state.error != null) builder.setErrorMessage(state.error)
        session.setPlaybackState(builder.build())
    }

    private fun actionsFor(state: PlayerUiState): Long {
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
        if (state.seekable) {
            actions = actions or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_FAST_FORWARD or
                PlaybackState.ACTION_REWIND
        }
        if (state.hasNext) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        if (state.hasPrevious) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        return actions
    }

    private fun customAction(action: String, name: String, icon: Int): PlaybackState.CustomAction =
        PlaybackState.CustomAction.Builder(action, name, icon).build()

    companion object {
        private const val TAG = "NovaFork"

        /** Fast forward and rewind from the media controls; the notification's icons say "10". */
        const val SEEK_STEP_MS = 10_000L

        /** A reported position this far from where the system expects it is sent again (a seek). */
        private const val POSITION_DRIFT_MS = 1_500L

        private const val CUSTOM_REWIND = "com.aakash.novafork.session.REWIND"
        private const val CUSTOM_FORWARD = "com.aakash.novafork.session.FORWARD"
        private const val CUSTOM_STOP = "com.aakash.novafork.session.STOP"
    }
}
