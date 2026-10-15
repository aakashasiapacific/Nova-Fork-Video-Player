package com.aakash.novafork.sources

import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import okhttp3.Credentials
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.StandardCharsets

/** Which artwork requests get a source's login or token from the image loader. */
class SourceImageAuthTest {
    private val webDav = SourceEntity(
        id = 1,
        type = SourceType.WEBDAV,
        name = "Cloud",
        location = "https://cloud.example.com/remote.php/dav/Movies",
        username = "ann",
        password = "s3cret",
    )
    private val plex = SourceEntity(id = 2, type = SourceType.PLEX, name = "Plex", location = "http://192.168.1.5:32400", token = "plex-tok")
    private val jellyfin = SourceEntity(
        id = 3,
        type = SourceType.JELLYFIN,
        name = "Jellyfin",
        location = "http://jf:8096",
        username = "ann",
        password = "pw",
        token = "jf-tok",
    )

    private val interceptor = SourceImageAuthInterceptor(CoroutineScope(Dispatchers.Unconfined)) { flowOf(listOf(webDav, plex, jellyfin)) }

    private fun authorize(url: String, vararg headers: Pair<String, String>): Request {
        val builder = Request.Builder().url(url)
        for ((name, value) in headers) builder.header(name, value)
        return interceptor.authorize(builder.build())
    }

    @Test
    fun plexArtworkGetsTheTokenAsAHeader() {
        val image = authorize("http://192.168.1.5:32400/photo/:/transcode?width=500&height=750&url=%2Flibrary%2Fmetadata%2F1%2Fthumb")
        assertEquals("plex-tok", image.header("X-Plex-Token"))
        assertNull(image.url.queryParameter("X-Plex-Token"))
        assertNull(image.header("Authorization"))
        // Another server, or the same host on another port or scheme, gets nothing.
        assertNull(authorize("http://192.168.1.6:32400/photo/:/transcode").header("X-Plex-Token"))
        assertNull(authorize("http://192.168.1.5:8080/poster.jpg").header("X-Plex-Token"))
        assertNull(authorize("https://192.168.1.5:32400/poster.jpg").header("X-Plex-Token"))
        assertNull(authorize("https://image.tmdb.org/t/p/w500/poster.jpg").header("X-Plex-Token"))
        // A token the request brings along stays.
        assertEquals("other", authorize("http://192.168.1.5:32400/photo/:/transcode", "X-Plex-Token" to "other").header("X-Plex-Token"))
    }

    @Test
    fun webDavArtworkGetsTheLogin() {
        val poster = authorize("https://cloud.example.com/remote.php/dav/Movies/Heat%20(1995)/poster.jpg")
        assertEquals(Credentials.basic("ann", "s3cret", StandardCharsets.UTF_8), poster.header("Authorization"))
        assertNull(poster.header("X-Plex-Token"))
        // Outside the source's folder, or on another server: no login.
        assertNull(authorize("https://cloud.example.com/remote.php/dav/Other/poster.jpg").header("Authorization"))
        assertNull(authorize("http://cloud.example.com/remote.php/dav/Movies/poster.jpg").header("Authorization"))
        // A request with a login of its own keeps it.
        assertEquals(
            "Bearer x",
            authorize("https://cloud.example.com/remote.php/dav/Movies/poster.jpg", "Authorization" to "Bearer x").header("Authorization"),
        )
    }

    @Test
    fun otherSourcesNeedNothing() {
        assertNull(SourceImageAuth.of(jellyfin))
        assertNull(SourceImageAuth.of(plex.copy(token = null)))
        assertNull(SourceImageAuth.of(plex.copy(token = "tök")))
        assertNull(SourceImageAuth.of(webDav.copy(username = null)))
        assertEquals(0, authorize("http://jf:8096/Items/1/Images/Primary?tag=t").headers.size)
    }
}
