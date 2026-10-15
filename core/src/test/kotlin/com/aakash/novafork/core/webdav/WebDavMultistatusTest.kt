package com.aakash.novafork.core.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Multistatus bodies as real servers send them (trimmed to a few entries). */
class WebDavMultistatusTest {

    /** Nextcloud / ownCloud (SabreDAV): "d:" prefix, missing props in a 404 propstat, '(' left raw. */
    private val nextcloud = """
        <?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">
         <d:response>
          <d:href>/remote.php/dav/files/bob/Movies/</d:href>
          <d:propstat>
           <d:prop>
            <d:getlastmodified>Tue, 07 May 2024 18:04:11 GMT</d:getlastmodified>
            <d:resourcetype><d:collection/></d:resourcetype>
           </d:prop>
           <d:status>HTTP/1.1 200 OK</d:status>
          </d:propstat>
          <d:propstat>
           <d:prop><d:getcontentlength/><d:getcontenttype/><d:displayname/></d:prop>
           <d:status>HTTP/1.1 404 Not Found</d:status>
          </d:propstat>
         </d:response>
         <d:response>
          <d:href>/remote.php/dav/files/bob/Movies/Am%C3%A9lie%20(2001)/</d:href>
          <d:propstat>
           <d:prop>
            <d:getlastmodified>Mon, 01 Jan 2024 10:00:00 GMT</d:getlastmodified>
            <d:resourcetype><d:collection/></d:resourcetype>
           </d:prop>
           <d:status>HTTP/1.1 200 OK</d:status>
          </d:propstat>
          <d:propstat>
           <d:prop><d:getcontentlength/><d:getcontenttype/></d:prop>
           <d:status>HTTP/1.1 404 Not Found</d:status>
          </d:propstat>
         </d:response>
         <d:response>
          <d:href>/remote.php/dav/files/bob/Movies/The%20Matrix%20(1999).mkv</d:href>
          <d:propstat>
           <d:prop>
            <d:getlastmodified>Mon, 01 Jan 2024 10:00:00 GMT</d:getlastmodified>
            <d:getcontentlength>1234567890</d:getcontentlength>
            <d:resourcetype/>
            <d:getcontenttype>video/x-matroska</d:getcontenttype>
           </d:prop>
           <d:status>HTTP/1.1 200 OK</d:status>
          </d:propstat>
          <d:propstat>
           <d:prop><d:displayname/></d:prop>
           <d:status>HTTP/1.1 404 Not Found</d:status>
          </d:propstat>
         </d:response>
         <d:response>
          <d:href>/remote.php/dav/files/bob/Movies/Tom%20%26%20Jerry.mp4</d:href>
          <d:propstat>
           <d:prop><d:getcontentlength>42</d:getcontentlength><d:resourcetype/></d:prop>
           <d:status>HTTP/1.1 200 OK</d:status>
          </d:propstat>
         </d:response>
        </d:multistatus>
    """.trimIndent()

