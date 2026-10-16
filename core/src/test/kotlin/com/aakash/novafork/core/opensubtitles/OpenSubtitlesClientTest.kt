package com.aakash.novafork.core.opensubtitles

import com.aakash.novafork.core.opensubtitles.OpenSubtitlesException.Reason
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.TimeUnit

class OpenSubtitlesClientTest {
    private lateinit var server: MockWebServer
    private var key: String? = API_KEY
    private var account: OpenSubtitlesLogin? = null
    private var now = 1_700_000_000_000L

    /** Like the app's shared client, which sets its own User-Agent on every request. */
    private val http = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", "NovaFork/1.0").build()) })
        .build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(retryDelaysMs: List<Long> = listOf(10L, 10L)) = OpenSubtitlesClient(
        http = http,
        apiKey = { key },
        login = { account },
        appVersion = "1.2.3",
        baseUrl = server.url("/api/v1/").toString(),
        retryDelaysMs = retryDelaysMs,
        rateLimitRequests = 0,
        clock = { now },
    )

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun routes(handler: (RecordedRequest) -> MockResponse?) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handler(request) ?: json(EMPTY)
        }
    }

    private fun recorded(): List<RecordedRequest> = List(server.requestCount) { server.takeRequest(1, TimeUnit.SECONDS)!! }

    private inline fun expectFailure(reason: Reason, block: () -> Unit): OpenSubtitlesException {
        try {
            block()
        } catch (e: OpenSubtitlesException) {
            assertEquals(e.message, reason, e.reason)
            return e
        }
        fail("Expected $reason")
        error("unreachable")
    }

    @Test
    fun searchSendsKeyUserAgentAndSortedLowerCaseParameters() = runBlocking<Unit> {
        server.enqueue(json(RESULTS))
        val results = client().search(
            SubtitleSearchRequest(
                languages = listOf("pt-BR", "en", "fr"),
                movieHash = "8E245D9679D31E12",
                tmdbId = 438631,
                title = "Dune",
                year = 2021,
            ),
        )
        val request = server.takeRequest()
        assertEquals(API_KEY, request.getHeader("Api-Key"))
        assertEquals("NovaFork v1.2.3", request.getHeader("User-Agent"))
        assertEquals("application/json", request.getHeader("Accept"))
        assertNull(request.getHeader("Authorization"))
        val url = request.requestUrl!!
        assertEquals("/api/v1/subtitles", url.encodedPath)
        assertEquals(listOf("languages", "moviehash", "order_by", "tmdb_id"), url.queryParameterNames.toList())
        assertEquals("en,fr,pt-br", url.queryParameter("languages"))
        assertEquals("8e245d9679d31e12", url.queryParameter("moviehash"))
        assertEquals("download_count", url.queryParameter("order_by"))
        assertEquals("438631", url.queryParameter("tmdb_id"))
        assertEquals(2, results.size)
    }

    @Test
    fun resultsAreParsed() = runBlocking<Unit> {
        server.enqueue(json(RESULTS))
        val results = client().search(SubtitleSearchRequest(tmdbId = 438631))
        val first = results[0]
        assertEquals("6224976", first.id)
        assertEquals(6994430L, first.fileId)
        assertEquals("Dune.2021.1080p.WEBRip.x264-RARBG.srt", first.fileName)
        assertEquals("en", first.language)
        assertEquals("Dune.2021.1080p.WEBRip.x264-RARBG", first.release)
        assertEquals(18946, first.downloadCount)
        assertFalse(first.hearingImpaired)
        assertTrue(first.movieHashMatch)
        assertEquals(23.976, first.fps!!, 0.0001)
        assertFalse(first.aiTranslated)
        assertFalse(first.machineTranslated)

        val second = results[1]
        assertEquals("pt-br", second.language)
        // No release name: the file name stands in. fps 0 = unknown.
        assertEquals("dune-ptbr.srt", second.release)
        assertTrue(second.hearingImpaired)
        assertFalse(second.movieHashMatch)
        assertNull(second.fps)
        assertTrue(second.machineTranslated)
        // The second CD's file is not the one offered.
        assertEquals(7000001L, second.fileId)
    }

    @Test
    fun entriesWithoutFilesAreSkipped() = runBlocking<Unit> {
        server.enqueue(
            json("""{"data":[{"id":"1","attributes":{"language":"en","files":[]}},{"id":"2","attributes":{"language":"en","files":[{"file_id":22}]}}]}"""),
        )
        val results = client().search(SubtitleSearchRequest(tmdbId = 1))
        assertEquals(listOf("2"), results.map { it.id })
        assertEquals("", results[0].release)
    }

    @Test
    fun episodesUseTheShowIdsAndNumbers() = runBlocking<Unit> {
        server.enqueue(json(RESULTS))
        client().search(SubtitleSearchRequest(languages = listOf("en"), tmdbId = 95396, season = 2, episode = 3, year = 2022))
        val url = server.takeRequest().requestUrl!!
        assertEquals(listOf("episode_number", "languages", "order_by", "parent_tmdb_id", "season_number"), url.queryParameterNames.toList())
        assertEquals("95396", url.queryParameter("parent_tmdb_id"))
        assertEquals("2", url.queryParameter("season_number"))
        assertEquals("3", url.queryParameter("episode_number"))
        assertNull(url.queryParameter("year"))
    }

    @Test
    fun fallsBackFromIdsToTheHashToTheTitle() = runBlocking<Unit> {
        routes { request ->
            if (request.requestUrl?.queryParameter("query") != null) json(RESULTS) else json(EMPTY)
        }
        val results = client().search(
            SubtitleSearchRequest(
                languages = listOf("en"),
                movieHash = "0123456789abcdef",
                tmdbId = 1,
                imdbId = "tt0012345",
                title = "Dune: Part.Two",
                year = 2024,
            ),
        )
        assertEquals(2, results.size)
        val urls = recorded().map { it.requestUrl!! }
        assertEquals(5, urls.size)
        assertEquals(listOf("languages", "moviehash", "order_by", "tmdb_id"), urls[0].queryParameterNames.toList())
        assertEquals("1", urls[0].queryParameter("tmdb_id"))
        assertEquals("12345", urls[1].queryParameter("imdb_id"))
        assertEquals("0123456789abcdef", urls[1].queryParameter("moviehash"))
        assertEquals(listOf("languages", "moviehash", "order_by"), urls[2].queryParameterNames.toList())
        // In case the hash narrowed the answer: the TMDB id once more without it.
        assertEquals(listOf("languages", "order_by", "tmdb_id"), urls[3].queryParameterNames.toList())
        assertEquals(listOf("languages", "order_by", "query", "year"), urls[4].queryParameterNames.toList())
        assertEquals("dune: part two", urls[4].queryParameter("query"))
        assertEquals("2024", urls[4].queryParameter("year"))
    }

    @Test
    fun withoutAHashTheIdsThenTheTitleAreTried() = runBlocking<Unit> {
        routes { json(EMPTY) }
        client().search(SubtitleSearchRequest(tmdbId = 7, title = "Heat", year = 1995))
        val urls = recorded().map { it.requestUrl!! }
        assertEquals(listOf("7", null), urls.map { it.queryParameter("tmdb_id") })
        assertEquals(listOf(null, "heat"), urls.map { it.queryParameter("query") })
    }

    @Test
    fun stopsAtTheFirstTryThatFindsSomething() = runBlocking<Unit> {
        routes { json(RESULTS) }
        client().search(SubtitleSearchRequest(tmdbId = 1, title = "Dune"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun nothingToSearchWithSendsNothing() = runBlocking<Unit> {
        assertEquals(emptyList<SubtitleResult>(), client().search(SubtitleSearchRequest(languages = listOf("en"), title = "  ")))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun invalidHashIsLeftOut() = runBlocking<Unit> {
        server.enqueue(json(EMPTY))
        client().search(SubtitleSearchRequest(movieHash = "xyz", title = "Dune"))
        assertNull(server.takeRequest().requestUrl!!.queryParameter("moviehash"))
    }

    @Test
    fun missingKeyFailsWithoutARequest() = runBlocking<Unit> {
        key = " "
        expectFailure(Reason.NO_KEY) { client().search(SubtitleSearchRequest(tmdbId = 1)) }
        assertEquals(0, server.requestCount)
        assertFalse(client().hasKey)
    }

    @Test
    fun refusedKeyIsReported() = runBlocking<Unit> {
        server.enqueue(json("""{"message":"You cannot consume this service","status":403}""", code = 403))
        val error = expectFailure(Reason.BAD_KEY) { client().search(SubtitleSearchRequest(tmdbId = 1)) }
        assertEquals(OpenSubtitlesException.BAD_KEY_MESSAGE, error.message)
        assertEquals(403, error.code)
    }

    @Test
    fun serverErrorsAreRetriedThenReported() = runBlocking<Unit> {
        repeat(3) { server.enqueue(json("""{"message":"oops"}""", code = 503)) }
        val error = expectFailure(Reason.SERVER) { client().search(SubtitleSearchRequest(tmdbId = 1)) }
        assertEquals(3, server.requestCount)
        assertTrue(error.message!!.contains("503"))
    }

    @Test
    fun aServerErrorThatGoesAwayIsHidden() = runBlocking<Unit> {
        server.enqueue(json("{}", code = 502))
        server.enqueue(json(RESULTS))
        assertEquals(2, client().search(SubtitleSearchRequest(tmdbId = 1)).size)
    }

    @Test
    fun networkFailuresBecomeReadable() = runBlocking<Unit> {
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
        val error = expectFailure(Reason.NETWORK) { client().search(SubtitleSearchRequest(tmdbId = 1)) }
        assertEquals(OpenSubtitlesException.NETWORK_MESSAGE, error.message)
    }

    @Test
    fun throttledRequestIsRetriedOnceAfterRetryAfter() = runBlocking<Unit> {
        server.enqueue(json("""{"message":"Throttle limit reached"}""", code = 429).setHeader("Retry-After", "0"))
        server.enqueue(json(RESULTS))
        assertEquals(2, client().search(SubtitleSearchRequest(tmdbId = 1)).size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun stillThrottledIsReported() = runBlocking<Unit> {
        repeat(2) { server.enqueue(json("""{"message":"Throttle limit reached"}""", code = 429).setHeader("Retry-After", "0")) }
        expectFailure(Reason.RATE_LIMITED) { client().search(SubtitleSearchRequest(tmdbId = 1)) }
    }

    @Test
    fun otherErrorsCarryTheServerMessage() = runBlocking<Unit> {
        server.enqueue(json("""{"errors":["Invalid languages"],"status":400}""", code = 400))
        val error = expectFailure(Reason.HTTP) { client().search(SubtitleSearchRequest(tmdbId = 1)) }
        assertEquals("OpenSubtitles: Invalid languages", error.message)
    }

    @Test
    fun anonymousDownloadFetchesTheLinkWithoutTheKey() = runBlocking<Unit> {
        val fileUrl = server.url("/download/abc/subfile/dune.srt").toString()
        routes { request ->
            when (request.requestUrl?.encodedPath) {
                "/api/v1/download" -> json(
                    """{"link":"$fileUrl","file_name":"dune.srt","requests":3,"remaining":97,
                       "reset_time":"07 hours and 30 minutes","reset_time_utc":"2022-04-08T13:03:16.000Z"}""",
                )
                "/download/abc/subfile/dune.srt" -> MockResponse().setBody("1\n00:00:01,000 --> 00:00:02,000\nHello\n")
                else -> null
            }
        }
        val subtitle = client().download(6994430)
        assertEquals("dune.srt", subtitle.fileName)
        assertArrayEquals("1\n00:00:01,000 --> 00:00:02,000\nHello\n".toByteArray(), subtitle.bytes)
        assertEquals(97, subtitle.remaining)
        assertEquals(Instant.parse("2022-04-08T13:03:16Z"), subtitle.resetAt)

        val (post, file) = recorded()
        assertEquals("POST", post.method)
        assertEquals("""{"file_id":6994430}""", post.body.readUtf8())
        assertTrue(post.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals(API_KEY, post.getHeader("Api-Key"))
        assertNull(post.getHeader("Authorization"))
        assertEquals("GET", file.method)
        assertNull(file.getHeader("Api-Key"))
        assertEquals("NovaFork v1.2.3", file.getHeader("User-Agent"))
    }

    @Test
    fun quotaReachedNamesTheResetTime() = runBlocking<Unit> {
        server.enqueue(
            json(
                """{"requests":21,"remaining":-1,"message":"You have downloaded your allowed 20 subtitles for 24h.",
                   "reset_time":"13 hours and 56 minutes","reset_time_utc":"2023-11-15T06:25:08.000Z"}""",
                code = 406,
            ),
        )
        val error = expectFailure(Reason.QUOTA) { client().download(1) }
        assertEquals(Instant.parse("2023-11-15T06:25:08Z"), error.resetAt)
        assertEquals("13 hours and 56 minutes", error.resetIn)
        assertTrue(error.message!!, error.message!!.startsWith("Download limit reached, resets at "))
    }

    @Test
    fun throttledDownloadWithQuotaDetailsIsTheQuota() = runBlocking<Unit> {
        repeat(2) {
            server.enqueue(json("""{"remaining":0,"reset_time":"5 hours"}""", code = 429).setHeader("Retry-After", "0"))
        }
        val error = expectFailure(Reason.QUOTA) { client().download(1) }
        assertEquals("Download limit reached, resets in 5 hours", error.message)
    }

    @Test
    fun quotaMessageFormats() {
        val now = Instant.parse("2024-05-01T10:00:00Z")
        // Newer JDKs put a narrow no-break space before "PM".
        fun plain(text: String) = text.replace('\u202f', ' ')
        assertEquals(
            "Download limit reached, resets at 1:03 PM",
            plain(OpenSubtitlesException.quotaMessage(Instant.parse("2024-05-01T13:03:16Z"), "3 hours", now, ZoneOffset.UTC, Locale.US)),
        )
        assertTrue(
            OpenSubtitlesException.quotaMessage(Instant.parse("2024-05-03T13:03:16Z"), null, now, ZoneOffset.UTC, Locale.US)
                .startsWith("Download limit reached, resets at 5/3/24"),
        )
        assertEquals(
            "Download limit reached, resets in 3 hours",
            OpenSubtitlesException.quotaMessage(Instant.parse("2024-05-01T09:00:00Z"), "3 hours.", now, ZoneOffset.UTC, Locale.US),
        )
        assertEquals("Download limit reached. Try again tomorrow.", OpenSubtitlesException.quotaMessage(null, " ", now))
    }

    @Test
    fun loginTokenIsUsedForDownloadsAndKept() = runBlocking<Unit> {
        account = OpenSubtitlesLogin("reader", "secret")
        val vipBase = server.url("/vip/api/v1").toString()
        val fileUrl = server.url("/file.srt").toString()
        routes { request ->
            when (request.requestUrl?.encodedPath) {
                "/api/v1/login" -> json("""{"user":{"allowed_downloads":100},"base_url":"$vipBase","token":"tok-1","status":200}""")
                "/vip/api/v1/download" -> json("""{"link":"$fileUrl","file_name":"a.srt","remaining":99}""")
                "/file.srt" -> MockResponse().setBody("x")
                else -> null
            }
        }
        val client = client()
        client.download(1)
        client.download(2)
        val requests = recorded()
        assertEquals(listOf("/api/v1/login", "/vip/api/v1/download", "/file.srt", "/vip/api/v1/download", "/file.srt"), requests.map { it.requestUrl!!.encodedPath })
        val login = requests[0]
        assertEquals("""{"username":"reader","password":"secret"}""", login.body.readUtf8())
        assertEquals(API_KEY, login.getHeader("Api-Key"))
        assertEquals("Bearer tok-1", requests[1].getHeader("Authorization"))
        assertEquals("Bearer tok-1", requests[3].getHeader("Authorization"))
        assertEquals(API_KEY, requests[1].getHeader("Api-Key"))
    }

    @Test
    fun anOldTokenIsRenewed() = runBlocking<Unit> {
        account = OpenSubtitlesLogin("reader", "secret")
        var logins = 0
        routes { request ->
            when (request.requestUrl?.encodedPath) {
                "/api/v1/login" -> json("""{"token":"tok-${++logins}"}""")
                "/api/v1/download" -> json("""{"link":"${server.url("/f.srt")}"}""")
                "/f.srt" -> MockResponse().setBody("x")
                else -> null
            }
        }
        val client = client()
        client.download(1)
        now += OpenSubtitlesClient.TOKEN_LIFETIME_MS + 1
        client.download(2)
        assertEquals(2, logins)
        val downloads = recorded().filter { it.requestUrl!!.encodedPath == "/api/v1/download" }
        assertEquals("Bearer tok-2", downloads[1].getHeader("Authorization"))
    }

    @Test
    fun aRefusedTokenGetsOneFreshLogin() = runBlocking<Unit> {
        account = OpenSubtitlesLogin("reader", "secret")
        var logins = 0
        routes { request ->
            when (request.requestUrl?.encodedPath) {
                "/api/v1/login" -> json("""{"token":"tok-${++logins}"}""")
                "/api/v1/download" ->
                    if (request.getHeader("Authorization") == "Bearer tok-1") {
                        json("""{"message":"token expired"}""", code = 401)
                    } else {
                        json("""{"link":"${server.url("/f.srt")}"}""")
                    }
                "/f.srt" -> MockResponse().setBody("x")
                else -> null
            }
        }
        client().download(1)
        assertEquals(2, logins)
    }

    @Test
    fun wrongPasswordIsReportedAndNotRetried() = runBlocking<Unit> {
        account = OpenSubtitlesLogin("reader", "wrong")
        routes { request ->
            if (request.requestUrl?.encodedPath == "/api/v1/login") json("""{"message":"Invalid username/password","status":401}""", code = 401) else null
        }
        val client = client()
        expectFailure(Reason.LOGIN_FAILED) { client.download(1) }
        expectFailure(Reason.LOGIN_FAILED) { client.download(1) }
        assertEquals(1, server.requestCount)
        // New credentials are tried.
        account = OpenSubtitlesLogin("reader", "right")
        expectFailure(Reason.LOGIN_FAILED) { client.download(1) }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun searchDoesNotLogIn() = runBlocking<Unit> {
        account = OpenSubtitlesLogin("reader", "secret")
        server.enqueue(json(RESULTS))
        client().search(SubtitleSearchRequest(tmdbId = 1))
        assertEquals("/api/v1/subtitles", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun expiredLinkIsNotFound() = runBlocking<Unit> {
        routes { request ->
            when (request.requestUrl?.encodedPath) {
                "/api/v1/download" -> json("""{"link":"${server.url("/gone.srt")}"}""")
                else -> MockResponse().setResponseCode(410)
            }
        }
        expectFailure(Reason.NOT_FOUND) { client().download(1) }
    }

    @Test
    fun tooLargeFileIsRefused() = runBlocking<Unit> {
        routes { request ->
            when (request.requestUrl?.encodedPath) {
                "/api/v1/download" -> json("""{"link":"${server.url("/big.srt")}"}""")
                else -> MockResponse().setBody("x".repeat(2_000))
            }
        }
        expectFailure(Reason.BAD_RESPONSE) { client().download(1, maxBytes = 1_000) }
    }

    @Test
    fun answerWithoutALinkIsUnreadable() = runBlocking<Unit> {
        server.enqueue(json("""{"message":"ok"}"""))
        expectFailure(Reason.BAD_RESPONSE) { client().requestDownload(1) }
    }

    @Test
    fun garbageIsUnreadable() = runBlocking<Unit> {
        server.enqueue(json("<html>maintenance</html>"))
        expectFailure(Reason.BAD_RESPONSE) { client().search(SubtitleSearchRequest(tmdbId = 1)) }
    }

    private companion object {
        const val API_KEY = "abcdef0123456789"
        const val EMPTY = """{"total_pages":0,"total_count":0,"per_page":60,"page":1,"data":[]}"""
        val RESULTS = """
            {
              "total_pages": 1, "total_count": 2, "per_page": 60, "page": 1,
              "data": [
                {
                  "id": "6224976",
                  "type": "subtitle",
                  "attributes": {
                    "subtitle_id": "6224976",
                    "language": "en",
                    "download_count": 18946,
                    "new_download_count": 98,
                    "hearing_impaired": false,
                    "hd": true,
                    "fps": 23.976,
                    "votes": 3,
                    "ratings": 6.0,
                    "from_trusted": false,
                    "foreign_parts_only": false,
                    "upload_date": "2021-10-24T12:54:50Z",
                    "ai_translated": false,
                    "machine_translated": false,
                    "release": "Dune.2021.1080p.WEBRip.x264-RARBG",
                    "uploader": {"uploader_id": 1, "name": "someone", "rank": "trusted"},
                    "feature_details": {"feature_id": 1, "feature_type": "Movie", "year": 2021, "title": "Dune", "imdb_id": 1160419, "tmdb_id": 438631},
                    "files": [{"file_id": 6994430, "cd_number": 1, "file_name": "Dune.2021.1080p.WEBRip.x264-RARBG.srt"}],
                    "moviehash_match": true
                  }
                },
                {
                  "id": "7000000",
                  "type": "subtitle",
                  "attributes": {
                    "language": "pt-BR",
                    "download_count": 120,
                    "hearing_impaired": true,
                    "fps": 0,
                    "machine_translated": true,
                    "release": null,
                    "files": [
                      {"file_id": 7000002, "cd_number": 2, "file_name": "dune-ptbr-cd2.srt"},
                      {"file_id": 7000001, "cd_number": 1, "file_name": "dune-ptbr.srt"}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
