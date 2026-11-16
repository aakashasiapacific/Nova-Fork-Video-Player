package com.aakash.novafork.ui.model

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStatsTest {

    private val zone = ZoneId.of("UTC")
    private val now = at(2026, 11, 15)
    private val hour = 3_600_000L

    private fun at(year: Int, month: Int, day: Int): Long =
        ZonedDateTime.of(year, month, day, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun record(
        movie: Boolean = false,
        show: String? = null,
        durationMs: Long? = hour,
        positionMs: Long = 0,
        watched: Boolean = false,
        playCount: Int = 0,
        lastPlayedAt: Long? = null,
    ) = PlayRecord(
        isMovie = movie,
        isEpisode = show != null,
        showKey = show,
        showTitle = show?.uppercase(),
        durationMs = durationMs,
        positionMs = positionMs,
        watched = watched,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
    )

    // Storage

    @Test
    fun `folders are grouped by source and sorted largest first with a total`() {
        val files = listOf(
            StoredFile(1, "Movies/Action", 4_000),
            StoredFile(1, "/Movies/Action/", 1_000),
            StoredFile(2, "Movies/Action", 3_000),
            StoredFile(1, "Music", 100),
        )

        val space = spaceByFolder(files, mapOf(1L to "Device", 2L to "NAS"))

        assertEquals(8_100L, space.totalBytes)
        assertEquals(4, space.totalFiles)
        assertEquals(
            listOf("Device" to "Movies/Action", "NAS" to "Movies/Action", "Device" to "Music"),
            space.folders.map { it.sourceName to it.folder },
        )
        assertEquals(listOf(5_000L, 3_000L, 100L), space.folders.map { it.bytes })
        assertEquals(listOf(2, 1, 1), space.folders.map { it.files })
    }

    @Test
    fun `files without a known size are not counted`() {
        val space = spaceByFolder(listOf(StoredFile(1, "Streams", null), StoredFile(1, "Streams", 0)), mapOf(1L to "Device"))

        assertTrue(space.isEmpty)
        assertTrue(space.folders.isEmpty())
    }

    @Test
    fun `only the largest folders are listed but all are in the total`() {
        val files = (1..5).map { StoredFile(1, "f$it", it * 100L) }

        val space = spaceByFolder(files, mapOf(1L to "Device"), limit = 2)

        assertEquals(listOf("f5", "f4"), space.folders.map { it.folder })
        assertEquals(3, space.moreFolders)
        assertEquals(1_500L, space.totalBytes)
        assertEquals(5, space.totalFiles)
    }

    // Watch stats

    @Test
    fun `nothing played gives empty stats`() {
        assertTrue(watchStats(emptyList(), now, zone).isEmpty)
        assertTrue(watchStats(listOf(record(watched = true)), now, zone).isEmpty)
    }

    @Test
    fun `all time is finished viewings plus the part under way`() {
        val records = listOf(
            record(watched = true, playCount = 2, lastPlayedAt = at(2026, 1, 3)),
            record(positionMs = hour / 2, lastPlayedAt = at(2026, 1, 4)),
            record(durationMs = null, watched = true, playCount = 3, lastPlayedAt = at(2026, 1, 5)),
        )

        assertEquals(2 * hour + hour / 2, watchStats(records, now, zone).allTimeMs)
    }

    @Test
    fun `this month counts the videos last played this month once each`() {
        val records = listOf(
            record(movie = true, watched = true, playCount = 3, lastPlayedAt = at(2026, 11, 2)),
            record(positionMs = hour / 4, lastPlayedAt = at(2026, 11, 14)),
            record(watched = true, playCount = 1, lastPlayedAt = at(2026, 10, 31)),
            record(watched = true, playCount = 1, lastPlayedAt = at(2026, 12, 1)),
        )

        val stats = watchStats(records, now, zone)

        assertEquals(hour + hour / 4, stats.monthMs)
        assertEquals(1, stats.filmsFinishedThisMonth)
    }

    @Test
    fun `films finished counts only movies played to the end this month`() {
        val records = listOf(
            record(movie = true, watched = true, playCount = 1, lastPlayedAt = at(2026, 11, 1)),
            record(movie = true, watched = true, playCount = 1, lastPlayedAt = at(2026, 10, 30)),
            record(movie = true, positionMs = 1_000, lastPlayedAt = at(2026, 11, 3)),
            // Marked watched by hand: never played, so nothing to date or count.
            record(movie = true, watched = true, playCount = 0),
            record(show = "s1", watched = true, playCount = 1, lastPlayedAt = at(2026, 11, 3)),
        )

        assertEquals(1, watchStats(records, now, zone).filmsFinishedThisMonth)
    }

    @Test
    fun `the month starts at midnight in the given zone`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val lastOfOctober = ZonedDateTime.of(2026, 10, 31, 23, 59, 0, 0, tokyo).toInstant().toEpochMilli()
        val firstOfNovember = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, tokyo).toInstant().toEpochMilli()
        val records = listOf(
            record(movie = true, watched = true, playCount = 1, lastPlayedAt = lastOfOctober),
            record(movie = true, watched = true, playCount = 1, lastPlayedAt = firstOfNovember),
        )

        assertEquals(1, watchStats(records, now, tokyo).filmsFinishedThisMonth)
    }

    @Test
    fun `top shows are the three with most episodes watched`() {
        val records = buildList {
            repeat(5) { add(record(show = "a", watched = true, playCount = 1)) }
            repeat(2) { add(record(show = "b", watched = true, playCount = 1)) }
            repeat(2) { add(record(show = "c", watched = true, playCount = 1)) }
            repeat(7) { add(record(show = "d", watched = false)) }
            add(record(show = "e", watched = true, playCount = 1))
            add(record(movie = true, watched = true, playCount = 1))
        }

        val top = watchStats(records, now, zone).topShows

        assertEquals(listOf(WatchedShow("A", 5), WatchedShow("B", 2), WatchedShow("C", 2)), top)
    }

    @Test
    fun `watch time reads in minutes then hours`() {
        assertEquals("0 min", watchTimeLabel(0))
        assertEquals("42 min", watchTimeLabel(42 * 60_000L))
        assertEquals("1.5 hours", watchTimeLabel(90 * 60_000L))
        assertEquals("12.3 hours", watchTimeLabel(12 * hour + 18 * 60_000L))
        assertEquals("120 hours", watchTimeLabel(120 * hour))
    }
}
