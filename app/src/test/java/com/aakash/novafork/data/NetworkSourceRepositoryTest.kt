package com.aakash.novafork.data

import android.app.Application
import com.aakash.novafork.data.db.SourceType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Saving, signing in to, looking up and removing FTP/SFTP/NFS/UPnP/WebDAV/media-server sources. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NetworkSourceRepositoryTest {
    private lateinit var fx: NetworkRepositoryFixture
    private val repo: LibraryRepositoryImpl get() = fx.repository

    @Before
    fun setUp() {
        fx = NetworkRepositoryFixture()
    }

    @After
    fun tearDown() {
        if (::fx.isInitialized) fx.close()
    }

    @Test
    fun savingAnFtpServerStoresItNormalised() = runTest {
        val id = repo.saveNetworkSource(
            null,
            NetworkSourceInput(SourceType.FTP, address = "ftp://bob:secret@nas.local:2121/My Movies/"),
        )

        val source = fx.source(id)
        assertEquals(SourceType.FTP, source.type)
        assertEquals("ftp://nas.local:2121/My%20Movies", source.location)
        assertEquals(2121, source.port)
        assertEquals("nas.local/My Movies", source.name)
        assertEquals("bob", source.username)
        assertEquals("secret", source.password)
        assertTrue(source.enabled)
        assertTrue(source.addedAt > 0)
        // Saving the same server again (a double tap) keeps one source.
        assertEquals(id, repo.saveNetworkSource(null, NetworkSourceInput(SourceType.FTP, address = "nas.local:2121/My%20Movies", username = "bob", password = "secret")))
        assertEquals(1, fx.database.sourceDao().all().size)
        // Another user on the same server is another source.
        assertNotEquals(id, repo.saveNetworkSource(null, NetworkSourceInput(SourceType.FTP, address = "nas.local:2121/My Movies", username = "carol")))
    }

    @Test
    fun editingKeepsTheTokenOnlyForTheSameAccount() = runTest {
        val input = NetworkSourceInput(SourceType.JELLYFIN, name = "Home", address = "jf.local", username = "alice", password = "pw")
        val id = repo.saveNetworkSource(null, input)
        repo.updateSourceAuth(id, token = " tok ", userId = "u1", serverId = "srv")
        fx.source(id).let {
            assertEquals("tok", it.token)
            assertEquals("u1", it.userId)
            assertEquals("srv", it.serverId)
        }

        // Renamed, libraries picked: same account, the sign-in stays.
        assertEquals(id, repo.saveNetworkSource(id, input.copy(name = "Living room", libraryIds = listOf("movies", "shows"))))
        fx.source(id).let {
            assertEquals("Living room", it.name)
            assertEquals("movies,shows", it.libraryIds)
            assertEquals("tok", it.token)
            assertEquals("u1", it.userId)
            assertEquals("pw", it.password)
        }

        // Another user: the old token is not theirs.
        repo.saveNetworkSource(id, input.copy(username = "bob"))
        fx.source(id).let {
            assertEquals("bob", it.username)
            assertNull(it.token)
            assertNull(it.userId)
            assertNull(it.serverId)
            assertNull(it.libraryIds)
        }

        // A pasted token is stored as given; blank values clear.
        repo.saveNetworkSource(id, input.copy(token = "pasted"))
        assertEquals("pasted", fx.source(id).token)
        repo.updateSourceAuth(id, token = " ", userId = null, serverId = null)
        assertNull(fx.source(id).token)
        // An unknown id is ignored.
        repo.updateSourceAuth(999, "x", null, null)
    }

    @Test
    fun editingWithAnIdOfAnotherTypeAddsANewSource() = runTest {
        val smb = repo.saveSmbServer(null, "NAS", "nas.local", null, "media", "", null, null, null)

        val ftp = repo.saveNetworkSource(smb, NetworkSourceInput(SourceType.FTP, address = "nas.local/media"))

        assertNotEquals(smb, ftp)
        assertEquals(SourceType.SMB, fx.source(smb).type)
        assertEquals(SourceType.FTP, fx.source(ftp).type)
    }

    @Test
    fun badInputIsRefusedBeforeAnythingIsStored() = runTest {
        val error = runCatching {
            repo.saveNetworkSource(null, NetworkSourceInput(SourceType.EMBY, address = "emby.local"))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Enter the user name", error?.message)
        assertTrue(fx.database.sourceDao().all().isEmpty())
    }

    @Test
    fun scanningAServerUsesItsScanner() = runTest {
        val id = repo.saveNetworkSource(null, NetworkSourceInput(SourceType.SFTP, address = "sftp://nas/home/me/videos"))
        fx.servers.serve(
            "sftp://nas/home/me/videos",
            scannedVideo("sftp://nas/home/me/videos/Clips/Beach.mp4", "Beach.mp4", "Clips"),
        )

        repo.scanSource(id)

        val video = fx.videoAt("sftp://nas/home/me/videos/Clips/Beach.mp4")
        assertEquals(id, video.sourceId)
        assertTrue(requireNotNull(fx.source(id).lastScanAt) > 0)
        // Folders are below the location already, as Browse lists them.
        assertEquals(listOf(video.id), repo.folderQueue(id, "Clips"))
        assertEquals("Clips/2020", browseFolder(SourceType.SFTP, "", "/Clips/2020/"))
        assertEquals("Movies", browseFolder(SourceType.PLEX, "", "Movies"))
    }

    @Test
    fun aTypeWithoutAScannerIsSavedButNotScanned() = runTest {
        val plain = RepositoryFixture()
        try {
            val id = plain.repository.saveNetworkSource(null, NetworkSourceInput(SourceType.WEBDAV, address = "dav.example.com/video"))
            plain.repository.scanSource(id)
            val source = requireNotNull(plain.database.sourceDao().byId(id))
            assertEquals("https://dav.example.com/video", source.location)
            assertNull(source.lastScanAt)
            assertNull(source.lastError)
            plain.repository.refreshAll()
            assertNull(plain.database.sourceDao().byId(id)?.lastError)
        } finally {
            plain.close()
        }
    }

    @Test
    fun sourcesAreFoundForVideosAndForFilesNextToThem() = runTest {
        val movies = repo.saveNetworkSource(null, NetworkSourceInput(SourceType.FTP, address = "nas.local/Movies", username = "bob"))
        val action = repo.saveNetworkSource(null, NetworkSourceInput(SourceType.FTP, address = "nas.local/Movies/Action", username = "bob"))
        val share = repo.saveSmbServer(null, "NAS", "nas.local", null, "media", "", "alice", "pw", null)
        fx.servers.serve("ftp://nas.local/Movies", scannedVideo("ftp://nas.local/Movies/Heat.mkv", "Heat.mkv", ""))
        repo.scanSource(movies)
        val heat = fx.videoAt("ftp://nas.local/Movies/Heat.mkv")

        assertEquals(movies, repo.sourceForVideo(heat.id)?.id)
        assertNull(repo.sourceForVideo(999))
        assertEquals(movies, repo.sourceForUri("ftp://nas.local/Movies/Heat.mkv")?.id)
        // Artwork that is no video: the deepest location holding it, whatever the host's case or a default port.
        assertEquals(action, repo.sourceForUri("ftp://nas.local/Movies/Action/poster.jpg")?.id)
        assertEquals(movies, repo.sourceForUri("ftp://NAS.LOCAL:21/Movies/fanart.jpg")?.id)
        assertEquals("bob", repo.sourceForUri("ftp://nas.local/Movies/fanart.jpg")?.username)
        assertEquals(share, repo.sourceForUri("smb://nas.local/Media/TV/poster.jpg")?.id)
        assertNull(repo.sourceForUri("ftp://nas.local/Music/cover.jpg"))
        assertNull(repo.sourceForUri("sftp://nas.local/Movies/poster.jpg"))
        assertNull(repo.sourceForUri("content://media/external/video/1"))
        // A turned-off source only when no other covers the file.
        fx.database.sourceDao().setEnabled(action, false)
        assertEquals(movies, repo.sourceForUri("ftp://nas.local/Movies/Action/poster.jpg")?.id)
    }

    @Test
    fun removingAnFtpFolderHandsItsFilesToTheServerAboveIt() = runTest {
        val root = repo.saveNetworkSource(null, NetworkSourceInput(SourceType.FTP, address = "nas.local/Movies"))
        val action = repo.saveNetworkSource(null, NetworkSourceInput(SourceType.FTP, address = "nas.local/Movies/Action"))
        val other = repo.saveNetworkSource(null, NetworkSourceInput(SourceType.SFTP, address = "nas.local/Movies"))
        val uri = "ftp://nas.local/Movies/Action/Heat.mkv"
        fx.servers.serve("ftp://nas.local/Movies/Action", scannedVideo(uri, "Heat.mkv", ""))
        repo.scanSource(action)
        repo.savePlayback(fx.videoAt(uri).id, 600_000, 6_000_000)

        repo.removeSource(action)

        fx.videoAt(uri).let {
            assertEquals(root, it.sourceId)
            assertEquals(600_000L, it.positionMs)
        }
        assertNull(fx.database.sourceDao().byId(action))
        assertTrue(fx.database.videoDao().bySource(other).isEmpty())
    }
}
