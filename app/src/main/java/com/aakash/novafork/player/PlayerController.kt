package com.aakash.novafork.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import com.aakash.novafork.AppGraph
import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.data.AspectMode
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.SmbCredentials
import com.aakash.novafork.data.db.VideoEntity
import com.aakash.novafork.data.db.VideoKind
import com.aakash.novafork.data.db.VideoWithMeta
import com.aakash.novafork.sources.SubtitleFinder
import com.aakash.novafork.ui.model.GestureFeedback
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState
import com.aakash.novafork.ui.model.TrackUi
import com.aakash.novafork.ui.model.UpNextUi
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import android.provider.Settings as SystemSettings

/** The decoded picture as it is displayed: sample aspect ratio and rotation already applied. */
data class VideoSize(val width: Int, val height: Int) {
    val isPortrait: Boolean get() = height > width
}

/**
 * Owns one libVLC MediaPlayer and turns its events into [PlayerUiState]; implements everything
 * the player UI can ask for. Created by [PlayerActivity] on the main thread; every call is
 * expected on the main thread (libVLC delivers its events there too).
 */
class PlayerController(
    private val activity: Activity,
    private val graph: AppGraph,
    private val scope: CoroutineScope,
    private val host: Host,
) : PlayerActions {

    /** What the controller needs from the screen that owns it. */
    interface Host {
        val supportsPictureInPicture: Boolean

        fun enterPictureInPicture()

        /** Opens the system document picker; false when the device has none. */
        fun launchSubtitlePicker(): Boolean

        /** Playback is over: closed by the user, nothing left to play, or nothing to open. */
        fun finishPlayback()
    }

    private class NowPlaying(val uri: String, val kind: UriKind, val fileName: String, val entry: VideoWithMeta?) {
        val videoId: Long? get() = entry?.video?.id
    }

    private val libVLC = VlcEngine.libVLC(activity)
    private val mediaPlayer: MediaPlayer = VlcEngine.newMediaPlayer(activity)
    private val audioManager: AudioManager = checkNotNull(activity.getSystemService(AudioManager::class.java))
    private val mainHandler = Handler(Looper.getMainLooper())
    private val subtitleFinder = SubtitleFinder(activity.applicationContext, graph.smb) { server, share ->
        graph.library.smbCredentials(server, share)
    }

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _videoSize = MutableStateFlow<VideoSize?>(null)

    /** Null until the first picture is shown (and for audio-only media). */
    val videoSize: StateFlow<VideoSize?> = _videoSize.asStateFlow()

    // The media being played
    private var nowPlaying: NowPlaying? = null
    private var lastRequest: PlayRequest? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var attachedLayout: VLCVideoLayout? = null
    private var released = false
    private var playbackStarted = false
    private var ended = false
    private var pendingSeekMs: Long? = null
    private var resumeBannerPending = false
    private var userRotated = false
    private var playWhenAttached = false

    // Track choices
    private var rememberedAudio: Int? = null
    private var rememberedSubtitle: Int? = null
    private var audioLanguage: Set<String> = emptySet()
    private var subtitleLanguage: Set<String> = emptySet()
    private var audioChoicePending = false
    private var externalSubtitles: List<File>? = null
    private var subtitleSetupDone = false
    private var pendingSubtitleRestore: Int? = null

    // Episodes around this one
    private var nextEntry: VideoWithMeta? = null
    private var previousVideoId: Long? = null

    // Pauses that should undo themselves
    private var pausedByFocusLoss = false
    private var resumeAfterPicker = false

    // Gestures
    private var volumeLevel = 0f
    private var volumeGestureActive = false
    private var scrubStartMs: Long? = null
    private var seekSeriesStartMs: Long? = null
    private var seekSeriesTargetMs: Long? = null
    private var lastPositionUpdate = 0L

    // Events of a media that was just stopped, still queued on the main looper
    private var staleEventToken = 0
    private var droppingStaleEvents = false

    private var openJob: Job? = null
    private var subtitleJob: Job? = null
    private var upNextJob: Job? = null
    private var resumeBannerJob: Job? = null
    private var feedbackJob: Job? = null

    private val eventListener = object : MediaPlayer.EventListener {
        override fun onEvent(event: MediaPlayer.Event) = onPlayerEvent(event)
    }

    private val audio = PlaybackAudio(
        activity,
        object : PlaybackAudio.Listener {
            override fun onFocusLost(transient: Boolean) {
                if (released || !mediaPlayer.isPlaying) return
                pausedByFocusLoss = transient
                mediaPlayer.pause()
            }

            override fun onFocusRegained() {
                if (released || !pausedByFocusLoss) return
                pausedByFocusLoss = false
                mediaPlayer.play()
            }

            override fun onDuck(ducked: Boolean) {
                if (!released) mediaPlayer.setVolume(if (ducked) DUCKED_VOLUME else FULL_VOLUME)
            }

            override fun onBecomingNoisy() = pause()
        },
    )

    init {
        mediaPlayer.setEventListener(eventListener)
        val settings = graph.settings.settings.value
        volumeLevel = systemVolumeLevel()
        _state.value = PlayerUiState(
            aspect = settings.defaultAspect,
            seekStepMs = seekStepMs(settings),
            canEnterPictureInPicture = host.supportsPictureInPicture,
            volume = volumeLevel,
            brightness = currentBrightness(),
        )
        scope.launch {
            while (true) {
                delay(SAVE_INTERVAL_MS)
                if (_state.value.isPlaying) saveProgress()
            }
        }
    }

    /** Current position for the activity's saved state. */
    val positionMs: Long get() = if (released) _state.value.positionMs else currentPositionMs()

    fun open(request: PlayRequest) {
        if (released) return
        saveProgress()
        openJob?.cancel()
        subtitleJob?.cancel()
        upNextJob?.cancel()
        resumeBannerJob?.cancel()
        if (mediaPlayer.hasMedia()) {
            mediaPlayer.stop()
            dropStaleEvents()
        }
        descriptor.closeQuietly()
        descriptor = null
        audio.unregisterNoisyReceiver()
        lastRequest = request
        nowPlaying = null
        resetSession()
        _state.update { old -> freshState(old, title = request.title.orEmpty()) }
        openJob = scope.launch { load(request) }
    }

    private suspend fun load(request: PlayRequest) {
        val settings = graph.settings.settings.value
        val entry = findEntry(request)
        val uri = entry?.video?.uri ?: request.uri
        if (uri == null) {
            showError("This video is no longer in your library")
            return
        }
        val kind = UriKind.of(uri)
        val fileName = entry?.video?.fileName ?: rawFileName(uri, kind)
        val titles = if (entry != null) {
            PlayerText.titlesFor(entry, size = null)
        } else {
            Titles(request.title ?: PlayerText.displayName(fileName), PlayerText.hostOf(uri))
        }
        val current = NowPlaying(uri, kind, fileName, entry)
        nowPlaying = current
        _state.update { it.copy(title = titles.title, subtitle = titles.subtitle) }

        val video = entry?.video
        rememberedAudio = video?.audioTrackId
        rememberedSubtitle = video?.subtitleTrackId
        audioLanguage = TrackLanguages.aliases(settings.preferredAudioLanguage)
        subtitleLanguage = TrackLanguages.aliases(settings.preferredSubtitleLanguage)
        audioChoicePending = rememberedAudio != null || audioLanguage.isNotEmpty()
        val resumeAt = if (
            video != null && settings.resumePlayback && !request.startOver && !video.watched &&
            video.positionMs > RESUME_MIN_MS
        ) {
            video.positionMs
        } else {
            null
        }
        pendingSeekMs = request.startPositionMs ?: resumeAt
        resumeBannerPending = request.startPositionMs == null && resumeAt != null

        startSubtitleSearch(current, settings)

        val opened = openMedia(uri, kind, settings)
        if (opened == null) {
            showError(errorMessage(kind))
            return
        }
        if (released) {
            opened.discard()
            return
        }
        startMedia(opened)
        loadNeighbours(entry)
    }

    private suspend fun findEntry(request: PlayRequest): VideoWithMeta? {
        val videoId = request.videoId
        val uri = request.uri
        return try {
            when {
                videoId != null -> graph.library.video(videoId)
                uri != null -> graph.library.findByUri(uri)
                else -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Library lookup failed", e)
            null
        }
    }

    /** Builds the media off the main thread; never leaks a descriptor when the open is cancelled. */
    private suspend fun openMedia(uri: String, kind: UriKind, settings: Settings): OpenedMedia? {
        val credentials = if (kind == UriKind.SMB) smbCredentialsFor(uri) else null
        val result = AtomicReference<OpenedMedia?>(null)
        try {
            withContext(Dispatchers.IO) {
                result.set(MediaSource.open(activity.applicationContext, libVLC, uri, settings, credentials))
            }
        } catch (e: CancellationException) {
            result.getAndSet(null)?.discard()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't open a ${kind.name.lowercase(Locale.ROOT)} video", e)
            return null
        }
        return result.getAndSet(null)
    }

    private suspend fun smbCredentialsFor(uri: String): SmbCredentials? {
        val location = SmbUris.parse(uri) ?: return null
        return try {
            graph.library.smbCredentials(location.host, location.share)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No SMB credentials", e)
            null
        }
    }

    private fun startMedia(opened: OpenedMedia) {
        mediaPlayer.setMedia(opened.media)
        // The player keeps its own reference.
        opened.media.release()
        descriptor.closeQuietly()
        descriptor = opened.descriptor
        // Without a surface libVLC would open the video output blind and play sound only.
        if (attachedLayout != null) {
            startPlayback()
        } else {
            playWhenAttached = true
        }
    }

    private fun startPlayback() {
        audio.requestFocus()
        mediaPlayer.play()
    }

    private fun resetSession() {
        playbackStarted = false
        ended = false
        pendingSeekMs = null
        resumeBannerPending = false
        rememberedAudio = null
        rememberedSubtitle = null
        audioChoicePending = false
        externalSubtitles = null
        subtitleSetupDone = false
        pendingSubtitleRestore = null
        nextEntry = null
        previousVideoId = null
        pausedByFocusLoss = false
        scrubStartMs = null
        seekSeriesStartMs = null
        seekSeriesTargetMs = null
        playWhenAttached = false
        _videoSize.value = null
    }

    private fun freshState(old: PlayerUiState, title: String): PlayerUiState = PlayerUiState(
        title = title,
        isBuffering = true,
        aspect = old.aspect,
        rate = old.rate,
        locked = old.locked,
        inPictureInPicture = old.inPictureInPicture,
        canEnterPictureInPicture = old.canEnterPictureInPicture,
        seekStepMs = seekStepMs(graph.settings.settings.value),
        volume = old.volume,
        brightness = old.brightness,
    )

    /**
     * stop() runs synchronously, so every event of the old media is already queued on the main
     * looper; the marker posted now runs right after them.
     */
    private fun dropStaleEvents() {
        val token = ++staleEventToken
        droppingStaleEvents = true
        mainHandler.post { if (token == staleEventToken) droppingStaleEvents = false }
    }

    fun attachSurface(layout: VLCVideoLayout) {
        if (released || attachedLayout === layout) return
        if (attachedLayout != null) mediaPlayer.detachViews()
        mediaPlayer.attachViews(layout, null, ENABLE_SUBTITLES, USE_TEXTURE_VIEW)
        attachedLayout = layout
        // The scale lives in the view helper that attachViews just created.
        mediaPlayer.setVideoScale(_state.value.aspect.toScaleType())
        if (playWhenAttached) {
            playWhenAttached = false
            startPlayback()
        }
    }

    /** Detaches the video views; with [layout], only when that layout is the attached one. */
    fun detachSurface(layout: VLCVideoLayout? = null) {
        val attached = attachedLayout ?: return
        if (layout != null && layout !== attached) return
        attachedLayout = null
        if (!released) mediaPlayer.detachViews()
    }

    fun onHostPaused() {
        if (released) return
        pausedByFocusLoss = false
        if (mediaPlayer.isPlaying) mediaPlayer.pause()
        saveProgress()
    }

    fun onPictureInPictureModeChanged(inPictureInPicture: Boolean) {
        if (inPictureInPicture) {
            scrubStartMs = null
            volumeGestureActive = false
            feedbackJob?.cancel()
        }
        _state.update {
            it.copy(inPictureInPicture = inPictureInPicture, feedback = if (inPictureInPicture) null else it.feedback)
        }
    }

    fun onBackPressed() {
        if (_state.value.locked) showLabel("Unlock to leave the player") else close()
    }

    fun onSubtitleFilePicked(uri: Uri?) {
        if (released) return
        if (uri == null) {
            resumeAfterPicking()
            return
        }
        scope.launch {
            val file = try {
                subtitleFinder.importPicked(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Subtitle import failed", e)
                null
            }
            if (released) return@launch
            val added = file != null && mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(file), true)
            if (added) pendingSubtitleRestore = null
            showLabel(if (added) "Subtitles added" else "Couldn't load that subtitle file")
            resumeAfterPicking()
        }
    }

    /** Stores the position of a library video. Skipped until playback really started (the resume seek is done). */
    fun saveProgress() {
        if (released || !playbackStarted || ended || pendingSeekMs != null || _state.value.error != null) return
        val videoId = nowPlaying?.videoId ?: return
        val position = mediaPlayer.time
        val duration = mediaPlayer.length.takeIf { it > 0 } ?: _state.value.durationMs
        if (position < 0 || duration <= 0) return
        persistPlayback(videoId, position, duration)
    }

    fun release() {
        if (released) return
        saveProgress()
        released = true
        openJob?.cancel()
        subtitleJob?.cancel()
        upNextJob?.cancel()
        resumeBannerJob?.cancel()
        feedbackJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        audio.release()
        mediaPlayer.setEventListener(null)
        mediaPlayer.stop()
        attachedLayout?.let { mediaPlayer.detachViews() }
        attachedLayout = null
        mediaPlayer.release()
        descriptor.closeQuietly()
        descriptor = null
    }

    override fun togglePlay() {
        if (released) return
        if (mediaPlayer.isPlaying) pause() else play()
    }

    override fun play() {
        if (released) return
        pausedByFocusLoss = false
        val request = lastRequest
        if (_state.value.error != null && request != null) {
            // Retry, continuing where it stopped when it had started.
            val position = _state.value.positionMs.takeIf { playbackStarted && it > 0 }
            open(request.copy(startPositionMs = position ?: request.startPositionMs))
            return
        }
        if (ended) {
            restartFromEnd(startAtMs = 0)
            return
        }
        startPlayback()
    }

    override fun pause() {
        if (released) return
        pausedByFocusLoss = false
        if (mediaPlayer.isPlaying) mediaPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        if (released || !_state.value.seekable) return
        seekInternal(clampPosition(positionMs))
    }

    override fun seekBy(deltaMs: Long) {
        if (released || !_state.value.seekable) return
        // Taps in quick succession add up ("+30 s") instead of each showing its own step.
        val start = seekSeriesStartMs ?: currentPositionMs()
        val target = clampPosition((seekSeriesTargetMs ?: start) + deltaMs)
        seekSeriesStartMs = start
        seekSeriesTargetMs = target
        seekInternal(target)
        showFeedback(GestureFeedback.Seek(target, target - start, _state.value.durationMs), GESTURE_FEEDBACK_MS)
    }

    override fun scrub(targetMs: Long, commit: Boolean) {
        val current = _state.value
        if (released || !current.seekable || current.durationMs <= 0) {
            scrubStartMs = null
            return
        }
        val start = scrubStartMs ?: currentPositionMs().also { scrubStartMs = it }
        val target = clampPosition(targetMs)
        val feedback = GestureFeedback.Seek(target, target - start, current.durationMs)
        if (commit) {
            scrubStartMs = null
            seekInternal(target)
            showFeedback(feedback, GESTURE_FEEDBACK_MS)
        } else {
            showFeedback(feedback, hideAfterMs = null)
        }
    }

    override fun adjustVolume(delta: Float) {
        if (released) return
        if (!volumeGestureActive) {
            volumeGestureActive = true
            volumeLevel = systemVolumeLevel()
        }
        // Kept as a fraction so slow drags add up across the coarse system steps.
        volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val index = (volumeLevel * max).roundToInt()
        if (!audioManager.isVolumeFixed && index != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            } catch (e: SecurityException) {
                Log.w(TAG, "Volume change refused", e)
            }
        }
        val level = volumeLevel
        _state.update { it.copy(volume = level) }
        showFeedback(GestureFeedback.Volume(level), hideAfterMs = null)
    }

    override fun adjustBrightness(delta: Float) {
        if (released) return
        val level = (_state.value.brightness + delta).coerceIn(MIN_BRIGHTNESS, 1f)
        val window = activity.window
        val attributes = window.attributes
        attributes.screenBrightness = level
        window.attributes = attributes
        _state.update { it.copy(brightness = level) }
        showFeedback(GestureFeedback.Brightness(level), hideAfterMs = null)
    }

    override fun endGesture() {
        volumeGestureActive = false
        scrubStartMs = null
        if (_state.value.feedback != null) hideFeedbackAfter(GESTURE_FEEDBACK_MS)
    }

    override fun selectAudioTrack(id: Int) {
        if (released) return
        mediaPlayer.setAudioTrack(id)
        audioChoicePending = false
        rememberedAudio = id
        refreshTracks()
        val name = _state.value.audioTracks.firstOrNull { it.id == id }?.name
        showLabel(if (id == DISABLED_TRACK) "Audio off" else name ?: "Audio track changed")
        saveTrackChoice()
    }

    override fun selectSubtitleTrack(id: Int) {
        if (released) return
        mediaPlayer.setSpuTrack(id)
        pendingSubtitleRestore = null
        rememberedSubtitle = id
        refreshTracks()
        val name = _state.value.subtitleTracks.firstOrNull { it.id == id }?.name
        showLabel(if (id == DISABLED_TRACK) "Subtitles off" else name ?: "Subtitles on")
        saveTrackChoice()
    }

    override fun pickSubtitleFile() {
        if (released) return
        resumeAfterPicker = mediaPlayer.isPlaying
        if (resumeAfterPicker) mediaPlayer.pause()
        if (!host.launchSubtitlePicker()) {
            showLabel("No file picker on this device")
            resumeAfterPicking()
        }
    }

    override fun setSubtitleDelay(delayMs: Long) {
        if (released) return
        // libVLC takes microseconds.
        mediaPlayer.setSpuDelay(delayMs * 1_000)
        _state.update { it.copy(subtitleDelayMs = delayMs) }
    }

    override fun cycleAspect() {
        val modes = AspectMode.entries
        setAspect(modes[(modes.indexOf(_state.value.aspect) + 1) % modes.size])
    }

    override fun setAspect(mode: AspectMode) {
        if (released) return
        mediaPlayer.setVideoScale(mode.toScaleType())
        _state.update { it.copy(aspect = mode) }
        showLabel(mode.label)
    }

    override fun setRate(rate: Float) {
        if (released) return
        val clamped = rate.coerceIn(MIN_RATE, MAX_RATE)
        mediaPlayer.setRate(clamped)
        _state.update { it.copy(rate = clamped) }
        showLabel(PlayerText.rateLabel(clamped))
    }

    override fun toggleLock() {
        val locked = !_state.value.locked
        _state.update { it.copy(locked = locked) }
        showLabel(if (locked) "Screen locked" else "Screen unlocked")
    }

    override fun rotate() {
        if (released) return
        val (orientation, label) = when (activity.requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT to "Portrait"
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR to "Auto-rotate"
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE to "Landscape"
        }
        userRotated = true
        activity.requestedOrientation = orientation
        showLabel(label)
    }

    override fun enterPictureInPicture() {
        if (!released && host.supportsPictureInPicture) host.enterPictureInPicture()
    }

    override fun startOver() {
        resumeBannerJob?.cancel()
        _state.update { it.copy(resumedFromMs = null) }
        if (!released) seekInternal(0)
    }

    override fun dismissResumeBanner() {
        resumeBannerJob?.cancel()
        _state.update { it.copy(resumedFromMs = null) }
    }

    override fun playNext() {
        val videoId = _state.value.upNext?.videoId ?: nextEntry?.video?.id ?: return
        open(PlayRequest(videoId = videoId))
    }

    override fun playPrevious() {
        val videoId = previousVideoId ?: return
        open(PlayRequest(videoId = videoId))
    }

    override fun cancelUpNext() {
        upNextJob?.cancel()
        _state.update { it.copy(upNext = null) }
    }

    override fun close() {
        saveProgress()
        host.finishPlayback()
    }

    private fun onPlayerEvent(event: MediaPlayer.Event) {
        if (released || droppingStaleEvents) return
        when (event.type) {
            MediaPlayer.Event.Opening -> _state.update { it.copy(isBuffering = true) }
            MediaPlayer.Event.Buffering -> {
                val buffering = event.buffering < 100f
                if (buffering != _state.value.isBuffering) _state.update { it.copy(isBuffering = buffering) }
            }
            MediaPlayer.Event.Playing -> onPlaying()
            MediaPlayer.Event.Paused -> onPaused()
            MediaPlayer.Event.Stopped -> onStopped()
            MediaPlayer.Event.EndReached -> onEndReached()
            MediaPlayer.Event.EncounteredError -> onError()
            MediaPlayer.Event.TimeChanged -> onTimeChanged(event.timeChanged)
            MediaPlayer.Event.LengthChanged -> {
                val length = event.lengthChanged.coerceAtLeast(0L)
                _state.update { it.copy(durationMs = length) }
            }
            MediaPlayer.Event.SeekableChanged -> {
                val seekable = event.seekable
                _state.update { it.copy(seekable = seekable) }
            }
            MediaPlayer.Event.Vout -> if (event.voutCount > 0) onVideoOutput()
            MediaPlayer.Event.ESAdded -> onTracksChanged(added = true)
            MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> onTracksChanged(added = false)
        }
    }

    private fun onPlaying() {
        _state.update { it.copy(isPlaying = true, error = null) }
        audio.requestFocus()
        audio.registerNoisyReceiver()
        val firstStart = !playbackStarted
        playbackStarted = true
        pendingSeekMs?.let { target ->
            pendingSeekMs = null
            if (mediaPlayer.isSeekable) {
                mediaPlayer.setTime(target)
                lastPositionUpdate = SystemClock.uptimeMillis()
                _state.update { it.copy(positionMs = target) }
                if (resumeBannerPending) showResumeBanner(target)
            }
            resumeBannerPending = false
        }
        if (firstStart) {
            val rate = _state.value.rate
            if (rate != 1f) mediaPlayer.setRate(rate)
            refreshTracks()
            if (audioChoicePending) applyAudioChoice()
            trySetupSubtitles()
        }
    }

    private fun onPaused() {
        val position = currentPositionMs()
        _state.update { it.copy(isPlaying = false, positionMs = position) }
        audio.unregisterNoisyReceiver()
        saveProgress()
    }

    private fun onStopped() {
        _state.update { it.copy(isPlaying = false, isBuffering = false) }
        audio.unregisterNoisyReceiver()
    }

    private fun onEndReached() {
        ended = true
        audio.unregisterNoisyReceiver()
        val duration = _state.value.durationMs.takeIf { it > 0 } ?: mediaPlayer.length.coerceAtLeast(0L)
        resumeBannerJob?.cancel()
        _state.update {
            it.copy(isPlaying = false, isBuffering = false, positionMs = duration, resumedFromMs = null)
        }
        val videoId = nowPlaying?.videoId
        // Saving the full length marks it watched.
        if (videoId != null && duration > 0) persistPlayback(videoId, duration, duration)
        upNextJob?.cancel()
        upNextJob = scope.launch {
            val next = if (videoId != null) nextEntry ?: fetchNextEpisode(videoId) else null
            if (next == null) host.finishPlayback() else offerUpNext(next)
        }
    }

    private fun onError() {
        val kind = nowPlaying?.kind
        audio.unregisterNoisyReceiver()
        _state.update { it.copy(isPlaying = false, isBuffering = false, error = errorMessage(kind)) }
        mediaPlayer.stop()
    }

    private fun onTimeChanged(timeMs: Long) {
        val now = SystemClock.uptimeMillis()
        if (now - lastPositionUpdate < POSITION_UPDATE_INTERVAL_MS) return
        lastPositionUpdate = now
        _state.update { it.copy(positionMs = timeMs) }
    }

    private fun onVideoOutput() {
        val track = mediaPlayer.currentVideoTrack ?: return
        var width = track.width
        var height = track.height
        if (width <= 0 || height <= 0) return
        if (track.sarNum > 0 && track.sarDen > 0 && track.sarNum != track.sarDen) {
            width = (width.toLong() * track.sarNum / track.sarDen).toInt()
        }
        if (track.orientation in TRANSPOSED_ORIENTATIONS) {
            val swap = width
            width = height
            height = swap
        }
        val size = VideoSize(width, height)
        _videoSize.value = size
        if (!userRotated && !_state.value.inPictureInPicture) {
            activity.requestedOrientation = if (size.isPortrait) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
        // Movies show their resolution; take it from the picture when the scan didn't know it.
        val entry = nowPlaying?.entry
        if (entry != null && entry.video.kind == VideoKind.MOVIE && (entry.video.width == null || entry.video.height == null)) {
            val subtitle = PlayerText.titlesFor(entry, size).subtitle
            _state.update { it.copy(subtitle = subtitle) }
        }
    }

    private fun onTracksChanged(added: Boolean) {
        refreshTracks()
        if (!added || !playbackStarted) return
        if (audioChoicePending) applyAudioChoice()
        val restore = pendingSubtitleRestore
        if (restore != null && realTracks(mediaPlayer.spuTracks).any { it.id == restore }) {
            pendingSubtitleRestore = null
            mediaPlayer.setSpuTrack(restore)
            refreshTracks()
        }
    }

    private fun refreshTracks() {
        val audioTracks = trackList(mediaPlayer.audioTracks, mediaPlayer.audioTrack)
        val subtitleTracks = trackList(mediaPlayer.spuTracks, mediaPlayer.spuTrack)
        _state.update { it.copy(audioTracks = audioTracks, subtitleTracks = subtitleTracks) }
    }

    private fun trackList(tracks: Array<out MediaPlayer.TrackDescription>?, selectedId: Int): List<TrackUi> {
        val real = realTracks(tracks)
        if (real.isEmpty()) return emptyList()
        return buildList {
            add(TrackUi(id = DISABLED_TRACK, name = "Disable", selected = selectedId == DISABLED_TRACK))
            for (track in real) {
                val name = track.name?.takeIf { it.isNotBlank() } ?: "Track ${track.id}"
                add(TrackUi(id = track.id, name = name, selected = track.id == selectedId))
            }
        }
    }

    private fun realTracks(tracks: Array<out MediaPlayer.TrackDescription>?): List<MediaPlayer.TrackDescription> =
        tracks?.filter { it.id != DISABLED_TRACK }.orEmpty()

    /** The remembered audio track when it exists, else the first one in the preferred language. */
    private fun applyAudioChoice() {
        val tracks = realTracks(mediaPlayer.audioTracks)
        if (tracks.isEmpty()) return
        val remembered = rememberedAudio
        val target = when {
            remembered != null && tracks.any { it.id == remembered } -> remembered
            else -> tracks.firstOrNull { TrackLanguages.matches(it.name, audioLanguage) }?.id
        } ?: return
        audioChoicePending = false
        if (mediaPlayer.audioTrack != target) {
            mediaPlayer.setAudioTrack(target)
            refreshTracks()
        }
    }

    private fun startSubtitleSearch(current: NowPlaying, settings: Settings) {
        if (!settings.autoLoadSubtitles || current.kind == UriKind.NETWORK) {
            externalSubtitles = emptyList()
            return
        }
        val languages = listOf(settings.preferredSubtitleLanguage, Locale.getDefault().language, "en")
            .filter { it.isNotBlank() }
            .distinct()
        subtitleJob = scope.launch {
            val files = try {
                subtitleFinder.find(current.uri, current.fileName, languages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Subtitle search failed", e)
                emptyList()
            }
            externalSubtitles = files
            trySetupSubtitles()
        }
    }

    /**
     * Runs once both the embedded tracks are known (first Playing) and the sibling search is done:
     * restores the remembered subtitle, else picks the preferred language, else turns on an
     * external file when the video has no subtitles of its own.
     */
    private fun trySetupSubtitles() {
        if (subtitleSetupDone || !playbackStarted || released) return
        val external = externalSubtitles ?: return
        subtitleSetupDone = true
        val embedded = realTracks(mediaPlayer.spuTracks)
        val remembered = rememberedSubtitle
        var selected: File? = null
        when {
            remembered == DISABLED_TRACK -> mediaPlayer.setSpuTrack(DISABLED_TRACK)
            remembered != null && embedded.any { it.id == remembered } -> mediaPlayer.setSpuTrack(remembered)
            // Most likely an external file: its track appears once the slave below is loaded.
            remembered != null -> pendingSubtitleRestore = remembered
            else -> {
                val preferred = embedded.firstOrNull { TrackLanguages.matches(it.name, subtitleLanguage) }
                if (preferred != null) {
                    mediaPlayer.setSpuTrack(preferred.id)
                } else {
                    selected = external.firstOrNull { isPreferredLanguage(it) }
                        ?: external.firstOrNull().takeIf { embedded.isEmpty() }
                }
            }
        }
        for (file in external) {
            mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(file), file == selected)
        }
        refreshTracks()
    }

    /** "Movie.eng.srt" for "Movie.mkv": only the part after the video's own name says the language. */
    private fun isPreferredLanguage(file: File): Boolean {
        if (subtitleLanguage.isEmpty()) return false
        val videoBase = VideoFiles.baseName(nowPlaying?.fileName.orEmpty())
        val name = VideoFiles.baseName(file.name)
        val languagePart = if (videoBase.isNotEmpty() && name.startsWith(videoBase, ignoreCase = true)) {
            name.substring(videoBase.length)
        } else {
            name
        }
        return TrackLanguages.matches(languagePart, subtitleLanguage)
    }

    private fun saveTrackChoice() {
        val videoId = nowPlaying?.videoId ?: return
        val audioTrack = rememberedAudio
        val subtitleTrack = rememberedSubtitle
        val library = graph.library
        graph.appScope.launch {
            try {
                library.saveTracks(videoId, audioTrack, subtitleTrack)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save the track choice", e)
            }
        }
    }

    private fun resumeAfterPicking() {
        if (!resumeAfterPicker) return
        resumeAfterPicker = false
        play()
    }

    private suspend fun loadNeighbours(entry: VideoWithMeta?) {
        val video = entry?.video ?: return
        if (video.kind != VideoKind.EPISODE || video.showKey == null) return
        val next = fetchNextEpisode(video.id)
        val previous = try {
            previousEpisode(video)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Previous episode lookup failed", e)
            null
        }
        nextEntry = next
        previousVideoId = previous?.id
        _state.update { it.copy(hasNext = next != null, hasPrevious = previous != null) }
    }

    private suspend fun fetchNextEpisode(videoId: Long): VideoWithMeta? =
        try {
            graph.library.nextEpisode(videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Next episode lookup failed", e)
            null
        }

    /** The closest earlier episode of the same show (previous episode, else last of an earlier season). */
    private suspend fun previousEpisode(current: VideoEntity): VideoEntity? {
        val showKey = current.showKey ?: return null
        val season = current.season ?: 0
        val episode = current.episode ?: return null
        return graph.library.observeVideos().first()
            .asSequence()
            .map { it.video }
            .filter { it.id != current.id && it.kind == VideoKind.EPISODE && it.showKey == showKey }
            .filter { other ->
                val otherSeason = other.season ?: 0
                val otherLast = other.episodeEnd ?: other.episode ?: return@filter false
                otherSeason < season || (otherSeason == season && otherLast < episode)
            }
            .maxWithOrNull(
                compareBy<VideoEntity>(
                    { it.season ?: 0 },
                    { it.episode ?: 0 },
                    { it.fileName.lowercase(Locale.ROOT) },
                ),
            )
    }

    private suspend fun offerUpNext(next: VideoWithMeta) {
        val settings = graph.settings.settings.value
        val titles = PlayerText.titlesFor(next, size = null)
        val countdown = if (settings.autoPlayNext) UP_NEXT_COUNTDOWN_SECONDS else null
        val upNext = UpNextUi(
            videoId = next.video.id,
            title = titles.title,
            subtitle = titles.subtitle,
            artwork = PlayerText.artworkFor(next, titles.title, settings.preferLocalArtwork),
            countdownSeconds = countdown,
        )
        _state.update { it.copy(upNext = upNext) }
        if (countdown == null) return
        var remaining = countdown
        while (remaining > 0) {
            delay(1_000)
            remaining--
            val seconds = remaining
            _state.update { it.copy(upNext = it.upNext?.copy(countdownSeconds = seconds)) }
        }
        playNext()
    }

    private fun restartFromEnd(startAtMs: Long) {
        ended = false
        upNextJob?.cancel()
        _state.update { it.copy(upNext = null) }
        pendingSeekMs = startAtMs.takeIf { it > 0 }
        resumeBannerPending = false
        // An ended input can't seek or play again; a fresh one starts from the same media.
        mediaPlayer.stop()
        startPlayback()
    }

    private fun seekInternal(targetMs: Long) {
        if (ended) {
            restartFromEnd(targetMs)
            return
        }
        if (!playbackStarted) {
            // Not playing yet: replaces the resume point instead.
            pendingSeekMs = targetMs
            resumeBannerPending = false
        } else {
            mediaPlayer.setTime(targetMs)
        }
        lastPositionUpdate = SystemClock.uptimeMillis()
        _state.update { it.copy(positionMs = targetMs) }
    }

    private fun currentPositionMs(): Long = mediaPlayer.time.takeIf { it >= 0 } ?: _state.value.positionMs

    private fun clampPosition(positionMs: Long): Long {
        val duration = _state.value.durationMs
        return if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
    }

    private fun persistPlayback(videoId: Long, positionMs: Long, durationMs: Long) {
        val library = graph.library
        // App scope: the save must outlive the activity that is closing.
        graph.appScope.launch {
            try {
                library.savePlayback(videoId, positionMs, durationMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save the position", e)
            }
        }
    }

    private fun showError(message: String) {
        _state.update { it.copy(error = message, isPlaying = false, isBuffering = false) }
    }

    private fun showResumeBanner(positionMs: Long) {
        resumeBannerJob?.cancel()
        _state.update { it.copy(resumedFromMs = positionMs) }
        resumeBannerJob = scope.launch {
            delay(RESUME_BANNER_MS)
            _state.update { it.copy(resumedFromMs = null) }
        }
    }

    private fun showLabel(text: String) = showFeedback(GestureFeedback.Label(text), LABEL_MS)

    private fun showFeedback(feedback: GestureFeedback, hideAfterMs: Long?) {
        feedbackJob?.cancel()
        _state.update { it.copy(feedback = feedback) }
        if (hideAfterMs != null) hideFeedbackAfter(hideAfterMs)
    }

    private fun hideFeedbackAfter(delayMs: Long) {
        feedbackJob?.cancel()
        feedbackJob = scope.launch {
            delay(delayMs)
            seekSeriesStartMs = null
            seekSeriesTargetMs = null
            _state.update { it.copy(feedback = null) }
        }
    }

    private fun systemVolumeLevel(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else 0f
    }

    private fun currentBrightness(): Float {
        val windowBrightness = activity.window.attributes.screenBrightness
        if (windowBrightness >= 0f) return windowBrightness.coerceIn(MIN_BRIGHTNESS, 1f)
        val system = SystemSettings.System.getInt(
            activity.contentResolver,
            SystemSettings.System.SCREEN_BRIGHTNESS,
            DEFAULT_SYSTEM_BRIGHTNESS,
        )
        return (system / 255f).coerceIn(MIN_BRIGHTNESS, 1f)
    }

    private suspend fun rawFileName(uri: String, kind: UriKind): String {
        val parsed = Uri.parse(uri)
        if (kind == UriKind.CONTENT) queryDisplayName(parsed)?.let { return it }
        return parsed.lastPathSegment?.takeIf { it.isNotBlank() } ?: parsed.host ?: uri
    }

    private suspend fun queryDisplayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "No display name for a content URI", e)
            null
        }
    }

    private companion object {
        const val TAG = "PlayerController"

        const val ENABLE_SUBTITLES = true
        const val USE_TEXTURE_VIEW = false
        const val DISABLED_TRACK = -1
        const val FULL_VOLUME = 100
        const val DUCKED_VOLUME = 30

        const val SAVE_INTERVAL_MS = 10_000L
        const val POSITION_UPDATE_INTERVAL_MS = 250L
        const val RESUME_MIN_MS = 10_000L
        const val RESUME_BANNER_MS = 6_000L
        const val GESTURE_FEEDBACK_MS = 600L
        const val LABEL_MS = 1_200L
        const val UP_NEXT_COUNTDOWN_SECONDS = 8

        const val MIN_RATE = 0.25f
        const val MAX_RATE = 4f
        const val MIN_BRIGHTNESS = 0.01f
        const val DEFAULT_SYSTEM_BRIGHTNESS = 128

        /** libvlc_video_orient_t values 4–7 swap width and height. */
        val TRANSPOSED_ORIENTATIONS = 4..7

        fun seekStepMs(settings: Settings): Long = settings.seekStepSeconds.coerceAtLeast(1) * 1_000L

        fun errorMessage(kind: UriKind?): String = when (kind) {
            UriKind.SMB -> "Can't play this file — check the share and login"
            UriKind.NETWORK -> "Can't play this file — check the URL and your connection"
            UriKind.CONTENT, UriKind.FILE, null -> "Can't play this file"
        }

        fun AspectMode.toScaleType(): MediaPlayer.ScaleType = when (this) {
            AspectMode.BEST_FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
            AspectMode.FIT_SCREEN -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            AspectMode.FILL -> MediaPlayer.ScaleType.SURFACE_FILL
            AspectMode.RATIO_16_9 -> MediaPlayer.ScaleType.SURFACE_16_9
            AspectMode.RATIO_4_3 -> MediaPlayer.ScaleType.SURFACE_4_3
            AspectMode.ORIGINAL -> MediaPlayer.ScaleType.SURFACE_ORIGINAL
        }
    }
}
