package com.aakash.novafork.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayGainTest {
    private fun bytes(vararg parts: Any): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (part in parts) when (part) {
            is String -> out.write(part.toByteArray(Charsets.ISO_8859_1))
            is Int -> out.write(part)
            is ByteArray -> out.write(part)
        }
        return out.toByteArray()
    }

    @Test
    fun readsAVorbisComment() {
        val head = bytes("fLaC", 0, "junk", "REPLAYGAIN_TRACK_GAIN=-7.25 dB", 0, "REPLAYGAIN_TRACK_PEAK=0.987654")
        assertEquals(-7.25, ReplayGain.gainDb(head)!!, 1e-9)
        assertEquals(0.987654, ReplayGain.peak(head)!!, 1e-9)
    }

    @Test
    fun readsAnId3TxxxFrame() {
        val head = bytes("ID3", 4, 0, 0, 0, 0, 0, 40, "TXXX", 0, 0, 0, 30, 0, 0, 0, "replaygain_track_gain", 0, "+3.50 dB")
        assertEquals(3.5, ReplayGain.gainDb(head)!!, 1e-9)
    }

    @Test
    fun readsAUtf16Id3Frame() {
        val key = "replaygain_track_gain".map { "$it\u0000" }.joinToString("")
        val value = "-4.10 dB".map { "$it\u0000" }.joinToString("")
        val head = bytes("TXXX", 1, 0xFF, 0xFE, key, 0, 0, 0xFF, 0xFE, value)
        assertEquals(-4.1, ReplayGain.gainDb(head)!!, 1e-9)
    }

    @Test
    fun readsAnMp4Atom() {
        // The data atom's size byte is an ASCII digit; it must not be taken for the value.
        val head = bytes("name", 0, 0, 0, 0, "replaygain_track_gain", 0, 0, 0, 0x34, "data", 0, 0, 0, 1, 0, 0, 0, 0, "-9.1 dB")
        assertEquals(-9.1, ReplayGain.gainDb(head)!!, 1e-9)
    }

    @Test
    fun readsITunNorm() {
        // 000004B2 = 1202 (the louder side): 10 * log10(1000 / 1202) dB of gain.
        val head = bytes("COMM", 0, "eng", "iTunNORM", 0, " 000004B2 000003E8 00000000")
        assertEquals(-10.0 * Math.log10(1.202), ReplayGain.gainDb(head)!!, 1e-9)
    }

    @Test
    fun replayGainBeatsITunNorm() {
        val head = bytes("iTunNORM", 0, " 000004B2 000004B2 ", "REPLAYGAIN_TRACK_GAIN=-2.0 dB")
        assertEquals(-2.0, ReplayGain.gainDb(head)!!, 1e-9)
    }

    @Test
    fun untaggedFilesHaveNoGain() {
        assertNull(ReplayGain.gainDb(bytes("ID3", 0, 0, 0, 0, 0, 0, 0, "just music")))
        assertNull(ReplayGain.gainDb(bytes("REPLAYGAIN_TRACK_GAIN=")))
    }

    @Test
    fun gainIsClampedToTwelveDecibels() {
        assertEquals(-12.0, ReplayGain.gainDb(bytes("REPLAYGAIN_TRACK_GAIN=-30.00 dB"))!!, 1e-9)
        assertEquals(12.0, ReplayGain.gainDb(bytes("REPLAYGAIN_TRACK_GAIN=+20.00 dB"))!!, 1e-9)
    }

    @Test
    fun volumeFollowsTheGain() {
        assertEquals(100, ReplayGain.volumePercent(0.0))
        assertEquals(50, ReplayGain.volumePercent(-6.0206))
        assertEquals(200, ReplayGain.volumePercent(12.0))
        // A tagged peak keeps a boost from clipping: a 0.9 peak allows about 1.11x at most.
        assertEquals(111, ReplayGain.volumePercent(6.0, peak = 0.9))
        assertEquals(100, ReplayGain.volumePercent(6.0, peak = 1.0))
        assertEquals(50, ReplayGain.volumePercent(-6.0206, peak = 0.1))
    }

    @Test
    fun id3HeaderSaysHowMuchToRead() {
        assertEquals(1024 * 1024, ReplayGain.bytesToRead(bytes("fLaC", 0, 0, 0, 0, 0, 0)))
        // A small tag is still read for at least 64 KB; a 1 MiB one in full (plus its header).
        assertEquals(64 * 1024, ReplayGain.bytesToRead(bytes("ID3", 4, 0, 0, 0, 0, 2, 0)))
        assertEquals(1024 * 1024 + 10, ReplayGain.bytesToRead(bytes("ID3", 4, 0, 0, 0, 0x40, 0, 0)))
    }
}
