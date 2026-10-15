package com.aakash.novafork

import com.aakash.novafork.data.NetworkSources
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.ui.NetworkForms
import com.aakash.novafork.ui.model.NetworkSourceForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkFormsTest {
    @Test
    fun fileServerFormsJoinServerAndFolder() {
        val form = NetworkSourceForm(
            type = SourceType.FTP,
            name = " Basement ",
            address = " ftps://nas.local/ ",
            port = "2121",
            path = "\\Movies\\Action/",
            username = " bob ",
            password = " secret ",
            token = "ignored",
            libraryIds = listOf("ignored"),
        )
        val input = NetworkForms.toInput(form)
        assertEquals("ftps://nas.local/Movies/Action", input.address)
        assertEquals(2121, input.port)
        assertEquals("Basement", input.name)
        assertEquals("bob", input.username)
        // Passwords are taken as typed.
        assertEquals(" secret ", input.password)
        assertNull(input.token)
        assertEquals(emptyList<String>(), input.libraryIds)

        val source = NetworkSources.toSource(input)
        assertEquals("ftps://nas.local:2121/Movies/Action", source.location)
    }

    @Test
    fun fieldsATypeDoesNotTakeAreLeftOut() {
        val nfs = NetworkForms.toInput(
            NetworkSourceForm(type = SourceType.NFS, address = "nas", port = "99", path = "export", username = "bob", password = "pw"),
        )
        assertEquals("nas/export", nfs.address)
        assertNull(nfs.port)
        assertNull(nfs.username)
        assertNull(nfs.password)

        val jellyfin = NetworkForms.toInput(
            NetworkSourceForm(
                type = SourceType.JELLYFIN,
                address = "jellyfin.local",
                path = "ignored",
                username = "ann",
                password = "",
                token = "ignored",
                libraryIds = listOf("a", "b"),
            ),
        )
        assertEquals("jellyfin.local", jellyfin.address)
        assertEquals("ann", jellyfin.username)
        assertNull(jellyfin.password)
        assertNull(jellyfin.token)
        assertEquals(listOf("a", "b"), jellyfin.libraryIds)

        val plex = NetworkForms.toInput(NetworkSourceForm(type = SourceType.PLEX, address = "plex.local", username = "x", token = " abc "))
        assertEquals("abc", plex.token)
        assertNull(plex.username)
    }

    @Test
    fun aBadPortIsRefused() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NetworkForms.toInput(NetworkSourceForm(type = SourceType.SFTP, address = "nas", port = "70000"))
        }
        assertEquals(NetworkForms.PORT_ERROR, error.message)
        assertNull(NetworkForms.port(NetworkSourceForm(type = SourceType.SFTP, address = "nas", port = " ")))
    }

    @Test
    fun savedSourcesComeBackAsTheirForms() {
        val ftp = NetworkForms.formFor(
            SourceEntity(
                id = 4,
                type = SourceType.FTP,
                name = "Basement",
                location = "ftp://nas.local:2121/My%20Movies",
                port = 2121,
                username = "bob",
                password = "pw",
                token = "never shown",
            ),
        )
        assertEquals(
            NetworkSourceForm(
                id = 4,
                type = SourceType.FTP,
                name = "Basement",
                address = "nas.local",
                port = "2121",
                path = "My Movies",
                username = "bob",
                password = "pw",
            ),
            ftp,
        )
        // The round trip stores the same location.
        assertEquals("ftp://nas.local:2121/My%20Movies", NetworkSources.toSource(NetworkForms.toInput(ftp)).location)

        val ftps = NetworkForms.formFor(SourceEntity(type = SourceType.FTP, name = "x", location = "ftps://[fd00::2]/v"))
        assertEquals("ftps://[fd00::2]", ftps.address)
        assertEquals("v", ftps.path)

        val dav = NetworkForms.formFor(SourceEntity(type = SourceType.WEBDAV, name = "x", location = "https://dav.example.com/remote.php/dav"))
        assertEquals("https://dav.example.com", dav.address)
        assertEquals("remote.php/dav", dav.path)

        val jellyfin = NetworkForms.formFor(
            SourceEntity(
                id = 9,
                type = SourceType.JELLYFIN,
                name = "Den",
                location = "http://192.168.1.5:8096",
                username = "ann",
                token = "secret",
                libraryIds = "a,b",
            ),
        )
        assertEquals("http://192.168.1.5:8096", jellyfin.address)
        assertEquals("", jellyfin.token)
        assertEquals(listOf("a", "b"), jellyfin.libraryIds)

        val plex = NetworkForms.formFor(SourceEntity(type = SourceType.PLEX, name = "P", location = "http://plex.local:32400", token = "tok"))
        assertEquals("tok", plex.token)
    }

    @Test
    fun typeLabels() {
        assertEquals("DLNA", NetworkForms.typeLabel(SourceType.UPNP))
        assertEquals("WebDAV", NetworkForms.typeLabel(SourceType.WEBDAV))
        assertEquals("Jellyfin", NetworkForms.typeLabel(SourceType.JELLYFIN))
    }
}
