package com.aakash.novafork.data

import com.aakash.novafork.data.db.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** How typed addresses of the network source types become stored sources ([NetworkSources]), and [NetworkLocation]. */
class NetworkSourcesTest {
    private fun source(type: SourceType, address: String, port: Int? = null, username: String? = null) =
        NetworkSources.toSource(NetworkSourceInput(type = type, address = address, port = port, username = username))

    private fun refused(input: NetworkSourceInput): String {
        try {
            NetworkSources.toSource(input)
        } catch (e: IllegalArgumentException) {
            return e.message.orEmpty()
        }
        fail("Accepted $input")
        throw AssertionError()
    }

    @Test
    fun fileServerAddressesBecomeEncodedBaseUris() {
        source(SourceType.FTP, " nas.local/My Movies/ ").let {
            assertEquals("ftp://nas.local/My%20Movies", it.location)
            assertNull(it.port)
            assertEquals("nas.local/My Movies", it.name)
            assertEquals(0L, it.id)
        }
        // Encoded or not, the same folder gives the same location.
        assertEquals("ftp://nas.local/My%20Movies", source(SourceType.FTP, "ftp://nas.local/My%20Movies").location)
        source(SourceType.FTP, "FTPS://Nas.Local:2121/Vidéos").let {
            assertEquals("ftps://Nas.Local:2121/Vid%C3%A9os", it.location)
            assertEquals(2121, it.port)
        }
        // A default port is left out; an explicit port field wins over the address's.
        source(SourceType.SFTP, "sftp://nas:22/home/me").let {
            assertEquals("sftp://nas/home/me", it.location)
            assertNull(it.port)
        }
        source(SourceType.SFTP, "nas:2200/home", port = 2222).let {
            assertEquals("sftp://nas:2222/home", it.location)
            assertEquals(2222, it.port)
        }
        assertEquals("nfs://192.168.1.2/export/video", source(SourceType.NFS, "192.168.1.2/export/video/").location)
        assertEquals("ftp://[fe80::1]:21000/media", source(SourceType.FTP, "[fe80::1]:21000/media").location)
    }

    @Test
    fun webDavDefaultsToHttpsAndTakesDavSchemes() {
        assertEquals("https://dav.example.com/remote.php/dav", source(SourceType.WEBDAV, "dav.example.com/remote.php/dav/").location)
        assertEquals("http://nas.local:5005/video", source(SourceType.WEBDAV, "dav://nas.local:5005/video").location)
        assertEquals("https://nas.local/video", source(SourceType.WEBDAV, "davs://nas.local:443/video").location)
        assertTrue(refused(NetworkSourceInput(SourceType.WEBDAV, address = "ftp://nas/video")).contains("https://"))
    }

    @Test
    fun mediaServersGetTheirUsualPortWhenTypedBare() {
        source(SourceType.JELLYFIN, "jellyfin.local", username = "alice").let {
            assertEquals("http://jellyfin.local:8096", it.location)
            assertEquals(8096, it.port)
            assertEquals("jellyfin.local", it.name)
        }
        assertEquals("http://emby.local:8920", source(SourceType.EMBY, "emby.local:8920", username = "bob").location)
        assertEquals("http://plex.local:32400", source(SourceType.PLEX, "plex.local").location)
        // A typed scheme means the address is complete: a reverse proxy on 443 with a base path.
        source(SourceType.JELLYFIN, "https://example.com/jellyfin/", username = "alice").let {
            assertEquals("https://example.com/jellyfin", it.location)
            assertNull(it.port)
            assertEquals("example.com", it.name)
        }
        // Query and fragment are not part of a base URL.
        assertEquals("http://plex.local:32400/web", source(SourceType.PLEX, "http://plex.local:32400/web/?x=1#y").location)
    }

    @Test
    fun aMediaServerOnTheInternetTypedBareIsAskedOverHttps() {
        source(SourceType.JELLYFIN, "jellyfin.example.com", username = "alice").let {
            assertEquals("https://jellyfin.example.com", it.location)
            assertNull(it.port)
        }
        assertEquals("https://emby.example.com:8920", source(SourceType.EMBY, "emby.example.com:8920", username = "bob").location)
        // At home it stays on the server's own http port; a typed scheme is always kept.
        assertEquals("http://192.168.1.5:8096", source(SourceType.JELLYFIN, "192.168.1.5", username = "alice").location)
        assertEquals("http://jellyfin:8096", source(SourceType.JELLYFIN, "jellyfin", username = "alice").location)
        assertEquals("http://100.101.102.103:8096", source(SourceType.JELLYFIN, "100.101.102.103", username = "alice").location)
        assertEquals("http://jellyfin.example.com:8096", source(SourceType.JELLYFIN, "http://jellyfin.example.com:8096", username = "alice").location)
        // Plex on 32400 has no certificate for a name of its own.
        assertEquals("http://plex.example.com:32400", source(SourceType.PLEX, "plex.example.com").location)
    }

