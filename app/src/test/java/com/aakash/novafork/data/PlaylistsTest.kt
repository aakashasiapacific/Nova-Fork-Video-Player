package com.aakash.novafork.data

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Playlists of [LibraryRepositoryImpl]: names, order, hidden files and removed sources. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaylistsTest {
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

    /** Scans [videos] into one folder and returns their ids, in order. */
    private suspend fun scan(vararg videos: ScannedVideo): List<Long> {
        folderId = repo.addFolder(TREE, "Videos")
        fx.folders.serve(TREE, *videos)
        repo.scanSource(folderId)
        return videos.map { fx.videoAt(it.uri).id }
    }

    private suspend fun videosIn(playlistId: Long): List<Long> = repo.playlistVideos(playlistId).map { it.video.id }

    private suspend fun playlist(id: Long): PlaylistWithCount? = repo.observePlaylists().first().firstOrNull { it.id == id }

    /** Every stored item's position, shown or not, in playlist order. */
    private suspend fun positions(playlistId: Long): List<Int> =
        fx.database.playlistDao().items(playlistId).map { it.position }

    /** Waits for the wall clock to move on, so the next change gets a later updatedAt. */
    private fun nextMillisecond() {
        val now = System.currentTimeMillis()
        while (System.currentTimeMillis() == now) Thread.sleep(1)
    }

    @Test
    fun playlistsAreCreatedRenamedAndDeleted() = runTest {
        val (a, b) = scan(MOVIE, CLIP)
        val id = repo.createPlaylist("  Road trip  ")

        val created = requireNotNull(playlist(id))
        assertEquals("Road trip", created.name)
        assertEquals(0, created.itemCount)
        assertNull(created.coverVideoId)
        assertTrue(videosIn(id).isEmpty())

        nextMillisecond()
        repo.renamePlaylist(id, " Summer ")
        val renamed = requireNotNull(playlist(id))
        assertEquals("Summer", renamed.name)
        assertTrue(renamed.updatedAt > created.updatedAt)

        // A blank name is refused and changes nothing.
        assertTrue(runCatching { repo.createPlaylist("   ") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repo.renamePlaylist(id, "") }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(listOf("Summer"), repo.observePlaylists().first().map { it.name })

        // Unknown ids are ignored.
        repo.renamePlaylist(id + 100, "Other")
        repo.addToPlaylist(id + 100, listOf(a))
        assertTrue(videosIn(id + 100).isEmpty())

        repo.addToPlaylist(id, listOf(a, b))
        repo.deletePlaylist(id)
        assertTrue(repo.observePlaylists().first().isEmpty())
        // Its items went with it; the videos stay in the library.
        assertTrue(fx.database.playlistDao().items(id).isEmpty())
        assertEquals(2, repo.observeVideos().first().size)
    }

    @Test
    fun addingKeepsTheGivenOrderAndSkipsWhatIsThere() = runTest {
        val (movie, clip, episode, other) = scan(MOVIE, CLIP, EPISODE, OTHER)
        val id = repo.createPlaylist("Mix")

        repo.addToPlaylist(id, listOf(clip, movie, clip))
        assertEquals(listOf(clip, movie), videosIn(id))

        // Already there, unknown, then new ones: appended in the order given.
        repo.addToPlaylist(id, listOf(movie, -5L, other, episode))
        assertEquals(listOf(clip, movie, other, episode), videosIn(id))
        assertEquals(listOf(0, 1, 2, 3), positions(id))

        val listed = requireNotNull(playlist(id))
        assertEquals(4, listed.itemCount)
        assertEquals(clip, listed.coverVideoId)

        // Nothing new: nothing changes, not even the date.
        val before = listed.updatedAt
        nextMillisecond()
        repo.addToPlaylist(id, listOf(movie, clip))
        repo.addToPlaylist(id, emptyList())
        assertEquals(before, playlist(id)?.updatedAt)

        // The same video can be in several playlists.
        val second = repo.createPlaylist("Movies")
        repo.addToPlaylist(second, listOf(movie))
        assertEquals(listOf(movie), videosIn(second))
        assertEquals(listOf(clip, movie, other, episode), videosIn(id))
    }

    @Test
    fun removingAVideoClosesTheGap() = runTest {
        val (movie, clip, episode) = scan(MOVIE, CLIP, EPISODE)
        val id = repo.createPlaylist("Mix")
        repo.addToPlaylist(id, listOf(movie, clip, episode))
        val before = requireNotNull(playlist(id)).updatedAt

        nextMillisecond()
        repo.removeFromPlaylist(id, clip)
        assertEquals(listOf(movie, episode), videosIn(id))
        assertEquals(listOf(0, 1), positions(id))
        assertTrue(requireNotNull(playlist(id)).updatedAt > before)

        // Not in it (any more): nothing happens.
        val after = requireNotNull(playlist(id)).updatedAt
        nextMillisecond()
        repo.removeFromPlaylist(id, clip)
        assertEquals(after, playlist(id)?.updatedAt)

        repo.removeFromPlaylist(id, movie)
        assertEquals(listOf(episode), videosIn(id))
        assertEquals(episode, playlist(id)?.coverVideoId)
        assertEquals(listOf(0), positions(id))
    }

    @Test
    fun movingAnItemShiftsTheOthers() = runTest {
        val (a, b, c, d) = scan(MOVIE, CLIP, EPISODE, OTHER)
        val id = repo.createPlaylist("Mix")
        repo.addToPlaylist(id, listOf(a, b, c, d))

        // Down: it lands after the item that was at the target index.
        repo.movePlaylistItem(id, 0, 2)
        assertEquals(listOf(b, c, a, d), videosIn(id))

        // Up: before it.
        repo.movePlaylistItem(id, 3, 0)
        assertEquals(listOf(d, b, c, a), videosIn(id))
        assertEquals(listOf(0, 1, 2, 3), positions(id))
        assertEquals(d, playlist(id)?.coverVideoId)

        // Out of range or nowhere to go: ignored.
        repo.movePlaylistItem(id, 0, 4)
        repo.movePlaylistItem(id, -1, 2)
        repo.movePlaylistItem(id, 2, 2)
        repo.movePlaylistItem(id + 100, 0, 1)
        assertEquals(listOf(d, b, c, a), videosIn(id))
    }

    @Test
    fun missingFilesStayInThePlaylistButAreLeftOut() = runTest {
        val (a, b, c) = scan(MOVIE, CLIP, EPISODE)
        val id = repo.createPlaylist("Mix")
        repo.addToPlaylist(id, listOf(a, b, c))

        // The clip is gone from the folder: hidden, not removed.
        fx.folders.serve(TREE, MOVIE, EPISODE)
        repo.scanSource(folderId)
        assertEquals(listOf(a, c), videosIn(id))
        assertEquals(2, playlist(id)?.itemCount)

        // Indices count what is shown; the hidden clip keeps its place after the movie.
        repo.movePlaylistItem(id, 1, 0)
        assertEquals(listOf(c, a), videosIn(id))
        assertEquals(listOf(0, 1, 2), positions(id))

        // Adding it again while hidden does not make a second copy.
        repo.addToPlaylist(id, listOf(b))
        assertEquals(3, fx.database.playlistDao().items(id).size)

        // Back in the folder, back in the playlist where it was.
        fx.folders.serve(TREE, MOVIE, CLIP, EPISODE)
        repo.scanSource(folderId)
        assertEquals(listOf(c, a, b), videosIn(id))
        assertEquals(3, playlist(id)?.itemCount)

        // A source that is turned off hides its videos the same way.
        fx.database.sourceDao().setEnabled(folderId, false)
        assertTrue(videosIn(id).isEmpty())
        assertEquals(0, playlist(id)?.itemCount)
        assertNull(playlist(id)?.coverVideoId)
        fx.database.sourceDao().setEnabled(folderId, true)
        assertEquals(listOf(c, a, b), videosIn(id))
    }

    @Test
    fun removingASourceTakesItsVideosOutOfPlaylists() = runTest {
        val (movie, clip) = scan(MOVIE, CLIP)
        val otherFolder = repo.addFolder(OTHER_TREE, "More")
        fx.folders.serve(OTHER_TREE, SINTEL)
        repo.scanSource(otherFolder)
        val sintel = fx.videoAt(SINTEL.uri).id
        val id = repo.createPlaylist("Mix")
        repo.addToPlaylist(id, listOf(sintel, movie, clip))

        repo.removeSource(otherFolder)

        assertEquals(listOf(movie, clip), videosIn(id))
        // Deleted with its video rather than hidden: nothing comes back with a new folder.
        assertEquals(listOf(movie, clip), fx.database.playlistDao().items(id).map { it.videoId })
        assertEquals(movie, playlist(id)?.coverVideoId)
        assertEquals(2, playlist(id)?.itemCount)
    }

    @Test
    fun recentlyChangedPlaylistsComeFirst() = runTest {
        val (movie) = scan(MOVIE)
        val first = repo.createPlaylist("First")
        nextMillisecond()
        val second = repo.createPlaylist("Second")
        assertEquals(listOf(second, first), repo.observePlaylists().first().map { it.id })

        nextMillisecond()
        repo.addToPlaylist(first, listOf(movie))
        assertEquals(listOf(first, second), repo.observePlaylists().first().map { it.id })

        nextMillisecond()
        repo.renamePlaylist(second, "Second, renamed")
        assertEquals(listOf(second, first), repo.observePlaylists().first().map { it.id })
    }

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AVideos"
        const val OTHER_TREE = "content://com.android.externalstorage.documents/tree/primary%3AMore"

        val MOVIE = scannedVideo("$TREE/document/movie", "Inception (2010).mkv", "Videos/Movies")
        val CLIP = scannedVideo("$TREE/document/clip", "VID_20240501_123456.mp4", "Videos/Camera")
        val EPISODE = scannedVideo("$TREE/document/office", "The.Office.S01E02.mkv", "Videos/TV/The Office")
        val OTHER = scannedVideo("$TREE/document/heat", "Heat (1995).mkv", "Videos/Movies")
        val SINTEL = scannedVideo("$OTHER_TREE/document/sintel", "Sintel (2010).mkv", "More")
    }
}
