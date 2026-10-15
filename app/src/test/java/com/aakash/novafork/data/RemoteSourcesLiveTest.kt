package com.aakash.novafork.data

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * A media server's videos with the repository's background work running: the metadata pass leaves
 * them to their server, and playback progress is reported back to it. Real time, so runBlocking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RemoteSourcesLiveTest {
    private val live = LiveScope()
    private lateinit var fx: NetworkRepositoryFixture
    private val repo: LibraryRepositoryImpl get() = fx.repository

    @Before
    fun setUp() {
        validatedNetwork()
        fx = NetworkRepositoryFixture(live.scope)
    }

    @After
    fun tearDown() {
        try {
            live.close()
        } finally {
            if (::fx.isInitialized) fx.close()
        }
    }

    /** Robolectric's default network has no internet capability; the metadata pass waits for one. */
    private fun validatedNetwork() {
        val manager = requireNotNull(
            ApplicationProvider.getApplicationContext<Context>().getSystemService(ConnectivityManager::class.java),
        )
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(manager).setNetworkCapabilities(requireNotNull(manager.activeNetwork), capabilities)
    }

    /** Adds the Jellyfin server serving [videos] and waits for its scan; returns the source id. */
    private suspend fun addServer(vararg videos: ScannedVideo): Long {
        fx.servers.serve(SERVER, *videos)
        val id = repo.saveNetworkSource(null, NetworkSourceInput(SourceType.JELLYFIN, address = SERVER, username = "alice"))
        repo.scanSource(id)
        return id
    }

    @Test
    fun theMetadataPassLeavesServerVideosToTheirServer() = runBlocking<Unit> {
        fx.tmdb.route("search/movie", TmdbJson.inceptionSearch)
        fx.tmdb.route("movie/${TmdbJson.INCEPTION_ID}", TmdbJson.inception)
        // Both have names TMDB would match; only the one without server metadata is looked up.
        val id = addServer(MATRIX, INCEPTION)

        awaitUntil("Inception to be matched on TMDB") { fx.videoAt(INCEPTION.uri).matchState == MatchState.MATCHED }

        fx.videoAt(MATRIX.uri).let {
            assertEquals(MatchState.MATCHED, it.matchState)
            assertEquals(ServerMetadata.key(id, MATRIX_META), it.metadataKey)
        }
        assertEquals("The Matrix", repo.video(fx.videoAt(MATRIX.uri).id)?.meta?.title)
        assertEquals(1, fx.tmdb.requestCount("search/movie"))
        assertEquals(1, fx.tmdb.requestCount("movie/${TmdbJson.INCEPTION_ID}"))
        // Clearing TMDB data re-matches Inception only.
        repo.clearMetadata()
        awaitUntil("Inception to be matched again") { fx.tmdb.requestCount("search/movie") == 2 }
        awaitUntil("the pass to settle") { fx.videoAt(INCEPTION.uri).matchState == MatchState.MATCHED }
        assertEquals(ServerMetadata.key(id, MATRIX_META), fx.videoAt(MATRIX.uri).metadataKey)
        assertEquals(2, fx.tmdb.requestCount("search/movie"))
    }

    @Test
    fun progressIsReportedToTheVideosServer() = runBlocking<Unit> {
        val sourceId = addServer(MATRIX)
        val videoId = fx.videoAt(MATRIX.uri).id
        val reports = fx.reporter.reports

        repo.savePlayback(videoId, 60_000, RUNTIME_MS)
        awaitUntil("the progress report") { reports.size == 1 }
        reports.last().let {
            assertEquals(sourceId, it.sourceId)
            assertEquals(videoId, it.videoId)
            assertEquals(60_000L, it.positionMs)
            assertEquals(RUNTIME_MS, it.durationMs)
            assertFalse(it.finished)
            // The row as saved.
            assertEquals(60_000L, it.storedPositionMs)
            assertFalse(it.watched)
        }

        repo.savePlayback(videoId, RUNTIME_MS - 10_000, RUNTIME_MS)
        awaitUntil("the finishing report") { reports.size == 2 }
        reports.last().let {
            assertTrue(it.finished)
            assertTrue(it.watched)
            assertEquals(0L, it.storedPositionMs)
        }

        repo.setWatched(videoId, false)
        awaitUntil("the unwatched report") { reports.size == 3 }
        reports.last().let {
            assertFalse(it.finished)
            assertFalse(it.watched)
            assertEquals(0L, it.positionMs)
            // The duration the library knows.
            assertEquals(RUNTIME_MS, it.durationMs)
        }
        repo.setWatched(videoId, true)
        awaitUntil("the watched report") { reports.size == 4 }
        assertTrue(reports.last().finished)
    }

    @Test
    fun aFailingReportDoesNotStopTheNextOnes() = runBlocking<Unit> {
        addServer(MATRIX)
        val videoId = fx.videoAt(MATRIX.uri).id
        val reporter = fx.reporter

        reporter.failNext = IOException("Server unreachable")
        repo.savePlayback(videoId, 10_000, RUNTIME_MS)
        awaitUntil("the failed attempt") { reporter.attempts.get() == 1 }
        repo.savePlayback(videoId, 20_000, RUNTIME_MS)
        awaitUntil("the next report") { reporter.reports.size == 1 }
        assertEquals(20_000L, reporter.reports.last().positionMs)

        // A reporter's own timeout is a CancellationException; delivery goes on after it too.
        reporter.failNext = CancellationException("Timed out")
        repo.savePlayback(videoId, 30_000, RUNTIME_MS)
        awaitUntil("the timed-out attempt") { reporter.attempts.get() == 3 }
        repo.savePlayback(videoId, 40_000, RUNTIME_MS)
        awaitUntil("the report after it") { reporter.reports.size == 2 }
        assertEquals(40_000L, reporter.reports.last().positionMs)
    }

    @Test
    fun videosOfSourcesWithoutAReporterAreNotReported() = runBlocking<Unit> {
        addServer(MATRIX)
        val ftp = repo.saveNetworkSource(null, NetworkSourceInput(SourceType.FTP, address = "nas.local/Movies"))
        fx.servers.serve("ftp://nas.local/Movies", scannedVideo("ftp://nas.local/Movies/Heat.mkv", "Heat.mkv", ""))
        repo.scanSource(ftp)
        val heat = fx.videoAt("ftp://nas.local/Movies/Heat.mkv").id
        val matrix = fx.videoAt(MATRIX.uri).id

        repo.savePlayback(heat, 60_000, RUNTIME_MS)
        repo.savePlayback(matrix, 60_000, RUNTIME_MS)
        awaitUntil("the server video's report") { fx.reporter.reports.any { it.videoId == matrix } }
        repo.savePlayback(matrix, 70_000, RUNTIME_MS)
        awaitUntil("the next report") { fx.reporter.reports.any { it.positionMs == 70_000L } }

        assertTrue(fx.reporter.reports.none { it.videoId == heat })
        assertEquals(60_000L, fx.videoAt("ftp://nas.local/Movies/Heat.mkv").positionMs)
    }

    private companion object {
        const val SERVER = "http://jf.local:8096"
        const val RUNTIME_MS = 8_160_000L

        val MATRIX_META = ServerMetadata(type = ServerItemType.MOVIE, id = "m1", title = "The Matrix", year = 1999)
        val MATRIX = ScannedVideo(
            uri = "$SERVER/Videos/m1/stream?static=true",
            fileName = "The.Matrix.1999.1080p.mkv",
            folder = "Movies",
            parentFolders = listOf("Movies"),
            dateAdded = 1_000L,
            remoteId = "m1",
            serverMetadata = MATRIX_META,
        )
        val INCEPTION = ScannedVideo(
            uri = "$SERVER/Videos/i1/stream?static=true",
            fileName = "Inception (2010).mkv",
            folder = "Movies",
            parentFolders = listOf("Movies"),
            dateAdded = 1_000L,
            remoteId = "i1",
        )
    }
}
