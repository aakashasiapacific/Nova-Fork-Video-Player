package com.aakash.novafork.core.jellyfin

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
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
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class JellyfinClientTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()
    private val device = JellyfinDevice(device = "Pixel 8", deviceId = "device-123", version = "1.4")
    private val auth = JellyfinAuth(token = "tok-abc", userId = "user1", serverId = "srv1")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(
        flavor: JellyfinFlavor = JellyfinFlavor.JELLYFIN,
        path: String = "/",
        retries: List<Long> = emptyList(),
        okHttp: OkHttpClient = http,
    ) = JellyfinClient(okHttp, server.url(path).toString(), flavor, device, retries)

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun routes(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handler(request)
        }
    }

    private fun take(): RecordedRequest = server.takeRequest(1, TimeUnit.SECONDS) ?: throw AssertionError("No request")

    private inline fun expectFailure(block: () -> Unit): JellyfinException {
        try {
            block()
        } catch (e: JellyfinException) {
            return e
        }
        fail("Expected a JellyfinException")
        throw AssertionError()
    }

    @Test
    fun signingInSendsTheClientIdentityAndReturnsTheToken() = runBlocking<Unit> {
        server.enqueue(json(AUTH))
        val result = client().authenticate(" alice ", "secret")

        assertEquals("tok-new", result.token)
        assertEquals("user1", result.userId)
        assertEquals("srv1", result.serverId)
        assertEquals("alice", result.userName)
        assertFalse("toString must hide the token", "tok-new" in result.toString())

        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/Users/AuthenticateByName", request.requestUrl?.encodedPath)
        val expected = "MediaBrowser Client=\"Nova Fork\", Device=\"Pixel 8\", DeviceId=\"device-123\", Version=\"1.4\""
        assertEquals(expected, request.getHeader("Authorization"))
        assertEquals(expected, request.getHeader("X-Emby-Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("alice", body["Username"]?.jsonPrimitive?.content)
        assertEquals("secret", body["Pw"]?.jsonPrimitive?.content)
    }

    @Test
    fun signedInRequestsCarryTheToken() = runBlocking<Unit> {
        server.enqueue(json(VIEWS))
        val libraries = client().libraries(auth)

        assertEquals(listOf("lib-movies", "lib-shows", "lib-music"), libraries.map { it.id })
        assertEquals("tvshows", libraries[1].collectionType)
        val request = take()
        assertEquals("/Users/user1/Views", request.requestUrl?.encodedPath)
        assertTrue(request.getHeader("Authorization").orEmpty().endsWith(", Token=\"tok-abc\""))
        assertNull("API calls keep the token out of the URL", request.requestUrl?.queryParameter("api_key"))
    }

    @Test
    fun embyGetsItsPrefixAndTokenHeader() = runBlocking<Unit> {
        server.enqueue(json(VIEWS))
        client(JellyfinFlavor.EMBY).libraries(auth)

        val request = take()
        assertEquals("/emby/Users/user1/Views", request.requestUrl?.encodedPath)
        val header = request.getHeader("X-Emby-Authorization").orEmpty()
        assertTrue(header, header.startsWith("Emby Client=\"Nova Fork\", Device=\"Pixel 8\", DeviceId=\"device-123\""))
        assertTrue(header, header.endsWith("Token=\"tok-abc\""))
        assertEquals("tok-abc", request.getHeader("X-Emby-Token"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun anEmbyAddressEndingInEmbyIsNotPrefixedTwice() = runBlocking<Unit> {
        server.enqueue(json(PUBLIC_INFO))
        client(JellyfinFlavor.EMBY, path = "/emby/").publicInfo()
        assertEquals("/emby/System/Info/Public", take().requestUrl?.encodedPath)
    }

    @Test
    fun anAddressCopiedFromTheWebClientStillWorks() = runBlocking<Unit> {
        server.enqueue(json(PUBLIC_INFO))
        val info = client(path = "/jellyfin/web/index.html").publicInfo()
        assertEquals("srv1", info.id)
        assertEquals("Living room", info.serverName)
        val request = take()
        assertEquals("/jellyfin/System/Info/Public", request.requestUrl?.encodedPath)
        // Nobody has signed in yet: no token.
        assertFalse(request.getHeader("Authorization").orEmpty().contains("Token="))
    }

    @Test
    fun aWrongPasswordIsReportedAsSuch() = runBlocking<Unit> {
        server.enqueue(json("""{"error":"Invalid username or password"}""", code = 401))
        val error = expectFailure { client().authenticate("alice", "nope") }
        assertEquals(401, error.code)
        assertEquals("Wrong username or password", error.message)
    }

    @Test
    fun aRefusedTokenIsA401() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(401))
        val error = expectFailure { client().libraries(auth) }
        assertEquals(401, error.code)
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("Sign in again"))
    }

    @Test
    fun somethingElseAtTheAddressIsNotTakenForAServer() = runBlocking<Unit> {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html><body>Router login</body></html>"))
        val html = expectFailure { client().publicInfo() }
        assertEquals("${server.hostName} doesn't look like a Jellyfin server", html.message)

        server.enqueue(MockResponse().setResponseCode(404))
        val missing = expectFailure { client(JellyfinFlavor.EMBY).publicInfo() }
        assertEquals(404, missing.code)
        assertEquals("${server.hostName} doesn't look like an Emby server", missing.message)
    }

    @Test
    fun aServerThatIsOffGivesAReadableMessage() = runBlocking<Unit> {
        val address = server.url("/").toString()
        server.shutdown()
        val error = expectFailure { JellyfinClient(http, address, JellyfinFlavor.JELLYFIN, device, emptyList()).publicInfo() }
        assertEquals(0, error.code)
        assertTrue(error.message.orEmpty(), error.message.orEmpty().startsWith("Can't connect to "))
        assertTrue(error.message.orEmpty(), error.message.orEmpty().endsWith("Is the server running?"))
    }

    @Test
    fun anUnknownHostGivesAReadableMessage() = runBlocking<Unit> {
        val noDns = http.newBuilder().dns(NoDns).build()
        val error = expectFailure {
            JellyfinClient(noDns, "http://jellyfin.invalid:8096", JellyfinFlavor.JELLYFIN, device, emptyList()).publicInfo()
        }
        assertEquals("Can't find jellyfin.invalid on the network", error.message)
    }

    @Test
    fun aServerThatDoesNotAnswerGivesAReadableMessage() = runBlocking<Unit> {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val impatient = http.newBuilder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        val error = expectFailure { client(okHttp = impatient).publicInfo() }
        assertEquals("${server.hostName} didn't answer", error.message)
    }

    @Test
    fun itemsArePagedUntilTheTotal() = runBlocking<Unit> {
        routes { request ->
            val start = request.requestUrl?.queryParameter("StartIndex")?.toInt() ?: 0
            val ids = (start until minOf(start + 2, 5)).map { "item$it" }
            json(page(ids, total = 5, start = start))
        }
        val counts = ArrayList<Int>()
        val query = JellyfinQuery(parentId = "lib-movies", recursive = true, includeItemTypes = listOf("Movie", "Episode"))
        val items = client().items(auth, query, pageSize = 2) { counts += it }

        assertEquals((0 until 5).map { "item$it" }, items.map { it.id })
        assertEquals(listOf(2, 4, 5), counts)
        val requests = List(3) { take() }
        assertEquals(listOf("0", "2", "4"), requests.map { it.requestUrl?.queryParameter("StartIndex") })
        val first = requireNotNull(requests.first().requestUrl)
        assertEquals("/Users/user1/Items", first.encodedPath)
        assertEquals("lib-movies", first.queryParameter("ParentId"))
        assertEquals("true", first.queryParameter("Recursive"))
        assertEquals("Movie,Episode", first.queryParameter("IncludeItemTypes"))
        assertEquals("2", first.queryParameter("Limit"))
        assertEquals("false", first.queryParameter("IsMissing"))
        val fields = first.queryParameter("Fields").orEmpty().split(',')
        assertTrue(fields.containsAll(listOf("Overview", "ProviderIds", "Path", "MediaSources", "DateCreated")))
    }

    @Test
    fun pagingStopsAtAShortPageWhenTheServerGivesNoTotal() = runBlocking<Unit> {
        server.enqueue(json(page(listOf("a", "b"), total = 0, start = 0)))
        server.enqueue(json(page(listOf("c"), total = 0, start = 2)))
        val items = client().items(auth, JellyfinQuery(), pageSize = 2)
        assertEquals(listOf("a", "b", "c"), items.map { it.id })
        assertEquals(2, server.requestCount)
    }

    @Test
    fun newerJellyfinRoutesAreTriedWhenTheOldOnesAreGone() = runBlocking<Unit> {
        routes { request ->
            when (request.requestUrl?.encodedPath) {
                "/UserViews" -> json(VIEWS)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val libraries = client().libraries(auth)
        assertEquals(3, libraries.size)
        assertEquals("/Users/user1/Views", take().requestUrl?.encodedPath)
        val fallback = take()
        assertEquals("/UserViews", fallback.requestUrl?.encodedPath)
        assertEquals("user1", fallback.requestUrl?.queryParameter("userId"))

        // Emby has only the old routes.
        val error = expectFailure { client(JellyfinFlavor.EMBY).libraries(auth) }
        assertEquals(404, error.code)
    }

    @Test
    fun imageAndStreamUrls() {
        val base = server.url("/").toString()
        val jellyfin = client()
        assertEquals("${base}Items/abc/Images/Primary?maxWidth=500&quality=90&tag=t1", jellyfin.imageUrl("abc", "Primary", "t1", 500))
        assertEquals("${base}Items/abc/Images/Backdrop/0?maxWidth=1280&quality=90&tag=b1", jellyfin.imageUrl("abc", "Backdrop", "b1", 1280, index = 0))
        assertNull(jellyfin.imageUrl(null, "Primary", "t1"))

        val stream = jellyfin.streamUrl("abc", "ms1")
        assertEquals("${base}Videos/abc/stream?static=true&MediaSourceId=ms1", stream)
        assertEquals("abc", JellyfinClient.itemIdOf(stream))
        assertEquals("ms1", JellyfinClient.mediaSourceIdOf(stream))
        val playable = jellyfin.withToken(stream, "tok-abc").toHttpUrl()
        assertEquals("tok-abc", playable.queryParameter("api_key"))
        assertEquals("tok-abc", playable.queryParameter("ApiKey"))
        assertEquals("ms1", playable.queryParameter("MediaSourceId"))
        // Adding a token again replaces the old one.
        assertEquals(listOf("new"), jellyfin.withToken(playable.toString(), "new").toHttpUrl().queryParameterValues("api_key"))

        val emby = client(JellyfinFlavor.EMBY)
        val embyStream = emby.streamUrl("abc", null)
        assertEquals("${base}emby/Videos/abc/stream?static=true", embyStream)
        assertEquals("abc", JellyfinClient.itemIdOf(embyStream))
        assertNull(JellyfinClient.mediaSourceIdOf(embyStream))
        val embyPlayable = emby.withToken(embyStream, "tok-abc").toHttpUrl()
        assertEquals("tok-abc", embyPlayable.queryParameter("api_key"))
        assertNull(embyPlayable.queryParameter("ApiKey"))
        assertNull(JellyfinClient.itemIdOf("http://host/Items/abc/Images/Primary"))
    }

    @Test
    fun playbackIsReportedInTicks() = runBlocking<Unit> {
        repeat(5) { server.enqueue(MockResponse().setResponseCode(204)) }
        val client = client()
        client.reportPlaying(auth, "item1", "ms1", 1_500)
        client.reportProgress(auth, "item1", "ms1", 12_000, paused = true)
        client.reportStopped(auth, "item1", null, 60_000)
        client.setPlayed(auth, "item1", played = true)
        client.setPlayed(auth, "item1", played = false)

        val playing = take()
        assertEquals("POST", playing.method)
        assertEquals("/Sessions/Playing", playing.requestUrl?.encodedPath)
        val start = Json.parseToJsonElement(playing.body.readUtf8()).jsonObject
        assertEquals("item1", start["ItemId"]?.jsonPrimitive?.content)
        assertEquals("ms1", start["MediaSourceId"]?.jsonPrimitive?.content)
        assertEquals(15_000_000L, start["PositionTicks"]?.jsonPrimitive?.long)
        assertEquals("DirectPlay", start["PlayMethod"]?.jsonPrimitive?.content)
        assertEquals("true", start["CanSeek"]?.jsonPrimitive?.content)

        val progress = take()
        assertEquals("/Sessions/Playing/Progress", progress.requestUrl?.encodedPath)
        val update = Json.parseToJsonElement(progress.body.readUtf8()).jsonObject
        assertEquals(120_000_000L, update["PositionTicks"]?.jsonPrimitive?.long)
        assertEquals("true", update["IsPaused"]?.jsonPrimitive?.content)

        val stopped = take()
        assertEquals("/Sessions/Playing/Stopped", stopped.requestUrl?.encodedPath)
        val stop = Json.parseToJsonElement(stopped.body.readUtf8()) as JsonObject
        assertEquals(600_000_000L, stop["PositionTicks"]?.jsonPrimitive?.long)
        assertFalse("no MediaSourceId when unknown", stop.containsKey("MediaSourceId"))

        val played = take()
        assertEquals("POST", played.method)
        assertEquals("/Users/user1/PlayedItems/item1", played.requestUrl?.encodedPath)
        val unplayed = take()
        assertEquals("DELETE", unplayed.method)
        assertEquals("/Users/user1/PlayedItems/item1", unplayed.requestUrl?.encodedPath)
    }

    @Test
    fun anEpisodeDecodesWithWhatScansNeed() = runBlocking<Unit> {
        server.enqueue(json("""{"Items":[$EPISODE],"TotalRecordCount":1}"""))
        val item = client().itemsPage(auth, JellyfinQuery(), 0, 10).items.single()

        assertEquals("Episode", item.type)
        assertEquals("series1", item.seriesId)
        assertEquals("Severance", item.seriesName)
        assertEquals(1, item.parentIndexNumber)
        assertEquals(2, item.indexNumber)
        assertEquals(3, item.indexNumberEnd)
        assertEquals(mapOf("tvdb" to "123", "imdb" to "tt1"), item.providers)
        assertEquals("ep-tag", item.imageTag("primary"))
        assertNull(item.imageTag("Thumb"))
        assertEquals(3_300_000L, item.durationMs)
        assertEquals(1920, item.mediaSources.single().video?.width)
        assertEquals(2_000_000_000L, item.mediaSources.single().size)
        assertEquals(emptyList<String>(), item.genres)
        assertFalse(item.isMissing)
        assertEquals("2022-02-25", JellyfinDates.day(item.premiereDate))
    }

    @Test
    fun anApiKeyStandsInForTheFirstAdministrator() = runBlocking<Unit> {
        routes { request ->
            when (request.requestUrl?.encodedPath) {
                "/Users/Me" -> MockResponse().setResponseCode(400)
                "/Users" -> json(
                    """[{"Id":"u0","Name":"kid","Policy":{"IsAdministrator":false}},
                        {"Id":"u1","Name":"off","Policy":{"IsAdministrator":true,"IsDisabled":true}},
                        {"Id":"u2","Name":"admin","Policy":{"IsAdministrator":true}}]""",
                )
                else -> MockResponse().setResponseCode(404)
            }
        }
        assertEquals("u2", client().currentUser("api-key").id)
    }

    @Test
    fun aFailedGetIsRetriedButAPostIsNot() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(json(PUBLIC_INFO))
        assertEquals("srv1", client(retries = listOf(1L)).publicInfo().id)
        assertEquals(2, server.requestCount)

        server.enqueue(MockResponse().setResponseCode(503))
        val error = expectFailure { client(retries = listOf(1L)).reportPlaying(auth, "item1", null, 0) }
        assertEquals(503, error.code)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun datesOfEitherServerAreRead() {
        assertEquals(1_673_778_030_123L, JellyfinDates.epochMillis("2023-01-15T10:20:30.1234567Z"))
        assertEquals(1_673_778_030_000L, JellyfinDates.epochMillis("2023-01-15T11:20:30.0000000+01:00"))
        assertEquals(1_673_778_030_000L, JellyfinDates.epochMillis("2023-01-15T10:20:30"))
        assertNull(JellyfinDates.epochMillis("0001-01-01T00:00:00.0000000Z"))
        assertNull(JellyfinDates.epochMillis("soon"))
        assertNull(JellyfinDates.epochMillis(null))
        assertEquals("2022-02-18", JellyfinDates.day("2022-02-18T00:00:00.0000000Z"))
        assertNull(JellyfinDates.day("0001-01-01T00:00:00.0000000Z"))
        assertNull(JellyfinDates.day("18.02.2022"))
    }

    @Test
    fun tokensAreKeptOutOfLogs() {
        val text = "Couldn't open http://h:8096/Videos/1/stream?static=true&api_key=abc123&ApiKey=abc123 " +
            "and http://plex:32400/library/parts/1/2/file.mkv?X-Plex-Token=xyz789&X-Plex-Product=Nova " +
            "header MediaBrowser Client=\"Nova Fork\", Token=\"abc123\" body {\"AccessToken\":\"abc123\",\"authToken\": \"xyz789\"}"
        val redacted = ServerSecrets.redact(text)
        assertFalse(redacted, "abc123" in redacted)
        assertFalse(redacted, "xyz789" in redacted)
        assertTrue(redacted, "static=true&api_key=***" in redacted)
        assertTrue(redacted, "X-Plex-Token=***&X-Plex-Product=Nova" in redacted)
        assertTrue(redacted, "Client=\"Nova Fork\"" in redacted)
        assertEquals("", ServerSecrets.redact(null))
    }

    private fun page(ids: List<String>, total: Int, start: Int): String =
        """{"Items":[${ids.joinToString(",") { """{"Id":"$it","Name":"Movie $it","Type":"Movie"}""" }}],"TotalRecordCount":$total,"StartIndex":$start}"""

    private companion object {
        const val AUTH = """{"User":{"Id":"user1","Name":"alice","ServerId":"srv1"},"AccessToken":"tok-new","ServerId":"srv1","SessionInfo":{"Id":"s"}}"""
        const val PUBLIC_INFO = """{"Id":"srv1","ServerName":"Living room","Version":"10.9.11","ProductName":"Jellyfin Server","LocalAddress":"http://192.168.1.5:8096"}"""
        const val VIEWS = """{"Items":[
            {"Id":"lib-movies","Name":"Movies","CollectionType":"movies","Type":"CollectionFolder","IsFolder":true},
            {"Id":"lib-shows","Name":"Shows","CollectionType":"tvshows","Type":"CollectionFolder","IsFolder":true},
            {"Id":"lib-music","Name":"Music","CollectionType":"music","Type":"CollectionFolder","IsFolder":true},
            {"Name":"no id"}],"TotalRecordCount":4}"""
        const val EPISODE = """{"Id":"ep1","Name":"Half Loop","Type":"Episode","SeriesId":"series1","SeriesName":"Severance",
            "SeasonId":"season1","SeasonName":"Season 1","ParentIndexNumber":1,"IndexNumber":2,"IndexNumberEnd":3,
            "PremiereDate":"2022-02-25T00:00:00.0000000Z","DateCreated":"2023-01-15T10:20:30.1234567Z",
            "ProviderIds":{"Tvdb":"123","Imdb":"tt1","Tmdb":null,"Zap2It":""},"ImageTags":{"Primary":"ep-tag"},
            "BackdropImageTags":[],"ParentBackdropItemId":"series1","ParentBackdropImageTags":["bd-tag"],
            "SeriesPrimaryImageTag":"series-tag","RunTimeTicks":33000000000,"Genres":null,"LocationType":"FileSystem",
            "MediaSources":[{"Id":"ep1","Path":"/media/Severance/Season 1/Severance S01E02E03.mkv","Protocol":"File",
              "Container":"mkv","Size":2000000000,"RunTimeTicks":33000000000,
              "MediaStreams":[{"Type":"Audio","Codec":"eac3"},{"Type":"Video","Codec":"hevc","Width":1920,"Height":1080}]}],
            "UserData":{"PlaybackPositionTicks":0,"PlayCount":1,"Played":true}}"""
    }

    /** Resolves nothing, like a network without that host. */
    private object NoDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> = throw UnknownHostException(hostname)
    }
}
