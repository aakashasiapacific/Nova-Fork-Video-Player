package com.aakash.novafork.music

import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.aakash.novafork.core.audio.Lyrics
import com.aakash.novafork.core.audio.MusicKeys
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.data.music.MusicLibrary
import com.aakash.novafork.data.music.MusicRepository
import com.aakash.novafork.data.music.SimilarSongs
import com.aakash.novafork.player.AudioEffects
import com.aakash.novafork.player.OpenedMedia
import com.aakash.novafork.player.PlayQueue
import com.aakash.novafork.player.PlaybackAudio
import com.aakash.novafork.player.VlcEngine
import com.aakash.novafork.player.closeQuietly
import com.aakash.novafork.ui.model.RepeatMode
import java.io.FileNotFoundException
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/** The song playing (or paused), as the Audio tab and the notification show it. */
data class NowPlaying(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String?,
    /** Cover file or embedded picture (see [MusicLibrary.artUri]). */
    val artUri: String,
)

/** When the sleep timer stops the music (owner): at a time, or when the playing song ends. */
sealed interface SleepTimer {
    /** [endsAt] on the elapsed-realtime clock; [minutes] as it was set. */
    data class At(val endsAt: Long, val minutes: Int) : SleepTimer

    data object EndOfSong : SleepTimer
}

data class MusicPlayerState(
    /** A queue is open (its first song may still be loading); false once closed. */
    val active: Boolean = false,
    /** Null while nothing is queued, and while the first song loads. */
    val nowPlaying: NowPlaying? = null,
    /** Set while the sleep timer runs. */
    val sleep: SleepTimer? = null,
    val isPlaying: Boolean = false,
    /**
     * The user wants sound: set by play, cleared only by pause, close, a permanent loss of audio
     * focus and the end of the queue. Unlike [isPlaying] it stays on between songs and while a
     * failed song is skipped, so the service keeps its foreground through them.
     */
    val playRequested: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    /** Position in the play order and the queue's size, for "3 of 12". */
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
    /** Why the current song doesn't play; null while it does. */
    val error: String? = null,
    /** The song's .lrc lyrics, once loaded; null when it has none. */
    val lyrics: Lyrics? = null,
    /**
     * The queue came back after the app was closed (cleared from recents) and hasn't played
     * since: paused at its song and position, shown only on the Audio tab.
     */
    val restored: Boolean = false,
    /** The songs queued after this one, in play order (the first [UP_NEXT_LIMIT]); Now Playing's queue. */
    val upNext: List<Long> = emptyList(),
    /** Where in [upNext] the songs Autoplay added begin (they sit under an "Autoplay" heading); -1 for none. */
    val autoplayFrom: Int = -1,
)

/**
 * Plays songs from the music library, one queue at a time, on its own libVLC player (the video
 * player has another). It lives as long as the process (see AppGraph); [MusicService] keeps the
 * process in the foreground with a notification while it plays. Video and music take audio focus
 * from each other, so starting one pauses the other. With a crossfade set, the next song starts
 * on a second player before this one ends and the two fade into each other; without one, the
 * next song is opened ahead so it follows with no gap. Main thread only.
 */
