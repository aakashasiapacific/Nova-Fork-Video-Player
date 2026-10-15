package com.aakash.novafork.data

import android.app.Application
import com.aakash.novafork.core.library.LibraryKeys
import com.aakash.novafork.core.tmdb.TmdbClient
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.MetadataEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Metadata a media server hands over with its files ([ScannedVideo.serverMetadata]): stored as the
 * videos' metadata, MATCHED, kept away from TMDB and from clearing TMDB data; Identify still wins.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ServerMetadataTest {
    private lateinit var fx: NetworkRepositoryFixture
    private val repo: LibraryRepositoryImpl get() = fx.repository
    private var serverId = 0L

    @Before
    fun setUp() = runTest {
        fx = NetworkRepositoryFixture()
        fx.tmdb.route("movie/${TmdbJson.INCEPTION_ID}", TmdbJson.inception)
        serverId = repo.saveNetworkSource(null, NetworkSourceInput(SourceType.JELLYFIN, name = "Home", address = SERVER, username = "alice"))
    }

    @After
    fun tearDown() {
        if (::fx.isInitialized) fx.close()
    }

    private suspend fun scan(vararg videos: ScannedVideo) {
        fx.servers.serve(SERVER, *videos)
        repo.scanSource(serverId)
    }

    private fun key(metadata: ServerMetadata) = ServerMetadata.key(serverId, metadata)

    @Test
    fun aMovieIsStoredAsTheServerDescribesIt() = runTest {
        scan(MATRIX)

        val video = fx.videoAt(MATRIX.uri)
        assertEquals(VideoKind.MOVIE, video.kind)
        assertEquals(MatchState.MATCHED, video.matchState)
        assertEquals("server:$serverId:movie:m1", video.metadataKey)
        assertEquals(key(MATRIX_META), video.metadataKey)
        assertNull(video.showKey)
        assertEquals("m1", video.remoteId)
        assertEquals("Movies", video.folder)

        val meta = requireNotNull(fx.database.metadataDao().get(key(MATRIX_META)))
        assertEquals("movie", meta.type)
        assertEquals("The Matrix", meta.title)
        assertEquals(1999, meta.year)
        assertEquals("A hacker learns the truth.", meta.overview)
        assertEquals(603, meta.tmdbId)
        assertEquals("Action, Science Fiction", meta.genres)
        assertEquals(136, meta.runtimeMin)
        assertEquals(8.2, requireNotNull(meta.rating), 0.001)
        // Full URLs, and the UI's URL helpers keep them as they are.
        assertEquals(MATRIX_POSTER, meta.posterPath)
        assertEquals(MATRIX_POSTER, TmdbClient.posterUrl(meta.posterPath))
        assertEquals("$SERVER/Items/m1/Images/Backdrop", TmdbClient.backdropUrl(meta.backdropPath))
        assertNull(meta.seasonPostersJson)
        assertEquals("The Matrix", repo.video(video.id)?.meta?.title)

        // Never a TMDB candidate, and never "restored" from TMDB.
        assertTrue(fx.database.videoDao().needingMatch(Long.MAX_VALUE).isEmpty())
        fx.database.metadataDao().clear()
        assertTrue(fx.database.videoDao().orphanMetadataKeys().isEmpty())
        assertTrue(fx.tmdb.requests.isEmpty())
    }

    @Test
    fun episodesTakeTheServersShowAndNumbering() = runTest {
        // File names that would parse as something else entirely: the server's numbers count.
        scan(SEV_1, SEV_2, SEV_S2)

        val showKey = "server:$serverId:tv:s1"
        for ((file, numbers) in listOf(SEV_1 to (1 to 1), SEV_2 to (1 to 2), SEV_S2 to (2 to 1))) {
            val episode = fx.videoAt(file.uri)
            assertEquals(VideoKind.EPISODE, episode.kind)
            assertEquals(MatchState.MATCHED, episode.matchState)
            assertEquals(showKey, episode.metadataKey)
            assertEquals(showKey, episode.showKey)
            assertEquals(numbers.first, episode.season)
            assertEquals(numbers.second, episode.episode)
        }
        fx.videoAt(SEV_2.uri).let {
            assertEquals(3, it.episodeEnd)
            assertEquals("Half Loop", it.episodeTitle)
            assertEquals("Mark goes to a dinner.", it.episodeOverview)
            assertEquals("$SERVER/Items/e2/Images/Primary", it.episodeStillPath)
            assertEquals("$SERVER/Items/e2/Images/Primary", TmdbClient.stillUrl(it.episodeStillPath))
            assertEquals("2022-02-18", it.episodeAirDate)
        }

        val show = requireNotNull(fx.database.metadataDao().get(showKey))
        assertEquals("tv", show.type)
        assertEquals("Severance", show.title)
        assertEquals(2022, show.year)
        assertEquals("Office workers.", show.overview)
        assertEquals(0, show.tmdbId)
        assertEquals(
            """{"1":"$SERVER/Items/season1/Images/Primary","2":"$SERVER/Items/season2/Images/Primary"}""",
            show.seasonPostersJson,
        )
        // One show, played in the server's order.
        assertEquals(SEV_2.uri, repo.nextEpisode(fx.videoAt(SEV_1.uri).id)?.video?.uri)
        assertEquals(listOf(SEV_1.uri, SEV_2.uri, SEV_S2.uri), repo.seasonQueue(showKey, null).map { fx.database.videoDao().byId(it)?.uri })
        assertTrue(fx.tmdb.requests.isEmpty())
    }

    @Test
    fun aRescanUpdatesWhatTheServerSaysAndDropsWhatItNoLongerSays() = runTest {
        scan(MATRIX, SEV_1)
        val matrixId = fx.videoAt(MATRIX.uri).id

        // The server corrected the title and lost the episode's metadata.
        scan(
            MATRIX.copy(serverMetadata = MATRIX_META.copy(title = "The Matrix (Remastered)")),
            SEV_1.copy(serverMetadata = null),
        )

        assertEquals(matrixId, fx.videoAt(MATRIX.uri).id)
        assertEquals("The Matrix (Remastered)", fx.database.metadataDao().get(key(MATRIX_META))?.title)
        assertEquals(MatchState.MATCHED, fx.videoAt(MATRIX.uri).matchState)
        // The file name and TMDB matching take over again.
        fx.videoAt(SEV_1.uri).let {
            assertFalse(ServerMetadata.isServerKey(it.metadataKey))
            assertFalse(ServerMetadata.isServerKey(it.showKey))
            assertNull(it.episodeTitle)
            assertTrue(it.matchState == MatchState.PENDING || it.matchState == MatchState.UNMATCHED)
        }
    }

    @Test
    fun identifyOutranksTheServerAndLastsThroughRescans() = runTest {
        scan(MATRIX)
        val id = fx.videoAt(MATRIX.uri).id

        repo.identify(id, TmdbSearchResult(TmdbJson.INCEPTION_ID, IdentifyType.MOVIE, "Inception", 2010, null, null))
        scan(MATRIX)

        fx.videoAt(MATRIX.uri).let {
            assertEquals(MatchState.MANUAL, it.matchState)
            assertEquals(LibraryKeys.movieKey(TmdbJson.INCEPTION_ID), it.metadataKey)
        }
        assertEquals("Inception", repo.video(id)?.meta?.title)
    }

    @Test
    fun clearingMetadataKeepsWhatServersDescribed() = runTest {
        // An absolute number in the file name ("- 30") that the server placed as S2E2.
        val frieren = ScannedVideo(
            uri = "$SERVER/Videos/f30/stream",
            fileName = "[Group] Frieren - 30 [1080p].mkv",
            folder = "Anime/Frieren",
            parentFolders = listOf("Frieren", "Anime"),
            dateAdded = 1_000L,
            serverMetadata = ServerMetadata(ServerItemType.EPISODE, id = "fr", title = "Frieren", season = 2, episode = 2),
        )
        scan(MATRIX, frieren)
        // A TMDB match of another source, which clearing must reset.
        val folderId = repo.addFolder(TREE, "Movies")
        fx.folders.serve(TREE, INCEPTION_FILE)
        repo.scanSource(folderId)
        val tmdbKey = LibraryKeys.movieKey(TmdbJson.INCEPTION_ID)
        fx.database.metadataDao().upsert(MetadataEntity(key = tmdbKey, tmdbId = TmdbJson.INCEPTION_ID, type = "movie", title = "Inception"))
        fx.database.videoDao().setMovieMatch(listOf(fx.videoAt(INCEPTION_FILE.uri).id), tmdbKey, 1L, "MOVIE", "Inception", 2010)
        assertEquals(MatchState.MATCHED, fx.videoAt(INCEPTION_FILE.uri).matchState)

        repo.clearMetadata()

        fx.videoAt(INCEPTION_FILE.uri).let {
            assertEquals(MatchState.PENDING, it.matchState)
            assertNull(it.metadataKey)
        }
        assertNull(fx.database.metadataDao().get(tmdbKey))
        fx.videoAt(MATRIX.uri).let {
            assertEquals(MatchState.MATCHED, it.matchState)
            assertEquals(key(MATRIX_META), it.metadataKey)
        }
        assertEquals("The Matrix", fx.database.metadataDao().get(key(MATRIX_META))?.title)
        fx.videoAt(frieren.uri).let {
            val showKey = "server:$serverId:tv:fr"
            assertEquals(MatchState.MATCHED, it.matchState)
            assertEquals(showKey, it.metadataKey)
            assertEquals(showKey, it.showKey)
            assertEquals(2, it.season)
            assertEquals(2, it.episode)
        }
    }

    @Test
    fun unusableMetadataIsIgnored() = runTest {
        scan(MATRIX.copy(serverMetadata = MATRIX_META.copy(title = " ")))

        // Parsed from its name and left for TMDB, like any file.
        val video = fx.videoAt(MATRIX.uri)
        assertFalse(ServerMetadata.isServerKey(video.metadataKey))
        assertEquals(VideoKind.MOVIE, video.kind)
        assertEquals("The Matrix", video.parsedTitle)
        assertEquals(MatchState.PENDING, video.matchState)
        assertNull(fx.database.metadataDao().get(key(MATRIX_META)))
    }

    @Test
    fun removingTheServerTakesItsMetadataAlong() = runTest {
        scan(MATRIX, SEV_1)

        repo.removeSource(serverId)

        assertNull(fx.database.metadataDao().get(key(MATRIX_META)))
        assertNull(fx.database.metadataDao().get("server:$serverId:tv:s1"))
    }

    private companion object {
        const val SERVER = "http://jf.local:8096"
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AMovies"
        const val MATRIX_POSTER = "$SERVER/Items/m1/Images/Primary"

        val MATRIX_META = ServerMetadata(
            type = ServerItemType.MOVIE,
            id = "m1",
            title = "The Matrix",
            year = 1999,
            overview = "A hacker learns the truth.",
            posterUrl = MATRIX_POSTER,
            backdropUrl = "$SERVER/Items/m1/Images/Backdrop",
            rating = 8.2,
            genres = listOf("Action", " Science Fiction "),
            runtimeMin = 136,
            providerIds = mapOf("Tmdb" to "603", "Imdb" to "tt0133093"),
        )
        val MATRIX = ScannedVideo(
            uri = "$SERVER/Videos/m1/stream?static=true",
            // A name TMDB matching would look up: it must not be.
            fileName = "The.Matrix.1999.1080p.BluRay.mkv",
            folder = "Movies",
            parentFolders = listOf("Movies"),
            dateAdded = 1_000L,
            remoteId = "m1",
            serverMetadata = MATRIX_META,
        )

        private fun severance(id: String, season: Int, episode: Int, fileName: String, meta: (ServerMetadata) -> ServerMetadata = { it }) =
            ScannedVideo(
                uri = "$SERVER/Videos/$id/stream?static=true",
                fileName = fileName,
                folder = "Shows/Severance/Season $season",
                parentFolders = listOf("Season $season", "Severance", "Shows"),
                dateAdded = 1_000L,
                remoteId = id,
                serverMetadata = meta(
                    ServerMetadata(
                        type = ServerItemType.EPISODE,
                        id = "s1",
                        title = "Severance",
                        year = 2022,
                        overview = "Office workers.",
                        season = season,
                        episode = episode,
                        seasonPosterUrl = "$SERVER/Items/season$season/Images/Primary",
                    ),
                ),
            )

        val SEV_1 = severance("e1", 1, 1, "sev-101.mkv")
        val SEV_2 = severance("e2", 1, 2, "sev-102.mkv") {
            it.copy(
                episodeEnd = 3,
                episodeTitle = "Half Loop",
                episodeOverview = "Mark goes to a dinner.",
                episodeStillUrl = "$SERVER/Items/e2/Images/Primary",
                episodeAirDate = "2022-02-18",
            )
        }
        val SEV_S2 = severance("e10", 2, 1, "sev-201.mkv")

        val INCEPTION_FILE = ScannedVideo(
            uri = "$TREE/document/inception",
            fileName = "Inception (2010).mkv",
            folder = "Movies",
            parentFolders = listOf("Movies"),
            dateAdded = 1_000L,
        )
    }
}
