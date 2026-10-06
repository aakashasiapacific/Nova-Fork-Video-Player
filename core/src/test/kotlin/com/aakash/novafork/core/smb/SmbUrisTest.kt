package com.aakash.novafork.core.smb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmbUrisTest {

    @Test
    fun buildsEncodedUris() {
        assertEquals(
            "smb://nas/Media/Movies/Inception%20%282010%29/Inception.mkv",
            SmbUris.build(SmbLocation("nas", null, "Media", "Movies/Inception (2010)/Inception.mkv")),
        )
        assertEquals("smb://nas/Media", SmbUris.build(SmbLocation("nas", share = "Media")))
        assertEquals("smb://192.168.1.10:4445/share/a.mkv", SmbUris.build(SmbLocation("192.168.1.10", 4445, "share", "a.mkv")))
    }

    @Test
    fun keepsUnreservedCharactersAndEncodesTheRest() {
        assertEquals("smb://h/s/a-b_c.d~e", SmbUris.build("h", "s", "a-b_c.d~e"))
        assertEquals("smb://h/s/100%25%20Real%20%231.mkv", SmbUris.build("h", "s", "100% Real #1.mkv"))
        assertEquals("smb://h/s/Am%C3%A9lie.mkv", SmbUris.build("h", "s", "Amélie.mkv"))
        assertEquals("smb://h/s/%E6%97%A5%E6%9C%AC.mkv", SmbUris.build("h", "s", "日本.mkv"))
        assertEquals("smb://h/My%20Share/a%2Bb%26c%3F.mkv", SmbUris.build("h", "My Share", "a+b&c?.mkv"))
    }

    @Test
    fun keepsTheHostAsGiven() {
        assertEquals("smb://MyNAS/share", SmbUris.build("MyNAS", "share"))
        assertEquals("smb://nas.local:445/share", SmbUris.build("nas.local", "share", port = 445))
    }

    @Test
    fun bracketsIpv6Hosts() {
        assertEquals("smb://[fe80::1]/share", SmbUris.build("fe80::1", "share"))
        assertEquals("smb://[fe80::1]:4445/share", SmbUris.build("[fe80::1]", "share", port = 4445))
        assertEquals(SmbLocation("fe80::1", 4445, "share"), SmbUris.parse("smb://[fe80::1]:4445/share"))
        assertEquals(SmbLocation("fe80::1", null, "share", "x.mkv"), SmbUris.parse("smb://[fe80::1]/share/x.mkv"))
    }

    @Test
    fun buildOverloadTrimsSlashes() {
        assertEquals("smb://nas/Media/Movies/a.mkv", SmbUris.build("nas", "Media", "/Movies/a.mkv/"))
        assertEquals("smb://nas/Media", SmbUris.build("nas", "Media", "/"))
    }

    @Test
    fun parsesEncodedUris() {
        assertEquals(
            SmbLocation("nas", null, "Media", "Movies/Inception (2010)/Inception.mkv"),
            SmbUris.parse("smb://nas/Media/Movies/Inception%20%282010%29/Inception.mkv"),
        )
        assertEquals(SmbLocation("nas", null, "Media", "Amélie.mkv"), SmbUris.parse("smb://nas/Media/Am%c3%a9lie.mkv"))
    }

    @Test
    fun parsesRawUris() {
        assertEquals(
            SmbLocation("nas", null, "My Media", "Movies/100% Real #1.mkv"),
            SmbUris.parse("smb://nas/My Media/Movies/100% Real #1.mkv"),
        )
        assertEquals(SmbLocation("NAS", null, "share"), SmbUris.parse("SMB://NAS/share"))
        assertEquals(SmbLocation("nas", null, "share", "dir"), SmbUris.parse("  smb://nas/share/dir/  "))
        assertEquals(SmbLocation("nas", null, "share", "a/b"), SmbUris.parse("smb://nas//share//a//b/"))
    }

    @Test
    fun stripsUserInfo() {
        assertEquals(SmbLocation("nas", 445, "share", "a.mkv"), SmbUris.parse("smb://user:p%40ss@nas:445/share/a.mkv"))
        assertEquals(SmbLocation("nas", null, "share"), SmbUris.parse("smb://DOMAIN;user:p@ss@nas/share"))
    }

    @Test
    fun rejectsOtherSchemesAndMissingParts() {
        assertNull(SmbUris.parse("http://nas/share"))
        assertNull(SmbUris.parse("content://com.android.providers/document/1"))
        assertNull(SmbUris.parse(""))
        assertNull(SmbUris.parse("smb://"))
        assertNull(SmbUris.parse("smb://nas"))
        assertNull(SmbUris.parse("smb://nas/"))
        assertNull(SmbUris.parse("smb:///share"))
        assertNull(SmbUris.parse("smb://nas:abc/share"))
        assertNull(SmbUris.parse("smb://nas:70000/share"))
        assertNull(SmbUris.parse("smb://[fe80::1/share"))
    }

    @Test
    fun roundTrips() {
        val locations = listOf(
            SmbLocation("nas", null, "Media"),
            SmbLocation("nas", 445, "Media", "Movies"),
            SmbLocation("192.168.0.2", null, "TV Shows", "The Office (2005)/Season 1/The Office - S01E01 - Pilot.mkv"),
            SmbLocation("nas", null, "share", "50% off/#hashtag/a?b=c&d.mkv"),
            SmbLocation("nas", null, "share", "100%25 literal/file.mkv"),
            SmbLocation("nas", null, "共有", "映画/千と千尋の神隠し (2001).mkv"),
            SmbLocation("fe80::1%eth0", 139, "share", "a b/c+d.mkv"),
            SmbLocation("nas", null, "share", "deep/nested/path/with spaces/and émojis 🎬/file.mkv"),
        )
        for (location in locations) {
            assertEquals(location, SmbUris.parse(SmbUris.build(location)))
        }
    }

    @Test
    fun locationHelpers() {
        val file = SmbLocation("nas", null, "Media", "Movies/Inception.mkv")
        assertEquals("Inception.mkv", file.name)
        assertEquals("Movies", file.parentPath)
        assertEquals("Media", SmbLocation("nas", share = "Media").name)
        assertEquals("Movies/Extras", SmbLocation("nas", share = "Media", path = "Movies").child("Extras").path)
        assertEquals("Movies", SmbLocation("nas", share = "Media").child("Movies").path)
    }
}
