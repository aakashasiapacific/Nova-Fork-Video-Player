package com.aakash.novafork.core.tmdb

import com.aakash.novafork.core.parse.ParsedKind
import com.aakash.novafork.core.parse.ParsedName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class TmdbClientTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()
    private var credentials: TmdbCredentials? = TmdbCredentials(API_KEY)
    private var language = "en-US"
    private lateinit var client: TmdbClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/3/").toString()
        client = TmdbClient(
            http = http,
            credentials = { credentials },
            baseUrl = { baseUrl },
            language = { language },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun routes(handler: (RecordedRequest) -> MockResponse?) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handler(request) ?: json(EMPTY_PAGE)
        }
    }

    private fun recorded(): List<RecordedRequest> = List(server.requestCount) { server.takeRequest(1, TimeUnit.SECONDS)!! }

    @Test
    fun v3KeyIsSentAsQueryParameter() = runBlocking<Unit> {
        server.enqueue(json(EMPTY_PAGE))
        client.searchMovie("Dune")
        val request = server.takeRequest()
        assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        assertNull(request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun readAccessTokenIsSentAsBearerHeader() = runBlocking<Unit> {
        credentials = TmdbCredentials(BEARER)
        server.enqueue(json(EMPTY_PAGE))
        client.searchMovie("Dune")
        val request = server.takeRequest()
        assertEquals("Bearer $BEARER", request.getHeader("Authorization"))
        assertNull(request.requestUrl?.queryParameter("api_key"))
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun credentialTypeIsDetected() {
        assertTrue(TmdbCredentials(BEARER).isBearer)
        assertFalse(TmdbCredentials(API_KEY).isBearer)
    }

    @Test
    fun searchMovieSendsQueryYearAndLanguage() = runBlocking<Unit> {
        language = "de-DE"
        server.enqueue(json(EMPTY_PAGE))
        client.searchMovie("Dune: Part Two", 2024)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/3/search/movie", url.encodedPath)
        assertEquals("Dune: Part Two", url.queryParameter("query"))
        assertEquals("2024", url.queryParameter("year"))
        assertEquals("false", url.queryParameter("include_adult"))
        assertEquals("de-DE", url.queryParameter("language"))
        assertEquals("1", url.queryParameter("page"))
    }

    @Test
    fun searchMovieWithoutYearOmitsTheParameter() = runBlocking<Unit> {
        server.enqueue(json(EMPTY_PAGE))
        client.searchMovie("Dune")
        assertNull(server.takeRequest().requestUrl?.queryParameter("year"))
    }

    @Test
    fun blankQueryMakesNoRequest() = runBlocking<Unit> {
        assertTrue(client.searchMovie("  ").isEmpty())
        assertTrue(client.searchTv("").isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun decodesMovieSearchResults() = runBlocking<Unit> {
        server.enqueue(json(MOVIE_SEARCH))
        val results = client.searchMovie("The Matrix")
        assertEquals(2, results.size)
        val matrix = results[0]
        assertEquals(603, matrix.id)
        assertEquals("The Matrix", matrix.title)
        assertEquals("The Matrix", matrix.originalTitle)
        assertEquals(1999, matrix.year)
        assertEquals("/poster.jpg", matrix.posterPath)
        assertEquals("/back.jpg", matrix.backdropPath)
        assertEquals(8.2, matrix.voteAverage, 0.0)
        assertEquals(85.5, matrix.popularity, 0.0)
        val reloaded = results[1]
        assertNull(reloaded.originalTitle)
        assertNull(reloaded.year)
        assertNull(reloaded.posterPath)
        assertEquals(0.0, reloaded.voteAverage, 0.0)
    }

    @Test
    fun searchTvSendsFirstAirDateYear() = runBlocking<Unit> {
        server.enqueue(json(TV_SEARCH))
        val results = client.searchTv("The Office", 2005)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/3/search/tv", url.encodedPath)
        assertEquals("2005", url.queryParameter("first_air_date_year"))
        assertEquals("false", url.queryParameter("include_adult"))
        assertEquals("en-US", url.queryParameter("language"))
        assertEquals(listOf(2996, 2316), results.map { it.id })
        assertEquals("The Office", results[1].name)
        assertEquals(2005, results[1].year)
        assertEquals("/us.jpg", results[1].posterPath)
    }

    @Test
    fun decodesMovieDetails() = runBlocking<Unit> {
        server.enqueue(json(MOVIE_DETAILS))
        val movie = client.movie(603)
        assertEquals("/3/movie/603", server.takeRequest().requestUrl?.encodedPath)
        assertEquals("The Matrix", movie.title)
        assertEquals(136, movie.runtime)
        assertEquals(listOf("Action", "Science Fiction"), movie.genres.map { it.name })
        assertEquals("tt0133093", movie.imdbId)
        assertEquals("Welcome to the Real World.", movie.tagline)
        assertEquals(1999, movie.year)
    }

    @Test
    fun decodesTvDetails() = runBlocking<Unit> {
        server.enqueue(json(TV_DETAILS))
        val show = client.tv(2316)
        assertEquals("/3/tv/2316", server.takeRequest().requestUrl?.encodedPath)
        assertEquals("The Office", show.name)
        assertEquals(9, show.numberOfSeasons)
        assertEquals(listOf(22), show.episodeRunTime)
        assertEquals(listOf(0, 1), show.seasons.map { it.seasonNumber })
        assertEquals(6, show.seasons[1].episodeCount)
        assertEquals(2005, show.year)
    }

    @Test
    fun decodesSeasonDetails() = runBlocking<Unit> {
        server.enqueue(json(SEASON_DETAILS))
        val season = client.season(2316, 1)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/3/tv/2316/season/1", url.encodedPath)
        assertEquals("en-US", url.queryParameter("language"))
        assertEquals(1, season.seasonNumber)
        assertEquals(2, season.episodes.size)
        val pilot = season.episodes[0]
        assertEquals("Pilot", pilot.name)
        assertEquals(1, pilot.episodeNumber)
        assertEquals("/s1.jpg", pilot.stillPath)
        assertEquals(23, pilot.runtime)
        assertNull(season.episodes[1].stillPath)
        assertNull(season.episodes[1].runtime)
    }

    @Test
    fun unauthorizedBecomesTmdbExceptionWithStatusMessage() = runBlocking<Unit> {
        server.enqueue(json("""{"status_code":7,"status_message":"Invalid API key: You must be granted a valid key.","success":false}""", 401))
        try {
            client.movie(603)
            fail("Expected TmdbException")
        } catch (e: TmdbException) {
            assertEquals(401, e.code)
            assertEquals("Invalid API key: You must be granted a valid key.", e.message)
        }
    }

    @Test
    fun errorWithoutJsonBodyStillCarriesTheCode() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(503).setBody("<html>Service Unavailable</html>"))
        try {
            client.tv(1)
            fail("Expected TmdbException")
        } catch (e: TmdbException) {
            assertEquals(503, e.code)
            assertTrue(e.message.orEmpty().contains("503"))
        }
    }

    @Test
    fun malformedJsonBecomesIOException() = runBlocking<Unit> {
        server.enqueue(json("""{"id": "not a number"""))
        try {
            client.movie(603)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertFalse(e is TmdbException)
        }
    }

    @Test
    fun rateLimitIsRetriedOnceAfterRetryAfter() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1"))
        server.enqueue(json(MOVIE_DETAILS))
        val started = System.nanoTime()
        val movie = client.movie(603)
        val waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        assertEquals(603, movie.id)
        assertEquals(2, server.requestCount)
        assertTrue("waited $waitedMs ms", waitedMs >= 900)
    }

    @Test
    fun secondRateLimitFails() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0").setBody("""{"status_message":"Slow down"}"""))
        try {
            client.movie(603)
            fail("Expected TmdbException")
        } catch (e: TmdbException) {
            assertEquals(429, e.code)
            assertEquals("Slow down", e.message)
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun missingCredentialsDisableTheClient() = runBlocking<Unit> {
        credentials = null
        assertFalse(client.isEnabled)
        try {
            client.searchMovie("Dune")
            fail("Expected TmdbDisabledException")
        } catch (e: TmdbDisabledException) {
            assertEquals(0, server.requestCount)
        }
        val result = client.testCredentials()
        assertTrue(result.exceptionOrNull() is TmdbDisabledException)
    }

    @Test
    fun testCredentialsCallsConfiguration() = runBlocking<Unit> {
        server.enqueue(json("""{"images":{"base_url":"http://image.tmdb.org/t/p/"},"change_keys":[]}"""))
        assertTrue(client.testCredentials().isSuccess)
        assertEquals("/3/configuration", server.takeRequest().requestUrl?.encodedPath)

        server.enqueue(json("""{"status_code":7,"status_message":"Invalid API key","success":false}""", 401))
        val failure = client.testCredentials().exceptionOrNull()
        assertTrue(failure is TmdbException)
        assertEquals(401, (failure as TmdbException).code)
    }

    @Test
    fun cancellationCancelsTheHttpCall() = runBlocking<Unit> {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val search = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { client.searchMovie("Slow") }
        server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals(1, http.dispatcher.runningCallsCount())
        withTimeout(2_000) {
            search.cancel()
            search.join()
            // The OkHttp call itself must stop, not just the coroutine waiting for it.
            while (http.dispatcher.runningCallsCount() > 0) delay(10)
        }
        assertTrue(search.isCancelled)
    }

    @Test
    fun findMovieFallsBackToASearchWithoutYear() = runBlocking<Unit> {
        routes { request ->
            val url = request.requestUrl!!
            when {
                url.encodedPath == "/3/search/movie" && url.queryParameter("year") == null ->
                    json(page("""{"id":10,"title":"Some Film","release_date":"2018-11-01","popularity":3.0}"""))
                url.encodedPath == "/3/movie/10" -> json("""{"id":10,"title":"Some Film","release_date":"2018-11-01"}""")
                else -> null
            }
        }
        val movie = client.findMovie(ParsedName(ParsedKind.MOVIE, "Some Film", 2019))
        assertEquals(10, movie?.id)
        val requests = recorded()
        assertEquals(3, requests.size)
        assertEquals("2019", requests[0].requestUrl?.queryParameter("year"))
        assertNull(requests[1].requestUrl?.queryParameter("year"))
        assertEquals("/3/movie/10", requests[2].requestUrl?.encodedPath)
    }

    @Test
    fun findMoviePicksTheBestCandidateOfTheYearSearch() = runBlocking<Unit> {
        routes { request ->
            val url = request.requestUrl!!
            when (url.encodedPath) {
                "/3/search/movie" -> json(
                    page(
                        """{"id":1,"title":"Dune","release_date":"1984-12-14","popularity":40.0}""",
                        """{"id":2,"title":"Dune","release_date":"2021-09-15","popularity":300.0}""",
                    ),
                )
                "/3/movie/1" -> json("""{"id":1,"title":"Dune","release_date":"1984-12-14"}""")
                else -> null
            }
        }
        assertEquals(1, client.findMovie(ParsedName(ParsedKind.MOVIE, "Dune", 1984))?.id)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun findMovieReturnsNullWithoutAConfidentMatch() = runBlocking<Unit> {
        routes { request ->
            if (request.requestUrl?.encodedPath == "/3/search/movie") {
                json(page("""{"id":5,"title":"Interstellar","release_date":"2014-11-05"}"""))
            } else {
                null
            }
        }
        assertNull(client.findMovie(ParsedName(ParsedKind.MOVIE, "Inception", null)))
        assertTrue(recorded().none { it.requestUrl?.encodedPath?.startsWith("/3/movie/") == true })
    }

    @Test
    fun findMovieRetriesTheTitleBeforeASubtitle() = runBlocking<Unit> {
        routes { request ->
            val url = request.requestUrl!!
            when {
                url.encodedPath == "/3/search/movie" && url.queryParameter("query") == "Mission Impossible" ->
                    json(
                        page(
                            """{"id":954,"title":"Mission: Impossible","release_date":"1996-05-22","popularity":60.0}""",
                            """{"id":56292,"title":"Mission: Impossible - Ghost Protocol","release_date":"2011-12-07","popularity":50.0}""",
                        ),
                    )
                url.encodedPath == "/3/movie/56292" -> json("""{"id":56292,"title":"Mission: Impossible - Ghost Protocol"}""")
                else -> null
            }
        }
        val movie = client.findMovie(ParsedName(ParsedKind.MOVIE, "Mission Impossible - Ghost Protocol", null))
        assertEquals(56292, movie?.id)
    }

    @Test
    fun findShowDropsTheCountrySuffixAndPrefersThatCountry() = runBlocking<Unit> {
        routes { request ->
            val url = request.requestUrl!!
            when {
                url.encodedPath == "/3/search/tv" && url.queryParameter("query") == "The Office" -> json(TV_SEARCH)
                url.encodedPath == "/3/tv/2316" -> json(TV_DETAILS)
                url.encodedPath == "/3/tv/2996" -> json("""{"id":2996,"name":"The Office","first_air_date":"2001-07-09"}""")
                else -> null
            }
        }
        val us = client.findShow(ParsedName(ParsedKind.EPISODE, "The Office US", season = 2, episode = 3))
        assertEquals(2316, us?.id)
        val queries = recorded().map { it.requestUrl?.queryParameter("query") }
        assertEquals(listOf("The Office US", "The Office", null), queries)

        val uk = client.findShow(ParsedName(ParsedKind.EPISODE, "The Office UK", season = 1, episode = 1))
        assertEquals(2996, uk?.id)
    }

    @Test
    fun findShowUsesTheYearFirst() = runBlocking<Unit> {
        routes { request ->
            val url = request.requestUrl!!
            when {
                url.encodedPath == "/3/search/tv" && url.queryParameter("first_air_date_year") == "2005" ->
                    json(page("""{"id":2316,"name":"The Office","first_air_date":"2005-03-24","origin_country":["US"]}"""))
                url.encodedPath == "/3/tv/2316" -> json(TV_DETAILS)
                else -> null
            }
        }
        assertEquals(2316, client.findShow(ParsedName(ParsedKind.EPISODE, "The Office", 2005, season = 1, episode = 1))?.id)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun imageUrlHelpers() {
        assertEquals("https://image.tmdb.org/t/p/w500/abc.jpg", TmdbClient.posterUrl("/abc.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w342/abc.jpg", TmdbClient.posterUrl("/abc.jpg", "w342"))
        assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", TmdbClient.backdropUrl("/b.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w780/s.jpg", TmdbClient.stillUrl("/s.jpg"))
        assertEquals("https://image.tmdb.org/t/p/original/x.png", TmdbClient.imageUrl("x.png", "original"))
        assertNull(TmdbClient.posterUrl(null))
        assertNull(TmdbClient.backdropUrl(""))
        assertNull(TmdbClient.stillUrl("  "))
    }

    private companion object {
        const val API_KEY = "0123456789abcdef0123456789abcdef"
        val BEARER = "eyJhbGciOiJIUzI1NiJ9." + "a".repeat(80) + ".signature"

        const val EMPTY_PAGE = """{"page":1,"results":[],"total_pages":0,"total_results":0}"""

        fun page(vararg results: String) =
            """{"page":1,"results":[${results.joinToString(",")}],"total_pages":1,"total_results":${results.size}}"""

        val MOVIE_SEARCH = page(
            """{"adult":false,"backdrop_path":"/back.jpg","genre_ids":[28,878],"id":603,"original_language":"en",
               "original_title":"The Matrix","overview":"A hacker learns the truth.","popularity":85.5,
               "poster_path":"/poster.jpg","release_date":"1999-03-31","title":"The Matrix","video":false,
               "vote_average":8.2,"vote_count":25000}""",
            """{"id":604,"title":"The Matrix Reloaded","original_title":null,"release_date":"","poster_path":null,
               "popularity":40.1,"vote_average":null}""",
        )

        val TV_SEARCH = page(
            """{"id":2996,"name":"The Office","original_name":"The Office","first_air_date":"2001-07-09",
               "origin_country":["GB"],"popularity":80.0,"poster_path":"/uk.jpg"}""",
            """{"id":2316,"name":"The Office","original_name":"The Office","first_air_date":"2005-03-24",
               "origin_country":["US"],"popularity":30.0,"poster_path":"/us.jpg"}""",
        )

        const val MOVIE_DETAILS = """{"id":603,"title":"The Matrix","original_title":"The Matrix",
            "release_date":"1999-03-31","runtime":136,"genres":[{"id":28,"name":"Action"},{"id":878,"name":"Science Fiction"}],
            "imdb_id":"tt0133093","tagline":"Welcome to the Real World.","vote_average":8.2,
            "poster_path":"/p.jpg","backdrop_path":"/b.jpg","overview":"A hacker learns the truth.",
            "production_companies":[{"id":79,"name":"Village Roadshow"}]}"""

        const val TV_DETAILS = """{"id":2316,"name":"The Office","original_name":"The Office",
            "first_air_date":"2005-03-24","number_of_seasons":9,"episode_run_time":[22],
            "genres":[{"id":35,"name":"Comedy"}],"vote_average":8.6,
            "seasons":[{"season_number":0,"name":"Specials","episode_count":29,"air_date":"2007-09-27"},
                       {"season_number":1,"name":"Season 1","episode_count":6,"poster_path":"/s1.jpg","air_date":"2005-03-24"}]}"""

        const val SEASON_DETAILS = """{"_id":"5256c89f","air_date":"2005-03-24","name":"Season 1","overview":"",
            "id":7848,"poster_path":"/s1.jpg","season_number":1,"episodes":[
            {"air_date":"2005-03-24","episode_number":1,"id":1,"name":"Pilot","overview":"The premiere.",
             "production_code":"","runtime":23,"season_number":1,"still_path":"/s1.jpg","vote_average":7.5,
             "crew":[],"guest_stars":[]},
            {"air_date":"2005-03-29","episode_number":2,"id":2,"name":"Diversity Day","season_number":1,
             "still_path":null,"runtime":null}]}"""
    }
}
