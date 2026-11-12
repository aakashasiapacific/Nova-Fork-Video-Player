package com.aakash.novafork.music

import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import com.aakash.novafork.core.audio.AudioQuality
import com.aakash.novafork.data.db.SongEntity
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the file itself says about its audio; every field is null when it doesn't say. */
data class AudioInfo(
    /** The audio track's codec as a mime type ("audio/flac", "audio/alac", "audio/mp4a-latm" …). */
    val codecMime: String? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
)

/**
 * Reads a song's [AudioInfo] lazily, for the Lossless badge and Song Info, and remembers it per
 * song address in memory. Nothing is read at scan time and nothing is stored.
 */
object AudioInfos {
    private const val TAG = "AudioInfos"
    private const val CACHE_SIZE = 200

    private val cache = object : LinkedHashMap<String, AudioInfo>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AudioInfo>?): Boolean = size > CACHE_SIZE
    }

    /** What has been read for [uri] so far; null before that. */
    fun cached(uri: String): AudioInfo? = synchronized(cache) { cache[uri] }

    /** Reads [uri] on the IO dispatcher (once; later calls answer from memory). A file that can't be read gives an empty [AudioInfo]. */
    suspend fun load(context: Context, uri: String): AudioInfo {
        cached(uri)?.let { return it }
        val info = withContext(Dispatchers.IO) { read(context.applicationContext, Uri.parse(uri)) }
        synchronized(cache) { cache[uri] = info }
        return info
    }

    /** Whether [song] is lossless as far as is known: by its name, or by the codec once read. */
    fun isLossless(song: SongEntity): Boolean = AudioQuality.isLossless(song.fileName, cached(song.uri)?.codecMime)

    /**
     * Whether every song of an album is lossless. Reads the codec of its m4a songs, which could
     * be AAC or ALAC; a song that is plainly lossy by name settles it with no reading.
     */
    suspend fun allLossless(context: Context, songs: List<SongEntity>): Boolean {
        if (songs.isEmpty()) return false
        val undecided = songs.filterNot { isLossless(it) }
        if (undecided.any { !AudioQuality.needsCodec(it.fileName) }) return false
        undecided.forEach { load(context, it.uri) }
        return songs.all { isLossless(it) }
    }

    /** Blocking; call off the main thread. */
    private fun read(context: Context, uri: Uri): AudioInfo {
        var codecMime: String? = null
        var bitrateKbps: Int? = null
        var sampleRateHz: Int? = null
        var bitDepth: Int? = null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime == null || !mime.startsWith("audio/")) continue
                codecMime = mime
                if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRateHz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                if (format.containsKey(MediaFormat.KEY_BIT_RATE)) bitrateKbps = format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000
                if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) bitDepth = pcmBits(format.getInteger(MediaFormat.KEY_PCM_ENCODING))
                break
            }
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't read the tracks of a music file", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't read the tracks of a music file", e)
        } finally {
            extractor.release()
        }
        val retriever = MediaMetadataRetriever()
        try {
            if (uri.scheme == "file") retriever.setDataSource(uri.path) else retriever.setDataSource(context, uri)
            fun number(key: Int): Int? = retriever.extractMetadata(key)?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            number(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let { bitrateKbps = it / 1000 }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sampleRateHz = number(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE) ?: sampleRateHz
                bitDepth = number(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE) ?: bitDepth
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't read the audio details of a music file", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Android 10+ declares an IOException; nothing is left to clean up either way.
                Log.w(TAG, "Couldn't release a metadata retriever", e)
            }
        }
        return AudioInfo(codecMime, bitrateKbps?.takeIf { it > 0 }, sampleRateHz?.takeIf { it > 0 }, bitDepth?.takeIf { it > 0 })
    }

    private fun pcmBits(encoding: Int): Int? = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 8
        AudioFormat.ENCODING_PCM_16BIT -> 16
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
        else -> null
    }
}
