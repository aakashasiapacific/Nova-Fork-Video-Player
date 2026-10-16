package com.aakash.novafork.sources.net

import com.aakash.novafork.core.webdav.WebDavClient
import com.aakash.novafork.data.NetworkLocation
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException
import java.util.Collections

/** A WebDAV share served from memory: folders by decoded path, answered as Nextcloud would. */
class WebDavSourceTest {
    private val requests: MutableList<Request> = Collections.synchronizedList(ArrayList())

    /** Decoded folder path below "/dav" → its entries (a trailing '/' marks a folder). */
    private val tree = mapOf(
        "" to listOf("Movies/", "TV/", ".Trash/"),
        "Movies" to listOf("Heat (1995)/", "Up (2009).mp4", "Amélie (2001).mkv", "poster.jpg", "notes.txt"),
        "Movies/Heat (1995)" to listOf("Heat (1995).mkv", "Heat (1995)-poster.jpg", "fanart.jpg", "sample.mkv"),
        "TV" to listOf("Severance/"),
        "TV/Severance" to listOf("poster.jpg", "fanart.jpg", "Season 1/", "Sample/"),
        "TV/Severance/Season 1" to listOf("Severance S01E01.mkv", "Severance S01E01-thumb.jpg"),
        "TV/Severance/Sample" to listOf("clip.mkv"),
    )

