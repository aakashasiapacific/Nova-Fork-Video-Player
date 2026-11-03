package com.aakash.novafork.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQualityTest {

    @Test
    fun flacWavAndAiffAreLosslessByName() {
        assertTrue(AudioQuality.isLossless("01 Song.FLAC"))
        assertTrue(AudioQuality.isLossless("a.wav"))
        assertTrue(AudioQuality.isLossless("a.aiff"))
        assertTrue(AudioQuality.isLossless("a.aif"))
        assertFalse(AudioQuality.isLossless("a.mp3"))
        assertFalse(AudioQuality.isLossless("a.ogg"))
    }

    @Test
    fun anM4aIsLosslessOnlyWhenItsCodecIsAlac() {
        assertFalse(AudioQuality.isLossless("a.m4a"))
        assertFalse(AudioQuality.isLossless("a.m4a", "audio/mp4a-latm"))
        assertTrue(AudioQuality.isLossless("a.m4a", "audio/alac"))
        assertTrue(AudioQuality.needsCodec("a.M4A"))
        assertFalse(AudioQuality.needsCodec("a.flac"))
    }

    @Test
    fun labelsLosslessAndHiRes() {
        assertNull(AudioQuality.label(false, 96_000, 24))
        assertEquals("Lossless", AudioQuality.label(true, null, null))
        assertEquals("Lossless", AudioQuality.label(true, 44_100, 16))
        assertEquals("Lossless", AudioQuality.label(true, 48_000, 16))
        assertEquals("Hi-Res Lossless", AudioQuality.label(true, 96_000, 16))
        assertEquals("Hi-Res Lossless", AudioQuality.label(true, 44_100, 24))
        assertEquals("Hi-Res Lossless", AudioQuality.label(true, null, 32))
    }

    @Test
    fun namesTheFormat() {
        assertEquals("FLAC", AudioQuality.formatName("a.flac"))
        assertEquals("FLAC", AudioQuality.formatName("a.mka", "audio/flac"))
        assertEquals("MP3", AudioQuality.formatName("a.mp3", "audio/mpeg"))
        assertEquals("AAC", AudioQuality.formatName("a.m4a", "audio/mp4a-latm"))
        assertEquals("ALAC", AudioQuality.formatName("a.m4a", "audio/alac"))
        assertEquals("M4A", AudioQuality.formatName("a.m4a"))
        assertEquals("WAV", AudioQuality.formatName("a.wav"))
        assertEquals("AIFF", AudioQuality.formatName("a.aifc"))
        assertEquals("Opus", AudioQuality.formatName("a.ogg", "audio/opus"))
        assertEquals("APE", AudioQuality.formatName("a.ape"))
        assertEquals("Unknown", AudioQuality.formatName("noextension"))
    }

    @Test
    fun formatsTheNumbers() {
        assertEquals("44.1 kHz", AudioQuality.sampleRate(44_100))
        assertEquals("48 kHz", AudioQuality.sampleRate(48_000))
        assertEquals("96 kHz", AudioQuality.sampleRate(96_000))
        assertEquals("88.2 kHz", AudioQuality.sampleRate(88_200))
        assertEquals("24-bit", AudioQuality.bitDepth(24))
        assertEquals("320 kbps", AudioQuality.bitrate(320))
    }

    @Test
    fun averagesTheBitrateFromSizeAndLength() {
        // 4,000,000 bytes over 100 s = 320 kbps.
        assertEquals(320, AudioQuality.averageBitrateKbps(4_000_000, 100_000))
        assertNull(AudioQuality.averageBitrateKbps(null, 100_000))
        assertNull(AudioQuality.averageBitrateKbps(4_000_000, 0))
    }
}
