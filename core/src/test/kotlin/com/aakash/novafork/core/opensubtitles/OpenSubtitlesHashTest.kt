package com.aakash.novafork.core.opensubtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

/**
 * The expected values were computed with an independent implementation of the published
 * algorithm (size + little-endian 64-bit sums of the first and last 64 KiB, modulo 2^64).
 */
class OpenSubtitlesHashTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun file(name: String, size: Int, byteAt: (Int) -> Int): File {
        val file = folder.newFile(name)
        file.writeBytes(ByteArray(size) { byteAt(it).toByte() })
        return file
    }

    @Test
    fun twoSeparateChunks() {
        assertEquals("bf7b30eba75ef876", OpenSubtitlesHash.of(file("a.bin", 131_072) { it % 251 }))
    }

    @Test
    fun skipsTheMiddleOfALargerFile() {
        assertEquals("5f9fe02060a3cd40", OpenSubtitlesHash.of(file("b.bin", 200_000) { (it * 31 + 7) and 0xff }))
    }

    @Test
    fun sumsWrapAroundAndChunksMayOverlap() {
        // One chunk long: head and tail are the same 8192 words of 0xFFFF…, each worth −1.
        assertEquals("000000000000c000", OpenSubtitlesHash.of(file("c.bin", 65_536) { 0xff }))
    }

    @Test
    fun zerosHashToTheSize() {
        val file = folder.newFile("zeros.bin")
        RandomAccessFile(file, "rw").use { it.setLength(1L shl 20) }
        assertEquals("0000000000100000", OpenSubtitlesHash.of(file))
    }

    @Test
    fun computeMatchesTheFileVersion() {
        val bytes = ByteArray(131_072) { (it % 251).toByte() }
        val head = bytes.copyOfRange(0, OpenSubtitlesHash.CHUNK_SIZE)
        val tail = bytes.copyOfRange(bytes.size - OpenSubtitlesHash.CHUNK_SIZE, bytes.size)
        assertEquals("bf7b30eba75ef876", OpenSubtitlesHash.compute(bytes.size.toLong(), head, tail))
    }

    @Test
    fun tooSmallOrMissingGivesNull() {
        assertNull(OpenSubtitlesHash.of(file("small.bin", 65_535) { 1 }))
        assertNull(OpenSubtitlesHash.of(File(folder.root, "missing.bin")))
        assertNull(OpenSubtitlesHash.compute(10, ByteArray(10), ByteArray(10)))
    }
}
