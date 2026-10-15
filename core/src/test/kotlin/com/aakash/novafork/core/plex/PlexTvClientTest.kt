package com.aakash.novafork.core.plex

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class PlexTvClientTest {
    private lateinit var server: MockWebServer
    private val info = PlexClientInfo(clientIdentifier = "client-1", version = "1.4", device = "Pixel 8")
    private lateinit var plexTv: PlexTvClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        plexTv = PlexTvClient(OkHttpClient(), info, server.url("/").toString(), retryDelaysMs = emptyList())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun pin(token: String?) =
        """{"id":42,"code":"ABCD","product":"Nova Fork","trusted":false,"clientIdentifier":"client-1","expiresIn":1800,
           "createdAt":"2026-10-14T10:00:00Z","expiresAt":"2026-10-14T10:30:00Z","authToken":${token?.let { "\"$it\"" } ?: "null"}}"""

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
    fun aPinIsCreatedForThisApp() = runBlocking<Unit> {
        server.enqueue(json(pin(null), code = 201))
        val created = plexTv.createPin()

        assertEquals(42L, created.id)
        assertEquals("ABCD", created.code)
        assertNull(created.authToken)
        assertEquals(1800, created.expiresIn)
        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/api/v2/pins", request.requestUrl?.encodedPath)
        assertEquals("false", request.requestUrl?.queryParameter("strong"))
        assertEquals("client-1", request.getHeader("X-Plex-Client-Identifier"))
        assertEquals("Nova Fork", request.getHeader("X-Plex-Product"))
        assertEquals("application/json", request.getHeader("Accept"))

        server.enqueue(json(pin(null), code = 201))
        plexTv.createPin(strong = true)
        assertEquals("true", take().requestUrl?.queryParameter("strong"))
    }

    @Test
    fun waitingForTheCodePollsUntilItIsLinked() = runBlocking<Unit> {
        server.enqueue(json(pin(null)))
        server.enqueue(json(pin(null)))
        server.enqueue(json(pin("account-token")))
        val pin = pinOf(pin(null))

        assertEquals("account-token", plexTv.awaitToken(pin, pollIntervalMs = 1))
        assertEquals(3, server.requestCount)
        val poll = take()
        assertEquals("GET", poll.method)
        assertEquals("/api/v2/pins/42", poll.requestUrl?.encodedPath)
        assertEquals("ABCD", poll.requestUrl?.queryParameter("code"))
        assertEquals("client-1", poll.getHeader("X-Plex-Client-Identifier"))
    }

    @Test
    fun anExpiredCodeStopsTheWait() = runBlocking<Unit> {
        server.enqueue(json(pin(null)))
        server.enqueue(MockResponse().setResponseCode(404))
        val gone = expectFailure { plexTv.awaitToken(pinOf(pin(null)), pollIntervalMs = 1) }
        assertEquals(410, gone.code)
        assertEquals("The code expired. Get a new one.", gone.message)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = json(pin(null))
        }
        val timedOut = expectFailure { plexTv.awaitToken(pinOf(pin(null)), pollIntervalMs = 5, timeoutMs = 50) }
        assertEquals("The code expired. Get a new one.", timedOut.message)
    }

    @Test
    fun theApprovalPageCarriesTheClientAndTheCode() {
        val url = plexTv.authUrl(PlexPin(id = 42, code = "ABCD"))
        assertEquals("https://app.plex.tv/auth#?clientID=client-1&code=ABCD&context%5Bdevice%5D%5Bproduct%5D=Nova%20Fork", url)
        assertEquals("https://plex.tv/link", PlexTvClient.LINK_URL)
    }

    @Test
    fun signingInWithAPassword() = runBlocking<Unit> {
        server.enqueue(json("""{"id":1,"username":"alice","authToken":"account-token"}""", code = 201))
        assertEquals("account-token", plexTv.signIn(" alice@example.com ", "p&ss word"))
        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/api/v2/users/signin", request.requestUrl?.encodedPath)
        val form = request.body.readUtf8()
        assertTrue(form, form.contains("login=alice%40example.com"))
        assertTrue(form, form.contains("password=p%26ss%20word"))

        server.enqueue(json("""{"errors":[{"code":1001,"message":"User could not be authenticated"}]}""", code = 401))
        val refused = expectFailure { plexTv.signIn("alice", "wrong") }
        assertEquals(401, refused.code)
        assertEquals("Wrong username or password", refused.message)
    }

    @Test
    fun onlyServersAreListed() = runBlocking<Unit> {
        server.enqueue(
            json(
                """[{"name":"NAS","product":"Plex Media Server","clientIdentifier":"m1","provides":"server","owned":true,
                     "accessToken":"server-token","connections":[{"protocol":"https","address":"192.168.1.5","port":32400,
                     "uri":"https://192-168-1-5.abc.plex.direct:32400","local":true,"relay":false,"IPv6":false}]},
                    {"name":"Phone","product":"Plex for Android","clientIdentifier":"p1","provides":"player,client"},
                    {"name":"Friend","clientIdentifier":"m2","provides":"server","owned":false,"accessToken":"shared-token","connections":[]}]""",
            ),
        )
        val servers = plexTv.servers("account-token")

        assertEquals(listOf("m1", "m2"), servers.map { it.clientIdentifier })
        assertEquals("server-token", servers[0].accessToken)
        assertEquals(true, servers[0].connections.single().local)
        assertEquals("https://192-168-1-5.abc.plex.direct:32400", servers[0].connections.single().uri)
        val request = take()
        assertEquals("/api/v2/resources", request.requestUrl?.encodedPath)
        assertEquals("account-token", request.getHeader("X-Plex-Token"))
    }

    @Test
    fun aRefusedAccountTokenIsA401() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(401))
        val error = expectFailure { plexTv.servers("old-token") }
        assertEquals(401, error.code)
    }

    /** A PIN as plex.tv describes it, without a request. */
    private fun pinOf(json: String): PlexPin = Json { ignoreUnknownKeys = true }.decodeFromString(PlexPin.serializer(), json)
}
