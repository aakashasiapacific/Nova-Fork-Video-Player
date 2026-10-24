package com.aakash.novafork.data.music

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.data.MusicSourceCount
import com.aakash.novafork.data.db.NovaDatabase
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MusicRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, NovaDatabase::class.java).allowMainThreadQueries().build()
    private val device = FakeMusicScanner()
    private val folder = FakeMusicScanner()
    private var time = 1_000L
    private val repository = MusicRepository(
        context = context,
        database = database,
        scanners = mapOf(SourceType.DEVICE to device, SourceType.FOLDER to folder),
        scope = CoroutineScope(Job()),
        now = { time },
    )

    @After
    fun close() = database.close()

    @Test
    fun aScanGroupsSongsIntoAlbumsAndArtists() = runTest {
        val deviceId = addSource(SourceType.DEVICE)
        device.songs[deviceId] = listOf(
            song("2.mp3", title = "Second", track = 2),
            song("1.mp3", title = "First", track = 1),
            song("x.mp3", title = "Other", album = "Other Album", artist = "Someone Else"),
        )
        repository.rescan()

        val library = repository.observeLibrary().first()
        assertEquals(3, library.songs.size)
        val album = library.albums.single { it.title == "Album" }
        assertEquals(listOf("First", "Second"), album.songs.map { it.title })
        assertEquals("Band", album.artist)
        assertEquals(listOf("Band", "Someone Else"), library.artists.map { it.name })
        assertEquals(listOf("Rock"), library.genres.map { it.name })
        assertNull(repository.scanState.value.error)
        assertEquals(time, repository.scanState.value.lastScanAt)
    }

    @Test
    fun aRescanKeepsPlayCountsAndHidesFilesThatAreGone() = runTest {
        val deviceId = addSource(SourceType.DEVICE)
        device.songs[deviceId] = listOf(song("a.mp3", title = "A"), song("b.mp3", title = "B"))
        repository.rescan()
        val a = repository.observeLibrary().first().songs.single { it.title == "A" }
        repository.markPlayed(a.id)

        device.songs[deviceId] = listOf(song("a.mp3", title = "A (Remastered)"))
        repository.rescan()

        val songs = repository.observeLibrary().first().songs
        assertEquals(listOf("A (Remastered)"), songs.map { it.title })
        assertEquals(a.id, songs.single().id)
        assertEquals(1, songs.single().playCount)
        // The missing file keeps its row (for playlists), hidden.
        val b = database.songDao().bySource(deviceId).single { it.fileName == "b.mp3" }
        assertFalse(b.present)

        device.songs[deviceId] = listOf(song("a.mp3", title = "A"), song("b.mp3", title = "B"))
        repository.rescan()
        assertEquals(2, repository.observeLibrary().first().songs.size)
    }

    @Test
    fun anUnchangedFileKeepsItsTags() = runTest {
        val folderId = addSource(SourceType.FOLDER)
        folder.songs[folderId] = listOf(song("a.flac", title = "Tagged", size = 10, modified = 5))
        repository.rescan()

        folder.songs[folderId] = listOf(
            ScannedSong(uri = uri("a.flac"), fileName = "a.flac", folder = "Music", sizeBytes = 10, dateAdded = 5,
                dateModified = 5, coverUri = "content://cover", tagsUnchanged = true),
        )
        repository.rescan()

        // The scanner was told what the library has, so it could skip reading the tags.
        assertEquals(KnownSong(10, 5), folder.lastKnown.values.single())
        val stored = repository.observeLibrary().first().songs.single()
        assertEquals("Tagged", stored.title)
        assertEquals("Band", stored.artist)
        assertEquals("content://cover", stored.coverUri)
    }

    @Test
    fun aFolderCopyOfADeviceSongIsKeptOnce() = runTest {
        val deviceId = addSource(SourceType.DEVICE)
        val folderId = addSource(SourceType.FOLDER)
        device.songs[deviceId] = listOf(song("Song.mp3", title = "Song", size = 300))
        folder.songs[folderId] = listOf(
            song("song.mp3", title = "Song", size = 300, uriPrefix = "content://tree/"),
            song("Only here.mp3", title = "Only here", size = 400, uriPrefix = "content://tree/"),
        )
        repository.rescan()

        val songs = repository.observeLibrary().first().songs
        assertEquals(listOf("Only here", "Song"), songs.map { it.title })
        assertEquals(deviceId, songs.single { it.title == "Song" }.sourceId)
    }

    @Test
    fun aSourceThatFailsKeepsItsSongs() = runTest {
        val deviceId = addSource(SourceType.DEVICE)
        device.songs[deviceId] = listOf(song("a.mp3", title = "A"))
        repository.rescan()

        device.failure = IOException("Allow access to music to scan this device")
        repository.rescan()

        assertEquals("Allow access to music to scan this device", repository.scanState.value.error)
        assertEquals(listOf("A"), repository.observeLibrary().first().songs.map { it.title })
    }

    @Test
    fun aSourceTurnedOffIsNotScannedAndHidden() = runTest {
        val folderId = addSource(SourceType.FOLDER)
        folder.songs[folderId] = listOf(song("a.mp3", title = "A"))
        repository.rescan()
        database.sourceDao().setEnabled(folderId, false)
        folder.scans = 0

        repository.rescan()

        assertEquals(0, folder.scans)
        assertTrue(repository.observeLibrary().first().songs.isEmpty())
    }

    @Test
    fun songsOfARemovedSourceGo() = runTest {
        val folderId = addSource(SourceType.FOLDER)
        folder.songs[folderId] = listOf(song("a.mp3", title = "A"))
        repository.rescan()
        database.sourceDao().delete(folderId)

        repository.rescan()

        assertTrue(database.songDao().bySource(folderId).isEmpty())
    }

    @Test
    fun playlistsKeepTheirOrderAndEachSongOnce() = runTest {
        val deviceId = addSource(SourceType.DEVICE)
        device.songs[deviceId] = listOf(song("a.mp3", title = "A"), song("b.mp3", title = "B"), song("c.mp3", title = "C"))
        repository.rescan()
        val byTitle = repository.observeLibrary().first().songs.associateBy { it.title }
        val playlistId = repository.createPlaylist("  Road trip ")

        repository.addToPlaylist(playlistId, listOf(byTitle.getValue("C").id, byTitle.getValue("A").id))
        repository.addToPlaylist(playlistId, listOf(byTitle.getValue("A").id, byTitle.getValue("B").id))

        assertEquals(listOf("C", "A", "B"), repository.playlistSongs(playlistId).map { it.title })
        val row = repository.observePlaylists().first().single()
        assertEquals("Road trip", row.name)
        assertEquals(3, row.songCount)
        assertEquals(byTitle.getValue("C").id, row.coverSongId)

        repository.removeFromPlaylist(playlistId, byTitle.getValue("A").id)
        assertEquals(listOf("C", "B"), repository.playlistSongs(playlistId).map { it.title })

        // A song-only playlist is the Audio tab's: the Video side's list leaves it out.
        assertTrue(database.playlistDao().observeWithCounts().first().isEmpty())
    }

    @Test
    fun aPlaylistRowNamesTheFirstSongOfItsFirstFourAlbums() = runTest {
        val deviceId = addSource(SourceType.DEVICE)
        device.songs[deviceId] = listOf(
            song("a1.mp3", title = "A1", album = "A"),
            song("a2.mp3", title = "A2", album = "A"),
            song("b1.mp3", title = "B1", album = "B"),
            song("c1.mp3", title = "C1", album = "C"),
            song("d1.mp3", title = "D1", album = "D"),
            song("e1.mp3", title = "E1", album = "E"),
        )
        repository.rescan()
        val byTitle = repository.observeLibrary().first().songs.associateBy { it.title }
        val playlistId = repository.createPlaylist("Mix")
        repository.addToPlaylist(playlistId, listOf("A2", "A1", "B1", "C1", "D1", "E1").map { byTitle.getValue(it).id })

        val ids = repository.observePlaylists().first().single().coverSongIds.orEmpty()
            .split(',').map { it.toLong() }

        assertEquals(listOf("A2", "B1", "C1", "D1"), ids.map { id -> byTitle.values.single { it.id == id }.title }.sorted())
    }

    @Test
    fun theWatchlistKeepsTheFirstDate() = runTest {
        repository.addToWatchlist("v:7")
        time = 2_000L
        repository.addToWatchlist("s:tv:1396")
        repository.addToWatchlist("v:7")

        assertEquals(listOf("s:tv:1396", "v:7"), repository.observeWatchlist().first())
        assertTrue(repository.isOnWatchlist("v:7"))
        repository.removeFromWatchlist("v:7")
        assertFalse(repository.isOnWatchlist("v:7"))
    }

    @Test
    fun lyricsComeFromTheLrcFile() = runTest {
        val deviceId = addSource(SourceType.DEVICE)
        val lrc = Uri.parse("content://lyrics/a.lrc")
        shadowOf(context.contentResolver).registerInputStream(
            lrc,
            ByteArrayInputStream("[00:01.00]Hello\n[00:03.50]World".toByteArray()),
        )
        device.songs[deviceId] = listOf(song("a.mp3", title = "A").copy(lyricsUri = lrc.toString()), song("b.mp3", title = "B"))
        repository.rescan()
        val songs = repository.observeLibrary().first().songs.associateBy { it.title }

        val lyrics = repository.lyrics(songs.getValue("A"))
        assertEquals(listOf("Hello", "World"), lyrics?.lines?.map { it.text })
        assertEquals(true, lyrics?.synced)
        assertNull(repository.lyrics(songs.getValue("B")))
    }

    @Test
    fun diagnosticsCountSongsPerSource() = runTest {
        val deviceId = addSource(SourceType.DEVICE)
        device.songs[deviceId] = listOf(song("a.mp3", title = "A"), song("b.mp3", title = "B"))
        repository.rescan()
        device.songs[deviceId] = listOf(song("a.mp3", title = "A"))
        repository.rescan()

        val report = repository.diagnostics()
        assertEquals(listOf(MusicSourceCount(name = "DEVICE", type = "DEVICE", shown = 1, hidden = 1)), report.sources)
        assertEquals(time, report.lastScanAt)
    }

    @Test
    fun aRestoreIsAppliedAfterEachScan() = runTest {
        var calls = 0
        val withHook = MusicRepository(context, database, mapOf(SourceType.DEVICE to device), CoroutineScope(Job()), { time }, afterScan = { calls++ })
        withHook.rescan()
        withHook.rescan()
        assertEquals(2, calls)
    }

    private suspend fun addSource(type: SourceType): Long =
        database.sourceDao().insert(
            SourceEntity(type = type, name = type.name, location = if (type == SourceType.DEVICE) "device" else "content://tree/${type.name}"),
        )

    private fun uri(name: String, prefix: String = "content://media/external/audio/media/"): String = prefix + Uri.encode(name)

    private fun song(
        name: String,
        title: String,
        album: String = "Album",
        artist: String = "Band",
        track: Int? = null,
        size: Long = name.length.toLong(),
        modified: Long? = null,
        uriPrefix: String = "content://media/external/audio/media/",
    ) = ScannedSong(
        uri = uri(name, uriPrefix),
        fileName = name,
        folder = "Music/$album",
        title = title,
        artist = artist,
        album = album,
        trackNumber = track,
        genre = "Rock",
        sizeBytes = size,
        dateAdded = 1,
        dateModified = modified,
    )

    private class FakeMusicScanner : MusicScanner {
        val songs = HashMap<Long, List<ScannedSong>>()
        var failure: Exception? = null
        var scans = 0
        var lastKnown: Map<String, KnownSong> = emptyMap()

        override suspend fun scan(source: SourceEntity, known: Map<String, KnownSong>): List<ScannedSong> {
            scans++
            lastKnown = known
            failure?.let { throw it }
            return songs[source.id].orEmpty()
        }
    }
}
