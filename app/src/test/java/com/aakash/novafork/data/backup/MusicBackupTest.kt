package com.aakash.novafork.data.backup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.core.audio.MusicKeys
import com.aakash.novafork.data.PrefsSettingsStore
import com.aakash.novafork.data.RepositoryFixture
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.db.PlaylistEntity
import com.aakash.novafork.data.db.PlaylistSongEntity
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.inertScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

/** Song plays and playlists of songs carried from one library ("device A") to another ("device B"). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MusicBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fixtures = ArrayList<RepositoryFixture>()

    @Before
    fun clearPending() {
        File(context.filesDir, "pending_restore.json").delete()
    }

    @After
    fun tearDown() = fixtures.forEach { it.close() }

    private fun fixture(): RepositoryFixture = RepositoryFixture(inertScope(), Settings(scanDeviceStorage = false)).also { fixtures += it }

    private fun manager(fx: RepositoryFixture) =
        BackupManager(fx.context, fx.database, PrefsSettingsStore(context), fx.repository, appVersion = "1.0.test")

    private suspend fun folder(fx: RepositoryFixture): Long =
        fx.database.sourceDao().insert(SourceEntity(type = SourceType.FOLDER, name = "Music", location = TREE))

    /** Inserts songs as a scan of [sourceId] would; returns their ids by file name. */
    private suspend fun songs(fx: RepositoryFixture, sourceId: Long, vararg names: String, uriPrefix: String = "$TREE/document/"): Map<String, Long> {
        fx.database.songDao().insertAll(
            names.map { name ->
                SongEntity(
                    sourceId = sourceId,
                    uri = uriPrefix + name,
                    fileName = name,
                    folder = "Music/Album",
                    title = name.substringBeforeLast('.'),
                    albumKey = MusicKeys.albumKey("Band", "Album", "Music/Album"),
                    artistKey = "band",
                    sizeBytes = name.length * 1000L,
                )
            },
        )
        return fx.database.songDao().bySource(sourceId).associate { it.fileName to it.id }
    }

    private suspend fun export(manager: BackupManager): String {
        val out = ByteArrayOutputStream()
        manager.export(out)
        return out.toString(Charsets.UTF_8.name())
    }

    @Test
    fun playsAndPlaylistSongsTravelAndWaitForTheirSongs() = runBlocking<Unit> {
        val a = fixture()
        val aFolder = folder(a)
        val aSongs = songs(a, aFolder, "One.flac", "Two.flac", "Three.flac")
        a.database.songDao().markPlayed(aSongs.getValue("Two.flac"), 5_000L)
        a.database.songDao().markPlayed(aSongs.getValue("Two.flac"), 6_000L)
        val mix = a.database.playlistDao().insert(PlaylistEntity(name = "Mix", createdAt = 1L, updatedAt = 1L))
        a.database.playlistSongDao().insertItems(
            listOf(
                PlaylistSongEntity(playlistId = mix, songId = aSongs.getValue("Three.flac"), position = 0),
                PlaylistSongEntity(playlistId = mix, songId = aSongs.getValue("One.flac"), position = 1),
            ),
        )
        val text = export(manager(a))
        val backup = manager(a).read(text.byteInputStream())
        assertEquals(1, backup.songs.size)
        assertEquals(2, backup.summary().playlistItems)

        // Device B has the same folder but hasn't scanned its music yet.
        val b = fixture()
        val bFolder = folder(b)
        val restorer = manager(b)
        val report = restorer.restore(backup)
        assertEquals(RestoreCounts(playlistItems = 2, songs = 1), report.waiting)
        assertEquals(1, report.playlistsCreated)

        // The music scan finds the songs; the waiting entries go to them.
        val bSongs = songs(b, bFolder, "Three.flac", "One.flac", "Two.flac")
        val applied = restorer.applyPending()
        assertEquals(RestoreCounts(playlistItems = 2, songs = 1), applied.applied)
        assertEquals(RestoreCounts(), restorer.waiting())

        val two = requireNotNull(b.database.songDao().byId(bSongs.getValue("Two.flac")))
        assertEquals(2, two.playCount)
        assertEquals(6_000L, two.lastPlayedAt)
        val bMix = b.database.playlistSongDao().observePlaylists().first().single { it.name == "Mix" }
        assertEquals(listOf("Three", "One"), b.database.playlistSongDao().shownSongs(bMix.id).map { it.title })
        // A playlist of songs only stays the Audio tab's.
        assertTrue(b.database.playlistDao().observeWithCounts().first().isEmpty())
    }

    @Test
    fun aSongOnANewDeviceIsFoundByNameAndSize() = runBlocking<Unit> {
        val a = fixture()
        val aSongs = songs(a, folder(a), "Song.mp3")
        a.database.songDao().markPlayed(aSongs.getValue("Song.mp3"), 9_000L)
        val backup = manager(a).read(export(manager(a)).byteInputStream())

        // On device B the song comes from MediaStore, under another URI and folder.
        val b = fixture()
        val device = b.database.sourceDao().insert(SourceEntity(type = SourceType.DEVICE, name = "Device", location = "device"))
        val bSongs = songs(b, device, "Song.mp3", uriPrefix = "content://media/external/audio/media/7/")
        manager(b).restore(backup)

        assertEquals(1, b.database.songDao().byId(bSongs.getValue("Song.mp3"))?.playCount)
    }

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
    }
}