    /** Apache httpd mod_dav: "D:" plus "lp1:" live properties, lower-case escapes, unix-directory type. */
    private val apache = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:multistatus xmlns:D="DAV:" xmlns:ns0="DAV:">
        <D:response xmlns:lp1="DAV:" xmlns:lp2="http://apache.org/dav/props/">
        <D:href>/dav/Movies/</D:href>
        <D:propstat>
        <D:prop>
        <lp1:resourcetype><D:collection/></lp1:resourcetype>
        <lp1:getlastmodified>Sat, 03 Feb 2024 12:00:00 GMT</lp1:getlastmodified>
        <D:getcontenttype>httpd/unix-directory</D:getcontenttype>
        </D:prop>
        <D:status>HTTP/1.1 200 OK</D:status>
        </D:propstat>
        <D:propstat>
        <D:prop>
        <ns0:getcontentlength/>
        <ns0:displayname/>
        </D:prop>
        <D:status>HTTP/1.1 404 Not Found</D:status>
        </D:propstat>
        </D:response>
        <D:response xmlns:lp1="DAV:" xmlns:lp2="http://apache.org/dav/props/">
        <D:href>/dav/Movies/Am%c3%a9lie%20(2001).mkv</D:href>
        <D:propstat>
        <D:prop>
        <lp1:resourcetype/>
        <lp1:getcontentlength>734003200</lp1:getcontentlength>
        <lp1:getlastmodified>Sat, 03 Feb 2024 12:00:00 GMT</lp1:getlastmodified>
        <D:getcontenttype>video/x-matroska</D:getcontenttype>
        </D:prop>
        <D:status>HTTP/1.1 200 OK</D:status>
        </D:propstat>
        </D:response>
        <D:response xmlns:lp1="DAV:" xmlns:lp2="http://apache.org/dav/props/">
        <D:href>/dav/Movies/Extras/</D:href>
        <D:propstat>
        <D:prop>
        <lp1:resourcetype><D:collection/></lp1:resourcetype>
        <D:getcontenttype>httpd/unix-directory</D:getcontenttype>
        </D:prop>
        <D:status>HTTP/1.1 200 OK</D:status>
        </D:propstat>
        </D:response>
        <D:response xmlns:lp1="DAV:" xmlns:lp2="http://apache.org/dav/props/">
        <D:href>/dav/Movies/poster.jpg</D:href>
        <D:propstat>
        <D:prop>
        <lp1:resourcetype/>
        <lp1:getcontentlength>51234</lp1:getcontentlength>
        <D:getcontenttype>image/jpeg</D:getcontenttype>
        </D:prop>
        <D:status>HTTP/1.1 200 OK</D:status>
        </D:propstat>
        </D:response>
        </D:multistatus>
    """.trimIndent()

    /** rclone serve webdav (golang.org/x/net/webdav): one line, root "/", namespace redeclared on collection. */
    private val rclone =
        """<?xml version="1.0" encoding="UTF-8"?><D:multistatus xmlns:D="DAV:"><D:response><D:href>/</D:href>""" +
            """<D:propstat><D:prop><D:resourcetype><D:collection xmlns:D="DAV:"/></D:resourcetype><D:displayname></D:displayname>""" +
            """<D:getlastmodified>Mon, 05 Feb 2024 09:30:00 GMT</D:getlastmodified></D:prop><D:status>HTTP/1.1 200 OK</D:status>""" +
            """</D:propstat></D:response><D:response><D:href>/TV%20Shows/</D:href><D:propstat><D:prop><D:resourcetype>""" +
            """<D:collection xmlns:D="DAV:"/></D:resourcetype><D:displayname>TV Shows</D:displayname>""" +
            """<D:getlastmodified>Mon, 05 Feb 2024 09:30:00 GMT</D:getlastmodified></D:prop><D:status>HTTP/1.1 200 OK</D:status>""" +
            """</D:propstat></D:response><D:response><D:href>/Heat%20%281995%29.mkv</D:href><D:propstat><D:prop>""" +
            """<D:resourcetype></D:resourcetype><D:displayname>Heat (1995).mkv</D:displayname>""" +
            """<D:getlastmodified>Mon, 05 Feb 2024 09:30:00 GMT</D:getlastmodified><D:getcontenttype>video/x-matroska</D:getcontenttype>""" +
            """<D:getcontentlength>987654321</D:getcontentlength><D:getetag>"17b0f5c3a1e2"</D:getetag></D:prop>""" +
            """<D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response></D:multistatus>"""

    /** IIS: full URLs in hrefs, status before prop, upper-case folder name in the href. */
    private val iis = """
        <?xml version="1.0" encoding="utf-8"?><D:multistatus xmlns:D="DAV:"><D:response><D:href>http://server/dav/MOVIES/</D:href><D:propstat><D:status>HTTP/1.1 200 OK</D:status><D:prop><D:getcontenttype/><D:getlastmodified>Tue, 06 Feb 2024 08:00:00 GMT</D:getlastmodified><D:getcontentlength>0</D:getcontentlength><D:resourcetype><D:collection/></D:resourcetype><D:displayname>MOVIES</D:displayname></D:prop></D:propstat></D:response><D:response><D:href>http://server/dav/MOVIES/Up.mp4</D:href><D:propstat><D:status>HTTP/1.1 200 OK</D:status><D:prop><D:getcontenttype>video/mp4</D:getcontenttype><D:getlastmodified>Tue, 06 Feb 2024 08:00:00 GMT</D:getlastmodified><D:getcontentlength>555</D:getcontentlength><D:resourcetype/><D:displayname>Up.mp4</D:displayname></D:prop></D:propstat></D:response></D:multistatus>
    """.trimIndent()

    @Test
    fun nextcloudListing() {
        val responses = WebDavMultistatus.parse(nextcloud)
        assertEquals(4, responses.size)
        assertTrue(responses[0].isCollection)
        assertEquals(1715105051000L, responses[0].lastModified)

        val entries = DavPaths.children(responses, listOf("remote.php", "dav", "files", "bob", "Movies"))
        assertEquals(listOf("Amélie (2001)", "The Matrix (1999).mkv", "Tom & Jerry.mp4"), entries.map { it.name })
        val folder = entries[0]
        assertTrue(folder.isCollection)
        assertNull(folder.sizeBytes)
        val movie = entries[1]
        assertFalse(movie.isCollection)
        assertEquals(1234567890L, movie.sizeBytes)
        assertEquals("video/x-matroska", movie.contentType)
        assertEquals(1704103200000L, movie.modifiedAt)
        // The 404 propstat for displayname does not erase anything.
        assertNull(movie.displayName)
    }

    @Test
    fun apacheListing() {
        val responses = WebDavMultistatus.parse(apache)
        val entries = DavPaths.children(responses, listOf("dav", "Movies"))
        assertEquals(listOf("Amélie (2001).mkv", "Extras", "poster.jpg"), entries.map { it.name })
        assertEquals(listOf(false, true, false), entries.map { it.isCollection })
        assertEquals(734003200L, entries[0].sizeBytes)
        // The directory content type marks a collection and is not reported as a file type.
        assertNull(entries[1].contentType)
        assertEquals("image/jpeg", entries[2].contentType)
    }

    @Test
    fun rcloneListingAtTheRoot() {
        val responses = WebDavMultistatus.parse(rclone)
        assertEquals(3, responses.size)
        val entries = DavPaths.children(responses, emptyList())
        assertEquals(listOf("TV Shows", "Heat (1995).mkv"), entries.map { it.name })
        assertTrue(entries[0].isCollection)
        assertEquals("TV Shows", entries[0].displayName)
        assertEquals(987654321L, entries[1].sizeBytes)
    }

    @Test
    fun iisFullUrlHrefsAndCaseInsensitiveSelf() {
        val responses = WebDavMultistatus.parse(iis)
        val entries = DavPaths.children(responses, listOf("dav", "Movies"))
        assertEquals(listOf("Up.mp4"), entries.map { it.name })
        assertEquals(555L, entries[0].sizeBytes)
    }

    @Test
    fun defaultNamespaceWithoutPrefix() {
        val xml = """
            <multistatus xmlns="DAV:">
              <response><href>/share/</href><propstat><prop><resourcetype><collection/></resourcetype></prop><status>HTTP/1.1 200 OK</status></propstat></response>
              <response><href>/share/clip.webm</href><propstat><prop><resourcetype/><getcontentlength>9</getcontentlength></prop><status>HTTP/1.1 200 OK</status></propstat></response>
            </multistatus>
        """.trimIndent()
        val entries = DavPaths.children(WebDavMultistatus.parse(xml), listOf("share"))
        assertEquals(listOf("clip.webm"), entries.map { it.name })
        assertEquals(9L, entries[0].sizeBytes)
    }

    @Test
    fun unboundPrefixIsReadAsDav() {
        val xml = "<D:multistatus><D:response><D:href>/a/b.mkv</D:href><D:propstat><D:prop>" +
            "<D:getcontentlength>5</D:getcontentlength></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response></D:multistatus>"
        val responses = WebDavMultistatus.parse(xml)
        assertEquals(1, responses.size)
        assertEquals(5L, responses[0].contentLength)
    }

    @Test
    fun otherNamespacesAreIgnored() {
        val xml = """
            <d:multistatus xmlns:d="DAV:" xmlns:x="urn:example">
              <d:response><d:href>/a/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
              <d:response><d:href>/a/b.mkv</d:href><x:href>/elsewhere</x:href><d:propstat><d:prop>
                <x:getcontentlength>999</x:getcontentlength><d:getcontentlength>7</d:getcontentlength></d:prop></d:propstat></d:response>
            </d:multistatus>
        """.trimIndent()
        val responses = WebDavMultistatus.parse(xml)
        assertEquals("/a/b.mkv", responses[1].href)
        assertEquals(7L, responses[1].contentLength)
    }

    @Test
    fun failedResponsesAndPropstatsAreSkipped() {
        val xml = """
            <D:multistatus xmlns:D="DAV:">
              <D:response><D:href>/a/</D:href><D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
              <D:response><D:href>/a/secret/</D:href><D:status>HTTP/1.1 403 Forbidden</D:status></D:response>
              <D:response><D:href>/a/x.mkv</D:href>
                <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop><D:status>HTTP/1.1 500 Server Error</D:status></D:propstat>
                <D:propstat><D:prop><D:getcontentlength>3</D:getcontentlength></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()
        val entries = DavPaths.children(WebDavMultistatus.parse(xml), listOf("a"))
        assertEquals(listOf("x.mkv"), entries.map { it.name })
        assertFalse(entries[0].isCollection)
        assertEquals(3L, entries[0].sizeBytes)
    }

    @Test
    fun entitiesCdataCommentsAndDoctypeAreHandled() {
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE multistatus [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <!-- a comment with <D:href>/fake</D:href> inside -->
            <D:multistatus xmlns:D='DAV:'>
              <D:response><D:href>/a/</D:href><D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat></D:response>
              <D:response><D:href><![CDATA[/a/Fast & Furious.mkv]]></D:href></D:response>
              <D:response><D:href>/a/Caf&#233;&#x20;&amp;&#x2F;x&xxe;.mkv</D:href></D:response>
            </D:multistatus>
        """.trimIndent()
        val responses = WebDavMultistatus.parse(xml)
        assertEquals(listOf("/a/", "/a/Fast & Furious.mkv", "/a/Café &/x&xxe;.mkv"), responses.map { it.href })
    }

    @Test
    fun truncatedBodyKeepsCompleteResponses() {
        val cut = apache.substring(0, apache.indexOf("/dav/Movies/Extras/"))
        val responses = WebDavMultistatus.parse(cut)
        assertEquals(listOf("/dav/Movies/", "/dav/Movies/Am%c3%a9lie%20(2001).mkv"), responses.map { it.href })
    }

    @Test
    fun hrefSegmentsAreDecoded() {
        assertEquals(listOf("dav", "a b", "Amélie"), DavPaths.segments("/dav/a%20b/Am%C3%A9lie/"))
        assertEquals(listOf("dav", "x+y.mkv"), DavPaths.segments("https://host:8443/dav/x+y.mkv?v=1#t"))
        assertEquals(listOf("100% real.mkv"), DavPaths.segments("/100% real.mkv"))
        assertEquals(emptyList<String>(), DavPaths.segments("http://host"))
        assertEquals("a%2Fb%20%28c%29~", DavPaths.encodeSegment("a/b (c)~"))
    }

    @Test
    fun statusAndDateParsing() {
        assertEquals(207, WebDavMultistatus.statusCode("HTTP/1.1 207 Multi-Status"))
        assertNull(WebDavMultistatus.statusCode("garbage"))
        assertEquals(784111777000L, WebDavMultistatus.parseDate("Sun, 06 Nov 1994 08:49:37 GMT"))
        assertEquals(784111777000L, WebDavMultistatus.parseDate("1994-11-06T08:49:37Z"))
        assertNull(WebDavMultistatus.parseDate("yesterday"))
    }
}
