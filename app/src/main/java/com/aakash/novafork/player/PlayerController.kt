package com.aakash.novafork.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings as SystemSettings
import android.util.Log
import android.view.KeyEvent
import androidx.compose.ui.graphics.asImageBitmap
import com.aakash.novafork.AppGraph
import com.aakash.novafork.CrashReporter
import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.core.opensubtitles.OpenSubtitlesException
import com.aakash.novafork.core.opensubtitles.OpenSubtitlesLanguages
import com.aakash.novafork.core.opensubtitles.SubtitleResult
import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.data.AspectMode
import com.aakash.novafork.data.HwDecoding
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.SmbCredentials
import com.aakash.novafork.data.db.VideoEntity
import com.aakash.novafork.data.db.VideoKind
import com.aakash.novafork.data.db.VideoWithMeta
import com.aakash.novafork.sources.DownloadedSubtitles
import com.aakash.novafork.sources.SubtitleFinder
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.CastDeviceUi
import com.aakash.novafork.ui.model.GestureFeedback
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState
import com.aakash.novafork.ui.model.QueueItemUi
import com.aakash.novafork.ui.model.RepeatMode
import com.aakash.novafork.ui.model.SubtitleSearchUi
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

/** The decoded picture as it is displayed: sample aspect ratio and rotation already applied. */
data class VideoSize(val width: Int, val height: Int) {
    val isPortrait: Boolean get() = height > width
}

/**
 * Owns one libVLC MediaPlayer and the play queue, and turns the player's events into
 * [PlayerUiState]; implements everything the player UI can ask for. Lives in [PlaybackService]
 * so the sound can go on without a screen; the player screen attaches itself as the [Host] while
 * it exists. Created on the main thread; every call is expected on the main thread (libVLC
 * delivers its events there too).
 *
 * Casting goes through libVLC's renderers (Chromecast, no Play Services): [CastDiscovery] lists
 * the devices while the screen is shown or a cast runs, and [startCasting] / [stopCasting] move
 * playback with MediaPlayer.setRenderer. While casting nothing is decoded for this screen: no
 * surface, audio focus, picture-in-picture or "no picture" watch, and leaving the screen keeps
 * the cast going in the service.
 */
