package com.aakash.novafork.ui.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.aakash.novafork.data.AspectMode

/** One audio or subtitle track as libVLC reports it. id -1 = "Disable". */
@Immutable
data class TrackUi(val id: Int, val name: String, val selected: Boolean)

/** Transient feedback drawn in the middle of the screen while a gesture runs. */
@Immutable
sealed interface GestureFeedback {
    /** Horizontal drag / double tap: where playback will land and how far that is. */
    data class Seek(val targetMs: Long, val deltaMs: Long, val durationMs: Long) : GestureFeedback
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

/** What happens when the playing item ends; next/previous buttons are never affected. */
enum class RepeatMode {
    /** Play on to the next item; stop after the last one. */
    OFF,

    /** After the last item, start again from the first (a single video loops). */
    ALL,

    /** Play the same item again. */
    ONE,
}

/** One library video of the play queue, in play order (shuffled order while shuffle is on). */
@Immutable
data class QueueItemUi(
    val videoId: Long,
    val title: String,
    /** "S1 · E3 · Pilot", "2024 · 4K", folder name … */
    val subtitle: String?,
    val artwork: Artwork,
)

/** A device on the local network the video can be cast to (Chromecast), as libVLC found it. */
@Immutable
data class CastDeviceUi(
    /** Pass to [PlayerActions.startCasting]; stays the same while the device is on the network. */
    val id: String,
    /** "Living Room TV" */
    val name: String,
)

/** One subtitle OpenSubtitles offers for the playing video. */
@Immutable
data class SubtitleResultUi(
    /** Pass to [PlayerActions.downloadSubtitle]. */
    val id: String,
    /** "English", "Portuguese (Brazil)". */
    val language: String,
    /** The release it was made for ("Dune.2021.1080p.WEBRip.x264-RARBG"); may be blank. */
    val release: String,
    /** Timed for exactly this file (OpenSubtitles knows its movie hash): "Matches this file". */
    val matchesFile: Boolean = false,
    val hearingImpaired: Boolean = false,
    val downloadCount: Int = 0,
    /** Translated by AI or a machine rather than by people. */
    val machineTranslated: Boolean = false,
    /** Already downloaded for this video: picking it again costs no download. */
    val downloaded: Boolean = false,
)

/** The "Download subtitles" list over the player; see [PlayerActions.searchSubtitles]. */
@Immutable
data class SubtitleSearchUi(
    /** Looking for subtitles. */
    val loading: Boolean = false,
    /** Best first. Empty while loading, after an error, or when nothing was found. */
    val results: List<SubtitleResultUi> = emptyList(),
    /** What went wrong ("Download limit reached, resets at 13:03"); results may still be shown. */
    val error: String? = null,
    /** The result being downloaded; the others wait. */
    val downloadingId: String? = null,
    /** The languages searched, for the header ("English, French"). */
    val languages: String = "",
)

@Immutable
data class PlayerUiState(
    val title: String = "",
    /** "S1 · E3 · Pilot", "1080p · nas.local" … */
    val subtitle: String? = null,
    /** The title's synopsis (TMDB), shown over the picture after a while paused. */
    val overview: String? = null,
    /** Played to the end with nothing to follow: it stays on the last frame with Replay (Samsung). */
    val finished: Boolean = false,
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
    /** False where the system ignores orientation requests (Android 16+ on screens ≥ 600dp). */
    val canRotate: Boolean = true,
    val feedback: GestureFeedback? = null,
    /** While the seek bar is dragged: the video's frame at that point (files on the phone only). */
    val scrubFrame: ImageBitmap? = null,
    /**
     * The media volume 0–1 while the player's own (iOS-style) volume indicator shows, after a
     * volume key; null while it is hidden. The system's volume panel stays away in the player.
     */
    val volumeHud: Float? = null,
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
    /** The queue plays in a shuffled order ([queue] is listed in that order). */
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /**
     * The library videos queued, in play order, the playing one included. Empty for a link that
     * isn't in the library; one item for a single video (next/previous then follow the episodes).
     */
    val queue: List<QueueItemUi> = emptyList(),
    /** Index of the playing item in [queue]; 0 while [queue] is empty. */
    val queueIndex: Int = 0,
    /** Cast devices on the local network; looked for while the player is shown or casting. */
    val castDevices: List<CastDeviceUi> = emptyList(),
    /**
     * Name of the device the video plays on; null = it plays here. While set, the screen shows
     * a casting placeholder instead of the video surface (nothing is drawn there).
     */
    val castingTo: String? = null,
    /** The subtitle download list while it is open; null = closed. */
    val subtitleSearch: SubtitleSearchUi? = null,
)

/**
 * Everything the player UI can ask for. Implemented by PlayerController (player package).
 * Calls are cheap and main-thread safe.
 */
interface PlayerActions {
    /** Samsung's Capture: the frame on screen saved as a picture. */
    fun captureFrame() {}

    fun togglePlay()
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)

    /** Live preview during a horizontal drag; [commit] = finger lifted. */
    fun scrub(targetMs: Long, commit: Boolean)

    /** Ends a scrub gesture: lets the overlay fade. */
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

    /** Shuffle on/off. The playing item stays where it is; the rest of the queue is reordered. */
    fun toggleShuffle()

    /** OFF → ALL → ONE → OFF. */
    fun cycleRepeat()

    /** Plays [index] of [PlayerUiState.queue]. */
    fun playQueueItem(index: Int)

    /** Leaves the player screen and keeps the sound playing, with controls in a notification. */
    fun playInBackground()

    /**
     * Plays on [id] of [PlayerUiState.castDevices] from where it is now (libVLC converts what
     * the device can't play). Also switches from one cast device to another.
     */
    fun startCasting(id: String)

    /** Plays on this device again, from where the cast was. */
    fun stopCasting()

    /** Looks for cast devices again; [PlayerUiState.castDevices] refills within a few seconds. */
    fun scanForCastDevices()

    fun close()

    // Subtitle downloads (OpenSubtitles). Default bodies keep other implementations compiling.

    /**
     * Opens [PlayerUiState.subtitleSearch] and looks for subtitles for the playing video (its
     * library details, and the file's movie hash when it is on this device). Also "try again".
     */
    fun searchSubtitles() {}

    /** Downloads [id] of [SubtitleSearchUi.results], turns it on and closes the list. */
    fun downloadSubtitle(id: String) {}

    /** Closes the list; a download already running still finishes and turns its subtitle on. */
    fun dismissSubtitleSearch() {}
}

open class NoOpPlayerActions : PlayerActions {
    override fun togglePlay() {}
    override fun play() {}
    override fun pause() {}
    override fun seekTo(positionMs: Long) {}
    override fun seekBy(deltaMs: Long) {}
    override fun scrub(targetMs: Long, commit: Boolean) {}
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
    override fun toggleShuffle() {}
    override fun cycleRepeat() {}
    override fun playQueueItem(index: Int) {}
    override fun playInBackground() {}
    override fun startCasting(id: String) {}
    override fun stopCasting() {}
    override fun scanForCastDevices() {}
    override fun close() {}
    override fun searchSubtitles() {}
    override fun downloadSubtitle(id: String) {}
    override fun dismissSubtitleSearch() {}
}
