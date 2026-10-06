package com.aakash.novafork.core.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatsTest {

    @Test
    fun clock() {
        assertEquals("1:02:03", Formats.clock(3_723_000))
        assertEquals("1:05", Formats.clock(65_000))
        assertEquals("0:00", Formats.clock(0))
        assertEquals("0:00", Formats.clock(-5_000))
        assertEquals("0:00", Formats.clock(999))
        assertEquals("4:05", Formats.clock(245_999))
        assertEquals("12:34", Formats.clock(754_000))
        assertEquals("59:59", Formats.clock(3_599_000))
        assertEquals("1:00:00", Formats.clock(3_600_000))
        assertEquals("10:00:00", Formats.clock(36_000_000))
    }

    @Test
    fun clockUsesAsciiDigitsInEveryLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("1:02:03", Formats.clock(3_723_000))
            assertEquals("1.4 GB", Formats.size((1.4 * 1024 * 1024 * 1024).toLong()))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun runtime() {
        assertEquals("2h 35m", Formats.runtime(155))
        assertEquals("45m", Formats.runtime(45))
        assertEquals("2h", Formats.runtime(120))
        assertEquals("1h 1m", Formats.runtime(61))
        assertEquals("0m", Formats.runtime(0))
        assertEquals("0m", Formats.runtime(-10))
    }

    @Test
    fun size() {
        assertEquals("1.4 GB", Formats.size((1.4 * 1024 * 1024 * 1024).toLong()))
        assertEquals("1.0 GB", Formats.size(1024L * 1024 * 1024))
        assertEquals("700 MB", Formats.size(700L * 1024 * 1024))
        assertEquals("12 KB", Formats.size(12L * 1024))
        assertEquals("500 B", Formats.size(500))
        assertEquals("0 B", Formats.size(0))
        assertEquals("0 B", Formats.size(-1))
        assertEquals("2.5 TB", Formats.size((2.5 * 1024 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun episodeCode() {
        assertEquals("S01E03", Formats.episodeCode(1, 3))
        assertEquals("S01E03-E04", Formats.episodeCode(1, 3, 4))
        assertEquals("S01E03", Formats.episodeCode(1, 3, 3))
        assertEquals("S01E03", Formats.episodeCode(1, 3, 2))
        assertEquals("S00E01", Formats.episodeCode(0, 1))
        assertEquals("S10E105", Formats.episodeCode(10, 105))
    }

    @Test
    fun remaining() {
        assertEquals("1h 12m left", Formats.remaining(0, 72 * 60_000L + 30_000))
        assertEquals("8m left", Formats.remaining(60_000, 9 * 60_000L + 10_000))
        assertEquals("1h left", Formats.remaining(0, 3_600_000))
        assertEquals("Under a minute left", Formats.remaining(0, 30_000))
        assertEquals("Under a minute left", Formats.remaining(10_000, 5_000))
    }
}
