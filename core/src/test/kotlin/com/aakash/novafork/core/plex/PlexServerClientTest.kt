package com.aakash.novafork.core.plex

import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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

class PlexServerClientTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()
    private val info = PlexClientInfo(clientIdentifier = "client-1", version = "1.4", device = "Pixel 8", platformVersion = "14")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(token: String? = "tok-plex", path: String = "/", okHttp: OkHttpClient = http) =
        PlexServerClient(okHttp, server.url(path).toString(), info, token, retryDelaysMs = emptyList())

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun routes(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handler(request)
        }
    }

    private fun take(): RecordedRequest = server.takeRequest(1, TimeUnit.SECONDS) ?: throw AssertionError("No request")

    private inline fun expectFailure(block: () -> Unit): PlexException {
        try {
            block()
        } catch (e: PlexException) {
            return e
        }
        fail("Expected a PlexException")
        throw AssertionError()
    }

    @Test
    fun requestsCarryTheTokenAndTheAppsIdentity() = runBlocking<Unit> {
        server.enqueue(json(SECTIONS))
        val sections = client().sections()

        assertEquals(listOf("1", "2", "3", "4"), sections.map { it.key })
        assertEquals(listOf(true, true, false, true), sections.map { it.isVideoSection })
        assertEquals(listOf(false, false, false, true), sections.map { it.isPersonalMedia })
        val request = take()
        assertEquals("/library/sections", request.requestUrl?.encodedPath)
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals("tok-plex", request.getHeader("X-Plex-Token"))
        assertEquals("client-1", request.getHeader("X-Plex-Client-Identifier"))
        assertEquals("Nova Fork", request.getHeader("X-Plex-Product"))
        assertEquals("1.4", request.getHeader("X-Plex-Version"))
        assertEquals("Android", request.getHeader("X-Plex-Platform"))
        assertEquals("14", request.getHeader("X-Plex-Platform-Version"))
        assertEquals("Pixel 8", request.getHeader("X-Plex-Device-Name"))
        assertNull("API calls keep the token out of the URL", request.requestUrl?.queryParameter("X-Plex-Token"))
    }

    @Test
    fun aServerOpenToTheNetworkIsAskedWithoutAToken() = runBlocking<Unit> {
        server.enqueue(json(SECTIONS))
        client(token = null).sections()
        assertNull(take().getHeader("X-Plex-Token"))
    }

    @Test
    fun sectionItemsArePaged() = runBlocking<Unit> {
        routes { request ->
            val start = request.requestUrl?.queryParameter("X-Plex-Container-Start")?.toInt() ?: 0
            val keys = (start until minOf(start + 2, 3)).map { 100 + it }
            json(
                """{"MediaContainer":{"size":${keys.size},"totalSize":3,"offset":$start,"Metadata":[${
                    keys.joinToString(",") { """{"ratingKey":"$it","type":"movie","title":"Movie $it"}""" }
                }]}}""",
            )
        }
        val counts = ArrayList<Int>()
        val items = client().sectionItems("1", PlexServerClient.TYPE_MOVIE, pageSize = 2) { counts += it }

        assertEquals(listOf("100", "101", "102"), items.map { it.ratingKey })
        assertEquals(listOf(2, 3), counts)
        val first = requireNotNull(take().requestUrl)
        assertEquals("/library/sections/1/all", first.encodedPath)
        assertEquals("1", first.queryParameter("type"))
        assertEquals("1", first.queryParameter("includeGuids"))
        assertEquals("0", first.queryParameter("X-Plex-Container-Start"))
        assertEquals("2", first.queryParameter("X-Plex-Container-Size"))
        assertEquals("2", take().requestUrl?.queryParameter("X-Plex-Container-Start"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun itemsDecodeWhetherIdsComeAsNumbersOrStrings() = runBlocking<Unit> {
        server.enqueue(json(EPISODES))
        val episodes = client().sectionItems("2", PlexServerClient.TYPE_EPISODE)

        val first = episodes[0]
        assertEquals("501", first.ratingKey)
        assertEquals("episode", first.type)
        assertEquals("Severance", first.grandparentTitle)
        assertEquals("400", first.grandparentRatingKey)
        assertEquals(1, first.parentIndex)
        assertEquals(2, first.index)
        assertEquals("2022-02-25", first.originallyAvailableAt)
        assertEquals(listOf("Drama", "Thriller"), first.genres.mapNotNull { it.tag })
        assertEquals(mapOf("imdb" to "tt11280740", "tmdb" to "95396", "tvdb" to "371980"), first.providers)
        val part = requireNotNull(first.part)
        assertEquals("9001", part.id)
        assertEquals("/library/parts/9001/1700000000/file.mkv", part.key)
        assertEquals(2_000_000_000L, part.size)
        assertEquals(1920, first.playableMedia?.width)
        assertEquals(3_300_000L, first.duration)
        assertEquals(1_700_000_000L, first.addedAt)

        // A legacy agent's guid still gives the ids; an item without a file has no part.
        val second = episodes[1]
        assertEquals(mapOf("tvdb" to "371980"), second.providers)
        assertNull(second.part)
    }

    @Test
    fun legacyAndNewGuidsGiveProviderIds() {
        assertEquals(
            mapOf("imdb" to "tt0133093", "tmdb" to "603"),
            PlexGuids.providerIds(
                listOf("plex://movie/5d7768", "com.plexapp.agents.imdb://tt0133093?lang=en", "tmdb://603", "imdb://tt9999999", "com.plexapp.agents.hama://anidb-1"),
            ),
        )
        assertEquals(mapOf("tmdb" to "1396"), PlexGuids.providerIds(listOf("com.plexapp.agents.themoviedb://1396?lang=en")))
        assertEquals(emptyMap<String, String>(), PlexGuids.providerIds(listOf("", "nonsense", "tmdb://")))
    }

    @Test
    fun fileImageAndPlayUrls() {
        val base = server.url("/").toString()
        val plex = client()
        val part = plex.partUrl("/library/parts/9001/1700000000/file.mkv")
        assertEquals("${base}library/parts/9001/1700000000/file.mkv", part)
        assertNull(plex.partUrl("library/parts/1/file.mkv"))

        val playable = plex.withToken(requireNotNull(part)).toHttpUrl()
        assertEquals("tok-plex", playable.queryParameter("X-Plex-Token"))
        assertEquals("client-1", playable.queryParameter("X-Plex-Client-Identifier"))
        assertEquals(listOf("library", "parts", "9001", "1700000000", "file.mkv"), playable.pathSegments)
        assertNull(client(token = null).withToken(part).toHttpUrl().queryParameter("X-Plex-Token"))

        val image = requireNotNull(plex.imageUrl("/library/metadata/7/thumb/1700000000", 500, 750)).toHttpUrl()
        assertEquals(listOf("photo", ":", "transcode"), image.pathSegments)
        assertEquals("/library/metadata/7/thumb/1700000000", image.queryParameter("url"))
        assertEquals("500", image.queryParameter("width"))
        assertEquals("750", image.queryParameter("height"))
        // Image URLs get stored: the image loader sends the token as a header instead.
        assertNull(image.queryParameter("X-Plex-Token"))
        assertFalse(image.toString(), "tok-plex" in image.toString())
        assertEquals("X-Plex-Token", PlexServerClient.TOKEN_HEADER)
        assertEquals("https://image.tmdb.org/t/p/original/x.jpg", plex.imageUrl("https://image.tmdb.org/t/p/original/x.jpg", 500, 750))
        assertNull(plex.imageUrl(" ", 500, 750))
    }

    @Test
    fun aServerBehindAProxyPathKeepsItsPath() = runBlocking<Unit> {
        val plex = client(path = "/plex/")
        assertEquals(server.url("/plex/library/parts/1/2/file.mp4").toString(), plex.partUrl("/library/parts/1/2/file.mp4"))
        server.enqueue(json("""{"MediaContainer":{"size":0}}"""))
        plex.children("/library/metadata/7/children")
        assertEquals("/plex/library/metadata/7/children", take().requestUrl?.encodedPath)
    }

    @Test
    fun onlyLibraryPathsAreFollowed() = runBlocking<Unit> {
        expectFailure { client().children("/:/prefs") }
        expectFailure { client().children("/library/../:/prefs") }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun timelineAndWatchedState() = runBlocking<Unit> {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(200)) }
        val plex = client()
        plex.timeline("7", PlexPlaybackState.PLAYING, 5_000, 60_000)
        plex.setWatched("7", watched = true)
        plex.setWatched("7", watched = false)

        val timeline = requireNotNull(take().requestUrl)
        assertEquals("/:/timeline", timeline.encodedPath)
        assertEquals("7", timeline.queryParameter("ratingKey"))
        assertEquals("/library/metadata/7", timeline.queryParameter("key"))
        assertEquals("playing", timeline.queryParameter("state"))
        assertEquals("5000", timeline.queryParameter("time"))
        assertEquals("60000", timeline.queryParameter("duration"))
        val scrobble = requireNotNull(take().requestUrl)
        assertEquals("/:/scrobble", scrobble.encodedPath)
        assertEquals("7", scrobble.queryParameter("key"))
        assertEquals("com.plexapp.plugins.library", scrobble.queryParameter("identifier"))
        assertEquals("/:/unscrobble", take().requestUrl?.encodedPath)
    }

    @Test
    fun aRefusedSignInSaysWhatToDo() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(401))
        val refused = expectFailure { client().serverInfo() }
        assertEquals(401, refused.code)
        assertTrue(refused.message.orEmpty(), refused.message.orEmpty().endsWith("Sign in again."))

        server.enqueue(MockResponse().setResponseCode(401))
        val missing = expectFailure { client(token = null).sections() }
        assertEquals("${server.hostName} needs you to sign in to Plex", missing.message)
    }

    @Test
    fun identityTellsAPlexServerFromSomethingElse() = runBlocking<Unit> {
        server.enqueue(json("""{"MediaContainer":{"size":0,"claimed":true,"machineIdentifier":"abc123","version":"1.40.0"}}"""))
        val identity = client().identity()
        assertEquals("abc123", identity.machineIdentifier)
        assertEquals("/identity", take().requestUrl?.encodedPath)

        server.enqueue(MockResponse().setBody("<html>NAS</html>"))
        assertEquals("${server.hostName} doesn't look like a Plex server", expectFailure { client().identity() }.message)
    }

    @Test
    fun unreachableServersGiveReadableMessages() = runBlocking<Unit> {
        val noDns = http.newBuilder().dns(NoDns).build()
        val unknown = expectFailure { PlexServerClient(noDns, "http://plex.invalid:32400", info, "t", emptyList()).sections() }
        assertEquals("Can't find plex.invalid on the network", unknown.message)
        assertEquals(0, unknown.code)

        val address = server.url("/").toString()
        server.shutdown()
        val off = expectFailure { PlexServerClient(http, address, info, "t", emptyList()).sections() }
        assertTrue(off.message.orEmpty(), off.message.orEmpty().startsWith("Can't connect to "))
    }

    @Test
    fun theTokenNeverShowsInPrintedObjects() {
        val pin = PlexPin(id = 1, code = "ABCD", authToken = "secret-token")
        val resource = PlexResource(name = "NAS", clientIdentifier = "m1", accessToken = "secret-token")
        assertFalse("secret-token" in pin.toString())
        assertFalse("secret-token" in resource.toString())
    }

    private companion object {
        const val SECTIONS = """{"MediaContainer":{"size":4,"Directory":[
            {"key":"1","title":"Movies","type":"movie","agent":"tv.plex.agents.movie","Location":[{"id":1,"path":"/data/movies"}]},
            {"key":"2","title":"TV Shows","type":"show","agent":"tv.plex.agents.series"},
            {"key":"3","title":"Music","type":"artist","agent":"tv.plex.agents.music"},
            {"key":4,"title":"Home Videos","type":"movie","agent":"com.plexapp.agents.none"}]}}"""

        const val EPISODES = """{"MediaContainer":{"size":2,"totalSize":2,"Metadata":[
            {"ratingKey":501,"key":"/library/metadata/501","type":"episode","title":"Half Loop","grandparentTitle":"Severance",
             "grandparentRatingKey":"400","parentRatingKey":"450","parentTitle":"Season 1","parentIndex":1,"index":2,
             "summary":"Mark gives a tour.","thumb":"/library/metadata/501/thumb/1700000001","parentThumb":"/library/metadata/450/thumb/1",
             "grandparentThumb":"/library/metadata/400/thumb/1","grandparentArt":"/library/metadata/400/art/1",
             "originallyAvailableAt":"2022-02-25","duration":3300000,"addedAt":1700000000,"updatedAt":1700000500,
             "Genre":[{"id":11,"filter":"genre=11","tag":"Drama"},{"tag":"Thriller"}],
             "Guid":[{"id":"imdb://tt11280740"},{"id":"tmdb://95396"},{"id":"tvdb://371980"}],
             "Media":[{"id":801,"duration":3300000,"width":1920,"height":1080,"container":"mkv","videoResolution":"1080",
               "Part":[{"id":9001,"key":"/library/parts/9001/1700000000/file.mkv","file":"/data/tv/Severance/S01E02.mkv","size":2000000000,"container":"mkv"}]}]},
            {"ratingKey":"502","type":"episode","title":"In Perpetuity","guid":"com.plexapp.agents.thetvdb://371980/1/3?lang=en",
             "Media":[{"id":"802","Part":[]}]}]}}"""
    }

    /** Resolves nothing, like a network without that host. */
    private object NoDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> = throw UnknownHostException(hostname)
    }
}
