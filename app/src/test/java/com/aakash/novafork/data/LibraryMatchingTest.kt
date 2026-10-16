package com.aakash.novafork.data

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.core.library.LibraryKeys
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Automatic matching: the repository runs with a live scope, so its background metadata pass
 * looks scanned files up on the fake TMDB. The tests wait for the database to settle (real time,
 * hence runBlocking rather than runTest's virtual clock).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LibraryMatchingTest {
    private val live = LiveScope()
    private lateinit var fx: RepositoryFixture
    private val repo: LibraryRepositoryImpl get() = fx.repository

    @Before
    fun setUp() {
        setNetwork(validated = true)
        fx = RepositoryFixture(live.scope)
        fx.tmdb.route("search/tv", TmdbJson.frierenSearch)
        fx.tmdb.route("tv/${TmdbJson.FRIEREN_ID}", TmdbJson.frieren)
        fx.tmdb.route("tv/${TmdbJson.FRIEREN_ID}/season/1", TmdbJson.season(1, 28) { "Frieren 1x$it" })
        fx.tmdb.route("tv/${TmdbJson.FRIEREN_ID}/season/2", TmdbJson.season(2, 10) { "Frieren 2x$it" })
    }

    @After
    fun tearDown() {
        try {
            live.close()
        } finally {
            if (::fx.isInitialized) fx.close()
        }
    }

    private fun connectivityManager(): ConnectivityManager =
        requireNotNull(ApplicationProvider.getApplicationContext<Context>().getSystemService(ConnectivityManager::class.java))

    /**
     * Robolectric's default network has no internet capability, and the metadata pass waits for
     * a validated one. Returns the capabilities now reported for the active network.
     */
    private fun setNetwork(validated: Boolean): NetworkCapabilities {
        val manager = connectivityManager()
        val network = requireNotNull(manager.activeNetwork)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (validated) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(manager).setNetworkCapabilities(network, capabilities)
        return capabilities
    }

    /** The network is validated again, and the system tells the registered callbacks so. */
    private fun networkReturns() {
        val capabilities = setNetwork(validated = true)
        val manager = connectivityManager()
        val network = requireNotNull(manager.activeNetwork)
        for (callback in shadowOf(manager).networkCallbacks.toList()) {
            callback.onCapabilitiesChanged(network, capabilities)
        }
    }

    /** Adds the folder source directly, so no background scan of it is started. Returns its id. */
    private suspend fun scanFolder(vararg videos: ScannedVideo): Long {
        val folderId = fx.database.sourceDao().insert(SourceEntity(type = SourceType.FOLDER, name = "Movies", location = TREE))
        fx.folders.serve(TREE, *videos)
        repo.scanSource(folderId)
        return folderId
    }

    /** Waits until no file of [videos] is waiting for a lookup any more; returns their rows. */
    private suspend fun awaitMatched(vararg videos: ScannedVideo): List<VideoEntity> {
        awaitUntil("${videos.map { it.fileName }} to leave PENDING") {
            videos.none { fx.videoAt(it.uri).matchState == MatchState.PENDING }
        }
        return videos.map { fx.videoAt(it.uri) }
    }

    /** The language the stored metadata was fetched in, as the repository recorded it. */
    private fun metadataLanguage(): String? =
        fx.context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getString(KEY_METADATA_LANGUAGE, null)

    @Test
    fun automaticMatchPlacesAbsoluteEpisodesIntoSeasons() = runBlocking<Unit> {
        // Served before the folder exists: the scan addFolder starts in the background sees them too.
        fx.folders.serve(TREE, FRIEREN_5, FRIEREN_30, FRIEREN_40)
        val folderId = repo.addFolder(TREE, "Anime")
        repo.scanSource(folderId)

        val (fifth, thirtieth, fortieth) = awaitMatched(FRIEREN_5, FRIEREN_30, FRIEREN_40)

        for (episode in listOf(fifth, thirtieth, fortieth)) {
            assertEquals(MatchState.MATCHED, episode.matchState)
            assertEquals(FRIEREN_KEY, episode.metadataKey)
            assertEquals(FRIEREN_KEY, episode.showKey)
            assertNotNull(episode.matchAttemptAt)
        }
        assertEquals(1, fifth.season)
        assertEquals(5, fifth.episode)
        assertEquals("Frieren 1x5", fifth.episodeTitle)
        // 28 episodes in season 1, specials not counted: 30 is S2E2.
        assertEquals(2, thirtieth.season)
        assertEquals(2, thirtieth.episode)
        assertEquals("Frieren 2x2", thirtieth.episodeTitle)
        assertEquals("/still-2-2.jpg", thirtieth.episodeStillPath)
        // Past the listed episodes the number stays absolute.
        assertNull(fortieth.season)
        assertEquals(40, fortieth.episode)
        assertNull(fortieth.episodeTitle)

        assertEquals("Frieren", fx.database.metadataDao().get(FRIEREN_KEY)?.title)
        assertEquals(
            FRIEREN_KEY,
            fx.database.matchCacheDao().get(LibraryKeys.matchQueryKey("tv", "Frieren", null))?.metadataKey,
        )
        // One search for the title, however many files it has.
        assertEquals(1, fx.tmdb.requestCount("search/tv"))
    }

    @Test
    fun titleTmdbDoesNotKnowIsMarkedUnmatched() = runBlocking<Unit> {
        fx.tmdb.route("search/movie", TmdbJson.NO_RESULTS)
        fx.folders.serve(TREE, UNKNOWN_MOVIE)
        val folderId = repo.addFolder(TREE, "Movies")
        repo.scanSource(folderId)

        val (movie) = awaitMatched(UNKNOWN_MOVIE)

        assertEquals(MatchState.UNMATCHED, movie.matchState)
        assertNull(movie.metadataKey)
        assertNotNull(movie.matchAttemptAt)
        // The miss is remembered, so the next pass does not ask again.
        val miss = fx.database.matchCacheDao().get(LibraryKeys.matchQueryKey("movie", "Unknown Film", 1999))
        assertNotNull(miss)
        assertNull(miss?.metadataKey)
    }

    @Test
    fun offlinePassWaitsForTheNetwork() = runBlocking<Unit> {
        setNetwork(validated = false)
        fx.tmdb.route("search/movie", TmdbJson.inceptionSearch)
        fx.tmdb.route("movie/${TmdbJson.INCEPTION_ID}", TmdbJson.inception)
        scanFolder(INCEPTION)

        // Offline a pass changes nothing, asks TMDB nothing and shows no error.
        delay(SETTLE_MS)
        fx.videoAt(INCEPTION.uri).let {
            assertEquals(MatchState.PENDING, it.matchState)
            assertNull(it.matchAttemptAt)
        }
        assertTrue(fx.tmdb.requests.isEmpty())
        assertNull(repo.scanState.value.lastError)

        // The network coming back starts the pass again; nothing else asks for one here.
        networkReturns()

        val (movie) = awaitMatched(INCEPTION)
        assertEquals(MatchState.MATCHED, movie.matchState)
        assertEquals(INCEPTION_KEY, movie.metadataKey)
        assertEquals("Inception", fx.database.metadataDao().get(INCEPTION_KEY)?.title)
    }

    @Test
    fun rejectedKeyStopsThePassUntilMatchingIsRetried() = runBlocking<Unit> {
        fx.tmdb.route("search/movie", TmdbJson.INVALID_KEY, code = 401)
        scanFolder(INCEPTION)

        awaitUntil("the key error") { repo.scanState.value.lastError == KEY_REJECTED }
        // Nothing is recorded against the title: a bad key says nothing about it.
        fx.videoAt(INCEPTION.uri).let {
            assertEquals(MatchState.PENDING, it.matchState)
            assertNull(it.matchAttemptAt)
        }
        assertNull(fx.database.matchCacheDao().get(LibraryKeys.matchQueryKey("movie", "Inception", 2010)))

        // A working key (Settings retries matching when the key changes).
        fx.tmdb.route("search/movie", TmdbJson.inceptionSearch)
        fx.tmdb.route("movie/${TmdbJson.INCEPTION_ID}", TmdbJson.inception)
        repo.retryMatching()

        val (movie) = awaitMatched(INCEPTION)
        assertEquals(MatchState.MATCHED, movie.matchState)
        assertEquals(INCEPTION_KEY, movie.metadataKey)
        // A pass that went through clears the error.
        awaitUntil("the key error to clear") { repo.scanState.value.lastError == null }
    }

    @Test
    fun unreachableTmdbStopsThePassUntilTheNextRefresh() = runBlocking<Unit> {
        fx.tmdb.route("search/movie", TmdbJson.UNAVAILABLE, code = 503)
        scanFolder(INCEPTION)

        awaitUntil("the TMDB error") { repo.scanState.value.lastError == UNREACHABLE }
        // An outage says nothing about the title either: no miss is recorded, it keeps waiting.
        fx.videoAt(INCEPTION.uri).let {
            assertEquals(MatchState.PENDING, it.matchState)
            assertNull(it.matchAttemptAt)
        }
        assertNull(fx.database.matchCacheDao().get(LibraryKeys.matchQueryKey("movie", "Inception", 2010)))

        // TMDB is back; the next refresh (here the launch one, which skips the folder scanned just
        // now) runs the lookups again.
        fx.tmdb.route("search/movie", TmdbJson.inceptionSearch)
        fx.tmdb.route("movie/${TmdbJson.INCEPTION_ID}", TmdbJson.inception)
        repo.refreshAll()

        val (movie) = awaitMatched(INCEPTION)
        assertEquals(MatchState.MATCHED, movie.matchState)
        assertEquals(INCEPTION_KEY, movie.metadataKey)
        assertEquals(1, fx.folders.scanCount)
        assertNull(repo.scanState.value.lastError)
    }

    @Test
    fun filesOfAnIdentifiedShowAddedLaterJoinIt() = runBlocking<Unit> {
        fx.tmdb.route("search/tv", TmdbJson.NO_RESULTS)
        fx.tmdb.route("tv/${TmdbJson.SEVERANCE_ID}", TmdbJson.severance)
        fx.tmdb.route("tv/${TmdbJson.SEVERANCE_ID}/season/1", TmdbJson.season(1, 9) { "Severance 1x$it" })
        val folderId = scanFolder(SEV_1, SEV_2)
        // TMDB finds nothing under the name in the files…
        awaitMatched(SEV_1, SEV_2).forEach { assertEquals(MatchState.UNMATCHED, it.matchState) }

        // …so the user picks the show.
        repo.identify(fx.videoAt(SEV_1.uri).id, severanceResult)
        fx.folders.serve(TREE, SEV_1, SEV_2, SEV_3)
        repo.scanSource(folderId)

        val (third) = awaitMatched(SEV_3)
        assertEquals(MatchState.MATCHED, third.matchState)
        assertEquals(SEVERANCE_KEY, third.metadataKey)
        assertEquals(SEVERANCE_KEY, third.showKey)
        assertEquals(1, third.season)
        assertEquals(3, third.episode)
        assertEquals("Severance 1x3", third.episodeTitle)
        // The earlier files stay the user's match.
        assertEquals(MatchState.MANUAL, fx.videoAt(SEV_2.uri).matchState)
        // The match cache answered for the new file: no second search.
        assertEquals(1, fx.tmdb.requestCount("search/tv"))
    }

    @Test
    fun clearingMetadataFetchesManualMatchesAgain() = runBlocking<Unit> {
        fx.tmdb.route("tv/${TmdbJson.SEVERANCE_ID}", TmdbJson.severance)
        fx.tmdb.route("tv/${TmdbJson.SEVERANCE_ID}/season/1", TmdbJson.season(1, 9) { "Severance 1x$it" })
        // A home video: nothing to look up automatically.
        scanFolder(CLIP)
        repo.identify(fx.videoAt(CLIP.uri).id, severanceResult)
        assertEquals("Severance 1x1", fx.videoAt(CLIP.uri).episodeTitle)

        // TMDB's episode data changed since (a corrected title, another language).
        fx.tmdb.route("tv/${TmdbJson.SEVERANCE_ID}/season/1", TmdbJson.season(1, 9) { "Good News About Hell $it" })
        repo.clearMetadata()

        awaitUntil("the show's data to come back") {
            fx.database.metadataDao().get(SEVERANCE_KEY) != null &&
                fx.videoAt(CLIP.uri).episodeTitle == "Good News About Hell 1"
        }
        val clip = fx.videoAt(CLIP.uri)
        assertEquals(MatchState.MANUAL, clip.matchState)
        assertEquals(SEVERANCE_KEY, clip.metadataKey)
        assertEquals(1, clip.season)
        assertEquals(1, clip.episode)
        assertEquals("Severance", fx.database.metadataDao().get(SEVERANCE_KEY)?.title)
        assertEquals(2, fx.tmdb.requestCount("tv/${TmdbJson.SEVERANCE_ID}"))
    }

    @Test
    fun newMetadataLanguageFetchesEverythingAgain() = runBlocking<Unit> {
        fx.tmdb.route("search/movie", TmdbJson.inceptionSearch)
        fx.tmdb.route("movie/${TmdbJson.INCEPTION_ID}", TmdbJson.inception)
        // On start the repository notes the language the library is (to be) fetched in.
        awaitUntil("the metadata language to be recorded") { metadataLanguage() == "en-US" }
        scanFolder(INCEPTION)
        assertEquals(MatchState.MATCHED, awaitMatched(INCEPTION).single().matchState)
        assertEquals(1, fx.tmdb.requestCount("movie/${TmdbJson.INCEPTION_ID}"))

        fx.settings.update { it.copy(tmdbLanguage = "de-DE") }

        awaitUntil("Inception to be fetched and matched again") {
            fx.tmdb.requestCount("movie/${TmdbJson.INCEPTION_ID}") == 2 &&
                fx.videoAt(INCEPTION.uri).matchState == MatchState.MATCHED
        }
        assertEquals(INCEPTION_KEY, fx.videoAt(INCEPTION.uri).metadataKey)
        assertEquals("Inception", fx.database.metadataDao().get(INCEPTION_KEY)?.title)
        awaitUntil("the new language to be recorded") { metadataLanguage() == "de-DE" }
    }

    private companion object {
        /** Ample time for a requested metadata pass to have run. */
        const val SETTLE_MS = 1_000L

        /** ScanState.lastError after TMDB answers 401, and after it can't be reached (MetadataEnricher). */
        const val KEY_REJECTED = "TMDB rejected the API key"
        const val UNREACHABLE = "Couldn't reach TMDB"

        /** Where LibraryRepositoryImpl records the language of the stored metadata. */
        const val STATE_PREFS = "library_state"
        const val KEY_METADATA_LANGUAGE = "metadata_language"

        val TREE = "content://com.android.externalstorage.documents/tree/primary%3AAnime"
        val FRIEREN_KEY = LibraryKeys.tvKey(TmdbJson.FRIEREN_ID)
        val SEVERANCE_KEY = LibraryKeys.tvKey(TmdbJson.SEVERANCE_ID)
        val INCEPTION_KEY = LibraryKeys.movieKey(TmdbJson.INCEPTION_ID)

        val severanceResult = TmdbSearchResult(TmdbJson.SEVERANCE_ID, IdentifyType.TV, "Severance", 2022, null, null)

        val FRIEREN_5 = scannedVideo("$TREE/document/frieren-5", "[SubsPlease] Frieren - 05 (1080p).mkv", "Anime/Frieren")
        val FRIEREN_30 = scannedVideo("$TREE/document/frieren-30", "[SubsPlease] Frieren - 30 (1080p).mkv", "Anime/Frieren")
        val FRIEREN_40 = scannedVideo("$TREE/document/frieren-40", "[SubsPlease] Frieren - 40 (1080p).mkv", "Anime/Frieren")
        val UNKNOWN_MOVIE = scannedVideo("$TREE/document/unknown", "Unknown Film (1999).mkv", "Movies")
        val INCEPTION = scannedVideo("$TREE/document/inception", "Inception (2010).mkv", "Movies")
        val CLIP = scannedVideo("$TREE/document/clip", "VID_20240501_123456.mp4", "Camera")
        val SEV_1 = scannedVideo("$TREE/document/sev-1", "Severance.S01E01.mkv", "TV/Severance")
        val SEV_2 = scannedVideo("$TREE/document/sev-2", "Severance.S01E02.mkv", "TV/Severance")
        val SEV_3 = scannedVideo("$TREE/document/sev-3", "Severance.S01E03.mkv", "TV/Severance")
    }
}