    @Test
    fun loginsThatWouldCrossTheInternetUnencryptedAreWarnedAbout() {
        fun warning(type: SourceType, address: String, username: String? = null, token: String? = null): String? =
            NetworkSources.cleartextWarning(
                NetworkSources.toSource(NetworkSourceInput(type = type, address = address, username = username, password = "pw", token = token)),
            )

        val webDav = warning(SourceType.WEBDAV, "http://cloud.example.com/remote.php/dav", username = "ann")
        assertTrue(webDav.orEmpty(), webDav.orEmpty().startsWith("Not encrypted: your password would cross the internet to cloud.example.com"))
        assertTrue(webDav.orEmpty(), webDav.orEmpty().contains("https://"))
        assertTrue(warning(SourceType.JELLYFIN, "http://jellyfin.example.com:8096", username = "ann").orEmpty().contains("your password and sign-in"))
        assertTrue(warning(SourceType.EMBY, "http://203.0.113.5:8096", username = "ann") != null)
        assertTrue(warning(SourceType.PLEX, "plex.example.com", token = "tok").orEmpty().contains("your Plex token"))
        assertTrue(warning(SourceType.FTP, "ftp.example.com/videos", username = "ann").orEmpty().contains("ftps:// or ftpes://"))

        // Encrypted, at home, or nothing secret to send: no warning.
        assertNull(warning(SourceType.WEBDAV, "cloud.example.com/remote.php/dav", username = "ann"))
        assertNull(warning(SourceType.WEBDAV, "http://nas.local:5005/video", username = "ann"))
        assertNull(warning(SourceType.WEBDAV, "http://cloud.example.com/public"))
        assertNull(warning(SourceType.JELLYFIN, "jellyfin.example.com", username = "ann"))
        assertNull(warning(SourceType.JELLYFIN, "http://192.168.1.5:8096", username = "ann"))
        assertNull(warning(SourceType.PLEX, "plex.example.com"))
        assertNull(warning(SourceType.PLEX, "https://1-2-3-4.abc.plex.direct:32400", token = "tok"))
        assertNull(warning(SourceType.FTP, "ftpes://ftp.example.com", username = "ann"))
        assertNull(warning(SourceType.FTP, "ftp.example.com"))
        assertNull(warning(SourceType.SFTP, "sftp.example.com", username = "ann"))
    }

    @Test
    fun upnpTakesADescriptionUrlOrAUdn() {
        source(SourceType.UPNP, "uuid:4d696e69-444c-164e-9d41-001e0680ae1d").let {
            assertEquals("uuid:4d696e69-444c-164e-9d41-001e0680ae1d", it.location)
            assertEquals(it.location, it.serverId)
            assertEquals("UPnP", it.name)
        }
        source(SourceType.UPNP, "http://192.168.1.5:49152/rootDesc.xml?v=2").let {
            assertEquals("http://192.168.1.5:49152/rootDesc.xml?v=2", it.location)
            assertEquals("192.168.1.5", it.name)
        }
        assertEquals("Enter the device's description URL or UDN", refused(NetworkSourceInput(SourceType.UPNP, address = "uuid: ")))
    }

    @Test
    fun credentialsLeaveTheAddressForTheirColumns() {
        val ftp = NetworkSources.toSource(NetworkSourceInput(SourceType.FTP, address = "ftp://bob:p%40ss@nas/videos"))
        assertEquals("ftp://nas/videos", ftp.location)
        assertEquals("bob", ftp.username)
        assertEquals("p@ss", ftp.password)
        // Typed fields win over the address's user info.
        val typed = NetworkSources.toSource(
            NetworkSourceInput(SourceType.FTP, address = "ftp://bob:old@nas/videos", username = " carol ", password = "new"),
        )
        assertEquals("carol", typed.username)
        assertEquals("new", typed.password)
        // Blank fields are stored as null; library ids are trimmed, unique and comma-joined.
        val jellyfin = NetworkSources.toSource(
            NetworkSourceInput(
                type = SourceType.JELLYFIN,
                name = "  Home  ",
                address = "jf.local",
                username = "alice",
                password = "",
                domain = " ",
                token = " abc ",
                libraryIds = listOf(" lib1", "lib2", "lib1", " "),
            ),
        )
        assertEquals("Home", jellyfin.name)
        assertNull(jellyfin.password)
        assertNull(jellyfin.domain)
        assertEquals("abc", jellyfin.token)
        assertEquals("lib1,lib2", jellyfin.libraryIds)
        assertEquals(listOf("lib1", "lib2"), NetworkSources.libraryIds(jellyfin))
        assertEquals(emptyList<String>(), NetworkSources.libraryIds(jellyfin.copy(libraryIds = null)))
    }

