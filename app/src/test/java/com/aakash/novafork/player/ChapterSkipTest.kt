package com.aakash.novafork.player

import com.aakash.novafork.ui.model.ChapterUi
import com.aakash.novafork.ui.player.ChapterSkip
import com.aakash.novafork.ui.player.chapterEndMs
import com.aakash.novafork.ui.player.chapterSkip
import com.aakash.novafork.ui.player.currentChapterIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterSkipTest {

    private val episode = listOf(
        ChapterUi("Opening", 0L, 90_000L),
        ChapterUi("Part A", 90_000L, 0L),
        ChapterUi("Part B", 900_000L, 0L),
        ChapterUi("Ending", 1_320_000L, 0L),
        ChapterUi("Preview", 1_410_000L, 0L),
    )
    private val length = 1_440_000L

    @Test
    fun `the chapter playing is the last one started`() {
        assertEquals(-1, currentChapterIndex(emptyList(), 5_000L))
        assertEquals(0, currentChapterIndex(episode, 0L))
        assertEquals(1, currentChapterIndex(episode, 90_000L))
        assertEquals(3, currentChapterIndex(episode, 1_400_000L))
    }

    @Test
    fun `a chapter ends at its own length, else at the next one, else at the end`() {
        assertEquals(90_000L, chapterEndMs(episode, 0, length))
        assertEquals(900_000L, chapterEndMs(episode, 1, length))
        assertEquals(length, chapterEndMs(episode, 4, length))
    }

    @Test
    fun `openings and closing credits are told by name, nothing else`() {
        assertEquals(ChapterSkip.INTRO, chapterSkip(episode, 0, length))
        assertNull(chapterSkip(episode, 1, length))
        assertEquals(ChapterSkip.CREDITS, chapterSkip(episode, 3, length))
        assertEquals(ChapterSkip.INTRO, chapterSkip(listOf(ChapterUi("OP", 0L, 0L), ChapterUi("Episode", 80_000L, 0L)), 0, length))
        // Credits at the start are not the end credits.
        val early = listOf(ChapterUi("Credits", 0L, 0L), ChapterUi("A", 60_000L, 0L), ChapterUi("B", 600_000L, 0L), ChapterUi("C", 900_000L, 0L))
        assertNull(chapterSkip(early, 0, length))
        // One chapter, or none, is no chapter list.
        assertNull(chapterSkip(listOf(ChapterUi("Intro", 0L, 0L)), 0, length))
        assertNull(chapterSkip(episode, -1, length))
    }
}