    private fun server(status: (Request) -> Int = { 207 }): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                requests += request
                val code = status(request)
                val path = request.url.pathSegments.filter { it.isNotEmpty() }.drop(1).joinToString("/")
                val entries = tree[path]
                val body = if (code == 207 && entries != null) multistatus(path, entries) else ""
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (code == 207 && entries == null) 404 else code)
                    .message("x")
                    .body(body.toResponseBody("application/xml; charset=utf-8".toMediaType()))
                    .build()
            },
        ).build()

    private fun href(path: String): String =
        "/dav/" + path.split('/').filter { it.isNotEmpty() }.joinToString("/") { NetworkLocation.encodeSegment(it) }

    private fun multistatus(path: String, entries: List<String>): String {
        val self = "<d:response><d:href>${href(path)}/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype>" +
            "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
        val children = entries.joinToString("") { entry ->
            val folder = entry.endsWith("/")
            val child = if (path.isEmpty()) entry.trimEnd('/') else "$path/${entry.trimEnd('/')}"
            "<d:response><d:href>${href(child)}${if (folder) "/" else ""}</d:href><d:propstat><d:prop>" +
                (if (folder) "<d:resourcetype><d:collection/></d:resourcetype>" else "<d:resourcetype/><d:getcontentlength>10</d:getcontentlength>") +
                "<d:getlastmodified>Mon, 01 Jan 2024 10:00:00 GMT</d:getlastmodified>" +
                "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
        }
        return "<?xml version=\"1.0\"?><d:multistatus xmlns:d=\"DAV:\">$self$children</d:multistatus>"
    }

    private val source = SourceEntity(
        id = 3,
        type = SourceType.WEBDAV,
        name = "cloud",
        location = "https://cloud.example.com/dav",
        username = "bob",
        password = "s3cret:@/",
    )

    @Test
    fun listShowsFoldersAndVideosInOrder() = runTest {
        val webDav = WebDavSource(WebDavClient(server()))
        val entries = webDav.list(source, "Movies")
        assertEquals(listOf("Heat (1995)", "Amélie (2001).mkv", "Up (2009).mp4"), entries.map { it.name })
        assertEquals("Movies/Heat (1995)", entries[0].path)
        assertTrue(entries[0].isDirectory)
        assertEquals("https://cloud.example.com/dav/Movies/Am%C3%A9lie%20%282001%29.mkv", entries[1].playUri)
        assertEquals(10L, entries[1].sizeBytes)
        assertEquals(1704103200000L, entries[1].modifiedAt)

        val request = requests.single()
        assertEquals("PROPFIND", request.method)
        assertEquals("/dav/Movies/", request.url.encodedPath)
        assertTrue(request.header("Authorization")!!.startsWith("Basic "))
    }

    @Test
    fun scanWalksFoldersWithArtwork() = runTest {
        val webDav = WebDavSource(WebDavClient(server()))
        val found = webDav.scan(source) {}.associateBy { it.fileName }
        assertEquals(setOf("Amélie (2001).mkv", "Up (2009).mp4", "Heat (1995).mkv", "Severance S01E01.mkv"), found.keys)

        val heat = found.getValue("Heat (1995).mkv")
        assertEquals("https://cloud.example.com/dav/Movies/Heat%20%281995%29/Heat%20%281995%29.mkv", heat.uri)
        assertEquals("Movies/Heat (1995)", heat.folder)
        assertEquals(listOf("Heat (1995)", "Movies", "dav"), heat.parentFolders)
        assertEquals("https://cloud.example.com/dav/Movies/Heat%20%281995%29/Heat%20%281995%29-poster.jpg", heat.localPoster)
        assertEquals("https://cloud.example.com/dav/Movies/Heat%20%281995%29/fanart.jpg", heat.localBackdrop)
        assertEquals(1704103200000L, heat.dateAdded)

        // Two videos share "Movies": its generic poster.jpg belongs to neither.
        assertNull(found.getValue("Amélie (2001).mkv").localPoster)

        val episode = found.getValue("Severance S01E01.mkv")
        assertEquals("TV/Severance/Season 1", episode.folder)
        assertEquals("https://cloud.example.com/dav/TV/Severance/poster.jpg", episode.localPoster)
        assertEquals("https://cloud.example.com/dav/TV/Severance/fanart.jpg", episode.localBackdrop)
        assertEquals("https://cloud.example.com/dav/TV/Severance/Season%201/Severance%20S01E01-thumb.jpg", episode.localThumb)

        // Hidden and sample folders are never listed.
        assertTrue(requests.none { it.url.encodedPath.contains("Trash") || it.url.encodedPath.contains("Sample") })
    }

    @Test
    fun aRefusedLoginFailsTheTestWithAMessage() = runTest {
        val webDav = WebDavSource(WebDavClient(server { 401 }))
        val result = webDav.test(source)
        assertEquals("The user name or password is wrong", result.exceptionOrNull()?.message)
    }

    @Test
    fun aMissingSubfolderIsSkippedButARefusedLoginEndsTheScan() = runTest {
        val skipping = WebDavSource(WebDavClient(server { if (it.url.encodedPath.startsWith("/dav/TV/")) 403 else 207 }))
        assertEquals(
            setOf("Amélie (2001).mkv", "Up (2009).mp4", "Heat (1995).mkv"),
            skipping.scan(source) {}.map { it.fileName }.toSet(),
        )
        val refusing = WebDavSource(WebDavClient(server { if (it.url.encodedPath.startsWith("/dav/TV/")) 401 else 207 }))
        try {
            refusing.scan(source) {}
            throw AssertionError("A refused login must fail the scan")
        } catch (e: IOException) {
            assertEquals("The user name or password is wrong", e.message)
        }
    }

    @Test
    fun playbackAddsTheLoginAsUserInfo() = runTest {
        val webDav = WebDavSource(WebDavClient(server()))
        val uri = "https://cloud.example.com/dav/Movies/Heat%20%281995%29.mkv"
        assertEquals("https://bob:s3cret%3A%40%2F@cloud.example.com/dav/Movies/Heat%20%281995%29.mkv", webDav.playUri(source, uri))
        // Never to another server, never twice, never without a login.
        assertEquals("https://other.example.com/x.mkv", webDav.playUri(source, "https://other.example.com/x.mkv"))
        assertEquals("https://me@cloud.example.com/dav/x.mkv", webDav.playUri(source, "https://me@cloud.example.com/dav/x.mkv"))
        assertEquals(uri, webDav.playUri(source.copy(username = null), uri))
        assertEquals(emptyList<String>(), webDav.mediaOptions(source, uri))
    }

    @Test
    fun networkFailuresReadAsMessages() {
        val location = requireNotNull(NetworkLocation.parse("https://cloud.example.com:8443/dav"))
        assertEquals(
            "Can't find cloud.example.com on the network",
            WebDavRules.networkMessage(location, UnknownHostException("cloud.example.com")),
        )
        assertEquals(
            "Can't connect to cloud.example.com:8443",
            WebDavRules.networkMessage(location, java.net.ConnectException("refused")),
        )
    }
}