    @Test
    fun badInputIsRefusedWithAMessage() {
        assertEquals("Enter the server name or address", refused(NetworkSourceInput(SourceType.FTP, address = "  ")))
        assertEquals("Enter the server address", refused(NetworkSourceInput(SourceType.PLEX, address = "")))
        assertEquals("Enter the server name or address", refused(NetworkSourceInput(SourceType.FTP, address = "ftp:///videos")))
        assertEquals("The port must be between 1 and 65535", refused(NetworkSourceInput(SourceType.FTP, address = "nas:70000")))
        assertEquals("The port must be between 1 and 65535", refused(NetworkSourceInput(SourceType.FTP, address = "nas", port = 0)))
        assertEquals("FTP addresses start with ftp:// or ftps:// or ftpes://", refused(NetworkSourceInput(SourceType.FTP, address = "http://nas")))
        assertEquals("Enter the user name", refused(NetworkSourceInput(SourceType.JELLYFIN, address = "jf.local")))
        assertEquals("Enter the user name", refused(NetworkSourceInput(SourceType.EMBY, address = "emby.local", username = " ")))
        assertTrue(refused(NetworkSourceInput(SourceType.JELLYFIN, address = "jf", token = "t", libraryIds = listOf("a,b"))).contains("comma"))
        // SMB and the local types have their own ways in.
        assertTrue(refused(NetworkSourceInput(SourceType.SMB, address = "smb://nas/share")).isNotEmpty())
        assertTrue(refused(NetworkSourceInput(SourceType.STREAM, address = "http://x/live.m3u8")).isNotEmpty())
        // Plex may be open to the local network without a token.
        assertNull(source(SourceType.PLEX, "plex.local").token)
    }

    @Test
    fun everyNetworkTypeHasSchemes() {
        for (type in NetworkSources.TYPES) assertTrue(type.name, NetworkSources.schemes(type).isNotEmpty())
        assertEquals(setOf(SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX), SourceType.entries.filter { it.isMediaServer }.toSet())
        assertTrue(SourceType.SMB.isNetworkShare)
        assertFalse(SourceType.STREAM.isNetworkShare)
        assertFalse(SourceType.PLEX.isNetworkShare)
    }

    @Test
    fun locationsParseEncodeAndContainEachOther() {
        val root = requireNotNull(NetworkLocation.parse("ftp://user:pw@NAS.local:21/Movies%20HD/"))
        assertEquals(NetworkLocation("ftp", "NAS.local", 21, "Movies HD"), root)
        assertEquals("Movies HD", root.name)
        assertEquals("ftp://NAS.local:21/Movies%20HD/Action/Die%20Hard%20%231.mkv", root.child("Action/Die Hard #1.mkv").toUri())
        assertEquals("Movies HD/Action", root.child("Action/Die Hard.mkv").parentPath)

        val file = requireNotNull(NetworkLocation.parse("ftp://nas.local/Movies%20HD/Action/x.mkv?a=1"))
        assertEquals("Movies HD/Action/x.mkv", file.path)
        // Host case and a default port make no difference; scheme, port and path case do.
        assertTrue(root.covers(file))
        assertTrue(root.covers(root))
        assertFalse(root.covers(file.copy(scheme = "sftp")))
        assertFalse(root.covers(file.copy(port = 2121)))
        assertFalse(root.covers(file.copy(path = "movies hd/Action/x.mkv")))
        assertFalse(root.covers(file.copy(path = "Movies HD2/x.mkv")))
        assertTrue(root.copy(path = "").covers(file))
        // SMB paths are case-insensitive.
        val share = requireNotNull(NetworkLocation.parse("smb://nas/Media"))
        assertTrue(share.covers(requireNotNull(NetworkLocation.parse("smb://NAS:445/media/TV/a.mkv"))))

        assertNull(NetworkLocation.parse("uuid:1234"))
        assertNull(NetworkLocation.parse("/storage/emulated/0/a.mkv"))
        assertNull(NetworkLocation.parse("ftp://nas:99999/a"))
        assertNull(NetworkLocation.parse("ftp:///a"))
        assertEquals("http://[::1]:8096", NetworkLocation("http", "::1", 8096).toUri())
        assertEquals("a b%zz", NetworkLocation.decode("a%20b%zz"))
        assertEquals("%C3%A9-_.~%2F", NetworkLocation.encodeSegment("é-_.~/"))
    }
}