class MusicPlayer(
    context: Context,
    private val music: MusicRepository,
    private val scope: CoroutineScope,
    /** The settings as they are now: the crossfade's length and the equaliser. */
    private val settings: () -> Settings = { Settings() },
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(MusicPlayerState())
    val state: StateFlow<MusicPlayerState> = _state.asStateFlow()

    /** Each queued song's artist, for smart shuffle (see [PlayQueue.groupOf]). */
    private val artistOf = HashMap<Long, String>()
    /** Each queued song's album, so an album played in order is never crossfaded (gapless albums). */
    private val albumOf = HashMap<Long, String>()
    private val queue = PlayQueue().also { it.groupOf = { id -> artistOf[id] } }

    /** The songs Autoplay added to this queue (Apple Music's infinity button), so Up Next can set them apart. */
    private val autoplayIds = HashSet<Long>()

    /** Autoplay choosing songs for the end of this song; set once per song, so it looks once. */
    private var autoplayJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var loadJob: Job? = null
    private var lyricsJob: Job? = null
    private var skipJob: Job? = null

    /** Sound Check's tag reader, and the volume (percent) the playing song's tag sets; 100 when off or untagged. */
    private val soundCheck = SoundCheck(appContext)
    private var songVolume = FULL_VOLUME

    /** The current song played to its end; play() opens it again. */
    private var ended = false

    /** Where a restored song resumes once it plays (0 = from the start). */
    private var pendingSeekMs = 0L
    private var lastSavedAt = 0L
    private val resumePrefs = appContext.getSharedPreferences(RESUME_PREFS, Context.MODE_PRIVATE)
    private var pausedByFocusLoss = false
    private var durationSaved = false

    /** The next song, opened ahead of its turn so it starts with no gap (no crossfade set). */
    private var preloaded: Pair<Long, OpenedMedia>? = null
    private var preloadJob: Job? = null

    /** The song fading in over this one (a crossfade), on its own player until it takes over. */
    private class Incoming(val position: Int, val song: SongEntity, val player: MediaPlayer, val descriptor: ParcelFileDescriptor?, val volume: Int)

    private var incoming: Incoming? = null

    /** The crossfade into the next song; set from its start until the song changes, so it runs once. */
    private var crossfadeJob: Job? = null

    private val eventListener = object : MediaPlayer.EventListener {
        override fun onEvent(event: MediaPlayer.Event) = this@MusicPlayer.onEvent(event)
    }

    private val audio = PlaybackAudio(
        appContext,
        object : PlaybackAudio.Listener {
            override fun onFocusLost(transient: Boolean) {
                val player = mediaPlayer ?: return
                if (!player.isPlaying) return
                pausedByFocusLoss = transient
                if (!transient) _state.update { it.copy(playRequested = false) }
                player.pause()
            }

            override fun onFocusRegained() {
                if (!pausedByFocusLoss) return
                pausedByFocusLoss = false
                mediaPlayer?.play()
            }

            override fun onDuck(ducked: Boolean) {
                mediaPlayer?.setVolume(if (ducked) DUCKED_VOLUME * songVolume / FULL_VOLUME else songVolume)
            }

            override fun onBecomingNoisy() = pause()
        },
        contentType = AudioAttributes.CONTENT_TYPE_MUSIC,
    )

    val hasContent: Boolean get() = _state.value.active

    init {
        scope.launch { restore() }
        // The equaliser follows the setting as it changes, on whichever player is current.
        AudioEffects.follow(appContext, scope) { mediaPlayer }
    }

    /** Brings back the queue saved when the app was last closed, paused (see [MusicPlayerState.restored]). */
    private suspend fun restore() {
        if (_state.value.active) return
        val ids = resumePrefs.getString(KEY_QUEUE, null)?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()
        if (ids.isEmpty()) return
        val position = resumePrefs.getInt(KEY_POSITION, 0).coerceIn(0, ids.lastIndex)
        val song = try {
            music.song(ids[position])
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't restore the queue", e)
            null
        } ?: return
        if (_state.value.active) return
        autoplayIds.clear()
        queue.set(ids, position, shuffle = false)
        pendingSeekMs = resumePrefs.getLong(KEY_POSITION_MS, 0L).coerceAtLeast(0L)
        val repeat = resumePrefs.getString(KEY_REPEAT, null)?.let { runCatching { RepeatMode.valueOf(it) }.getOrNull() } ?: RepeatMode.OFF
        _state.value = MusicPlayerState(
            active = true,
            nowPlaying = nowPlaying(song),
            positionMs = pendingSeekMs,
            durationMs = song.durationMs ?: 0,
            repeat = repeat,
            restored = true,
        ).withQueue()
        lyricsJob = scope.launch {
            val lyrics = music.lyrics(song)
            if (_state.value.nowPlaying?.songId == song.id) _state.update { it.copy(lyrics = lyrics) }
        }
    }

    /** Remembers the queue, song and position for after the app is closed. */
    private fun saveResume() {
        val state = _state.value
        if (!state.active || queue.isEmpty) {
            resumePrefs.edit().clear().apply()
            return
        }
        lastSavedAt = System.currentTimeMillis()
        resumePrefs.edit()
            .putString(KEY_QUEUE, queue.playOrder.joinToString(","))
            .putInt(KEY_POSITION, queue.position)
            .putLong(KEY_POSITION_MS, state.positionMs)
            .putString(KEY_REPEAT, state.repeat.name)
            .apply()
    }

    /**
     * Plays [songIds] (in library order) starting with [startIndex]; with [shuffle] the rest
     * follow in random order. Replaces what was queued.
     */
    fun playSongs(
        songIds: List<Long>,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        artists: Map<Long, String> = emptyMap(),
        albums: Map<Long, String> = emptyMap(),
    ) {
        artistOf.putAll(artists)
        albumOf.putAll(albums)
        if (songIds.isEmpty()) return
        autoplayIds.clear()
        queue.set(songIds, startIndex, shuffle)
        pendingSeekMs = 0L
        _state.update { it.copy(active = true, isBuffering = true, playRequested = true, shuffle = shuffle, restored = false) }
        MusicService.start(appContext)
        startCurrent()
    }

    fun play() {
        if (!hasContent || _state.value.nowPlaying == null) return
        _state.update { it.copy(playRequested = true, restored = false) }
        // The service may have stopped while paused (Android ends idle ones); this brings back
        // its notification and media session.
        MusicService.start(appContext)
        val player = mediaPlayer
        if (ended || player == null || !player.hasMedia() || _state.value.error != null) {
            startCurrent()
            return
        }
        pausedByFocusLoss = false
        audio.requestFocus()
        audio.registerNoisyReceiver()
        player.play()
    }

    fun pause() {
        pausedByFocusLoss = false
        // A crossfade under way is dropped: the song playing now plays on alone when resumed.
        cancelCrossfade()
        _state.update { it.copy(playRequested = false) }
        mediaPlayer?.pause()
        saveResume()
    }

    fun togglePlay() {
        if (_state.value.isPlaying) pause() else play()
    }

    private var sleepJob: Job? = null

    /** The next song to load waits, paused, instead of playing (the sleep timer ran out with a song). */
    private var holdNextStart = false

    /**
     * The sleep timer: the music fades out and pauses [minutes] from now, or at the end of the
     * playing song when [minutes] is negative; 0 turns it off.
     */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        cancelCrossfade()
        mediaPlayer?.volume = songVolume
        when {
            minutes == 0 -> _state.update { it.copy(sleep = null) }
            minutes < 0 -> _state.update { it.copy(sleep = SleepTimer.EndOfSong) }
            else -> {
                val length = minutes * 60_000L
                _state.update { it.copy(sleep = SleepTimer.At(SystemClock.elapsedRealtime() + length, minutes)) }
                sleepJob = scope.launch {
                    delay((length - SLEEP_FADE_MS).coerceAtLeast(0L))
                    fallAsleep()
                }
            }
        }
    }

    /** The timer is up: the music fades out over a few seconds, then pauses. */
    private suspend fun fallAsleep() {
        cancelCrossfade()
        val player = mediaPlayer
        if (player != null && _state.value.isPlaying) {
            for (step in SLEEP_FADE_STEPS downTo 0) {
                player.volume = songVolume * step / SLEEP_FADE_STEPS
                delay(SLEEP_FADE_MS / SLEEP_FADE_STEPS)
            }
        }
        pause()
        mediaPlayer?.volume = songVolume
        _state.update { it.copy(sleep = null) }
    }

    /** The next song; at the end of the queue only with repeat on. */
    fun next() {
        val position = queue.nextPosition(wrap = _state.value.repeat != RepeatMode.OFF) ?: return
        moveTo(position)
    }

    /** Back to the start of the song after its first seconds, else the song before. */
    fun previous() {
        val state = _state.value
        if (state.positionMs > RESTART_AFTER_MS && !ended) {
            seekTo(0)
            return
        }
        val position = queue.previousPosition(wrap = state.repeat == RepeatMode.ALL)
        if (position == null) seekTo(0) else moveTo(position)
    }

    /**
     * Apple Music's Play Next ([next]) and Play Last: queues [songIds] after the playing song or
     * after the whole queue. With nothing queued they simply play.
     */
    fun enqueue(songIds: List<Long>, next: Boolean) {
        if (songIds.isEmpty()) return
        if (!hasContent || queue.isEmpty) {
            playSongs(songIds)
            return
        }
        // What comes next may have changed: a crossfade or a song opened ahead starts again.
        cancelCrossfade()
        dropPreloaded()
        // Autoplay's songs stay last: what the person queues goes before them, and Autoplay
        // chooses again at the end.
        val upcoming = queue.upNext(Int.MAX_VALUE).filter { it in autoplayIds }.toSet()
        if (upcoming.isNotEmpty()) {
            queue.remove(upcoming)
            autoplayIds.removeAll(upcoming)
        }
        queue.insert(songIds, next)
        _state.update { it.withQueue() }
        saveResume()
    }

    /** Up Next reordered: the song [from] places after this one goes to [to] places after it. */
    fun moveUpNext(from: Int, to: Int) {
        cancelCrossfade()
        dropPreloaded()
        queue.move(queue.position + from, queue.position + to)
        _state.update { it.withQueue() }
        saveResume()
    }

    /** Apple Music's Clear Up Next: the songs queued after the playing one go; it keeps playing. */
    fun clearUpNext() {
        if (queue.upNext(1).isEmpty()) return
        cancelCrossfade()
        dropPreloaded()
        autoplayJob?.cancel()
        autoplayJob = null
        queue.clearUpcoming()
        autoplayIds.clear()
        _state.update { it.withQueue() }
        saveResume()
    }

    /** Plays the song at [position] of the play order. */
    fun skipTo(position: Int) {
        if (position in 0 until queue.size) moveTo(position)
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        if (!player.isSeekable) return
        // Back from the last seconds: the song coming in goes again; it starts over nearer the end.
        cancelCrossfade()
        val target = positionMs.coerceIn(0L, _state.value.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        player.setTime(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun setShuffle(on: Boolean) {
        cancelCrossfade()
        dropPreloaded()
        queue.setShuffled(on)
        _state.update { it.copy(shuffle = on).withQueue() }
    }

    /** Off → all → one → off, like Apple Music's repeat button. */
    fun cycleRepeat() {
        val next = when (_state.value.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.update { it.copy(repeat = next).withQueue() }
    }

    /**
     * Stops the player; the notification goes. [forget] drops the queue for good; without it
     * (the app cleared from recents) the queue is kept for the next start, paused.
     */
    fun close(forget: Boolean = true) {
        if (forget) resumePrefs.edit().clear().apply() else saveResume()
        sleepJob?.cancel()
        holdNextStart = false
        cancelCrossfade()
        dropPreloaded()
        loadJob?.cancel()
        lyricsJob?.cancel()
        skipJob?.cancel()
        mediaPlayer?.let { player ->
            player.setEventListener(null)
            player.stop()
            player.release()
        }
        mediaPlayer = null
        descriptor.closeQuietly()
        descriptor = null
        queue.clear()
        autoplayIds.clear()
        autoplayJob?.cancel()
        autoplayJob = null
        audio.release()
        SongPulses.clear()
        ended = false
        _state.value = MusicPlayerState(shuffle = _state.value.shuffle, repeat = _state.value.repeat)
    }

    private fun moveTo(position: Int) {
        queue.moveTo(position) ?: return
        pendingSeekMs = 0L
        startCurrent()
    }

    private fun startCurrent() {
        val songId = queue.currentId ?: return close()
        cancelCrossfade()
        // Its open may be under way: a media it finishes is still taken below when it is this song.
        preloadJob?.cancel()
        preloadJob = null
        loadJob?.cancel()
        lyricsJob?.cancel()
        skipJob?.cancel()
        ended = false
        durationSaved = false
        autoplayJob = null
        loadJob = scope.launch {
            val song = try {
                music.song(songId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Library lookup failed", e)
                null
            }
            if (song == null) {
                onFailed(null, "This song is no longer in the library")
                return@launch
            }
            _state.update {
                it.copy(
                    nowPlaying = nowPlaying(song),
                    isPlaying = false,
                    // A new song always starts playing (next/previous while paused too).
                    playRequested = true,
                    isBuffering = true,
                    restored = false,
                    positionMs = pendingSeekMs,
                    durationMs = song.durationMs ?: 0,
                    error = null,
                    lyrics = null,
                ).withQueue()
            }
            // The bars follow this song's own rhythm, read from its file in the background.
            SongPulses.prepare(appContext, scope, song.id, song.uri)
            lyricsJob = scope.launch {
                val lyrics = music.lyrics(song)
                if (_state.value.nowPlaying?.songId == song.id) _state.update { it.copy(lyrics = lyrics) }
            }
            // Opened ahead while the song before played, when it was this one: no gap.
            val opened = takePreloaded(song.id) ?: open(song.uri)
            if (opened == null) {
                onFailed(song, "Can't open this song")
                return@launch
            }
            songVolume = volumeOf(song)
            val player = try {
                (mediaPlayer ?: VlcEngine.newMediaPlayer(appContext).also {
                    it.setEventListener(eventListener)
                    mediaPlayer = it
                }).also {
                    it.setMedia(opened.media)
                    it.volume = songVolume
                    // The equaliser (Now Playing, the video player) on the new player.
                    AudioEffects.applyCurrent(appContext, it)
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "libVLC refused a song", e)
                opened.discard()
                onFailed(song, "Can't play this song")
                return@launch
            }
            // The player keeps its own reference.
            opened.media.release()
            descriptor.closeQuietly()
            descriptor = opened.descriptor
            pausedByFocusLoss = false
            if (holdNextStart) {
                holdNextStart = false
                _state.update { it.copy(playRequested = false) }
            } else {
                audio.requestFocus()
                audio.registerNoisyReceiver()
                player.play()
            }
            saveResume()
        }
    }

    /** The libVLC media of a local song, built off the main thread; never leaks a descriptor when cancelled. */
    private suspend fun open(uri: String): OpenedMedia? {
        val libVLC = VlcEngine.libVLC(appContext)
        val result = AtomicReference<OpenedMedia?>(null)
        try {
            withContext(Dispatchers.IO) {
                val parsed = Uri.parse(uri)
                val opened = if (parsed.scheme == "content") {
                    val fd = appContext.contentResolver.openFileDescriptor(parsed, "r")
                        ?: throw FileNotFoundException("The provider returned no file descriptor")
                    try {
                        OpenedMedia(Media(libVLC, fd.fileDescriptor), fd)
                    } catch (e: RuntimeException) {
                        fd.closeQuietly()
                        throw e
                    }
                } else {
                    OpenedMedia(Media(libVLC, parsed.path ?: uri.removePrefix("file://")), null)
                }
                // Embedded cover art is a video track to libVLC; there is nothing to show it on.
                opened.media.addOption(":no-video")
                result.set(opened)
            }
        } catch (e: CancellationException) {
            result.getAndSet(null)?.discard()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't open a song", e)
            return null
        }
        return result.getAndSet(null)
    }

    private fun onEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                _state.update { it.copy(isPlaying = true, isBuffering = false, error = null) }
                // A restored song picks up where it was left.
                if (pendingSeekMs > 0L) {
                    mediaPlayer?.setTime(pendingSeekMs)
                    pendingSeekMs = 0L
                }
            }
            MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> _state.update { it.copy(isPlaying = false) }
            MediaPlayer.Event.Buffering -> {
                val buffering = event.buffering < 100f
                if (buffering != _state.value.isBuffering) _state.update { it.copy(isBuffering = buffering) }
            }
            MediaPlayer.Event.TimeChanged -> {
                val position = event.timeChanged.coerceAtLeast(0L)
                _state.update { it.copy(positionMs = position) }
                if (System.currentTimeMillis() - lastSavedAt > SAVE_EVERY_MS) saveResume()
                nearingTheEnd(position)
            }
            MediaPlayer.Event.LengthChanged -> {
                val length = event.lengthChanged
                if (length > 0) {
                    _state.update { it.copy(durationMs = length) }
                    saveDuration(length)
                }
            }
            MediaPlayer.Event.EndReached -> onEnded()
            MediaPlayer.Event.EncounteredError -> {
                val songId = _state.value.nowPlaying?.songId
                onFailed(null, "Can't play this song", songId)
            }
        }
        _state.value.let { SongPulses.report(it.positionMs, it.isPlaying) }
    }

    private fun saveDuration(length: Long) {
        val songId = _state.value.nowPlaying?.songId ?: return
        if (durationSaved) return
        durationSaved = true
        scope.launch { runCatchingLogged { music.fillDuration(songId, length) } }
    }

    private fun onEnded() {
        // Ended while the next song was still fading in: it takes over now, at the volume it has.
        if (incoming != null) {
            crossfadeJob?.cancel()
            handOver()
            return
        }
        val songId = _state.value.nowPlaying?.songId
        if (songId != null) scope.launch { runCatchingLogged { music.markPlayed(songId) } }
        // Asleep at the end of this song: the next one loads but waits, paused.
        if (_state.value.sleep == SleepTimer.EndOfSong) {
            _state.update { it.copy(sleep = null) }
            val next = queue.nextPosition(wrap = _state.value.repeat == RepeatMode.ALL)
            if (next != null) {
                holdNextStart = true
                moveTo(next)
                return
            }
            // Asleep at the queue's end: Autoplay doesn't carry on.
            if (_state.value.repeat == RepeatMode.OFF) {
                endQueue()
                return
            }
        }
        val repeat = _state.value.repeat
        if (repeat == RepeatMode.ONE) {
            startCurrent()
            return
        }
        val next = queue.nextPosition(wrap = repeat == RepeatMode.ALL)
        if (next != null) {
            moveTo(next)
            return
        }
        if (autoplayOn()) {
            // Autoplay had no time to choose (or found nothing yet): it chooses now and plays on.
            val earlier = autoplayJob
            autoplayJob = scope.launch {
                earlier?.join()
                val songId = queue.currentId
                if (queue.nextPosition(wrap = false) == null) extendWithAutoplay()
                val following = queue.nextPosition(wrap = false)
                if (following != null && queue.currentId == songId) moveTo(following) else endQueue()
            }
            return
        }
        endQueue()
    }

    /** The end of the queue: the last song stays, ready to play again. */
    private fun endQueue() {
        ended = true
        audio.release()
        _state.update { it.copy(isPlaying = false, isBuffering = false, playRequested = false, positionMs = 0) }
    }

    /** Autoplay is on and the queue plays through once (repeat off), as Apple Music's infinity button needs. */
    private fun autoplayOn() = settings().musicAutoplay && _state.value.repeat == RepeatMode.OFF

    /**
     * Autoplay: appends up to [AUTOPLAY_SONGS] songs of the library like the last ones queued (see
     * [SimilarSongs]), none already in this queue. False when the library has none to add.
     */
    private suspend fun extendWithAutoplay(): Boolean {
        val library = try {
            music.shownSongs()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Autoplay couldn't read the library", e)
            return false
        }
        val queued = queue.playOrder
        val byId = library.associateBy { it.id }
        val seeds = queued.takeLast(AUTOPLAY_SEEDS).mapNotNull { byId[it] }
        val picked = SimilarSongs.pick(seeds, library, exclude = queued.toSet(), limit = AUTOPLAY_SONGS)
        if (picked.isEmpty()) return false
        for (song in picked) {
            artistOf[song.id] = song.artistKey
            albumOf[song.id] = song.albumKey
        }
        autoplayIds += picked.map { it.id }
        queue.insert(picked.map { it.id }, next = false)
        _state.update { it.withQueue() }
        saveResume()
        return true
    }

    /**
     * The song can't be played: says so, and moves on to the next one after a moment, as Apple
     * Music does. [song] is the one that failed when known before it reached the player.
     */
    private fun onFailed(song: SongEntity?, message: String, failedId: Long? = song?.id) {
        _state.update { state ->
            state.copy(
                nowPlaying = song?.let { nowPlaying(it) } ?: state.nowPlaying,
                isPlaying = false,
                isBuffering = false,
                error = message,
            ).withQueue()
        }
        val next = queue.nextPosition(wrap = _state.value.repeat == RepeatMode.ALL)
        if (next == null) {
            _state.update { it.copy(playRequested = false) }
            return
        }
        skipJob?.cancel()
        skipJob = scope.launch {
            delay(SKIP_AFTER_ERROR_MS)
            if (queue.currentId == (failedId ?: queue.currentId)) moveTo(next)
        }
    }

    /**
     * The playing song is near its end: with a crossfade set, the next song starts fading in over
     * it; without one, the next song is opened ahead so it follows with no gap.
     */
    private fun nearingTheEnd(positionMs: Long) {
        val state = _state.value
        val duration = state.durationMs
        if (duration <= 0L || ended || !state.playRequested) return
        // The last song of the queue: Autoplay chooses what follows before it ends, so it follows with no gap.
        if (autoplayJob == null && duration - positionMs <= AUTOPLAY_BEFORE_END_MS && queue.nextPosition(wrap = false) == null && state.sleep != SleepTimer.EndOfSong && autoplayOn()) {
            autoplayJob = scope.launch { extendWithAutoplay() }
        }
        val fadeMs = settings().crossfadeSeconds.coerceIn(0, MAX_CROSSFADE_SECONDS) * 1_000L
        if (fadeMs > 0L) {
            // Not into the same song again (repeat one), nor past a sleep timer set for the song's end.
            if (crossfadeJob != null || !state.isPlaying || state.repeat == RepeatMode.ONE || state.sleep == SleepTimer.EndOfSong) return
            if (duration < fadeMs * 3 || positionMs < duration - fadeMs) return
            val next = queue.nextPosition(wrap = state.repeat == RepeatMode.ALL) ?: return
            val nextId = queue.idAt(next) ?: return
            if (nextId == queue.currentId) return
            // An album playing in order runs on without a fade (owner: gapless albums), as live and
            // classical albums need; the preload below makes it seamless.
            val sameAlbum = !queue.shuffled && queue.currentId?.let { albumOf[it] }?.let { it == albumOf[nextId] } == true
            if (!sameAlbum) {
                crossfadeJob = scope.launch { crossfadeInto(next, nextId, fadeMs) }
                return
            }
        }
        if (preloadJob != null || preloaded != null || duration - positionMs > PRELOAD_BEFORE_END_MS) return
        val nextId = if (state.repeat == RepeatMode.ONE) {
            queue.currentId
        } else {
            queue.nextPosition(wrap = state.repeat == RepeatMode.ALL)?.let { queue.idAt(it) }
        } ?: return
        preloadJob = scope.launch {
            val song = findSong(nextId) ?: return@launch
            // Its loudness tag is read now, while this song plays, so it starts on time.
            volumeOf(song)
            val opened = open(song.uri) ?: return@launch
            preloaded?.second?.discard()
            preloaded = nextId to opened
        }
    }

    /** The media opened ahead for [songId], if it was that song; any other is let go. */
    private fun takePreloaded(songId: Long): OpenedMedia? {
        val ahead = preloaded ?: return null
        preloaded = null
        if (ahead.first == songId) return ahead.second
        ahead.second.discard()
        return null
    }

    private fun dropPreloaded() {
        preloadJob?.cancel()
        preloadJob = null
        preloaded?.second?.discard()
        preloaded = null
    }

    /**
     * Starts the song at [position] of the play order silently on a second player and fades it
     * up over [fadeMs] while this one fades down, then hands over to it ([handOver]).
     */
    private suspend fun crossfadeInto(position: Int, songId: Long, fadeMs: Long) {
        val song = findSong(songId) ?: return
        val incomingVolume = volumeOf(song)
        val opened = open(song.uri) ?: return
        val player = try {
            VlcEngine.newMediaPlayer(appContext).also { it.setMedia(opened.media) }
        } catch (e: RuntimeException) {
            Log.w(TAG, "libVLC refused the song to fade into", e)
            opened.discard()
            return
        }
        // The player keeps its own reference.
        opened.media.release()
        AudioEffects.applyCurrent(appContext, player)
        player.volume = 0
        player.play()
        incoming = Incoming(position, song, player, opened.descriptor, incomingVolume)
        val outgoing = mediaPlayer
        val steps = (fadeMs / CROSSFADE_STEP_MS).toInt().coerceAtLeast(1)
        for (step in 1..steps) {
            delay(CROSSFADE_STEP_MS)
            val share = step.toFloat() / steps
            player.volume = (incomingVolume * share).roundToInt()
            outgoing?.volume = (songVolume * (1f - share)).roundToInt()
        }
        handOver()
    }

    /** The song that faded in becomes the playing one; the one that faded out is let go. */
    private fun handOver() {
        val next = incoming ?: return
        incoming = null
        crossfadeJob = null
        _state.value.nowPlaying?.songId?.let { endedId -> scope.launch { runCatchingLogged { music.markPlayed(endedId) } } }
        mediaPlayer?.let { old ->
            old.setEventListener(null)
            old.stop()
            old.release()
        }
        descriptor.closeQuietly()
        mediaPlayer = next.player
        descriptor = next.descriptor
        songVolume = next.volume
        next.player.volume = songVolume
        next.player.setEventListener(eventListener)
        queue.moveTo(next.position)
        lyricsJob?.cancel()
        skipJob?.cancel()
        ended = false
        durationSaved = false
        autoplayJob = null
        pendingSeekMs = 0L
        val song = next.song
        _state.update {
            it.copy(
                nowPlaying = nowPlaying(song),
                isPlaying = next.player.isPlaying,
                playRequested = true,
                isBuffering = false,
                restored = false,
                positionMs = next.player.time.coerceAtLeast(0L),
                durationMs = song.durationMs ?: next.player.length.coerceAtLeast(0L),
                error = null,
                lyrics = null,
            ).withQueue()
        }
        SongPulses.prepare(appContext, scope, song.id, song.uri)
        lyricsJob = scope.launch {
            val lyrics = music.lyrics(song)
            if (_state.value.nowPlaying?.songId == song.id) _state.update { it.copy(lyrics = lyrics) }
        }
        saveResume()
    }

    /** Stops a crossfade: the song coming in is let go and the playing one is back at full volume. */
    private fun cancelCrossfade() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        incoming?.let { coming ->
            coming.player.stop()
            coming.player.release()
            coming.descriptor.closeQuietly()
        }
        incoming = null
        mediaPlayer?.volume = songVolume
    }

    /** The volume (percent) [song]'s loudness tag asks for with Sound Check on; 100 otherwise. */
    private suspend fun volumeOf(song: SongEntity): Int =
        if (settings().soundCheck) soundCheck.volumeFor(song.uri) else FULL_VOLUME

    private suspend fun findSong(songId: Long): SongEntity? = try {
        music.song(songId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Library lookup failed", e)
        null
    }

    private fun MusicPlayerState.withQueue(): MusicPlayerState {
        val wrap = repeat == RepeatMode.ALL
        val upcoming = queue.upNext(UP_NEXT_LIMIT)
        return copy(
            hasNext = queue.nextPosition(wrap = repeat != RepeatMode.OFF) != null,
            hasPrevious = queue.previousPosition(wrap = wrap) != null,
            queueIndex = queue.position,
            queueSize = queue.size,
            upNext = upcoming,
            autoplayFrom = upcoming.indexOfFirst { it in autoplayIds },
        )
    }

    private fun nowPlaying(song: SongEntity) = NowPlaying(
        songId = song.id,
        title = song.title,
        artist = MusicKeys.artistOf(null, song.artist ?: song.albumArtist),
        album = song.album,
        artUri = MusicLibrary.artUri(song),
    )

    private suspend fun runCatchingLogged(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Saving playback state failed", e)
        }
    }

    private companion object {
        const val TAG = "MusicPlayer"
        const val FULL_VOLUME = 100
        const val DUCKED_VOLUME = 30

        /** The sleep timer's fade: how long, and in how many steps. */
        const val SLEEP_FADE_MS = 6_000L
        const val SLEEP_FADE_STEPS = 30

        /** "Previous" restarts the song after this much of it played. */
        const val RESTART_AFTER_MS = 3_000L

        /** The longest crossfade, and the steps its volumes move in. */
        const val MAX_CROSSFADE_SECONDS = 8
        const val CROSSFADE_STEP_MS = 50L

        /** How long before a song ends the next one is opened, without a crossfade. */
        const val PRELOAD_BEFORE_END_MS = 8_000L
        const val SKIP_AFTER_ERROR_MS = 1_500L
        const val SAVE_EVERY_MS = 5_000L
        const val UP_NEXT_LIMIT = 50

        /** Autoplay: songs added at a time, the last songs it takes after, and how early it chooses. */
        const val AUTOPLAY_SONGS = 20
        const val AUTOPLAY_SEEDS = 5
        const val AUTOPLAY_BEFORE_END_MS = 30_000L

        const val RESUME_PREFS = "music_resume"
        const val KEY_QUEUE = "queue"
        const val KEY_POSITION = "position"
        const val KEY_POSITION_MS = "positionMs"
        const val KEY_REPEAT = "repeat"
    }
}
