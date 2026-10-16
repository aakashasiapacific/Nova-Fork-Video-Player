package com.aakash.novafork.core.opensubtitles

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Locale

/**
 * The OpenSubtitles "movie hash": the file size plus the 64-bit little-endian words of the first
 * and the last 64 KiB, added with wrap-around, as 16 lower-case hex digits. A file that has it
 * on OpenSubtitles gets the subtitles timed for exactly that release.
 */
object OpenSubtitlesHash {
    const val CHUNK_SIZE: Int = 64 * 1024

    /** From [size] and its first and last [CHUNK_SIZE] bytes; null when the file is smaller than one chunk. */
    fun compute(size: Long, head: ByteArray, tail: ByteArray): String? {
        if (size < CHUNK_SIZE || head.size != CHUNK_SIZE || tail.size != CHUNK_SIZE) return null
        var hash = size
        hash += sum(head)
        hash += sum(tail)
        return String.format(Locale.ROOT, "%016x", hash)
    }

    /** Reads the two chunks at their positions (the channel's own position is left alone); null when it can't. */
    fun of(channel: FileChannel): String? {
        val size = channel.size()
        if (size < CHUNK_SIZE) return null
        val head = readFully(channel, 0L) ?: return null
        val tail = readFully(channel, size - CHUNK_SIZE) ?: return null
        return compute(size, head, tail)
    }

    /** Null for a missing, unreadable or too small file. */
    fun of(file: File): String? =
        try {
            RandomAccessFile(file, "r").use { of(it.channel) }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }

    private fun sum(chunk: ByteArray): Long {
        val words = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
        var total = 0L
        while (words.hasRemaining()) total += words.get()
        return total
    }

    private fun readFully(channel: FileChannel, position: Long): ByteArray? {
        val buffer = ByteBuffer.allocate(CHUNK_SIZE)
        var at = position
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, at)
            if (read < 0) return null
            at += read
        }
        return buffer.array()
    }
}
