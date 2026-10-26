package com.aakash.novafork.music

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * How loud a song is, moment by moment, in four bands (bass, low mids, high mids, treble), for
 * the "now playing" bars to move with the music (owner) instead of on their own. Read once from
 * the file itself in the background, so it needs no microphone access (which Android's live
 * Visualizer would); each band is scaled to its own loud parts.
 */
class SongPulse internal constructor(private val levels: FloatArray, private val frames: Int) {
    /** The four bands' levels (0–1) at [positionMs], into [out]. */
    fun levelsAt(positionMs: Long, out: FloatArray) {
        val frame = (positionMs / FRAME_MS).toInt().coerceIn(0, frames - 1)
        for (band in 0 until BANDS) out[band] = levels[frame * BANDS + band]
    }

    companion object {
        const val BANDS = 4
        internal const val FRAME_MS = 40L
    }
}

/**
 * The playing song's [SongPulse] and where in it the song is: [MusicPlayer] says which song plays
 * and reports its position; the bars read [levelsNow] every frame, which carries the position on
 * between the player's reports.
 */
object SongPulses {
    private val _current = MutableStateFlow<SongPulse?>(null)

    /** The playing song's pulse; null while it is being read, or when it could not be. */
    val current: StateFlow<SongPulse?> = _current.asStateFlow()

    private val cache = object : LinkedHashMap<Long, SongPulse>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, SongPulse>?): Boolean = size > CACHE_SIZE
    }
    private var songId: Long? = null
    private var job: Job? = null

    @Volatile private var positionMs = 0L
    @Volatile private var reportedAt = 0L
    @Volatile private var playing = false

    /** [id] (at [uri]) is now the playing song: its pulse, from the cache or read in the background. */
    fun prepare(context: Context, scope: CoroutineScope, id: Long, uri: String) {
        if (id == songId) return
        songId = id
        job?.cancel()
        val cached = synchronized(cache) { cache[id] }
        _current.value = cached
        if (cached != null) return
        val appContext = context.applicationContext
        job = scope.launch(Dispatchers.Default) {
            val pulse = try {
                analyse(appContext, Uri.parse(uri))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Couldn't read the song's rhythm", e)
                null
            } ?: return@launch
            synchronized(cache) { cache[id] = pulse }
            if (songId == id) _current.value = pulse
        }
    }

    /** Nothing plays any more. */
    fun clear() {
        job?.cancel()
        songId = null
        _current.value = null
        playing = false
    }

    /** The player's report of where the song is and whether it plays. */
    fun report(positionMs: Long, playing: Boolean) {
        this.positionMs = positionMs
        this.playing = playing
        reportedAt = SystemClock.elapsedRealtime()
    }

    /** The bands' levels now, the position carried on from the last report while playing. */
    fun levelsNow(pulse: SongPulse, out: FloatArray) {
        val since = if (playing) SystemClock.elapsedRealtime() - reportedAt else 0L
        pulse.levelsAt(positionMs + since.coerceAtMost(MAX_CARRY_MS), out)
    }

    /** Decodes the song once and sums each band's energy per frame. */
    private suspend fun analyse(context: Context, uri: Uri): SongPulse? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val bands = Bands(sampleRate)
            val energies = ArrayList<FloatArray>()
            var frameSum = FloatArray(SongPulse.BANDS)
            var frameCount = 0
            var samplesPerFrame = (sampleRate * SongPulse.FRAME_MS / 1000).toInt()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex) ?: continue
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = decoder.outputFormat
                        sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                            out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        bands.setRate(sampleRate)
                        samplesPerFrame = (sampleRate * SongPulse.FRAME_MS / 1000).toInt()
                    }
                    outIndex >= 0 -> {
                        val buffer = decoder.getOutputBuffer(outIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val pcm = buffer.slice().order(ByteOrder.nativeOrder())
                            val bytesPerSample = if (floatPcm) 4 else 2
                            val frameSamples = info.size / (bytesPerSample * channels.coerceAtLeast(1))
                            for (i in 0 until frameSamples) {
                                var mono = 0f
                                for (c in 0 until channels) {
                                    val index = (i * channels + c) * bytesPerSample
                                    mono += if (floatPcm) pcm.getFloat(index) else pcm.getShort(index) / 32768f
                                }
                                bands.add(mono / channels, frameSum)
                                frameCount++
                                if (frameCount >= samplesPerFrame) {
                                    energies += FloatArray(SongPulse.BANDS) { sqrt(frameSum[it] / frameCount) }
                                    frameSum = FloatArray(SongPulse.BANDS)
                                    frameCount = 0
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            return if (energies.isEmpty()) null else normalise(energies)
        } finally {
            try {
                codec?.stop()
            } catch (e: IllegalStateException) {
                // Never started, or already stopped by the error that got here.
            }
            codec?.release()
            extractor.release()
        }
    }

    /**
     * Each band scaled to its own loud parts (its 95th percentile reads as full), then shaped so
     * the beats stand out from what lies between them.
     */
    private fun normalise(energies: List<FloatArray>): SongPulse {
        val frames = energies.size
        val levels = FloatArray(frames * SongPulse.BANDS)
        for (band in 0 until SongPulse.BANDS) {
            val sorted = FloatArray(frames) { energies[it][band] }.also { it.sort() }
            val loud = sorted[((frames - 1) * LOUD_PERCENTILE).toInt()].coerceAtLeast(1e-6f)
            for (frame in 0 until frames) {
                val level = (energies[frame][band] / loud).coerceIn(0f, 1f)
                levels[frame * SongPulse.BANDS + band] = level * level
            }
        }
        return SongPulse(levels, frames)
    }

    /** Splits a signal into four bands with one-pole filters, adding each band's energy to a sum. */
    private class Bands(sampleRate: Int) {
        private var bass = 0f
        private var lowMid = 0f
        private var highMid = 0f
        private var aBass = 0f
        private var aLowMid = 0f
        private var aHighMid = 0f

        init {
            setRate(sampleRate)
        }

        fun setRate(sampleRate: Int) {
            aBass = coefficient(BASS_HZ, sampleRate)
            aLowMid = coefficient(LOW_MID_HZ, sampleRate)
            aHighMid = coefficient(HIGH_MID_HZ, sampleRate)
        }

        fun add(sample: Float, sums: FloatArray) {
            bass += aBass * (sample - bass)
            lowMid += aLowMid * (sample - lowMid)
            highMid += aHighMid * (sample - highMid)
            val b0 = bass
            val b1 = lowMid - bass
            val b2 = highMid - lowMid
            val b3 = sample - highMid
            sums[0] += b0 * b0
            sums[1] += b1 * b1
            sums[2] += b2 * b2
            sums[3] += b3 * b3
        }

        private fun coefficient(cutoffHz: Float, sampleRate: Int): Float =
            (1f - exp(-2f * Math.PI.toFloat() * cutoffHz / sampleRate.coerceAtLeast(1)))
    }

    private const val TAG = "SongPulses"
    private const val CACHE_SIZE = 24
    private const val TIMEOUT_US = 10_000L
    private const val MAX_CARRY_MS = 1_500L
    private const val LOUD_PERCENTILE = 0.95f
    private const val BASS_HZ = 150f
    private const val LOW_MID_HZ = 600f
    private const val HIGH_MID_HZ = 3_000f
}
