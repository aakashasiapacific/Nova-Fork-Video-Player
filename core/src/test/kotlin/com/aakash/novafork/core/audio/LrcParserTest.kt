package com.aakash.novafork.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun readsTimedLinesInOrder() {
        val lyrics = LrcParser.parse(
            """
            [ti:Song]
            [ar:Artist]
            [00:12.00]First line
            [00:17.20]Second line
            [01:02.5]Third line
            """.trimIndent(),
        )
        assertTrue(lyrics.synced)
        assertEquals(
            listOf(LyricLine(12_000, "First line"), LyricLine(17_200, "Second line"), LyricLine(62_500, "Third line")),
            lyrics.lines,
        )
    }

    @Test
    fun oneTextWithSeveralTimesBecomesSeveralLines() {
        val lyrics = LrcParser.parse("[00:30.00]Verse\n[00:10.00][00:50.00]Chorus")
        assertEquals(listOf(10_000L, 30_000L, 50_000L), lyrics.lines.map { it.timeMs })
        assertEquals(listOf("Chorus", "Verse", "Chorus"), lyrics.lines.map { it.text })
    }

    @Test
    fun acceptsOtherTimeSpellings() {
        assertEquals(83_000L, LrcParser.parseTime("01:23"))
        assertEquals(83_450L, LrcParser.parseTime("01:23:45"))
        assertEquals(83_456L, LrcParser.parseTime("01:23.456"))
        assertEquals(3_723_100L, LrcParser.parseTime("1:02:03.1"))
        assertEquals(4_500_000L, LrcParser.parseTime("75:00.00"))
        assertNull(LrcParser.parseTime("ar:Artist"))
        assertNull(LrcParser.parseTime("00:61.00"))
        assertNull(LrcParser.parseTime("12"))
        assertNull(LrcParser.parseTime("00:1a"))
    }

    @Test
    fun offsetMovesEveryLine() {
        val sooner = LrcParser.parse("[offset:+500]\n[00:10.00]A\n[00:00.20]B")
        assertEquals(listOf(0L, 9_500L), sooner.lines.map { it.timeMs })
        val later = LrcParser.parse("[offset:-250]\n[00:10.00]A")
        assertEquals(10_250L, later.lines.single().timeMs)
    }

    @Test
    fun dropsWordTimesOfEnhancedLrc() {
        val lyrics = LrcParser.parse("[00:01.00]<00:01.00>Hello <00:01.50>world <not a time>")
        assertEquals("Hello world <not a time>", lyrics.lines.single().text)
    }

    @Test
    fun keepsEmptyTimedLinesAsBreaks() {
        val lyrics = LrcParser.parse("[00:01.00]Line\n[00:05.00]\n[00:09.00]Next")
        assertEquals(listOf("Line", "", "Next"), lyrics.lines.map { it.text })
    }

    @Test
    fun plainTextIsUnsynced() {
        val lyrics = LrcParser.parse("﻿\nFirst\n\nSecond\n\n")
        assertFalse(lyrics.synced)
        assertEquals(listOf(LyricLine(null, "First"), LyricLine(null, ""), LyricLine(null, "Second")), lyrics.lines)
        assertEquals(-1, lyrics.activeIndex(10_000))
    }

    @Test
    fun onlyTagsMeansNoLyrics() {
        assertTrue(LrcParser.parse("[ar:Someone]\n[ti:Something]\n").isEmpty)
        assertTrue(LrcParser.parse("").isEmpty)
    }

    @Test
    fun activeIndexFollowsThePosition() {
        val lyrics = LrcParser.parse("[00:10.00]A\n[00:20.00]B\n[00:30.00]C")
        assertEquals(-1, lyrics.activeIndex(9_999))
        assertEquals(0, lyrics.activeIndex(10_000))
        assertEquals(0, lyrics.activeIndex(19_999))
        assertEquals(1, lyrics.activeIndex(20_000))
        assertEquals(2, lyrics.activeIndex(99_000))
    }
}
