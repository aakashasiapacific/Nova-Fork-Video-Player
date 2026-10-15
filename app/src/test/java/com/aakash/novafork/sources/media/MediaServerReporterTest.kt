package com.aakash.novafork.sources.media

import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** How the library's progress saves become playback sessions on a media server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaServerReporterTest {
    /** Every call the reporter made, as "start 1 @5000". */
    private class FakeServer(private val failStop: Boolean = false) : MediaServerPlayback {
        val calls = ArrayList<String>()

        override suspend fun start(source: SourceEntity, video: VideoEntity, positionMs: Long, durationMs: Long?) {
            calls += "start ${video.id} @$positionMs"
        }

        override suspend fun progress(source: SourceEntity, video: VideoEntity, positionMs: Long, durationMs: Long?) {
            calls += "progress ${video.id} @$positionMs"
        }

        override suspend fun stop(source: SourceEntity, video: VideoEntity, positionMs: Long, durationMs: Long?) {
            calls += "stop ${video.id} @$positionMs"
            if (failStop) throw IOException("server went away")
        }

        override suspend fun setWatched(source: SourceEntity, video: VideoEntity, watched: Boolean) {
            calls += "watched ${video.id} $watched"
        }
    }

    private val source = SourceEntity(id = 3, type = SourceType.JELLYFIN, name = "Jellyfin", location = "http://jf:8096")

    /** Wall clock of the saves: [VideoEntity.lastPlayedAt] is "now" for playback saves. */
    private val now = 1_700_000_000_000L

    private fun video(id: Long, played: Boolean = true, watched: Boolean = false) = VideoEntity(
        id = id,
        sourceId = source.id,
        uri = "http://jf:8096/Videos/item$id/stream?static=true",
        fileName = "Video $id.mkv",
        folder = "Movies",
        parsedTitle = "Video $id",
        lastPlayedAt = if (played) now else null,
        watched = watched,
        remoteId = "item$id",
    )

    private fun TestScope.reporter(server: FakeServer, scope: CoroutineScope = backgroundScope) = MediaServerReporter(
        servers = mapOf(SourceType.JELLYFIN to server, SourceType.EMBY to server),
        scope = scope,
        clock = { testScheduler.currentTime },
        wallClock = { now },
    )

    @Test
    fun savesBecomeAStartAndProgressEveryTenSeconds() = runTest {
        val server = FakeServer()
        val reporter = reporter(server)
        val video = video(1)
        reporter.report(source, video, 5_000, 60_000, finished = false)
        advanceTimeBy(4_000)
        reporter.report(source, video, 9_000, 60_000, finished = false)
        advanceTimeBy(6_000)
        reporter.report(source, video, 15_000, 60_000, finished = false)
        advanceTimeBy(10_000)
        reporter.report(source, video, 25_000, 60_000, finished = false)
        assertEquals(listOf("start 1 @5000", "progress 1 @15000", "progress 1 @25000"), server.calls)
    }

    @Test
    fun aFinishedVideoIsStoppedAndMarkedOnceThroughTheCredits() = runTest {
        val server = FakeServer()
        val reporter = reporter(server)
        val video = video(1)
        reporter.report(source, video, 50_000, 60_000, finished = false)
        reporter.report(source, video, 58_000, 60_000, finished = true)
        reporter.report(source, video, 59_000, 60_000, finished = true)
        reporter.report(source, video, 59_500, 60_000, finished = true)
        assertEquals(listOf("start 1 @50000", "stop 1 @58000", "watched 1 true"), server.calls)
    }

    @Test
    fun aFailedStopStillMarksTheVideoWatched() = runTest {
        val server = FakeServer(failStop = true)
        val reporter = reporter(server)
        val video = video(1)
        reporter.report(source, video, 50_000, 60_000, finished = false)
        reporter.report(source, video, 58_000, 60_000, finished = true)
        assertEquals(listOf("start 1 @50000", "stop 1 @58000", "watched 1 true"), server.calls)
    }

    @Test
    fun markingAnotherVideoLeavesThePlayingOneAlone() = runTest {
        val server = FakeServer()
        val reporter = reporter(server)
        reporter.report(source, video(1), 5_000, 60_000, finished = false)
        reporter.report(source, video(2, played = false, watched = true), 0, null, finished = true)
        reporter.report(source, video(3, played = false, watched = false), 0, null, finished = false)
        advanceTimeBy(10_000)
        reporter.report(source, video(1), 15_000, 60_000, finished = false)
        assertEquals(
            listOf("start 1 @5000", "watched 2 true", "watched 3 false", "progress 1 @15000"),
            server.calls,
        )
    }

    @Test
    fun anotherVideoStartingStopsThePreviousOneWhereItWas() = runTest {
        val server = FakeServer()
        val reporter = reporter(server)
        reporter.report(source, video(1), 5_000, 60_000, finished = false)
        advanceTimeBy(3_000)
        reporter.report(source, video(1), 8_000, 60_000, finished = false)
        reporter.report(source, video(2), 0, 90_000, finished = false)
        assertEquals(listOf("start 1 @5000", "stop 1 @8000", "start 2 @0"), server.calls)
    }

    @Test
    fun noSaveForAWhileStopsTheSessionAtTheLastPosition() = runTest {
        val server = FakeServer()
        val reporter = reporter(server)
        reporter.report(source, video(1), 5_000, 60_000, finished = false)
        advanceTimeBy(MediaServerReporter.IDLE_STOP_MS - 1)
        runCurrent()
        assertEquals(listOf("start 1 @5000"), server.calls)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("start 1 @5000", "stop 1 @5000"), server.calls)
        // Playing on after the stop starts a new session.
        reporter.report(source, video(1), 6_000, 60_000, finished = false)
        assertEquals("start 1 @6000", server.calls.last())
    }

    @Test
    fun sourcesWithoutAServerAreIgnored() = runTest {
        val server = FakeServer()
        val reporter = reporter(server)
        reporter.report(source.copy(type = SourceType.FTP), video(1), 5_000, 60_000, finished = false)
        assertEquals(emptyList<String>(), server.calls)
    }
}
