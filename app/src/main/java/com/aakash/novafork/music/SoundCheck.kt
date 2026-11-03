package com.aakash.novafork.music

import android.content.Context
import android.net.Uri
import android.util.Log
import com.aakash.novafork.core.audio.ReplayGain
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sound Check: the volume a song's ReplayGain / iTunNORM tag asks for, read from the file the
 * first time the song comes up and remembered per uri. Songs without the tag stay at 100%.
 */
class SoundCheck(context: Context) {
    private val appContext = context.applicationContext
    private val volumes = ConcurrentHashMap<String, Int>()

    /** libVLC volume (percent, 100 = unchanged) for the song at [uri]; reads its tags once, off the main thread. */
    suspend fun volumeFor(uri: String): Int {
        volumes[uri]?.let { return it }
        val volume = withContext(Dispatchers.IO) { read(uri) }
        volumes[uri] = volume
        return volume
    }

    private fun read(uri: String): Int = try {
        appContext.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
            val first = ByteArray(HEADER)
            val got = readFully(input, first, 0, HEADER)
            if (got < HEADER) return@use UNCHANGED
            val head = ByteArray(ReplayGain.bytesToRead(first))
            first.copyInto(head)
            val total = HEADER + readFully(input, head, HEADER, head.size - HEADER)
            val bytes = if (total == head.size) head else head.copyOf(total)
            val gain = ReplayGain.gainDb(bytes) ?: return@use UNCHANGED
            ReplayGain.volumePercent(gain, ReplayGain.peak(bytes))
        } ?: UNCHANGED
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read a song's loudness tag", e)
        UNCHANGED
    }

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
        var total = 0
        while (total < length) {
            val read = input.read(buffer, offset + total, length - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private companion object {
        const val TAG = "SoundCheck"
        const val HEADER = 10
        const val UNCHANGED = 100
    }
}