class PlayerController(
    context: Context,
    private val graph: AppGraph,
    private val scope: CoroutineScope,
    /** Playback is over (closed, stopped, nothing left to play): the service ends the session. */
    private val onFinished: () -> Unit,
) : PlayerActions {

    /** What the controller needs from the screen that shows it. */
    interface Host {
        val supportsPictureInPicture: Boolean

        fun enterPictureInPicture()

        /** Opens the system document picker; false when the device has none. */
        fun launchSubtitlePicker(): Boolean

        /** Playback is over: closed by the user, nothing left to play, or nothing to open. */
        fun finishPlayback()

        /** Leaves the player screen without closing the player ("Play in background"). */
        fun leavePlayer()

        /** The window's brightness 0–1, or a negative value while it follows the system's. */
        var windowBrightness: Float

        /** The screen orientation the player asks for, an ActivityInfo.SCREEN_ORIENTATION_* value. */
        var screenOrientation: Int

        /** The configuration of the screen the player is shown on. */
        val screenConfiguration: Configuration
    }

    private class NowPlaying(val uri: String, val kind: UriKind, val fileName: String, val entry: VideoWithMeta?) {
        val videoId: Long? get() = entry?.video?.id

        /** Where its downloaded subtitles are kept (see [DownloadedSubtitles]). */
        val videoKey: String = DownloadedSubtitles.videoKey(entry?.video?.id, uri)

        val subtitleTarget: SubtitleDownloads.Target get() = SubtitleDownloads.Target(videoKey, uri, kind, fileName, entry)
    }

    /** A subtitle file just added and the subtitle tracks there were before it: the new one is its track. */
    private class PendingTrack(val path: String, val before: Set<Int>)

    /** What the user chose in a session that is reopened in software, handed to the new one. */
    private class CarriedChoices(val audioTrack: Int?, val subtitleTrack: Int?, val subtitleDelayMs: Long)

    private val appContext: Context = context.applicationContext
    private val libVLC = VlcEngine.libVLC(appContext)
    private val mediaPlayer: MediaPlayer = VlcEngine.newMediaPlayer(appContext)
    private val audioManager: AudioManager = checkNotNull(appContext.getSystemService(AudioManager::class.java))
    private val mainHandler = Handler(Looper.getMainLooper())
    private val subtitleFinder = SubtitleFinder(appContext, graph.smb) { server, share ->
        graph.library.smbCredentials(server, share)
    }
    private val subtitleDownloads = SubtitleDownloads(appContext, graph, subtitleFinder)

    /** The player screen, while one is shown. */
    private var host: Host? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _videoSize = MutableStateFlow<VideoSize?>(null)

    /** Null until the first picture is shown (and for audio-only media). */
    val videoSize: StateFlow<VideoSize?> = _videoSize.asStateFlow()

    private val _artwork = MutableStateFlow<Artwork?>(null)

    /** Artwork of the library video playing; null for a link. For the notification and media session. */
    val artwork: StateFlow<Artwork?> = _artwork.asStateFlow()

    /** The cast devices on the network, while the screen is shown or a cast runs. */
    private val castDiscovery = CastDiscovery(appContext, libVLC, scope) { devices -> onCastDevicesChanged(devices) }

    /**
     * The renderer set on the player, with a reference of its own (discovery may drop its one
     * at any time); null = playing on this device.
     */
    private var castTarget: RendererItem? = null

    /** The player screen is visible (see [onScreenVisibilityChanged]). */
    private var screenVisible = false

    private val _playRequested = MutableStateFlow(false)

    /**
     * The user wants playback to run: true from open or play until a pause (by the user, the
     * headphones, a lasting audio focus loss), an error, the end with nothing to follow, or stop.
     * Stays true while a video loads and through a short focus loss (a call).
     */
    val playRequested: StateFlow<Boolean> = _playRequested.asStateFlow()

    /** Something was opened and playback hasn't been stopped. */
    var hasContent = false
        private set

    /** Playing without the screen: sound only, the video track off. See [enterBackground]. */
    var inBackground = false
        private set

    /** "Play in background" was picked: the next leave keeps playing even with the setting off. */
    private var backgroundRequested = false

    /** The video track turned off for the background, turned back on when the screen returns. */
    private var savedVideoTrack: Int? = null

    // The play queue: library video ids; next/previous follow the episodes while it holds one.
    private val queue = PlayQueue()
    private val queueItems = HashMap<Long, QueueItemUi>()

    /** All items of the queue were looked up; until then [PlayerUiState.queue] stays empty. */
    private var queueItemsReady = false
    private var queueJob: Job? = null

    /** Bumped by every open(): work that finishes later must not land in the next video. */
    private var session = 0

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

    /** Frames for the scrub bubble: the open retriever, for which uri, and the latest request. */
    private var frameRetriever: MediaMetadataRetriever? = null
    private var frameRetrieverUri: String? = null
    private var frameJob: Job? = null
    private val frameLock = Mutex()

    /** The current video's shape read from its file before playback, for turning the screen early. */
    private var fileShape: VideoSize? = null

    /** False from open() until the new media is set: the player still holds the previous one. */
    private var mediaReady = false

    // A start waits for the video surface (onStart) and for the screen being in front (onResume).
    private var playWhenAttached = false
    private var playOnResume = false

    /** The activity is paused or stopped outside picture-in-picture: nothing is on screen. */
    private var hostPaused = false

    // Track choices
    private var rememberedAudio: Int? = null
    private var rememberedSubtitle: Int? = null
    private var audioLanguage: Set<String> = emptySet()
    private var subtitleLanguage: Set<String> = emptySet()
    private var audioChoicePending = false
    private var externalSubtitles: List<File>? = null
    private var subtitleSetupDone = false
    private var pendingSubtitleRestore: Int? = null

    /**
     * The paths of the subtitle files added to this session, in the order they were added (setup's,
     * downloads, picked ones). Setup and downloads never add a file twice; [trackOfFile] finds the
     * files' tracks from this order.
     */
    private val addedSubtitleFiles = ArrayList<String>()

    /** The subtitle tracks the video had before the first file was added; null until then. */
    private var tracksBeforeFiles: Set<Int>? = null

    // Subtitle downloads (OpenSubtitles)
    /** The results on the download list, by id. */
    private var subtitleResults: Map<String, SubtitleResult> = emptyMap()

    /** A downloaded file just added, waiting for libVLC to select its track. */
    private var pendingDownloadTrack: PendingTrack? = null

    /** The track of each downloaded file added to this session, by path. */
    private val downloadedTracks = HashMap<String, Int>()

    // Episodes around this one
    private var nextEntry: VideoWithMeta? = null
    private var previousVideoId: Long? = null

    // Pauses that should undo themselves
    private var pausedByFocusLoss = false
    private var resumeAfterPicker = false

    // Gestures
    private var volumeLevel = 0f
    private var volumeHudJob: Job? = null
    private var scrubStartMs: Long? = null
    private var seekSeriesStartMs: Long? = null
    private var seekSeriesTargetMs: Long? = null
    private var lastPositionUpdate = 0L

    // Events of a media that was just stopped, still queued on the main looper
    private var staleEventToken = 0
    private var droppingStaleEvents = false

    // Hardware decoding fallback (see fallBackToSoftwareDecoding)
    /** How the current media decodes; null until it is opened. */
    private var decoding: HwDecoding? = null
    private var videoTrackSeen = false
    private var videoOutputSeen = false

    /** Playback time without a picture so far, counted by [watchForPicture]. */
    private var noPictureMs = 0L
    private var carried: CarriedChoices? = null

    /** The last position save: a reopen reads the library only once it is written. */
    private var pendingSave: Job? = null

    private var openJob: Job? = null
    private var subtitleJob: Job? = null
    private var subtitleSearchJob: Job? = null
    private var subtitleDownloadJob: Job? = null
    private var autoSubtitleJob: Job? = null
    private var upNextJob: Job? = null
    private var resumeBannerJob: Job? = null
    private var feedbackJob: Job? = null
    private var pictureWatchJob: Job? = null

    private val eventListener = object : MediaPlayer.EventListener {
        override fun onEvent(event: MediaPlayer.Event) = onPlayerEvent(event)
    }

    private val audio = PlaybackAudio(
        appContext,
        object : PlaybackAudio.Listener {
            override fun onFocusLost(transient: Boolean) {
                PlayerDiagnostics.record(if (transient) "audio focus lost for a while" else "audio focus lost")
                if (released || !mediaPlayer.isPlaying) return
                pausedByFocusLoss = transient
                if (!transient) _playRequested.value = false
                mediaPlayer.pause()
            }

            override fun onFocusRegained() {
                PlayerDiagnostics.record("audio focus regained")
                if (released || !pausedByFocusLoss) return
                pausedByFocusLoss = false
                _playRequested.value = true
                mediaPlayer.play()
            }

            override fun onDuck(ducked: Boolean) {
                if (!released) mediaPlayer.setVolume(if (ducked) DUCKED_VOLUME else FULL_VOLUME)
            }

            override fun onBecomingNoisy() {
                PlayerDiagnostics.record("headphones disconnected")
                pause()
            }
        },
    )

    init {
        mediaPlayer.setEventListener(eventListener)
        val settings = graph.settings.settings.value
        volumeLevel = systemVolumeLevel()
        _state.value = PlayerUiState(
            aspect = settings.defaultAspect,
            seekStepMs = seekStepMs(settings),
            // Both come from the screen once one attaches.
            canEnterPictureInPicture = false,
            canRotate = canRotateScreen(appContext.resources.configuration),
            volume = volumeLevel,
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

    /** The library video playing (or opening), if it is one. */
    val currentVideoId: Long? get() = nowPlaying?.videoId ?: lastRequest?.videoId

    /** Leaving the player screen keeps the sound playing: the setting, or "Play in background" just picked. */
    val continuesInBackground: Boolean
        get() = backgroundRequested || graph.settings.settings.value.backgroundAudio

    /** Playing, or about to (loading, a short audio focus loss), with nothing gone wrong. */
    val wantsToPlay: Boolean
        get() = !released && hasContent && _playRequested.value && _state.value.error == null

    /**
     * Leaving the player screen now would keep playback going (see [enterBackground]): the sound
     * when it is wanted in the background, a cast always (paused or not).
     */
    val canPlayInBackground: Boolean
        get() = inBackground || (isCasting && hasContent && !released) || (continuesInBackground && wantsToPlay)

    /** Playback goes to a cast device ([PlayerUiState.castingTo]) rather than this screen. */
    val isCasting: Boolean get() = castTarget != null

    /** Opens one video (or link); it replaces the queue. */
    fun open(request: PlayRequest) {
        if (released) return
        setQueue(listOfNotNull(request.videoId), startIndex = 0, shuffle = queue.shuffled)
        openSession(request, carry = null)
    }

    /** Plays [request]'s videos one after the other; [startPositionMs] continues the first one exactly there. */
    fun openQueue(request: QueueRequest, startPositionMs: Long? = null) {
        if (released) return
        setQueue(request.videoIds, request.startIndex, request.shuffle)
        val videoId = queue.currentId ?: return
        openSession(PlayRequest(videoId = videoId, startPositionMs = startPositionMs), carry = null)
        if (queue.size > 1) loadQueueItems()
    }

    /** The player screen is shown: it gets the brightness, rotation and picture-in-picture calls. */
    fun attachHost(host: Host) {
        this.host = host
        userRotated = false
        val brightness = currentBrightness(host)
        _state.update {
            it.copy(
                canEnterPictureInPicture = host.supportsPictureInPicture && !isCasting,
                canRotate = canRotateScreen(host.screenConfiguration),
                inPictureInPicture = false,
                brightness = brightness,
            )
        }
        (_videoSize.value ?: fileShape)?.let { applyOrientation(it) }
    }

    fun detachHost(host: Host) {
        if (this.host !== host) return
        this.host = null
        _state.update { it.copy(inPictureInPicture = false) }
    }

    /**
     * The player screen became visible or stopped being visible. Cast devices are looked for
     * only while it is, or while a cast runs.
     */
    fun onScreenVisibilityChanged(visible: Boolean) {
        screenVisible = visible
        updateCastDiscovery()
    }

    /**
     * The player screen is going away while something plays (or is about to): keeps the sound
     * playing without a picture when [continuesInBackground]. False when it doesn't; the screen
     * then pauses as before.
     */
    fun enterBackground(): Boolean {
        if (inBackground) return true
        if (!canPlayInBackground) return false
        inBackground = true
        PlayerDiagnostics.record("background: sound only")
        hostPaused = false
        pictureWatchJob?.cancel()
        scrubStartMs = null
        // Off before the surface goes: a decoder writing to a vanished surface can fail.
        disableVideo()
        detachSurface()
        val startHeldBack = playWhenAttached || playOnResume
        playWhenAttached = false
        playOnResume = false
        if (startHeldBack && mediaReady) startPlayback()
        return true
    }

    /** The player screen is back (call after [attachSurface]): the picture returns. */
    fun exitBackground() {
        backgroundRequested = false
        if (!inBackground) return
        inBackground = false
        PlayerDiagnostics.record("background: picture back")
        val restored = restoreVideo()
        if (mediaPlayer.isPlaying) {
            watchForPicture()
        } else if (restored && playbackStarted && !ended && mediaPlayer.isSeekable) {
            // Paused: a video track turned back on shows nothing until a frame is decoded; a seek
            // to where it is decodes one.
            mediaPlayer.setTime(currentPositionMs())
        }
    }

    /**
     * Ends the session for good: saves the position, stops the media, lets go of audio focus,
     * empties the queue and closes the player screen. The controller can open something new after.
     */
    fun stop() {
        if (released) return
        val hadContent = hasContent
        saveProgress()
        openJob?.cancel()
        subtitleJob?.cancel()
        subtitleSearchJob?.cancel()
        subtitleDownloadJob?.cancel()
        autoSubtitleJob?.cancel()
        upNextJob?.cancel()
        resumeBannerJob?.cancel()
        feedbackJob?.cancel()
        pictureWatchJob?.cancel()
        queueJob?.cancel()
        if (mediaPlayer.hasMedia()) {
            mediaPlayer.stop()
            dropStaleEvents()
        }
        // The session is over, and with it the cast: the device goes back to idle.
        dropCastTarget()
        descriptor.closeQuietly()
        descriptor = null
        audio.release()
        session++
        resetSession()
        nowPlaying = null
        lastRequest = null
        mediaReady = false
        hasContent = false
        inBackground = false
        backgroundRequested = false
        hostPaused = false
        _playRequested.value = false
        queue.clear()
        queueItems.clear()
        queueItemsReady = false
        _artwork.value = null
        _state.update { old ->
            freshState(old, title = "").copy(
                isBuffering = false,
                queue = emptyList(),
                queueIndex = 0,
                feedback = null,
            )
        }
        if (hadContent) PlayerDiagnostics.record("playback stopped")
        host?.finishPlayback()
    }

    /**
     * Starts a new session for [request] without touching the queue (an item of it, a retry, a
     * restart); [carry] keeps the user's choices across a software reopen.
     */
    private fun openSession(request: PlayRequest, carry: CarriedChoices?) {
        if (released) return
        saveProgress()
        openJob?.cancel()
        subtitleJob?.cancel()
        // Belong to the previous video. A download the user picked still finishes and is kept
        // for that video, but is not added to this one (see downloadSubtitle).
        subtitleSearchJob?.cancel()
        autoSubtitleJob?.cancel()
        upNextJob?.cancel()
        resumeBannerJob?.cancel()
        pictureWatchJob?.cancel()
        if (mediaPlayer.hasMedia()) {
            mediaPlayer.stop()
            dropStaleEvents()
        }
        descriptor.closeQuietly()
        descriptor = null
        audio.unregisterNoisyReceiver()
        lastRequest = request
        nowPlaying = null
        mediaReady = false
        hasContent = true
        _playRequested.value = true
        session++
        resetSession()
        carried = carry
        val subtitleDelayMs = carry?.subtitleDelayMs ?: 0L
        _state.update { old -> freshState(old, title = request.title.orEmpty()).copy(subtitleDelayMs = subtitleDelayMs) }
        publishQueue()
        updateNavigation()
        val what = if (request.videoId != null) "library video" else "link"
        CrashReporter.log("Player: opening a $what")
        PlayerDiagnostics.record("open: a $what${if (request.startPositionMs != null) " at a set position" else ""}")
        val lastSave = pendingSave
        openJob = scope.launch {
            try {
                // Reopening the video that was just saved (a new intent, a retry) must resume from
                // that save, not from the one before it. Bounded: a busy database never holds up playback.
                if (lastSave != null) withTimeoutOrNull(SAVE_WAIT_MS) { lastSave.join() }
                load(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Whatever goes wrong while preparing a video ends in the error card, not a crash.
                Log.w(TAG, "Opening failed", e)
                CrashReporter.log("Player: opening failed", e)
                PlayerDiagnostics.record("open: failed with ${e.javaClass.simpleName}")
                showError(errorMessage(nowPlaying?.kind))
            }
        }
    }

    private fun setQueue(videoIds: List<Long>, startIndex: Int, shuffle: Boolean) {
        queueJob?.cancel()
        queue.set(videoIds, startIndex, shuffle)
        queueItems.clear()
        queueItemsReady = false
    }

    /** Looks up every queued video for [PlayerUiState.queue]; drops the ones gone from the library. */
    private fun loadQueueItems() {
        queueJob?.cancel()
        val ids = queue.playOrder.distinct().filter { it !in queueItems }
        val settings = graph.settings.settings.value
        queueJob = scope.launch {
            // One read of the library for the whole queue; the few it doesn't show (a source
            // turned off, a file missing at the last scan) are looked up one by one.
            val shown = try {
                val videos = graph.library.observeVideos().first()
                val wanted = ids.toHashSet()
                withContext(Dispatchers.Default) {
                    videos.filter { it.video.id in wanted }.associateBy { it.video.id }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Queue lookup failed", e)
                emptyMap()
            }
            val found = HashMap<Long, QueueItemUi>()
            val gone = HashSet<Long>()
            for (id in ids) {
                val entry = shown[id] ?: try {
                    graph.library.video(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Kept: it may well play; only its row in the list stays blank.
                    Log.w(TAG, "Queue lookup failed", e)
                    continue
                }
                if (entry == null) gone.add(id) else found[id] = queueItemFor(entry, settings)
            }
            if (released) return@launch
            queueItems.putAll(found)
            queue.remove(gone)
            queueItemsReady = true
            publishQueue()
            updateNavigation()
        }
    }

    private fun queueItemFor(entry: VideoWithMeta, settings: Settings): QueueItemUi {
        val titles = PlayerText.titlesFor(entry, size = null)
        return QueueItemUi(
            videoId = entry.video.id,
            title = titles.title,
            subtitle = titles.subtitle,
            artwork = PlayerText.artworkFor(entry, titles.title, settings.preferLocalArtwork),
        )
    }

    private fun publishQueue() {
        val items = if (queueItemsReady) {
            queue.playOrder.map { id ->
                queueItems[id] ?: QueueItemUi(videoId = id, title = "", subtitle = null, artwork = Artwork())
            }
        } else {
            emptyList()
        }
        val index = if (items.isEmpty()) 0 else queue.position
        _state.update { it.copy(queue = items, queueIndex = index, shuffle = queue.shuffled) }
    }

    /** A queue of two or more moves through itself; a single video follows its episodes. */
    private val usesQueue: Boolean get() = queue.size > 1

    private val wrapsQueue: Boolean get() = _state.value.repeatMode == RepeatMode.ALL

    private fun updateNavigation() {
        val hasPrevious: Boolean
        val hasNext: Boolean
        if (usesQueue) {
            hasPrevious = queue.previousPosition(wrapsQueue) != null
            hasNext = queue.nextPosition(wrapsQueue) != null
        } else {
            hasPrevious = previousVideoId != null
            hasNext = nextEntry != null
        }
        _state.update { it.copy(hasPrevious = hasPrevious, hasNext = hasNext) }
    }

    /** Plays the queue item at [position] of the play order. */
    private fun playQueuePosition(position: Int) {
        val videoId = queue.moveTo(position) ?: return
        PlayerDiagnostics.record("queue: item ${position + 1} of ${queue.size}")
        openSession(PlayRequest(videoId = videoId), carry = null)
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
        if (entry != null) {
            val videoId = entry.video.id
            // A link that turned out to be a library video becomes a queue of one.
            if (queue.isEmpty) queue.set(listOf(videoId), startIndex = 0, shuffle = queue.shuffled)
            queueItems[videoId] = queueItemFor(entry, settings)
            if (queue.size == 1) queueItemsReady = true
            publishQueue()
            _artwork.value = PlayerText.artworkFor(entry, titles.title, settings.preferLocalArtwork)
        } else {
            _artwork.value = null
        }

        val video = entry?.video
        val carry = carried
        rememberedAudio = carry?.audioTrack ?: video?.audioTrackId
        rememberedSubtitle = carry?.subtitleTrack ?: video?.subtitleTrackId
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

        // Turn the screen to the video's shape from the file itself, before the first frame: a
        // phone's reel opens in portrait straight away instead of landscape (owner).
        fileShape = if (kind == UriKind.CONTENT || kind == UriKind.FILE) readFileShape(uri) else null
        fileShape?.let { applyOrientation(it) }

        val decoding = MediaSource.decodingFor(uri, settings.hardwareDecoding)
        this.decoding = decoding
        PlayerDiagnostics.record("open: ${describeSource(kind, fileName)}, ${decodingLabel(decoding)}")
        val opened = openMedia(uri, kind, settings, decoding)
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

    /**
     * The picture's shape as it is shown: the stored width and height, swapped when the file
     * says to turn it a quarter (how phones store videos filmed upright). Null when unknown.
     */
    private suspend fun readFileShape(uri: String): VideoSize? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val parsed = Uri.parse(uri)
            if (parsed.scheme == "file") retriever.setDataSource(parsed.path) else retriever.setDataSource(appContext, parsed)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            when {
                width <= 0 || height <= 0 -> null
                rotation % 180 != 0 -> VideoSize(height, width)
                else -> VideoSize(width, height)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Builds the media off the main thread; never leaks a descriptor when the open is cancelled. */
    private suspend fun openMedia(uri: String, kind: UriKind, settings: Settings, decoding: HwDecoding): OpenedMedia? {
        val credentials = if (kind == UriKind.SMB) smbCredentialsFor(uri) else null
        val forRenderer = isCasting
        val result = AtomicReference<OpenedMedia?>(null)
        try {
            withContext(Dispatchers.IO) {
                result.set(MediaSource.open(appContext, libVLC, uri, settings, decoding, credentials, forRenderer))
            }
        } catch (e: CancellationException) {
            result.getAndSet(null)?.discard()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't open a ${kind.name.lowercase(Locale.ROOT)} video", e)
            PlayerDiagnostics.record("open: couldn't build the media: ${e.javaClass.simpleName}")
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
        mediaReady = true
        requestStart()
    }

    /**
     * Starts playback once it can be seen: without a surface libVLC would open the video output
     * blind and play sound only, and a screen left while the video was loading must not start
     * playing in the background. In the background (see [enterBackground]) sound only is the
     * point, and a cast needs no surface at all.
     */
    private fun requestStart() {
        when {
            isHidden -> {
                playOnResume = true
                PlayerDiagnostics.record("start: waiting for the screen to come back")
            }
            inBackground || isCasting -> startPlayback()
            attachedLayout == null -> {
                playWhenAttached = true
                PlayerDiagnostics.record("start: waiting for the video surface")
            }
            else -> startPlayback()
        }
    }

    /**
     * The player screen was left without background playback (paused, or stopped outside
     * picture-in-picture): a start waits until it is back in front, or until [enterBackground].
     * Never while casting: the video is on the TV, not on this screen.
     */
    val isHidden: Boolean get() = hostPaused && !_state.value.inPictureInPicture && !isCasting

    private fun startPlayback() {
        // A cast plays on the TV: this device's audio (and other apps playing on it) is left alone.
        if (!isCasting) audio.requestFocus()
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
        addedSubtitleFiles.clear()
        tracksBeforeFiles = null
        subtitleResults = emptyMap()
        pendingDownloadTrack = null
        downloadedTracks.clear()
        nextEntry = null
        previousVideoId = null
        pausedByFocusLoss = false
        scrubStartMs = null
        seekSeriesStartMs = null
        seekSeriesTargetMs = null
        playWhenAttached = false
        playOnResume = false
        // A picker result that arrives after this belongs to the previous video.
        resumeAfterPicker = false
        decoding = null
        videoTrackSeen = false
        videoOutputSeen = false
        noPictureMs = 0L
        carried = null
        savedVideoTrack = null
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
        canRotate = old.canRotate,
        seekStepMs = seekStepMs(graph.settings.settings.value),
        volume = old.volume,
        brightness = old.brightness,
        shuffle = old.shuffle,
        repeatMode = old.repeatMode,
        queue = old.queue,
        queueIndex = old.queueIndex,
        castDevices = old.castDevices,
        castingTo = old.castingTo,
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
        PlayerDiagnostics.record("surface attached")
        // The scale lives in the view helper that attachViews just created.
        mediaPlayer.setVideoScale(_state.value.aspect.toScaleType())
        if (playWhenAttached) {
            playWhenAttached = false
            requestStart()
        }
    }

    /** Detaches the video views; with [layout], only when that layout is the attached one. */
    fun detachSurface(layout: VLCVideoLayout? = null) {
        val attached = attachedLayout ?: return
        if (layout != null && layout !== attached) return
        attachedLayout = null
        if (!released) mediaPlayer.detachViews()
        PlayerDiagnostics.record("surface detached")
    }

    /**
     * The screen is no longer in front (outside picture-in-picture). Only a playing media is
     * paused: pausing one that is still opening would stop its input. One that starts later is
     * held back by [requestStart] and [onPlaying] until [onHostResumed].
     */
    fun onHostPaused() {
        if (released) return
        if (!hostPaused) PlayerDiagnostics.record("screen left")
        hostPaused = true
        pausedByFocusLoss = false
        // A cast goes on without the screen.
        if (mediaPlayer.isPlaying && !isCasting) {
            _playRequested.value = false
            mediaPlayer.pause()
        }
        saveProgress()
    }

    /** The screen is in front again: a start that was held back while it was away happens now. */
    fun onHostResumed() {
        if (hostPaused) PlayerDiagnostics.record("screen back in front")
        hostPaused = false
        if (released || !playOnResume) return
        playOnResume = false
        requestStart()
    }

    /** Android 16 ignores orientation requests on large screens; the rotate control hides there. */
    fun onConfigurationChanged(configuration: Configuration) {
        val canRotate = canRotateScreen(configuration)
        if (canRotate != _state.value.canRotate) _state.update { it.copy(canRotate = canRotate) }
    }

    fun onPictureInPictureModeChanged(inPictureInPicture: Boolean) {
        PlayerDiagnostics.record(if (inPictureInPicture) "picture-in-picture on" else "picture-in-picture off")
        if (inPictureInPicture) {
            scrubStartMs = null
            feedbackJob?.cancel()
            volumeHudJob?.cancel()
        }
        _state.update {
            it.copy(
                inPictureInPicture = inPictureInPicture,
                feedback = if (inPictureInPicture) null else it.feedback,
                volumeHud = if (inPictureInPicture) null else it.volumeHud,
            )
        }
    }

    fun onBackPressed() {
        val host = host
        when {
            _state.value.locked -> showLabel("Unlock to leave the player")
            // The video goes on on the TV: back only leaves the screen, the notification brings it back.
            isCasting && hasContent && host != null -> host.leavePlayer()
            else -> close()
        }
    }

    fun onSubtitleFilePicked(uri: Uri?) {
        if (released) return
        if (uri == null) {
            resumeAfterPicking()
            return
        }
        val pickedFor = session
        scope.launch {
            val file = try {
                subtitleFinder.importPicked(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Subtitle import failed", e)
                null
            }
            // Another video started while the file was copied: it isn't that video's subtitle.
            if (released || session != pickedFor) return@launch
            val added = file != null && addSubtitleFile(file, select = true)
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
        frameJob?.cancel()
        // Not while a read still runs; the retriever then goes with the process.
        if (frameLock.tryLock()) {
            frameRetriever?.let { retriever -> runCatching { retriever.release() } }
            frameRetriever = null
            frameLock.unlock()
        }
        openJob?.cancel()
        subtitleJob?.cancel()
        subtitleSearchJob?.cancel()
        subtitleDownloadJob?.cancel()
        autoSubtitleJob?.cancel()
        upNextJob?.cancel()
        resumeBannerJob?.cancel()
        feedbackJob?.cancel()
        pictureWatchJob?.cancel()
        queueJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        PlayerDiagnostics.record("player released")
        host = null
        _playRequested.value = false
        audio.release()
        castDiscovery.release()
        mediaPlayer.setEventListener(null)
        mediaPlayer.stop()
        attachedLayout?.let { mediaPlayer.detachViews() }
        attachedLayout = null
        // The player lets go of its own reference to the renderer; this one goes after it.
        mediaPlayer.release()
        castTarget?.release()
        castTarget = null
        descriptor.closeQuietly()
        descriptor = null
    }

    override fun togglePlay() {
        if (released) return
        if (mediaPlayer.isPlaying) pause() else play()
    }

    override fun play() {
        if (released || !hasContent) return
        pausedByFocusLoss = false
        val request = lastRequest
        if (_state.value.error != null && request != null) {
            // Retry, continuing where it stopped when it had started.
            val position = _state.value.positionMs.takeIf { playbackStarted && it > 0 }
            openSession(request.copy(startPositionMs = position ?: request.startPositionMs), carry = null)
            return
        }
        if (ended) {
            restartFromEnd(startAtMs = 0)
            return
        }
        _playRequested.value = true
        // Still loading: the player holds the previous media (already closed), and the new one
        // starts by itself once it is set.
        if (!mediaReady) return
        if (isHidden) {
            // A headset button with the screen gone: plays once it is back (or in the background).
            playOnResume = true
            return
        }
        startPlayback()
    }

    override fun pause() {
        if (released) return
        pausedByFocusLoss = false
        _playRequested.value = false
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
            frameJob?.cancel()
            _state.update { it.copy(scrubFrame = null) }
            seekInternal(target)
            showFeedback(feedback, GESTURE_FEEDBACK_MS)
        } else {
            showFeedback(feedback, hideAfterMs = null)
            previewFrame(target)
        }
    }

    /**
     * The frame at [positionMs] for the scrub bubble, as Samsung's player shows one: read from a
     * file on the phone (not a stream) with one retriever kept open while scrubbing, the latest
     * request winning.
     */
    private fun previewFrame(positionMs: Long) {
        val playing = nowPlaying ?: return
        if (playing.kind != UriKind.CONTENT && playing.kind != UriKind.FILE) return
        frameJob?.cancel()
        frameJob = scope.launch {
            delay(FRAME_PREVIEW_DELAY_MS)
            val frame = withContext(Dispatchers.IO) {
                // One read at a time: a cancelled read may still be running when the next starts.
                frameLock.withLock {
                    runCatching {
                        val retriever = frameRetriever?.takeIf { frameRetrieverUri == playing.uri } ?: MediaMetadataRetriever().also { opened ->
                            frameRetriever?.let { old -> runCatching { old.release() } }
                            val parsed = Uri.parse(playing.uri)
                            if (parsed.scheme == "file") opened.setDataSource(parsed.path) else opened.setDataSource(appContext, parsed)
                            frameRetriever = opened
                            frameRetrieverUri = playing.uri
                        }
                        val micros = positionMs * 1000
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            retriever.getScaledFrameAtTime(micros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, FRAME_PREVIEW_PX, FRAME_PREVIEW_PX)
                        } else {
                            retriever.getFrameAtTime(micros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        }
                    }.getOrNull()?.asImageBitmap()
                }
            }
            if (frame != null && scrubStartMs != null) _state.update { it.copy(scrubFrame = frame) }
        }
    }

    /**
     * A volume key while the player is on screen (not in picture-in-picture): one step of the
     * media volume, without the system's volume panel, shown with the player's own indicator
     * ([PlayerUiState.volumeHud]). False when the system should handle the key: a device with
     * fixed volume, or a change it refused.
     */
    fun onVolumeKey(keyCode: Int): Boolean {
        if (released || audioManager.isVolumeFixed) return false
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            KeyEvent.KEYCODE_VOLUME_MUTE -> AudioManager.ADJUST_TOGGLE_MUTE
            else -> return false
        }
        try {
            // No FLAG_SHOW_UI: the player shows the level itself.
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "Volume change refused", e)
            return false
        }
        val level = if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) 0f else systemVolumeLevel()
        volumeLevel = level
        _state.update { it.copy(volume = level, volumeHud = level) }
        volumeHudJob?.cancel()
        volumeHudJob = scope.launch {
            delay(VOLUME_HUD_MS)
            _state.update { it.copy(volumeHud = null) }
        }
        return true
    }

    override fun endGesture() {
        scrubStartMs = null
        if (_state.value.feedback != null) hideFeedbackAfter(GESTURE_FEEDBACK_MS)
    }

    override fun selectAudioTrack(id: Int) {
        if (released) return
        PlayerDiagnostics.record("user: audio track $id")
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
        PlayerDiagnostics.record("user: subtitle track $id")
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
        if (host?.launchSubtitlePicker() != true) {
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
        val host = host
        if (released || host == null || !_state.value.canRotate) return
        val (orientation, label) = when (host.screenOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT to "Portrait"
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR to "Auto-rotate"
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE to "Landscape"
        }
        userRotated = true
        host.screenOrientation = orientation
        showLabel(label)
    }

    override fun enterPictureInPicture() {
        val host = host
        // Casting, this screen shows no video to put in a window.
        if (!released && !isCasting && host != null && host.supportsPictureInPicture) host.enterPictureInPicture()
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
        if (released || !hasContent) return
        if (usesQueue) {
            queue.nextPosition(wrap = wrapsQueue)?.let { playQueuePosition(it) }
            return
        }
        val videoId = _state.value.upNext?.videoId ?: nextEntry?.video?.id ?: return
        open(PlayRequest(videoId = videoId))
    }

    override fun playPrevious() {
        if (released || !hasContent) return
        if (usesQueue) {
            queue.previousPosition(wrap = wrapsQueue)?.let { playQueuePosition(it) }
            return
        }
        val videoId = previousVideoId ?: return
        open(PlayRequest(videoId = videoId))
    }

    override fun cancelUpNext() {
        upNextJob?.cancel()
        // Ended, and nothing follows now.
        if (ended) _playRequested.value = false
        _state.update { it.copy(upNext = null) }
    }

    override fun toggleShuffle() {
        if (released) return
        val on = !queue.shuffled
        queue.setShuffled(on)
        PlayerDiagnostics.record(if (on) "user: shuffle on" else "user: shuffle off")
        publishQueue()
        updateNavigation()
        showLabel(if (on) "Shuffle on" else "Shuffle off")
        reofferAfterEnd()
    }

    override fun cycleRepeat() {
        if (released) return
        val mode = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        PlayerDiagnostics.record("user: repeat ${mode.name.lowercase(Locale.ROOT)}")
        _state.update { it.copy(repeatMode = mode) }
        updateNavigation()
        showLabel(
            when (mode) {
                RepeatMode.OFF -> "Repeat off"
                RepeatMode.ALL -> if (usesQueue) "Repeat all" else "Repeat on"
                RepeatMode.ONE -> "Repeat this video"
            },
        )
        reofferAfterEnd()
    }

    override fun playQueueItem(index: Int) {
        if (released || !hasContent || index !in 0 until queue.size) return
        if (index == queue.position && !ended && _state.value.error == null) {
            play()
            return
        }
        playQueuePosition(index)
    }

    override fun playInBackground() {
        val host = host
        if (released || !hasContent || host == null) return
        PlayerDiagnostics.record("user: play in background")
        backgroundRequested = true
        when {
            // Ended with the up-next card up: what follows starts now rather than this one again.
            ended && _state.value.upNext != null -> playNext()
            !mediaPlayer.isPlaying -> play()
        }
        host.leavePlayer()
    }

    override fun startCasting(id: String) {
        if (released || castTarget?.name == id) return
        // A reference of its own, kept while the player uses it: discovery may drop its one any time.
        val item = castDiscovery.find(id)?.takeIf { it.retain() }
        if (item == null) {
            showLabel("That device is no longer available")
            return
        }
        val name = CastDiscovery.displayNameOf(item, id)
        PlayerDiagnostics.record("user: cast to a ${item.type ?: "renderer"}")
        CrashReporter.log("Player: casting")
        if (moveToRenderer(item, name)) {
            showLabel("Casting to $name")
        } else {
            item.release()
            showLabel("Couldn't cast to $name")
        }
    }

    /** Called from the player screen: the video comes back to it. */
    override fun stopCasting() {
        if (released || castTarget == null) return
        PlayerDiagnostics.record("user: stop casting")
        CrashReporter.log("Player: casting stopped")
        if (moveToRenderer(null, null)) showLabel("Playing on this device")
    }

    override fun scanForCastDevices() {
        if (released) return
        PlayerDiagnostics.record("user: look for cast devices")
        // Asked from the screen, so it is shown: looks even if its visibility hasn't been reported yet.
        castDiscovery.rescan()
    }

    override fun close() {
        saveProgress()
        onFinished()
    }

    override fun searchSubtitles() {
        if (released) return
        subtitleSearchJob?.cancel()
        val current = nowPlaying
        val languages = subtitleDownloads.languages(graph.settings.settings.value)
        val label = SubtitleDownloadRules.languagesLabel(languages)
        val problem = when {
            current == null -> "The video is still opening. Try again in a moment."
            !subtitleDownloads.hasKey -> OpenSubtitlesException.NO_KEY_MESSAGE
            else -> null
        }
        if (current == null || problem != null) {
            _state.update { it.copy(subtitleSearch = SubtitleSearchUi(error = problem, languages = label)) }
            return
        }
        PlayerDiagnostics.record("user: search subtitles (${if (current.kind.isLocal) "with" else "without"} movie hash)")
        _state.update { it.copy(subtitleSearch = SubtitleSearchUi(loading = true, languages = label)) }
        val forSession = session
        subtitleSearchJob = scope.launch {
            try {
                val results = subtitleDownloads.search(current.subtitleTarget, languages)
                val downloaded = subtitleDownloads.downloadedNames(current.videoKey)
                if (released || session != forSession) return@launch
                subtitleResults = results.associateBy { it.id }
                val items = results.map { SubtitleDownloadRules.toUi(it, downloaded) }
                _state.update { state ->
                    state.copy(subtitleSearch = state.subtitleSearch?.copy(loading = false, results = items, error = null))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Subtitle search failed", e)
                if (released || session != forSession) return@launch
                val message = SubtitleDownloadRules.errorMessage(e)
                _state.update { state ->
                    state.copy(subtitleSearch = state.subtitleSearch?.copy(loading = false, results = emptyList(), error = message))
                }
            }
        }
    }

    override fun downloadSubtitle(id: String) {
        if (released) return
        val current = nowPlaying ?: return
        val result = subtitleResults[id] ?: return
        val search = _state.value.subtitleSearch ?: return
        if (search.downloadingId != null) return
        PlayerDiagnostics.record("user: download a subtitle${if (result.movieHashMatch) " matching the file" else ""}")
        _state.update { state -> state.copy(subtitleSearch = state.subtitleSearch?.copy(downloadingId = id, error = null)) }
        val forSession = session
        subtitleDownloadJob = scope.launch {
            val message = try {
                val file = subtitleDownloads.download(current.subtitleTarget, result)
                // Kept for its video either way; only that video's session gets the track.
                if (released || session != forSession) return@launch
                addDownloadedSubtitle(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Subtitle download failed", e)
                if (released || session != forSession) return@launch
                SubtitleDownloadRules.errorMessage(e)
            }
            val waiting = _state.value.subtitleSearch?.downloadingId == id
            if (message == null) {
                // The list closes once its subtitle is on; a list opened again meanwhile stays.
                if (waiting) _state.update { it.copy(subtitleSearch = null) }
                showLabel("Subtitles on: ${OpenSubtitlesLanguages.displayName(result.language)}")
            } else if (waiting) {
                _state.update { state -> state.copy(subtitleSearch = state.subtitleSearch?.copy(downloadingId = null, error = message)) }
            } else {
                showFeedback(GestureFeedback.Label(message), MESSAGE_LABEL_MS)
            }
        }
    }

    override fun dismissSubtitleSearch() {
        subtitleSearchJob?.cancel()
        if (_state.value.subtitleSearch != null) _state.update { it.copy(subtitleSearch = null) }
    }

    /**
     * Moves playback to [target] (null = this device) named [targetName]; [target] carries a
     * reference for the player, released here once it is replaced. libVLC 3 sends an input to
     * the renderer that was set when it started, so the media is stopped, the renderer set and
     * the video opened again where it was, with the user's track choices (libVLC converts what
     * the device can't play). An ended video only gets the renderer: replaying it opens it anew.
     * False when libVLC refused the renderer; playback then goes on where it was.
     */
    private fun moveToRenderer(target: RendererItem?, targetName: String?): Boolean {
        val request = lastRequest
        val reopen = hasContent && request != null && !ended
        val current = _state.value
        val resumeAtMs = when {
            !playbackStarted -> null
            current.error != null -> current.positionMs
            else -> currentPositionMs()
        }?.takeIf { it > 0 }
        val carry = CarriedChoices(
            audioTrack = rememberedAudio ?: carried?.audioTrack,
            subtitleTrack = rememberedSubtitle ?: carried?.subtitleTrack,
            subtitleDelayMs = current.subtitleDelayMs,
        )
        if (hasContent && mediaPlayer.hasMedia()) {
            // Stopped before the switch: setting a renderer on a running input would move it
            // there by itself, just before it is stopped and opened again.
            saveProgress()
            mediaPlayer.stop()
            dropStaleEvents()
        }
        val moved = mediaPlayer.setRenderer(target) == 0
        if (moved) {
            val previous = castTarget
            castTarget = target
            previous?.release()
            // The TV plays the sound: no audio focus or headphone handling on this device.
            if (target != null) audio.release()
            val pip = target == null && host?.supportsPictureInPicture == true
            _state.update { it.copy(castingTo = targetName, canEnterPictureInPicture = pip) }
            PlayerDiagnostics.record(if (target != null) "cast: renderer set" else "cast: back on this device")
            updateCastDiscovery()
        } else {
            PlayerDiagnostics.record("cast: renderer refused")
        }
        if (reopen && request != null) {
            openSession(request.copy(startPositionMs = resumeAtMs ?: request.startPositionMs), carry)
        }
        return moved
    }

    /** Ends a cast without reopening anything (the session is over). */
    private fun dropCastTarget() {
        val target = castTarget ?: return
        mediaPlayer.setRenderer(null)
        castTarget = null
        target.release()
        PlayerDiagnostics.record("cast: ended with the session")
        val pip = host?.supportsPictureInPicture == true
        _state.update { it.copy(castingTo = null, canEnterPictureInPicture = pip) }
        updateCastDiscovery()
    }

    /** Discovery runs while the screen is visible or a cast runs. */
    private fun updateCastDiscovery() {
        if (!released && (screenVisible || isCasting)) castDiscovery.start() else castDiscovery.stop()
    }

    private fun onCastDevicesChanged(devices: List<CastDeviceUi>) {
        if (devices != _state.value.castDevices) _state.update { it.copy(castDevices = devices) }
    }

    private fun onPlayerEvent(event: MediaPlayer.Event) {
        if (released || droppingStaleEvents) return
        recordEvent(event)
        when (event.type) {
            MediaPlayer.Event.Opening -> _state.update { it.copy(isBuffering = true) }
            MediaPlayer.Event.Buffering -> {
                val buffering = event.buffering < 100f
                if (buffering != _state.value.isBuffering) {
                    PlayerDiagnostics.record(if (buffering) "vlc: buffering" else "vlc: buffered")
                    _state.update { it.copy(isBuffering = buffering) }
                }
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
            MediaPlayer.Event.Vout -> {
                onVideoOutputsChanged(event.voutCount)
                if (inBackground && event.voutCount > 0) disableVideo()
            }
            MediaPlayer.Event.ESAdded -> {
                if (event.esChangedType == IMedia.Track.Type.Video) videoTrackSeen = true
                onTracksChanged(added = true)
                // The picture's size can be known here already; the video output event may come
                // before libVLC reports the track.
                if (event.esChangedType == IMedia.Track.Type.Video) onVideoOutput()
            }
            MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> {
                onTracksChanged(added = false)
                // A video that starts in the background gets its track turned off as soon as it has one.
                if (inBackground && event.esChangedType == IMedia.Track.Type.Video) disableVideo()
            }
        }
    }

    /** libVLC's events for the diagnostics trail; the frequent time and position ones are left out. */
    private fun recordEvent(event: MediaPlayer.Event) {
        val line = when (event.type) {
            MediaPlayer.Event.Opening -> "opening"
            MediaPlayer.Event.Playing -> "playing"
            MediaPlayer.Event.Paused -> "paused at ${formatPosition(currentPositionMs())}"
            MediaPlayer.Event.Stopped -> "stopped"
            MediaPlayer.Event.EndReached -> "end reached"
            MediaPlayer.Event.EncounteredError -> "error"
            MediaPlayer.Event.LengthChanged -> "length ${formatPosition(event.lengthChanged)}"
            MediaPlayer.Event.SeekableChanged -> if (event.seekable) "seekable" else "not seekable"
            MediaPlayer.Event.Vout -> "video outputs: ${event.voutCount}"
            MediaPlayer.Event.ESAdded -> "track added: ${trackTypeName(event.esChangedType)} ${event.esChangedID}"
            MediaPlayer.Event.ESDeleted -> "track removed: ${trackTypeName(event.esChangedType)} ${event.esChangedID}"
            MediaPlayer.Event.ESSelected -> "track selected: ${trackTypeName(event.esChangedType)} ${event.esChangedID}"
            else -> return
        }
        PlayerDiagnostics.record("vlc: $line")
    }

    private fun onPlaying() {
        // Left while the media was still opening: the setup below runs, then it waits paused.
        val unseen = isHidden
        _state.update { it.copy(isPlaying = !unseen, error = null) }
        if (!unseen && !isCasting) {
            audio.requestFocus()
            audio.registerNoisyReceiver()
        }
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
            // libVLC keeps the delay per input: one set while opening (or carried over) applies now.
            val subtitleDelayMs = _state.value.subtitleDelayMs
            if (subtitleDelayMs != 0L) mediaPlayer.setSpuDelay(subtitleDelayMs * 1_000)
            refreshTracks()
            if (audioChoicePending) applyAudioChoice()
            trySetupSubtitles()
        }
        when {
            unseen -> {
                playOnResume = true
                mediaPlayer.pause()
            }
            inBackground -> disableVideo()
            else -> watchForPicture()
        }
    }

    private fun onPaused() {
        pictureWatchJob?.cancel()
        val position = currentPositionMs()
        _state.update { it.copy(isPlaying = false, positionMs = position) }
        audio.unregisterNoisyReceiver()
        saveProgress()
    }

    private fun onStopped() {
        pictureWatchJob?.cancel()
        _state.update { it.copy(isPlaying = false, isBuffering = false) }
        audio.unregisterNoisyReceiver()
    }

    private fun onEndReached() {
        pictureWatchJob?.cancel()
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
        continueAfterEnd()
    }

    /**
     * What follows an ended video: itself again (repeat), the next queue item, or the next
     * episode; offered on the up-next card, started at once in the background when it would
     * start by itself. With nothing to follow the player closes.
     */
    private fun continueAfterEnd() {
        val videoId = nowPlaying?.videoId
        val repeat = _state.value.repeatMode
        upNextJob?.cancel()
        _state.update { it.copy(upNext = null) }
        if (repeat == RepeatMode.ONE || (repeat == RepeatMode.ALL && !usesQueue)) {
            _playRequested.value = true
            restartFromEnd(startAtMs = 0)
            return
        }
        upNextJob = scope.launch {
            val next: VideoWithMeta?
            // A queue the user built plays through; episodes follow the "play next" setting.
            val automatic: Boolean
            if (usesQueue) {
                val position = queue.nextPosition(wrap = repeat == RepeatMode.ALL)
                if (position == null) {
                    finishPlayback()
                    return@launch
                }
                if (inBackground) {
                    playQueuePosition(position)
                    return@launch
                }
                next = queue.idAt(position)?.let { lookUp(it) }
                if (next == null) {
                    playQueuePosition(position)
                    return@launch
                }
                automatic = true
            } else {
                next = if (videoId != null) nextEntry ?: fetchNextEpisode(videoId) else null
                if (next == null) {
                    finishPlayback()
                    return@launch
                }
                automatic = graph.settings.settings.value.autoPlayNext
                if (inBackground && automatic) {
                    open(PlayRequest(videoId = next.video.id))
                    return@launch
                }
            }
            _playRequested.value = automatic
            offerUpNext(next, countdown = if (automatic) UP_NEXT_COUNTDOWN_SECONDS else null)
        }
    }

    /** Shuffle or repeat changed while the up-next card waits: it offers what follows now. */
    private fun reofferAfterEnd() {
        if (ended && hasContent && _state.value.error == null) continueAfterEnd()
    }

    private fun finishPlayback() {
        PlayerDiagnostics.record("nothing left to play")
        onFinished()
    }

    private suspend fun lookUp(videoId: Long): VideoWithMeta? =
        try {
            graph.library.video(videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Library lookup failed", e)
            null
        }

    private fun onError() {
        pictureWatchJob?.cancel()
        val fallBack = errorMayBeHardwareDecoding()
        reportPlaybackError(fallingBack = fallBack)
        if (fallBack && fallBackToSoftwareDecoding("playback error")) return
        val kind = nowPlaying?.kind
        val castingTo = _state.value.castingTo
        val message = if (castingTo != null) castErrorMessage(castingTo) else errorMessage(kind)
        audio.unregisterNoisyReceiver()
        _playRequested.value = false
        _state.update { it.copy(isPlaying = false, isBuffering = false, error = message) }
        mediaPlayer.stop()
    }

    /**
     * Whether an error is worth one more try in software. Only with "Automatic" decoding and once
     * a video track exists (an input that can't even be opened has none). On SMB and network
     * sources an error after the picture was shown is almost always the connection: reopening
     * would only delay the error card and keep the video in slow software decoding for nothing.
     */
    private fun errorMayBeHardwareDecoding(): Boolean {
        val current = nowPlaying ?: return false
        // Casting, an error is almost always the device or the network, not this device's decoder.
        if (isCasting || decoding != HwDecoding.AUTO || !videoTrackSeen) return false
        return !videoOutputSeen || current.kind.isLocal
    }

    /**
     * A short report of a libVLC error with the last player events, kept in the crash reporter's
     * trail so it reaches the next crash report.
     */
    private fun reportPlaybackError(fallingBack: Boolean) {
        val current = nowPlaying
        val summary = buildString {
            append("Player: libVLC error (")
            append(if (current != null) describeSource(current.kind, current.fileName) else "nothing open")
            append(", ").append(decodingLabel(decoding))
            if (playbackStarted) {
                append(", at ").append(formatPosition(currentPositionMs()))
            } else {
                append(", before playback started")
            }
            append(if (videoOutputSeen) ", picture shown" else ", no picture yet")
            if (fallingBack) append("; retrying with software decoding")
            append(")")
        }
        val trail = PlayerDiagnostics.recent(ERROR_REPORT_EVENTS)
        CrashReporter.log(
            buildString {
                append(summary)
                for (line in trail) append("\n    ").append(line)
            },
        )
    }

    /**
     * Reopens the current video once with software decoding, where it stopped, and remembers that
     * choice for the video until the process ends. [MediaSource.decodingFor] then never hands it
     * "Automatic" again, so this can't repeat for the same video. False when there is nothing to reopen.
     */
    private fun fallBackToSoftwareDecoding(reason: String): Boolean {
        val current = nowPlaying ?: return false
        val request = lastRequest ?: return false
        if (released || decoding != HwDecoding.AUTO) return false
        MediaSource.rememberSoftwareDecoding(current.uri)
        // Before the first start the pending resume point is recomputed from the request.
        val position = if (playbackStarted) currentPositionMs().takeIf { it > 0 } else null
        PlayerDiagnostics.record("decoding: $reason with hardware decoding, reopening in software")
        CrashReporter.log("Player: $reason with hardware decoding, switched to software decoding")
        val carry = CarriedChoices(
            audioTrack = rememberedAudio,
            subtitleTrack = rememberedSubtitle,
            subtitleDelayMs = _state.value.subtitleDelayMs,
        )
        openSession(request.copy(startPositionMs = position ?: request.startPositionMs), carry)
        showFeedback(GestureFeedback.Label("Switched to software decoding"), SOFTWARE_DECODING_LABEL_MS)
        return true
    }

    /**
     * With hardware decoding a failing decoder or surface can leave the screen black while the
     * sound plays: no video output ever appears. After [NO_PICTURE_TIMEOUT_MS] of such playback
     * the video is reopened in software. Only time that really played counts, with the surface
     * attached: opening, buffering, pauses and a hidden screen don't.
     */
    private fun watchForPicture() {
        // Casting, no picture is ever shown here.
        if (isCasting || inBackground || videoOutputSeen) return
        if (decoding != HwDecoding.AUTO || pictureWatchJob?.isActive == true) return
        val watched = session
        pictureWatchJob = scope.launch {
            var lastTime = mediaPlayer.time
            while (noPictureMs < NO_PICTURE_TIMEOUT_MS) {
                delay(PICTURE_CHECK_INTERVAL_MS)
                if (released || session != watched || videoOutputSeen) return@launch
                val time = mediaPlayer.time
                if (mediaPlayer.isPlaying && attachedLayout != null && time > lastTime) {
                    noPictureMs += PICTURE_CHECK_INTERVAL_MS
                }
                lastTime = time
            }
            if (!videoOutputSeen && hasVideoTrack()) {
                fallBackToSoftwareDecoding("no picture after ${NO_PICTURE_TIMEOUT_MS / 1_000} s")
            }
        }
    }

    private fun hasVideoTrack(): Boolean = videoTrackSeen || realTracks(mediaPlayer.videoTracks).isNotEmpty()

    private fun onVideoOutputsChanged(count: Int) {
        if (count <= 0) return
        if (!videoOutputSeen) {
            videoOutputSeen = true
            pictureWatchJob?.cancel()
            mediaPlayer.currentVideoTrack?.let { track ->
                PlayerDiagnostics.record("picture: ${track.codec ?: "unknown codec"} ${track.width}x${track.height}")
            }
        }
        onVideoOutput()
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
        applyOrientation(size)
        // Movies show their resolution; take it from the picture when the scan didn't know it.
        val entry = nowPlaying?.entry
        if (entry != null && entry.video.kind == VideoKind.MOVIE && (entry.video.width == null || entry.video.height == null)) {
            val subtitle = PlayerText.titlesFor(entry, size).subtitle
            _state.update { it.copy(subtitle = subtitle) }
        }
    }

    /** Turns the screen to the video's shape, unless the user turned it themselves. */
    private fun applyOrientation(size: VideoSize) {
        val host = host ?: return
        if (userRotated || _state.value.inPictureInPicture || !_state.value.canRotate) return
        host.screenOrientation = if (size.isPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    /**
     * No video output in the background: saves decoding work, and there is no surface for it.
     * Never while casting: the video track is what the cast device shows.
     */
    private fun disableVideo() {
        if (!mediaReady || isCasting) return
        val track = mediaPlayer.videoTrack
        if (track == DISABLED_TRACK) return
        savedVideoTrack = track
        mediaPlayer.setVideoTrack(DISABLED_TRACK)
        PlayerDiagnostics.record("background: video track off")
    }

    /** The track [disableVideo] turned off (or the first one) back on; false when nothing changed. */
    private fun restoreVideo(): Boolean {
        val saved = savedVideoTrack
        savedVideoTrack = null
        if (!mediaReady || mediaPlayer.videoTrack != DISABLED_TRACK) return false
        val tracks = realTracks(mediaPlayer.videoTracks)
        val target = tracks.firstOrNull { it.id == saved }?.id ?: tracks.firstOrNull()?.id ?: return false
        mediaPlayer.setVideoTrack(target)
        PlayerDiagnostics.record("background: video track on")
        return true
    }

    private fun onTracksChanged(added: Boolean) {
        refreshTracks()
        rememberDownloadedTrack()
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

    /**
     * Looks for the subtitle files of [current]: the ones next to it (with auto-load on, not for
     * streams) and the ones downloaded for it earlier, which always come back.
     */
    private fun startSubtitleSearch(current: NowPlaying, settings: Settings) {
        val siblings = settings.autoLoadSubtitles && current.kind != UriKind.NETWORK
        val languages = listOf(settings.preferredSubtitleLanguage, Locale.getDefault().language, "en")
            .filter { it.isNotBlank() }
            .distinct()
        subtitleJob = scope.launch {
            val files = try {
                if (siblings) {
                    subtitleFinder.find(current.uri, current.fileName, languages, current.videoKey)
                } else {
                    subtitleFinder.downloaded(current.videoKey)
                }
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
            if (file.absolutePath !in addedSubtitleFiles) addSubtitleFile(file, select = file == selected)
        }
        refreshTracks()
        maybeDownloadSubtitles(embedded, external, remembered)
    }

    /**
     * Turns on a downloaded subtitle file for the playing video. A file this session has loaded
     * already (setup loads the ones downloaded before) gets its track turned on and remembered;
     * adding it again would make a second track, one the next session doesn't have. Else the
     * file is added, and its track remembered for the video once libVLC has selected it.
     * Null when the subtitle is on (or about to be), else a message saying why not.
     */
    private fun addDownloadedSubtitle(file: File): String? {
        if (released || !mediaReady) return "Couldn't load that subtitle file"
        val path = file.absolutePath
        if (path in addedSubtitleFiles) {
            // Added just now, and libVLC turns it on once it is loaded.
            if (pendingDownloadTrack?.path == path) return null
            val tracks = realTracks(mediaPlayer.spuTracks)
            val track = downloadedTracks[path]?.takeIf { known -> tracks.any { it.id == known } }
                ?: trackOfFile(path)
                ?: return "That subtitle is loaded already: choose it in the subtitle tracks"
            mediaPlayer.setSpuTrack(track)
            pendingSubtitleRestore = null
            rememberedSubtitle = track
            refreshTracks()
            saveTrackChoice()
            return null
        }
        val before = realTracks(mediaPlayer.spuTracks).map { it.id }.toSet()
        if (!addSubtitleFile(file, select = true)) return "Couldn't load that subtitle file"
        pendingDownloadTrack = PendingTrack(path, before)
        pendingSubtitleRestore = null
        return null
    }

    /** Adds [file] to the playing video as a subtitle track ([select]: turned on); false when libVLC refused it. */
    private fun addSubtitleFile(file: File, select: Boolean): Boolean {
        if (tracksBeforeFiles == null) tracksBeforeFiles = realTracks(mediaPlayer.spuTracks).map { it.id }.toSet()
        if (!mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(file), select)) return false
        addedSubtitleFiles += file.absolutePath
        return true
    }

    /**
     * The subtitle track of a file added to this session, or null when it can't be told. libVLC
     * opens added files one after the other, in the order they were added, and numbers each new
     * track one higher than the last, so the n-th track after the video's own is the n-th file's,
     * as long as every file got exactly one (none still loading, none refused, no captions found
     * in the video since).
     */
    private fun trackOfFile(path: String): Int? {
        val before = tracksBeforeFiles ?: return null
        val index = addedSubtitleFiles.indexOf(path)
        if (index < 0) return null
        val added = realTracks(mediaPlayer.spuTracks).map { it.id }.filter { it !in before }.sorted()
        return if (added.size == addedSubtitleFiles.size) added[index] else null
    }

    /** The track of a subtitle just downloaded, once libVLC selected it, becomes the video's remembered choice. */
    private fun rememberDownloadedTrack() {
        val pending = pendingDownloadTrack ?: return
        val selected = mediaPlayer.spuTrack
        if (selected == DISABLED_TRACK || selected in pending.before) return
        pendingDownloadTrack = null
        downloadedTracks[pending.path] = selected
        rememberedSubtitle = selected
        saveTrackChoice()
    }

    /**
     * With automatic downloads on and nothing chosen for the video yet: when neither its own
     * tracks nor its files are in the first download language, fetches the best match
     * (timed for this file first, then the most downloaded) in the first language that has one,
     * among those it lacks. Only a match sure to fit is fetched: the video is matched in the
     * library (its TMDB id), or the subtitle is timed for this very file (see
     * [SubtitleDownloads.autoPick]). Settled once per video: a download, or a search by id that
     * found nothing.
     */
    private fun maybeDownloadSubtitles(embedded: List<MediaPlayer.TrackDescription>, external: List<File>, remembered: Int?) {
        val current = nowPlaying ?: return
        val settings = graph.settings.settings.value
        if (!settings.autoDownloadSubtitles || remembered != null || !subtitleDownloads.hasKey) return
        val languages = subtitleDownloads.languages(settings)
        val missing = SubtitleDownloadRules.missingLanguages(languages) { language ->
            val aliases = SubtitleDownloadRules.aliases(language)
            embedded.any { TrackLanguages.matches(it.name, aliases) } ||
                external.any { SubtitleDownloadRules.fileIsInLanguage(it.name, current.fileName, aliases) }
        }
        if (missing.isEmpty()) return
        val forSession = session
        autoSubtitleJob?.cancel()
        autoSubtitleJob = scope.launch {
            try {
                if (subtitleDownloads.autoDownloadSettled(current.videoKey)) return@launch
                val target = current.subtitleTarget
                val best = when (val pick = subtitleDownloads.autoPick(target, missing)) {
                    is SubtitleDownloadRules.AutoPick.Download -> pick.result
                    SubtitleDownloadRules.AutoPick.Settle -> {
                        PlayerDiagnostics.record("subtitles: nothing to download automatically")
                        subtitleDownloads.settleAutoDownload(current.videoKey)
                        return@launch
                    }
                    // Not settled: a later play, once the library matched the video, tries again.
                    SubtitleDownloadRules.AutoPick.Later -> {
                        PlayerDiagnostics.record("subtitles: none sure to fit, not downloaded automatically")
                        return@launch
                    }
                }
                val file = subtitleDownloads.download(target, best)
                subtitleDownloads.settleAutoDownload(current.videoKey)
                if (released || session != forSession) return@launch
                PlayerDiagnostics.record("subtitles: downloaded automatically")
                if (addDownloadedSubtitle(file) == null) {
                    showFeedback(
                        GestureFeedback.Label("Downloaded ${OpenSubtitlesLanguages.displayName(best.language)} subtitles"),
                        MESSAGE_LABEL_MS,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not settled: the next time the video plays tries again.
                Log.w(TAG, "Automatic subtitle download failed", e)
                if (released || session != forSession) return@launch
                showFeedback(
                    GestureFeedback.Label("Couldn't download subtitles: ${SubtitleDownloadRules.errorMessage(e)}"),
                    MESSAGE_LABEL_MS,
                )
            }
        }
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
        updateNavigation()
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
        // No season (absolute numbering not placed yet) counts as season 1, as in the library.
        val season = current.season ?: 1
        val episode = current.episode ?: return null
        val videos = graph.library.observeVideos().first()
        // The whole library: sifted off the main thread.
        return withContext(Dispatchers.Default) {
            videos.asSequence()
                .map { it.video }
                .filter { it.id != current.id && it.kind == VideoKind.EPISODE && it.showKey == showKey }
                .filter { other ->
                    val otherSeason = other.season ?: 1
                    val otherLast = other.episodeEnd ?: other.episode ?: return@filter false
                    otherSeason < season || (otherSeason == season && otherLast < episode)
                }
                .maxWithOrNull(
                    compareBy<VideoEntity>(
                        { it.season ?: 1 },
                        { it.episode ?: 0 },
                        { it.fileName.lowercase(Locale.ROOT) },
                    ),
                )
        }
    }

    /** Shows [next] on the up-next card; plays it after [countdown] seconds, or waits for a tap when null. */
    private suspend fun offerUpNext(next: VideoWithMeta, countdown: Int?) {
        val settings = graph.settings.settings.value
        val titles = PlayerText.titlesFor(next, size = null)
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

    /**
     * Plays an ended video again through a fresh session. libVLC can't restart an ended input, and
     * replaying the same fd:// media would read on from the descriptor's offset (the end), so a
     * content:// video would fail. A new session also re-applies tracks and the subtitle delay.
     */
    private fun restartFromEnd(startAtMs: Long) {
        val current = nowPlaying
        val request = lastRequest
        val videoId = current?.videoId ?: request?.videoId
        val uri = current?.uri ?: request?.uri
        if (videoId == null && uri == null) return
        openSession(
            PlayRequest(
                videoId = videoId,
                uri = uri,
                title = request?.title,
                startOver = startAtMs <= 0L,
                startPositionMs = startAtMs.takeIf { it > 0 },
            ),
            carry = null,
        )
    }

    private fun seekInternal(targetMs: Long) {
        PlayerDiagnostics.record("seek to ${formatPosition(targetMs)}")
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
        val previous = pendingSave
        // App scope: the save must outlive the activity that is closing. One after the other, so
        // a periodic save still in flight can't overwrite the "watched" written at the end.
        pendingSave = graph.appScope.launch {
            previous?.join()
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
        _playRequested.value = false
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

    private fun currentBrightness(host: Host): Float {
        val windowBrightness = host.windowBrightness
        if (windowBrightness >= 0f) return windowBrightness.coerceIn(MIN_BRIGHTNESS, 1f)
        val system = SystemSettings.System.getInt(
            appContext.contentResolver,
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
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
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

        /** The volume indicator stays this long after the last volume key, like iOS's. */
        const val VOLUME_HUD_MS = 1_500L

        const val SAVE_INTERVAL_MS = 10_000L
        const val POSITION_UPDATE_INTERVAL_MS = 250L
        const val RESUME_MIN_MS = 10_000L
        const val RESUME_BANNER_MS = 6_000L
        const val GESTURE_FEEDBACK_MS = 600L
        const val LABEL_MS = 1_200L

        /** A sentence to read (a download failed, a subtitle was fetched) stays longer than a label. */
        const val MESSAGE_LABEL_MS = 4_000L
        const val UP_NEXT_COUNTDOWN_SECONDS = 8

        const val MIN_RATE = 0.25f
        const val MAX_RATE = 4f
        const val MIN_BRIGHTNESS = 0.01f
        const val DEFAULT_SYSTEM_BRIGHTNESS = 128

        /** Played time without a picture after which hardware decoding counts as failed. */
        const val NO_PICTURE_TIMEOUT_MS = 4_000L
        const val PICTURE_CHECK_INTERVAL_MS = 500L
        const val SOFTWARE_DECODING_LABEL_MS = 3_000L

        /** How long a reopen waits for the position save of the session it replaces. */
        const val SAVE_WAIT_MS = 1_000L

        /** Player events that go into an error report. */
        const val ERROR_REPORT_EVENTS = 25

        /** Stream playlists, named in reports next to the video file extensions. */
        val STREAM_EXTENSIONS = setOf("m3u8", "m3u", "mpd")

        /** libvlc_video_orient_t values 4–7 swap width and height. */
        val TRANSPOSED_ORIENTATIONS = 4..7

        /** How long a scrub rests before its frame is read, so a fast drag reads few. */
        const val FRAME_PREVIEW_DELAY_MS = 60L

        /** The scrub bubble frame's longest side, in pixels. */
        const val FRAME_PREVIEW_PX = 320

        /** Android 16 (API 36) ignores orientation requests of apps targeting it on screens this wide. */
        const val ORIENTATION_IGNORED_SDK = 36
        const val LARGE_SCREEN_DP = 600

        fun canRotateScreen(configuration: Configuration): Boolean =
            Build.VERSION.SDK_INT < ORIENTATION_IGNORED_SDK ||
                configuration.smallestScreenWidthDp < LARGE_SCREEN_DP

        fun seekStepMs(settings: Settings): Long = settings.seekStepSeconds.coerceAtLeast(1) * 1_000L

        fun castErrorMessage(deviceName: String): String =
            "Can't play this on $deviceName — check that it is on and on the same network"

        fun errorMessage(kind: UriKind?): String = when (kind) {
            UriKind.SMB -> "Can't play this file — check the share and login"
            UriKind.NETWORK -> "Can't play this file — check the URL and your connection"
            UriKind.CONTENT, UriKind.FILE, null -> "Can't play this file"
        }

        /** "smb .mkv": the kind of source and a known extension, never the name, path or host. */
        fun describeSource(kind: UriKind, fileName: String): String {
            val source = kind.name.lowercase(Locale.ROOT)
            val extension = VideoFiles.extension(fileName)
                .takeIf { VideoFiles.isVideo(fileName) || it in STREAM_EXTENSIONS }
            return if (extension != null) "$source .$extension" else source
        }

        fun decodingLabel(decoding: HwDecoding?): String = when (decoding) {
            HwDecoding.AUTO -> "hw decoding auto"
            HwDecoding.FORCED -> "hw decoding forced"
            HwDecoding.DISABLED -> "sw decoding"
            null -> "decoding not set"
        }

        fun trackTypeName(type: Int): String = when (type) {
            IMedia.Track.Type.Audio -> "audio"
            IMedia.Track.Type.Video -> "video"
            IMedia.Track.Type.Text -> "subtitles"
            else -> "other"
        }

        /** "1:02:03" or "2:03"; "?" when libVLC doesn't know. */
        fun formatPosition(ms: Long): String {
            if (ms < 0) return "?"
            val totalSeconds = ms / 1_000
            val hours = totalSeconds / 3_600
            val minutes = totalSeconds / 60 % 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
            }
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
