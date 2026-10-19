package com.aakash.novafork.music

import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.aakash.novafork.core.audio.Lyrics
import com.aakash.novafork.core.audio.MusicKeys
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.data.music.MusicLibrary
import com.aakash.novafork.data.music.MusicRepository
import com.aakash.novafork.player.OpenedMedia
import com.aakash.novafork.player.PlayQueue
import com.aakash.novafork.player.PlaybackAudio
import com.aakash.novafork.player.VlcEngine
import com.aakash.novafork.player.closeQuietly
import com.aakash.novafork.ui.model.RepeatMode
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
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicReference

/** The song playing (or paused), as the Audio tab and the notification show it. */
data class NowPlaying(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String?,
    /** Cover file or embedded picture (see [MusicLibrary.artUri]). */
    val artUri: String,
)

data class MusicPlayerState(
    /** A queue is open (its first song may still be loading); false once closed. */
    val active: Boolean = false,
    /** Null while nothing is queued, and while the first song loads. */
    val nowPlaying: NowPlaying? = null,
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
)

/**
 * Plays songs from the music library, one queue at a time, on its own libVLC player (the video
 * player has another). It lives as long as the process (see AppGraph); [MusicService] keeps the
 * process in the foreground with a notification while it plays. Video and music take audio focus
 * from each other, so starting one pauses the other. Main thread only.
 */
class MusicPlayer(
    context: Context,
    private val music: MusicRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(MusicPlayerState())
    val state: StateFlow<MusicPlayerState> = _state.asStateFlow()

    private val queue = PlayQueue()
    private var mediaPlayer: MediaPlayer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var loadJob: Job? = null
    private var lyricsJob: Job? = null
    private var skipJob: Job? = null

    /** The current song played to its end; play() opens it again. */
    private var ended = false

    /** Where a restored song resumes once it plays (0 = from the start). */
    private var pendingSeekMs = 0L
    private var lastSavedAt = 0L
    private val resumePrefs = appContext.getSharedPreferences(RESUME_PREFS, Context.MODE_PRIVATE)
    private var pausedByFocusLoss = false
    private var durationSaved = false

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
                mediaPlayer?.setVolume(if (ducked) DUCKED_VOLUME else FULL_VOLUME)
            }

            override fun onBecomingNoisy() = pause()
        },
        contentType = AudioAttributes.CONTENT_TYPE_MUSIC,
    )

    val hasContent: Boolean get() = _state.value.active

    init {
        scope.launch { restore() }
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
    fun playSongs(songIds: List<Long>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songIds.isEmpty()) return
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
        _state.update { it.copy(playRequested = false) }
        mediaPlayer?.pause()
        saveResume()
    }

    fun togglePlay() {
        if (_state.value.isPlaying) pause() else play()
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

    /** Plays the song at [position] of the play order. */
    fun skipTo(position: Int) {
        if (position in 0 until queue.size) moveTo(position)
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        if (!player.isSeekable) return
        val target = positionMs.coerceIn(0L, _state.value.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        player.setTime(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun setShuffle(on: Boolean) {
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
        audio.release()
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
        loadJob?.cancel()
        lyricsJob?.cancel()
        skipJob?.cancel()
        ended = false
        durationSaved = false
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
            lyricsJob = scope.launch {
                val lyrics = music.lyrics(song)
                if (_state.value.nowPlaying?.songId == song.id) _state.update { it.copy(lyrics = lyrics) }
            }
            val opened = open(song.uri)
            if (opened == null) {
                onFailed(song, "Can't open this song")
                return@launch
            }
            val player = try {
                (mediaPlayer ?: VlcEngine.newMediaPlayer(appContext).also {
                    it.setEventListener(eventListener)
                    mediaPlayer = it
                }).also { it.setMedia(opened.media) }
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
            audio.requestFocus()
            audio.registerNoisyReceiver()
            player.play()
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
                _state.update { it.copy(positionMs = event.timeChanged.coerceAtLeast(0L)) }
                if (System.currentTimeMillis() - lastSavedAt > SAVE_EVERY_MS) saveResume()
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
    }

    private fun saveDuration(length: Long) {
        val songId = _state.value.nowPlaying?.songId ?: return
        if (durationSaved) return
        durationSaved = true
        scope.launch { runCatchingLogged { music.fillDuration(songId, length) } }
    }

    private fun onEnded() {
        val songId = _state.value.nowPlaying?.songId
        if (songId != null) scope.launch { runCatchingLogged { music.markPlayed(songId) } }
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
        // The end of the queue: the last song stays, ready to play again.
        ended = true
        audio.release()
        _state.update { it.copy(isPlaying = false, isBuffering = false, playRequested = false, positionMs = 0) }
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

    private fun MusicPlayerState.withQueue(): MusicPlayerState {
        val wrap = repeat == RepeatMode.ALL
        return copy(
            hasNext = queue.nextPosition(wrap = repeat != RepeatMode.OFF) != null,
            hasPrevious = queue.previousPosition(wrap = wrap) != null,
            queueIndex = queue.position,
            queueSize = queue.size,
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

        /** "Previous" restarts the song after this much of it played. */
        const val RESTART_AFTER_MS = 3_000L
        const val SKIP_AFTER_ERROR_MS = 1_500L
        const val SAVE_EVERY_MS = 5_000L

        const val RESUME_PREFS = "music_resume"
        const val KEY_QUEUE = "queue"
        const val KEY_POSITION = "position"
        const val KEY_POSITION_MS = "positionMs"
        const val KEY_REPEAT = "repeat"
    }
}
