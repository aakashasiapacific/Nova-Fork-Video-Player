package com.aakash.novafork.core.tmdb

import org.junit.Assert.assertEquals
import org.junit.Test

/** Media servers store full artwork URLs where TMDB paths usually go; the URL helpers keep them. */
class ServerImageUrlTest {
    @Test
    fun fullUrlsPassThroughUnchanged() {
        val poster = "http://jellyfin.local:8096/Items/abc/Images/Primary?tag=1"
        assertEquals(poster, TmdbClient.posterUrl(poster))
        assertEquals(poster, TmdbClient.posterUrl(poster, "w342"))
        val backdrop = "HTTPS://plex.example.com/library/metadata/7/art/1700000000"
        assertEquals(backdrop, TmdbClient.backdropUrl(backdrop))
        assertEquals(backdrop, TmdbClient.stillUrl(backdrop))
        // A TMDB path that merely contains "http" is still a path.
        assertEquals("https://image.tmdb.org/t/p/w500/httpabc.jpg", TmdbClient.posterUrl("/httpabc.jpg"))
    }
}
