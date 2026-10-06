package com.aakash.novafork.ui.model

import androidx.compose.runtime.Immutable
import com.aakash.novafork.data.AspectMode

/** One audio or subtitle track as libVLC reports it. id -1 = "Disable". */
@Immutable
data class TrackUi(val id: Int, val name: String, val selected: Boolean)

/** Transient feedback drawn in the middle of the screen while a gesture runs. */
@Immutable
sealed interface GestureFeedback {
    /** Horizontal drag / double tap: where playback will land and how far that is. */
    data class Seek(val targetMs: Long, val deltaMs: Long, val durationMs: Long) : GestureFeedback
    /** Right-side vertical drag. 0–1. */
    data class Volume(val level: Float) : GestureFeedback
    /** Left-side vertical drag. 0–1. */
    data class Brightness(val level: Float) : GestureFeedback
    /** Short label after a button press: "Fit screen", "1.5×", "Subtitles off". */
    data class Label(val text: String) : GestureFeedback
}

@Immutable
data class UpNextUi(
    val videoId: Long,
    val title: String,
    val subtitle: String?,
    val artwork: Artwork,
    /** Seconds until it starts by itself; null = waits for a tap. */
    val countdownSeconds: Int?,
)

@Immutable
data class PlayerUiState(
    val title: String = "",
    /** "S1 · E3 · Pilot", "1080p · nas.local" … */
    val subtitle: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val seekable: Boolean = true,
    val audioTracks: List<TrackUi> = emptyList(),
    val subtitleTracks: List<TrackUi> = emptyList(),
    val aspect: AspectMode = AspectMode.BEST_FIT,
    /** 0.25 – 4.0 */
    val rate: Float = 1f,
    val subtitleDelayMs: Long = 0,
    /** Lock: hides everything but an unlock button and ignores gestures. */
    val locked: Boolean = false,
    val inPictureInPicture: Boolean = false,
    val canEnterPictureInPicture: Boolean = true,
    val feedback: GestureFeedback? = null,
    /** Shown for a few seconds after auto-resume: "Resumed at 12:34" with a Start over button. */
    val resumedFromMs: Long? = null,
    val upNext: UpNextUi? = null,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val error: String? = null,
    /** Seek step for buttons/double tap, from Settings. */
    val seekStepMs: Long = 10_000,
    /** Current volume/brightness 0–1 (for the gesture overlay start value). */
    val volume: Float = 0.5f,
    val brightness: Float = 0.5f,
)

/**
 * Everything the player UI can ask for. Implemented by PlayerController (player package).
 * Calls are cheap and main-thread safe.
 */
interface PlayerActions {
    fun togglePlay()
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)

    /** Live preview during a horizontal drag; [commit] = finger lifted. */
    fun scrub(targetMs: Long, commit: Boolean)

    /** Vertical drag on the right half; [delta] is a fraction of full scale (+ = up). */
    fun adjustVolume(delta: Float)

    /** Vertical drag on the left half; [delta] is a fraction of full scale (+ = up). */
    fun adjustBrightness(delta: Float)

    /** Ends a volume/brightness/scrub gesture: lets the overlay fade. */
    fun endGesture()

    fun selectAudioTrack(id: Int)
    fun selectSubtitleTrack(id: Int)

    /** Opens the system file picker for an external subtitle file. */
    fun pickSubtitleFile()
    fun setSubtitleDelay(delayMs: Long)

    fun cycleAspect()
    fun setAspect(mode: AspectMode)
    fun setRate(rate: Float)

    fun toggleLock()
    fun rotate()
    fun enterPictureInPicture()

    fun startOver()
    fun dismissResumeBanner()
    fun playNext()
    fun playPrevious()
    fun cancelUpNext()

    fun close()
}

open class NoOpPlayerActions : PlayerActions {
    override fun togglePlay() {}
    override fun play() {}
    override fun pause() {}
    override fun seekTo(positionMs: Long) {}
    override fun seekBy(deltaMs: Long) {}
    override fun scrub(targetMs: Long, commit: Boolean) {}
    override fun adjustVolume(delta: Float) {}
    override fun adjustBrightness(delta: Float) {}
    override fun endGesture() {}
    override fun selectAudioTrack(id: Int) {}
    override fun selectSubtitleTrack(id: Int) {}
    override fun pickSubtitleFile() {}
    override fun setSubtitleDelay(delayMs: Long) {}
    override fun cycleAspect() {}
    override fun setAspect(mode: AspectMode) {}
    override fun setRate(rate: Float) {}
    override fun toggleLock() {}
    override fun rotate() {}
    override fun enterPictureInPicture() {}
    override fun startOver() {}
    override fun dismissResumeBanner() {}
    override fun playNext() {}
    override fun playPrevious() {}
    override fun cancelUpNext() {}
    override fun close() {}
}
