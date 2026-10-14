package com.aakash.novafork.data

import android.app.Application
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Resume points, the watched rules and "next episode" of [LibraryRepositoryImpl]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LibraryPlaybackTest {
    private lateinit var fx: RepositoryFixture
    private val repo: LibraryRepositoryImpl get() = fx.repository

    @Before
    fun setUp() {
        fx = RepositoryFixture()
    }

    @After
    fun tearDown() {
        if (::fx.isInitialized) fx.close()
    }

    private var folderId = 0L

    /** Scans [videos] into one folder and returns their ids, in order. */
    private suspend fun scan(vararg videos: ScannedVideo): List<Long> {
        folderId = repo.addFolder(TREE, "Videos")
        fx.folders.serve(TREE, *videos)
        repo.scanSource(folderId)
        return videos.map { fx.videoAt(it.uri).id }
    }

    @Test
    fun longVideoIsWatchedWithinThreePercentOfTheEnd() = runTest {
        // Two hours: 3% is 216 s, more than 90 s.
        val id = scan(MOVIE).single()

        repo.savePlayback(id, 6_900_000, 7_200_000)
        fx.videoAt(MOVIE.uri).let {
            assertFalse(it.watched)
            assertEquals(6_900_000L, it.positionMs)
            assertEquals(7_200_000L, it.durationMs)
            assertNotNull(it.lastPlayedAt)
            assertEquals(0, it.playCount)
        }

        repo.savePlayback(id, 7_000_000, 7_200_000)
        fx.videoAt(MOVIE.uri).let {
            assertTrue(it.watched)
            assertEquals(0L, it.positionMs)
            assertEquals(1, it.playCount)
        }

        // The player saving the end position once more is the same viewing.
        repo.savePlayback(id, 7_150_000, 7_200_000)
        assertEquals(1, fx.videoAt(MOVIE.uri).playCount)

        // Watching it again counts again.
        repo.savePlayback(id, 1_000_000, 7_200_000)
        fx.videoAt(MOVIE.uri).let {
            assertTrue(it.watched)
            assertEquals(1_000_000L, it.positionMs)
            assertEquals(1, it.playCount)
        }
        repo.savePlayback(id, 7_100_000, 7_200_000)
        fx.videoAt(MOVIE.uri).let {
            assertEquals(0L, it.positionMs)
            assertEquals(2, it.playCount)
        }
    }

    @Test
    fun shortVideoIsWatchedWithinNinetySecondsOfTheEnd() = runTest {
        // Twenty minutes: 3% is only 36 s, so the 90 s rule applies.
        val id = scan(EPISODE).single()

        repo.savePlayback(id, 1_100_000, 1_200_000)
        fx.videoAt(EPISODE.uri).let {
            assertFalse(it.watched)
            assertEquals(1_100_000L, it.positionMs)
        }

        repo.savePlayback(id, 1_115_000, 1_200_000)
        fx.videoAt(EPISODE.uri).let {
            assertTrue(it.watched)
            assertEquals(0L, it.positionMs)
            assertEquals(1, it.playCount)
        }
    }

    @Test
    fun clipShorterThanNinetySecondsNeedsHalfOfItPlayed() = runTest {
        val id = scan(CLIP).single()

        repo.savePlayback(id, 10_000, 61_000)
        fx.videoAt(CLIP.uri).let {
            assertFalse(it.watched)
            assertEquals(10_000L, it.positionMs)
            // The scanner's duration is kept; the player's only fills a missing one.
            assertEquals(60_000L, it.durationMs)
        }

        repo.savePlayback(id, 45_000, 61_000)
        assertTrue(fx.videoAt(CLIP.uri).watched)
    }

    @Test
    fun unknownDurationNeverMarksWatched() = runTest {
        val id = scan(MOVIE).single()

        repo.savePlayback(id, 7_000_000, 0)

        fx.videoAt(MOVIE.uri).let {
            assertFalse(it.watched)
            assertEquals(7_000_000L, it.positionMs)
            assertNull(it.durationMs)
            assertNotNull(it.lastPlayedAt)
        }
    }

    @Test
    fun setWatchedStartsOverEitherWay() = runTest {
        val id = scan(MOVIE).single()
        repo.savePlayback(id, 500_000, 7_200_000)

        repo.setWatched(id, true)
        fx.videoAt(MOVIE.uri).let {
            assertTrue(it.watched)
            assertEquals(0L, it.positionMs)
            assertEquals(0, it.playCount)
        }

        repo.savePlayback(id, 400_000, 7_200_000)
        repo.setWatched(id, false)
        fx.videoAt(MOVIE.uri).let {
            assertFalse(it.watched)
            assertEquals(0L, it.positionMs)
        }
    }

    @Test
    fun nextEpisodeFollowsSeasonAndEpisodeOrder() = runTest {
        val ids = scan(S1E1E2, S2E1, S1E3, S1E4, S1E3_HD, OTHER_SHOW, MOVIE)
        val double = ids[0]
        val season2 = ids[1]
        val third = ids[2]
        val fourth = ids[3]
        val thirdHd = ids[4]
        val otherShow = ids[5]
        val movie = ids[6]

        // After a double episode comes the one after its last part; of two copies of it the
        // first by name wins.
        assertEquals(S1E3_HD.fileName, repo.nextEpisode(double)?.video?.fileName)
        // Another copy of the same episode is not "next".
        assertEquals(fourth, repo.nextEpisode(third)?.video?.id)
        assertEquals(fourth, repo.nextEpisode(thirdHd)?.video?.id)
        // Then the first episode of the next season.
        assertEquals(season2, repo.nextEpisode(fourth)?.video?.id)
        assertNull(repo.nextEpisode(season2))
        assertNull(repo.nextEpisode(otherShow))
        assertNull(repo.nextEpisode(movie))
        assertNull(repo.nextEpisode(-1))

        // A file that is gone is skipped.
        fx.folders.serve(TREE, S1E1E2, S2E1, S1E3, S1E3_HD, OTHER_SHOW, MOVIE)
        repo.scanSource(folderId)
        assertEquals(season2, repo.nextEpisode(third)?.video?.id)
    }

    private companion object {
        val TREE = "content://com.android.externalstorage.documents/tree/primary%3AVideos"

        val MOVIE = scannedVideo("$TREE/document/movie", "Inception (2010).mkv", "Movies")
        val EPISODE = scannedVideo("$TREE/document/office", "The.Office.S01E02.mkv", "TV/The Office/Season 1")
        val CLIP = scannedVideo("$TREE/document/clip", "VID_20240501_123456.mp4", "Camera", durationMs = 60_000L)

        val S1E1E2 = scannedVideo("$TREE/document/sev-1-1", "Severance.S01E01E02.mkv", "TV/Severance")
        val S1E3 = scannedVideo("$TREE/document/sev-1-3", "Severance.S01E03.mkv", "TV/Severance")
        val S1E3_HD = scannedVideo("$TREE/document/sev-1-3-hd", "Severance.S01E03.1080p.mkv", "TV/Severance")
        val S1E4 = scannedVideo("$TREE/document/sev-1-4", "Severance.S01E04.mkv", "TV/Severance")
        val S2E1 = scannedVideo("$TREE/document/sev-2-1", "Severance.S02E01.mkv", "TV/Severance")
        val OTHER_SHOW = scannedVideo("$TREE/document/foundation-1-1", "Foundation.S01E01.mkv", "TV/Foundation")
    }
}
