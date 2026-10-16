package com.aakash.novafork.core.webdav

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Base64
import java.util.concurrent.TimeUnit

class WebDavClientTest {
    private lateinit var server: MockWebServer
    private val client = WebDavClient(OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun multistatus(vararg responses: String): MockResponse =
        MockResponse()
            .setResponseCode(207)
            .setHeader("Content-Type", "application/xml; charset=utf-8")
            .setBody("""<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">${responses.joinToString("")}</d:multistatus>""")

    private fun response(href: String, collection: Boolean, length: Long? = null): String =
        "<d:response><d:href>$href</d:href><d:propstat><d:prop>" +
            (if (collection) "<d:resourcetype><d:collection/></d:resourcetype>" else "<d:resourcetype/>") +
            (length?.let { "<d:getcontentlength>$it</d:getcontentlength>" } ?: "") +
            "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"

    @Test
    fun listsWithPropfindDepthOneAndBasicAuth() = runTest {
        server.enqueue(
            multistatus(
                response("/dav/My%20Movies/", collection = true),
                response("/dav/My%20Movies/Heat%20(1995).mkv", collection = false, length = 100),
                response("/dav/My%20Movies/Season%201/", collection = true),
                response("/dav/My%20Movies/.hidden.mkv", collection = false, length = 1),
            ),
        )
        val entries = client.list(server.url("/dav/My%20Movies").toString(), "bob", "pä:ss")

        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        // The collection is asked for with its trailing slash, so no redirect is needed.
        assertEquals("/dav/My%20Movies/", request.path)
        assertEquals("1", request.getHeader("Depth"))
        val expected = "Basic " + Base64.getEncoder().encodeToString("bob:pä:ss".toByteArray(Charsets.UTF_8))
        assertEquals(expected, request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("<D:resourcetype/>"))

        assertEquals(listOf("Heat (1995).mkv", "Season 1"), entries.map { it.name })
        assertEquals(100L, entries[0].sizeBytes)
        assertTrue(entries[1].isCollection)
    }

    @Test
    fun noLoginSendsNoAuthorization() = runTest {
        server.enqueue(multistatus(response("/", collection = true)))
        val entries = client.list(server.url("/").toString(), null, null)
        assertEquals(emptyList<DavEntry>(), entries)
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun statusCodesBecomeUserMessages() = runTest {
        for ((code, text) in listOf(
            401 to "The user name or password is wrong",
            403 to "The server doesn't allow access to this folder",
            404 to "This folder doesn't exist on the server",
            405 to "The server doesn't offer WebDAV at this address",
            503 to "The server failed (HTTP 503)",
        )) {
            server.enqueue(MockResponse().setResponseCode(code))
            try {
                client.list(server.url("/dav/").toString(), "bob", "x")
                fail("HTTP $code should fail")
            } catch (e: WebDavException) {
                assertEquals(code, e.status)
                assertEquals(text, e.message)
            }
        }
    }

    @Test
    fun anHtmlPageIsNotAListing() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html><body>Welcome</body></html>"))
        try {
            client.list(server.url("/").toString(), null, null)
            fail("HTML should not pass for a listing")
        } catch (e: WebDavException) {
            assertEquals(0, e.status)
        }
    }

    @Test
    fun followsASameHostRedirectWithPropfind() = runTest {
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "/real/"))
        server.enqueue(multistatus(response("/real/", collection = true), response("/real/a.mp4", collection = false)))
        val entries = client.list(server.url("/old/").toString(), "bob", "pw")
        assertEquals(listOf("a.mp4"), entries.map { it.name })
        server.takeRequest()
        val followed = server.takeRequest()
        assertEquals("PROPFIND", followed.method)
        assertEquals("/real/", followed.path)
        assertTrue(followed.getHeader("Authorization")!!.startsWith("Basic "))
    }

    @Test
    fun invalidAddressIsRejected() = runTest {
        try {
            client.list("ftp://nas/videos", null, null)
            fail("Only http(s) is WebDAV")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("ftp://nas/videos"))
        }
    }
}
