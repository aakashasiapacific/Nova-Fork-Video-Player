package com.aakash.novafork.data

import android.app.Application
import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.data.db.SourceType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The folder, season and "next up" queues of [LibraryRepositoryImpl]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class QueueBuildersTest {
    private lateinit var fx: RepositoryFixture
    private val repo: LibraryRepositoryImpl get() = fx.repository

    @Before
    fun setUp() {
        fx = RepositoryFixture()
    }

    @After
    fun tearDown() {
        if (::fx.isInitialized) fx.close()
    }

    private var folderId = 0L

    /** Scans [videos] into the picked folder "Videos" and returns their ids, in order. */
    private suspend fun scan(vararg videos: ScannedVideo): List<Long> {
        folderId = repo.addFolder(TREE, "Videos")
        fx.folders.serve(TREE, *videos)
        repo.scanSource(folderId)
        return videos.map { fx.videoAt(it.uri).id }
    }

    private suspend fun names(ids: List<Long>): List<String> =
        ids.map { id -> requireNotNull(repo.video(id)).video.fileName }

    @Test
    fun folderQueueListsOneFolderWithNumbersInOrder() = runTest {
        scan(EP_10, EP_2, INTRO, EP_1, OLD, EP_3_LOWER)

        // Browse's paths: below the picked folder, whose own name is not part of them.
        assertEquals(
            listOf("Episode 1.mp4", "Episode 2.mp4", "episode 3.mp4", "Episode 10.mp4"),
            names(repo.folderQueue(folderId, "Clips")),
        )
        assertEquals(names(repo.folderQueue(folderId, "Clips")), names(repo.folderQueue(folderId, "/Clips/")))
        assertEquals(listOf("Intro.mp4"), names(repo.folderQueue(folderId, "")))
        assertEquals(listOf("Episode 0.mp4"), names(repo.folderQueue(folderId, "Clips/Old")))
        assertTrue(repo.folderQueue(folderId, "Videos/Clips").isEmpty())
        assertTrue(repo.folderQueue(folderId, "Nowhere").isEmpty())
        assertTrue(repo.folderQueue(folderId + 100, "Clips").isEmpty())

        // A file gone from the folder is left out.
        fx.folders.serve(TREE, EP_10, INTRO, EP_1, OLD, EP_3_LOWER)
        repo.scanSource(folderId)
        assertEquals(listOf("Episode 1.mp4", "episode 3.mp4", "Episode 10.mp4"), names(repo.folderQueue(folderId, "Clips")))

        // So is everything of a source that is turned off.
        fx.database.sourceDao().setEnabled(folderId, false)
        assertTrue(repo.folderQueue(folderId, "Clips").isEmpty())
    }

    @Test
    fun folderQueueOnAShareStartsBelowTheSharesFolder() = runTest {
        val shareId = repo.saveSmbServer(
            id = null, name = "NAS", host = "nas.local", port = null, share = "media", path = "TV",
            username = null, password = null, domain = null,
        )
        assertEquals(SourceType.SMB, repo.source(shareId)?.type)
        fx.shares.serve(SMB_TV, SMB_E2, SMB_E10, SMB_LOOSE)
        repo.scanSource(shareId)

        assertEquals(listOf("Show.S01E02.mkv", "Show.S01E10.mkv"), names(repo.folderQueue(shareId, "Show")))
        assertEquals(listOf("Loose.mkv"), names(repo.folderQueue(shareId, "")))
    }

    @Test
    fun browseFoldersAreRelativeToTheSourceRoot() {
        assertEquals("Movies/Action", browseFolder(SourceType.DEVICE, "", "/Movies/Action/"))
        assertEquals("", browseFolder(SourceType.FOLDER, "", "Videos"))
        assertEquals("Clips/Old", browseFolder(SourceType.FOLDER, "", "Videos/Clips/Old"))
        assertEquals("", browseFolder(SourceType.SMB, "TV", "tv"))
        assertEquals("Show/Season 1", browseFolder(SourceType.SMB, "/TV/", "TV/Show/Season 1"))
        // A share path outside the source's folder (overlapping shares) is kept whole.
        assertEquals("Movies", browseFolder(SourceType.SMB, "TV", "Movies"))
        assertEquals("TV/Show", browseFolder(SourceType.SMB, "", "TV/Show"))
        assertEquals("", browseFolder(SourceType.STREAM, "", "anything"))
    }

    @Test
    fun seasonQueueFollowsSeasonsAndEpisodes() = runTest {
        scan(S2E1, S1E4, SPECIAL, S1E3, S1E2, S1E1E2, S1E3_HD, OTHER_SHOW, MOVIE)
        val showKey = requireNotNull(fx.videoAt(S1E3.uri).showKey)

        // A double episode stands in for its parts; of two copies the first by name plays.
        assertEquals(
            listOf(S1E1E2.fileName, S1E3_HD.fileName, S1E4.fileName),
            names(repo.seasonQueue(showKey, 1)),
        )
        assertEquals(listOf(S2E1.fileName), names(repo.seasonQueue(showKey, 2)))
        assertEquals(listOf(SPECIAL.fileName), names(repo.seasonQueue(showKey, 0)))
        // The whole show: specials last.
        assertEquals(
            listOf(S1E1E2.fileName, S1E3_HD.fileName, S1E4.fileName, S2E1.fileName, SPECIAL.fileName),
            names(repo.seasonQueue(showKey, null)),
        )
        assertTrue(repo.seasonQueue(showKey, 3).isEmpty())
        assertTrue(repo.seasonQueue("name:nothing", null).isEmpty())

        // Gone from the folder, gone from the queue (its copy takes over).
        fx.folders.serve(TREE, S2E1, S1E4, SPECIAL, S1E3, S1E2, S1E1E2, OTHER_SHOW, MOVIE)
        repo.scanSource(folderId)
        assertEquals(
            listOf(S1E1E2.fileName, S1E3.fileName, S1E4.fileName),
            names(repo.seasonQueue(showKey, 1)),
        )
    }

    @Test
    fun episodesWithoutASeasonAreListedInSeasonOne() = runTest {
        scan(ABSOLUTE_2, ABSOLUTE_1)
        val showKey = requireNotNull(fx.videoAt(ABSOLUTE_1.uri).showKey)
        // Absolute anime numbers wait for a match to be placed into seasons.
        assertEquals(null, fx.videoAt(ABSOLUTE_1.uri).season)

        assertEquals(listOf(ABSOLUTE_1.fileName, ABSOLUTE_2.fileName), names(repo.seasonQueue(showKey, 1)))
        assertEquals(listOf(ABSOLUTE_1.fileName, ABSOLUTE_2.fileName), names(repo.seasonQueue(showKey, null)))
    }

    @Test
    fun nextUpQueuePlaysTheRestOfTheShow() = runTest {
        val ids = scan(S2E1, S1E4, SPECIAL, S1E3, S1E2, S1E1E2, S1E3_HD, OTHER_SHOW, MOVIE)
        val s1e3 = ids[3]
        val double = ids[5]
        val otherShow = ids[7]
        val movie = ids[8]

        // From the copy that sorts second: the other copy is not played after it.
        assertEquals(
            listOf(S1E3.fileName, S1E4.fileName, S2E1.fileName, SPECIAL.fileName),
            names(repo.nextUpQueue(s1e3)),
        )
        // A double episode skips the episode it contains.
        assertEquals(
            listOf(S1E1E2.fileName, S1E3_HD.fileName, S1E4.fileName, S2E1.fileName, SPECIAL.fileName),
            names(repo.nextUpQueue(double)),
        )
        assertEquals(listOf(otherShow), repo.nextUpQueue(otherShow))
        assertEquals(listOf(movie), repo.nextUpQueue(movie))
        assertTrue(repo.nextUpQueue(-1).isEmpty())

        // The episode asked for plays even while its file is missing; the rest must be there.
        fx.folders.serve(TREE, S2E1, SPECIAL, S1E2, S1E1E2, S1E3_HD, OTHER_SHOW, MOVIE)
        repo.scanSource(folderId)
        assertEquals(
            listOf(S1E3.fileName, S2E1.fileName, SPECIAL.fileName),
            names(repo.nextUpQueue(s1e3)),
        )
    }

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AVideos"

        // The picked folder's display paths start with its own name.
        val INTRO = scannedVideo("$TREE/document/intro", "Intro.mp4", "Videos")
        val EP_1 = scannedVideo("$TREE/document/ep1", "Episode 1.mp4", "Videos/Clips")
        val EP_2 = scannedVideo("$TREE/document/ep2", "Episode 2.mp4", "Videos/Clips")
        val EP_3_LOWER = scannedVideo("$TREE/document/ep3", "episode 3.mp4", "Videos/Clips")
        val EP_10 = scannedVideo("$TREE/document/ep10", "Episode 10.mp4", "Videos/Clips")
        val OLD = scannedVideo("$TREE/document/old", "Episode 0.mp4", "Videos/Clips/Old")

        val SMB_TV = SmbUris.build("nas.local", "media", "TV")

        // Share files carry their folder inside the share.
        val SMB_E2 = scannedVideo(SmbUris.build("nas.local", "media", "TV/Show/Show.S01E02.mkv"), "Show.S01E02.mkv", "TV/Show")
        val SMB_E10 = scannedVideo(SmbUris.build("nas.local", "media", "TV/Show/Show.S01E10.mkv"), "Show.S01E10.mkv", "TV/Show")
        val SMB_LOOSE = scannedVideo(SmbUris.build("nas.local", "media", "TV/Loose.mkv"), "Loose.mkv", "TV")

        val S1E1E2 = scannedVideo("$TREE/document/sev-1-1", "Severance.S01E01E02.mkv", "Videos/TV/Severance")
        val S1E2 = scannedVideo("$TREE/document/sev-1-2", "Severance.S01E02.mkv", "Videos/TV/Severance")
        val S1E3 = scannedVideo("$TREE/document/sev-1-3", "Severance.S01E03.mkv", "Videos/TV/Severance")
        val S1E3_HD = scannedVideo("$TREE/document/sev-1-3-hd", "Severance.S01E03.1080p.mkv", "Videos/TV/Severance")
        val S1E4 = scannedVideo("$TREE/document/sev-1-4", "Severance.S01E04.mkv", "Videos/TV/Severance")
        val S2E1 = scannedVideo("$TREE/document/sev-2-1", "Severance.S02E01.mkv", "Videos/TV/Severance")
        val SPECIAL = scannedVideo("$TREE/document/sev-0-1", "Severance.S00E01.mkv", "Videos/TV/Severance")
        val OTHER_SHOW = scannedVideo("$TREE/document/foundation-1-1", "Foundation.S01E01.mkv", "Videos/TV/Foundation")
        val MOVIE = scannedVideo("$TREE/document/movie", "Inception (2010).mkv", "Videos/Movies")

        val ABSOLUTE_1 = scannedVideo("$TREE/document/frieren-1", "[SubsPlease] Frieren - 01 (1080p).mkv", "Videos/Anime/Frieren")
        val ABSOLUTE_2 = scannedVideo("$TREE/document/frieren-2", "[SubsPlease] Frieren - 02 (1080p).mkv", "Videos/Anime/Frieren")
    }
}
