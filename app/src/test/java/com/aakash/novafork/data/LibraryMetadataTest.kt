package com.aakash.novafork.data

import android.app.Application
import com.aakash.novafork.core.library.LibraryKeys
import com.aakash.novafork.data.db.MatchCacheEntity
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.MetadataEntity
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

/** Identify, clearing metadata and retrying matches, against a real Room database and a fake TMDB. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LibraryMetadataTest {
    private lateinit var fx: RepositoryFixture
    private val repo: LibraryRepositoryImpl get() = fx.repository

    @Before
    fun setUp() {
        fx = RepositoryFixture()
        fx.tmdb.route("tv/${TmdbJson.SEVERANCE_ID}", TmdbJson.severance)
        fx.tmdb.route("tv/${TmdbJson.SEVERANCE_ID}/season/1", TmdbJson.season(1, 9) { "Severance 1x$it" })
        fx.tmdb.route("tv/${TmdbJson.FRIEREN_ID}", TmdbJson.frieren)
        fx.tmdb.route("tv/${TmdbJson.FRIEREN_ID}/season/1", TmdbJson.season(1, 28) { "Frieren 1x$it" })
        fx.tmdb.route("tv/${TmdbJson.FRIEREN_ID}/season/2", TmdbJson.season(2, 10) { "Frieren 2x$it" })
        fx.tmdb.route("movie/${TmdbJson.INCEPTION_ID}", TmdbJson.inception)
    }

    @After
    fun tearDown() {
        if (::fx.isInitialized) fx.close()
    }

    /** Scans [videos] into one folder; returns the folder's source id. */
    private suspend fun scan(vararg videos: ScannedVideo): Long {
        val folderId = repo.addFolder(TREE, "Videos")
        fx.folders.serve(TREE, *videos)
        repo.scanSource(folderId)
        return folderId
    }

    @Test
    fun identifyingAnEpisodeMovesTheWholeShow() = runTest {
        val folderId = scan(SEV_1, SEV_2, SEV_3, FOUNDATION)
        // A file that is gone keeps its watch history and moves with the show too.
        fx.folders.serve(TREE, SEV_1, SEV_2, FOUNDATION)
        repo.scanSource(folderId)
        assertFalse(fx.videoAt(SEV_3.uri).present)

        repo.identify(fx.videoAt(SEV_2.uri).id, severanceResult)

        for ((video, number) in listOf(SEV_1 to 1, SEV_2 to 2, SEV_3 to 3)) {
            val episode = fx.videoAt(video.uri)
            assertEquals(VideoKind.EPISODE, episode.kind)
            assertEquals(MatchState.MANUAL, episode.matchState)
            assertEquals(SEVERANCE_KEY, episode.metadataKey)
            assertEquals(SEVERANCE_KEY, episode.showKey)
            assertEquals(1, episode.season)
            assertEquals(number, episode.episode)
            assertEquals("Severance 1x$number", episode.episodeTitle)
            assertEquals("Episode $number.", episode.episodeOverview)
            assertEquals("/still-1-$number.jpg", episode.episodeStillPath)
            assertEquals("2023-01-01", episode.episodeAirDate)
        }
        // Another show is left alone.
        val other = fx.videoAt(FOUNDATION.uri)
        assertEquals(LibraryKeys.showKey(null, "Foundation"), other.showKey)
        assertEquals(MatchState.PENDING, other.matchState)
        assertNull(other.metadataKey)

        val show = requireNotNull(fx.database.metadataDao().get(SEVERANCE_KEY))
        assertEquals("tv", show.type)
        assertEquals("Severance", show.title)
        assertEquals(2022, show.year)
        assertEquals("Drama", show.genres)
        assertEquals(50, show.runtimeMin)
        assertEquals("/severance.jpg", show.posterPath)
        assertEquals("""{"1":"/severance-s1.jpg","2":"/severance-s2.jpg"}""", show.seasonPostersJson)
        // Files of the show added later (same parsed title) find it through the match cache.
        assertEquals(
            SEVERANCE_KEY,
            fx.database.matchCacheDao().get(LibraryKeys.matchQueryKey("tv", "Severance", null))?.metadataKey,
        )
        // One season request serves every episode.
        assertEquals(1, fx.tmdb.requests.count { it == "tv/${TmdbJson.SEVERANCE_ID}/season/1" })
        // The show still plays in order under its new key.
        assertEquals(SEV_2.uri, repo.nextEpisode(fx.videoAt(SEV_1.uri).id)?.video?.uri)
    }

    @Test
    fun identifyKeepsNumbersTmdbHasNoEpisodesFor() = runTest {
        scan(SEV_1, SEV_DOUBLE, SEV_S3E1)

        // TMDB knows season 1 only: season 3 answers 404.
        repo.identify(fx.videoAt(SEV_S3E1.uri).id, severanceResult)

        fx.videoAt(SEV_S3E1.uri).let {
            assertEquals(MatchState.MANUAL, it.matchState)
            assertEquals(SEVERANCE_KEY, it.metadataKey)
            assertEquals(3, it.season)
            assertEquals(1, it.episode)
            assertNull(it.episodeTitle)
            assertNull(it.episodeStillPath)
        }
        // A double episode keeps its last part and gets the first part's data.
        fx.videoAt(SEV_DOUBLE.uri).let {
            assertEquals(MatchState.MANUAL, it.matchState)
            assertEquals(1, it.season)
            assertEquals(4, it.episode)
            assertEquals(5, it.episodeEnd)
            assertEquals("Severance 1x4", it.episodeTitle)
        }
        assertEquals("Severance 1x1", fx.videoAt(SEV_1.uri).episodeTitle)
        assertTrue(fx.tmdb.requests.contains("tv/${TmdbJson.SEVERANCE_ID}/season/3"))
        // In order through the gap: S1E1, S1E4-5, S3E1.
        assertEquals(SEV_DOUBLE.uri, repo.nextEpisode(fx.videoAt(SEV_1.uri).id)?.video?.uri)
        assertEquals(SEV_S3E1.uri, repo.nextEpisode(fx.videoAt(SEV_DOUBLE.uri).id)?.video?.uri)
    }

    @Test
    fun identifyingOneEpisodeAsAMovieTakesOnlyThatFile() = runTest {
        scan(SEV_1, SEV_2)

        repo.identify(fx.videoAt(SEV_2.uri).id, inceptionResult)

        fx.videoAt(SEV_2.uri).let {
            assertEquals(VideoKind.MOVIE, it.kind)
            assertEquals(MatchState.MANUAL, it.matchState)
            assertEquals(INCEPTION_KEY, it.metadataKey)
            assertNull(it.showKey)
            assertNull(it.season)
            assertNull(it.episode)
        }
        fx.videoAt(SEV_1.uri).let {
            assertEquals(VideoKind.EPISODE, it.kind)
            assertEquals(MatchState.PENDING, it.matchState)
            assertEquals(LibraryKeys.showKey(null, "Severance"), it.showKey)
        }
        assertNull(repo.nextEpisode(fx.videoAt(SEV_1.uri).id))
        assertNull(repo.nextEpisode(fx.videoAt(SEV_2.uri).id))
    }

    @Test
    fun identifyingAHomeVideoAsAMovie() = runTest {
        scan(CLIP, SEV_1)

        repo.identify(fx.videoAt(CLIP.uri).id, inceptionResult)

        val video = fx.videoAt(CLIP.uri)
        assertEquals(VideoKind.MOVIE, video.kind)
        assertEquals(MatchState.MANUAL, video.matchState)
        assertEquals(INCEPTION_KEY, video.metadataKey)
        assertNull(video.showKey)
        assertNull(video.season)
        assertNull(video.episode)
        val movie = requireNotNull(fx.database.metadataDao().get(INCEPTION_KEY))
        assertEquals("movie", movie.type)
        assertEquals("Inception", movie.title)
        assertEquals(2010, movie.year)
        assertEquals(148, movie.runtimeMin)
        assertEquals("Action, Science Fiction", movie.genres)
        assertEquals("Your mind is the scene of the crime.", movie.tagline)
        assertEquals(8.4, requireNotNull(movie.rating), 0.001)
        assertEquals(MatchState.PENDING, fx.videoAt(SEV_1.uri).matchState)
    }

    @Test
    fun identifyingAHomeVideoAsAShowMakesItTheFirstEpisode() = runTest {
        scan(CLIP, SEV_1)

        repo.identify(fx.videoAt(CLIP.uri).id, severanceResult)

        fx.videoAt(CLIP.uri).let {
            assertEquals(VideoKind.EPISODE, it.kind)
            assertEquals(MatchState.MANUAL, it.matchState)
            assertEquals(SEVERANCE_KEY, it.metadataKey)
            assertEquals(SEVERANCE_KEY, it.showKey)
            assertEquals(1, it.season)
            assertEquals(1, it.episode)
            assertNull(it.episodeEnd)
            assertEquals("Severance 1x1", it.episodeTitle)
        }
        // A home video has no show of its own to bring along: the scanned episode is left alone.
        fx.videoAt(SEV_1.uri).let {
            assertEquals(MatchState.PENDING, it.matchState)
            assertNull(it.metadataKey)
            assertEquals(LibraryKeys.showKey(null, "Severance"), it.showKey)
        }
        assertEquals("Severance", fx.database.metadataDao().get(SEVERANCE_KEY)?.title)
    }

    @Test
    fun absoluteEpisodeNumbersArePlacedIntoTheShowsSeasons() = runTest {
        scan(FRIEREN_5, FRIEREN_30, FRIEREN_40)
        // Absolute numbers are stored without a season until a match places them.
        fx.videoAt(FRIEREN_30.uri).let {
            assertEquals(VideoKind.EPISODE, it.kind)
            assertEquals("Frieren", it.parsedTitle)
            assertNull(it.season)
            assertEquals(30, it.episode)
            assertEquals(LibraryKeys.showKey(null, "Frieren"), it.showKey)
        }

        repo.identify(fx.videoAt(FRIEREN_30.uri).id, frierenResult)

        fx.videoAt(FRIEREN_5.uri).let {
            assertEquals(1, it.season)
            assertEquals(5, it.episode)
            assertEquals("Frieren 1x5", it.episodeTitle)
        }
        // Season 1 has 28 episodes (the specials don't count): 30 is the second of season 2.
        fx.videoAt(FRIEREN_30.uri).let {
            assertEquals(2, it.season)
            assertEquals(2, it.episode)
            assertEquals("Frieren 2x2", it.episodeTitle)
        }
        // Past the last listed episode the number stays absolute.
        fx.videoAt(FRIEREN_40.uri).let {
            assertNull(it.season)
            assertEquals(40, it.episode)
            assertNull(it.episodeTitle)
        }
        for (video in listOf(FRIEREN_5, FRIEREN_30, FRIEREN_40)) {
            val episode = fx.videoAt(video.uri)
            assertEquals(MatchState.MANUAL, episode.matchState)
            assertEquals(FRIEREN_KEY, episode.showKey)
            assertEquals(FRIEREN_KEY, episode.metadataKey)
        }
        assertFalse(fx.tmdb.requests.contains("tv/${TmdbJson.FRIEREN_ID}/season/0"))
    }

    @Test
    fun clearMetadataResetsEveryAutomaticMatch() = runTest {
        scan(INCEPTION, HEAT, SEV_1, SEV_2, FRIEREN_30, CLIP)
        val metadata = fx.database.metadataDao()
        metadata.upsert(MetadataEntity(key = INCEPTION_KEY, tmdbId = TmdbJson.INCEPTION_ID, type = "movie", title = "Inception"))
        metadata.upsert(MetadataEntity(key = HEAT_KEY, tmdbId = 949, type = "movie", title = "Heat"))
        metadata.upsert(MetadataEntity(key = SEVERANCE_KEY, tmdbId = TmdbJson.SEVERANCE_ID, type = "tv", title = "Severance"))
        metadata.upsert(MetadataEntity(key = FRIEREN_KEY, tmdbId = TmdbJson.FRIEREN_ID, type = "tv", title = "Frieren"))
        val movieQuery = LibraryKeys.matchQueryKey("movie", "Inception", 2010)
        val missQuery = LibraryKeys.matchQueryKey("movie", "Unknown Film", 1999)
        fx.database.matchCacheDao().put(MatchCacheEntity(query = movieQuery, metadataKey = INCEPTION_KEY, checkedAt = 1L))
        fx.database.matchCacheDao().put(MatchCacheEntity(query = missQuery, metadataKey = null, checkedAt = 1L))
        // Rows as automatic matching and Identify leave them.
        fx.edit(INCEPTION.uri) { it.copy(metadataKey = INCEPTION_KEY, matchState = MatchState.MATCHED, matchAttemptAt = 1L) }
        fx.edit(HEAT.uri) { it.copy(metadataKey = HEAT_KEY, matchState = MatchState.MANUAL, matchAttemptAt = 1L) }
        fx.edit(SEV_1.uri) {
            it.copy(
                metadataKey = SEVERANCE_KEY,
                showKey = SEVERANCE_KEY,
                episodeTitle = "Good News About Hell",
                episodeStillPath = "/still.jpg",
                matchState = MatchState.MATCHED,
                matchAttemptAt = 1L,
            )
        }
        fx.edit(SEV_2.uri) {
            it.copy(metadataKey = SEVERANCE_KEY, showKey = SEVERANCE_KEY, matchState = MatchState.MANUAL, matchAttemptAt = 1L)
        }
        fx.edit(FRIEREN_30.uri) {
            it.copy(
                metadataKey = FRIEREN_KEY,
                showKey = FRIEREN_KEY,
                season = 2,
                episode = 2,
                episodeTitle = "Frieren 2x2",
                matchState = MatchState.MATCHED,
                matchAttemptAt = 1L,
            )
        }

        repo.clearMetadata()

        for (key in listOf(INCEPTION_KEY, HEAT_KEY, SEVERANCE_KEY, FRIEREN_KEY)) {
            assertNull(key, fx.database.metadataDao().get(key))
        }
        assertNull(fx.database.matchCacheDao().get(movieQuery))
        assertNull(fx.database.matchCacheDao().get(missQuery))

        fx.videoAt(INCEPTION.uri).let {
            assertEquals(MatchState.PENDING, it.matchState)
            assertNull(it.metadataKey)
            assertNull(it.matchAttemptAt)
        }
        // Manual matches stay; the next pass fetches their data again.
        fx.videoAt(HEAT.uri).let {
            assertEquals(MatchState.MANUAL, it.matchState)
            assertEquals(HEAT_KEY, it.metadataKey)
        }
        fx.videoAt(SEV_2.uri).let {
            assertEquals(MatchState.MANUAL, it.matchState)
            assertEquals(SEVERANCE_KEY, it.showKey)
        }
        fx.videoAt(SEV_1.uri).let {
            assertEquals(MatchState.PENDING, it.matchState)
            assertNull(it.metadataKey)
            assertEquals(LibraryKeys.showKey(null, "Severance"), it.showKey)
            assertNull(it.episodeTitle)
            assertNull(it.episodeStillPath)
            assertEquals(1, it.season)
            assertEquals(1, it.episode)
        }
        // The season the old match placed the absolute number into is forgotten too.
        fx.videoAt(FRIEREN_30.uri).let {
            assertEquals(MatchState.PENDING, it.matchState)
            assertNull(it.season)
            assertEquals(30, it.episode)
            assertNull(it.episodeTitle)
            assertEquals(LibraryKeys.showKey(null, "Frieren"), it.showKey)
        }
        fx.videoAt(CLIP.uri).let {
            assertEquals(VideoKind.VIDEO, it.kind)
            assertEquals(MatchState.UNMATCHED, it.matchState)
        }
    }

    @Test
    fun retryMatchingRetriesMissesOnly() = runTest {
        scan(INCEPTION, HEAT, CLIP)
        val missQuery = LibraryKeys.matchQueryKey("movie", "Inception", 2010)
        val hitQuery = LibraryKeys.matchQueryKey("movie", "Heat", 1995)
        fx.database.matchCacheDao().put(MatchCacheEntity(query = missQuery, metadataKey = null, checkedAt = 1L))
        fx.database.matchCacheDao().put(MatchCacheEntity(query = hitQuery, metadataKey = HEAT_KEY, checkedAt = 1L))
        fx.edit(INCEPTION.uri) { it.copy(matchState = MatchState.UNMATCHED, matchAttemptAt = 1L) }
        fx.edit(HEAT.uri) { it.copy(metadataKey = HEAT_KEY, matchState = MatchState.MATCHED, matchAttemptAt = 1L) }

        repo.retryMatching()

        assertEquals(MatchState.PENDING, fx.videoAt(INCEPTION.uri).matchState)
        assertEquals(MatchState.MATCHED, fx.videoAt(HEAT.uri).matchState)
        assertEquals(MatchState.UNMATCHED, fx.videoAt(CLIP.uri).matchState)
        assertNull(fx.database.matchCacheDao().get(missQuery))
        assertEquals(HEAT_KEY, fx.database.matchCacheDao().get(hitQuery)?.metadataKey)
    }

    @Test
    fun searchRanksMoviesAndShowsTogether() = runTest {
        fx.tmdb.route(
            "search/movie",
            """{"results":[{"id":27205,"title":"Inception","release_date":"2010-07-15","popularity":80.0,"poster_path":"/inception.jpg"}]}""",
        )
        fx.tmdb.route(
            "search/tv",
            """{"results":[{"id":1,"name":"Inception: The Series","first_air_date":"2019-01-01","popularity":95.0}]}""",
        )

        val results = repo.searchTmdb("  Inception ", null)

        assertEquals(listOf(IdentifyType.TV, IdentifyType.MOVIE), results.map { it.type })
        assertEquals("Inception", results[1].title)
        assertEquals(2010, results[1].year)
        assertEquals("https://image.tmdb.org/t/p/w342/inception.jpg", results[1].posterUrl)
        assertEquals(listOf(27205), repo.searchTmdb("Inception", IdentifyType.MOVIE).map { it.tmdbId })
        assertTrue(repo.searchTmdb("   ", null).isEmpty())
    }

    private companion object {
        val TREE = "content://com.android.externalstorage.documents/tree/primary%3AVideos"

        val SEVERANCE_KEY = LibraryKeys.tvKey(TmdbJson.SEVERANCE_ID)
        val FRIEREN_KEY = LibraryKeys.tvKey(TmdbJson.FRIEREN_ID)
        val INCEPTION_KEY = LibraryKeys.movieKey(TmdbJson.INCEPTION_ID)
        val HEAT_KEY = LibraryKeys.movieKey(949)

        val severanceResult = TmdbSearchResult(TmdbJson.SEVERANCE_ID, IdentifyType.TV, "Severance", 2022, null, null)
        val frierenResult = TmdbSearchResult(TmdbJson.FRIEREN_ID, IdentifyType.TV, "Frieren", 2023, null, null)
        val inceptionResult = TmdbSearchResult(TmdbJson.INCEPTION_ID, IdentifyType.MOVIE, "Inception", 2010, null, null)

        val INCEPTION = scannedVideo("$TREE/document/inception", "Inception (2010).mkv", "Movies")
        val HEAT = scannedVideo("$TREE/document/heat", "Heat (1995).mkv", "Movies")
        val CLIP = scannedVideo("$TREE/document/clip", "VID_20240501_123456.mp4", "Camera")
        val SEV_1 = scannedVideo("$TREE/document/sev-1", "Severance.S01E01.mkv", "TV/Severance")
        val SEV_2 = scannedVideo("$TREE/document/sev-2", "Severance.S01E02.mkv", "TV/Severance")
        val SEV_3 = scannedVideo("$TREE/document/sev-3", "Severance.S01E03.mkv", "TV/Severance")
        val SEV_DOUBLE = scannedVideo("$TREE/document/sev-4-5", "Severance.S01E04E05.mkv", "TV/Severance")
        val SEV_S3E1 = scannedVideo("$TREE/document/sev-3-1", "Severance.S03E01.mkv", "TV/Severance")
        val FOUNDATION = scannedVideo("$TREE/document/foundation-1", "Foundation.S01E01.mkv", "TV/Foundation")
        val FRIEREN_5 = scannedVideo("$TREE/document/frieren-5", "[SubsPlease] Frieren - 05 (1080p).mkv", "Anime/Frieren")
        val FRIEREN_30 = scannedVideo("$TREE/document/frieren-30", "[SubsPlease] Frieren - 30 (1080p).mkv", "Anime/Frieren")
        val FRIEREN_40 = scannedVideo("$TREE/document/frieren-40", "[SubsPlease] Frieren - 40 (1080p).mkv", "Anime/Frieren")
    }
}
