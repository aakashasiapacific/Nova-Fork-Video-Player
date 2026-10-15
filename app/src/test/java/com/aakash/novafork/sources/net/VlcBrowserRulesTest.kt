package com.aakash.novafork.sources.net

import com.aakash.novafork.data.NetworkLocation
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How libVLC's folder listings (FTP, SFTP, NFS) become RemoteEntry paths and URIs. */
class VlcBrowserRulesTest {
    private fun location(uri: String): NetworkLocation = requireNotNull(NetworkLocation.parse(uri))

    @Test
    fun unknownTypesAreFoldersUnlessTheNameIsAFile() {
        // vsftpd has no MLSD: libVLC lists every name without a type.
        assertTrue(VlcBrowserRules.isDirectory(VlcItemKind.UNKNOWN, "Movies"))
        assertTrue(VlcBrowserRules.isDirectory(VlcItemKind.UNKNOWN, "Show.Name.S01.720p.WEB"))
        assertTrue(VlcBrowserRules.isDirectory(VlcItemKind.UNKNOWN, "The.Matrix.1999.1080p.BluRay.x264-GROUP"))
        assertFalse(VlcBrowserRules.isDirectory(VlcItemKind.UNKNOWN, "The Matrix (1999).mkv"))
        assertFalse(VlcBrowserRules.isDirectory(VlcItemKind.UNKNOWN, "movie.NFO"))
        assertFalse(VlcBrowserRules.isDirectory(VlcItemKind.UNKNOWN, "poster.jpg"))
        assertTrue(VlcBrowserRules.isDirectory(VlcItemKind.DIRECTORY, "a.mkv"))
        assertFalse(VlcBrowserRules.isDirectory(VlcItemKind.FILE, "Movies"))
    }

    @Test
    fun ftpItemsCarryTheDefaultPortAndARawFolderPath() {
        // libVLC's ftp module writes "ftp://host:21/<decoded folder>/<encoded name>".
        val root = location("ftp://nas/My%20Videos")
        val items = listOf(
            VlcItem("ftp://nas:21/My Videos/Heat%20%281995%29.mkv", "Heat (1995).mkv", VlcItemKind.FILE),
            VlcItem("ftp://nas:21/My Videos/TV", "TV", VlcItemKind.DIRECTORY),
            VlcItem("ftp://nas:21/My Videos/.hidden", ".hidden", VlcItemKind.DIRECTORY),
            VlcItem("ftp://nas:21/My Videos/notes.txt", "notes.txt", VlcItemKind.UNKNOWN),
        )
        val entries = VlcBrowserRules.entries(root, "", items)
        assertEquals(listOf("Heat (1995).mkv", "TV", "notes.txt"), entries.map { it.name })
        assertEquals(listOf("Heat (1995).mkv", "TV", "notes.txt"), entries.map { it.path })
        assertEquals("ftp://nas/My%20Videos/Heat%20%281995%29.mkv", entries[0].playUri)
        assertTrue(entries[1].isDirectory)
        assertNull(entries[1].playUri)
        assertFalse(entries[2].isDirectory)
        assertNull(entries[2].playUri)
    }

    @Test
    fun subfolderPathsAreBelowTheListedFolder() {
        val root = location("ftps://nas:2121/media")
        val items = listOf(VlcItem("ftps://nas:2121/media/TV/Severance/S01E01.mkv", "S01E01.mkv", VlcItemKind.FILE))
        val entries = VlcBrowserRules.entries(root, "TV/Severance", items)
        assertEquals("TV/Severance/S01E01.mkv", entries.single().path)
        assertEquals("ftps://nas:2121/media/TV/Severance/S01E01.mkv", entries.single().playUri)
    }

    @Test
    fun sftpHomeFolderShowsUpUnderItsRealPath() {
        // "sftp://host" lists the user's home; libVLC reports its entries by absolute path.
        val root = location("sftp://seedbox")
        val items = listOf(VlcItem("sftp://seedbox/home/bob/Movies", "Movies", VlcItemKind.DIRECTORY))
        val entry = VlcBrowserRules.entries(root, "", items).single()
        assertEquals("home/bob/Movies", entry.path)
        assertEquals("sftp://seedbox/home/bob/Movies", root.child(entry.path).toUri())
    }

    @Test
    fun nfsExportsAreFolders() {
        val root = location("nfs://filer")
        val items = listOf(VlcItem("nfs://filer/srv/media", "/srv/media", VlcItemKind.DIRECTORY))
        val entry = VlcBrowserRules.entries(root, "", items).single()
        assertEquals("srv/media", entry.path)
        assertEquals("/srv/media", entry.name)
        assertTrue(entry.isDirectory)
    }

    @Test
    fun anUnexpectedUriFallsBackToTheName() {
        val root = location("ftp://nas/videos")
        val entry = VlcBrowserRules.entries(root, "a", listOf(VlcItem("vlc://nop", "b.mp4", VlcItemKind.FILE))).single()
        assertEquals("a/b.mp4", entry.path)
        assertEquals("ftp://nas/videos/a/b.mp4", entry.playUri)
    }

    @Test
    fun loginOptionsPerType() {
        val ftp = SourceEntity(type = SourceType.FTP, name = "nas", location = "ftp://nas", username = "bob", password = "p=w:d")
        assertEquals(listOf(":ftp-user=bob", ":ftp-pwd=p=w:d"), VlcBrowserRules.loginOptions(ftp))
        val sftp = ftp.copy(type = SourceType.SFTP, location = "sftp://nas", password = null)
        assertEquals(listOf(":sftp-user=bob"), VlcBrowserRules.loginOptions(sftp))
        val nfs = ftp.copy(type = SourceType.NFS, location = "nfs://nas/export")
        assertEquals(emptyList<String>(), VlcBrowserRules.loginOptions(nfs))
        // A control character would let a value spill into another option.
        assertEquals(listOf(":ftp-user=bob"), VlcBrowserRules.loginOptions(ftp.copy(password = "a\nb")))
    }
}
