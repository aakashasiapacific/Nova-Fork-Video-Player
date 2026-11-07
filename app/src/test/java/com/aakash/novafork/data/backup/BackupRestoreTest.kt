package com.aakash.novafork.data.backup

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.data.IdentifyType
import com.aakash.novafork.data.LayoutMode
import com.aakash.novafork.data.LiveScope
import com.aakash.novafork.data.PrefsSettingsStore
import com.aakash.novafork.data.RepositoryFixture
import com.aakash.novafork.data.ScannedVideo
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.SettingsStore
import com.aakash.novafork.data.ThemeMode
import com.aakash.novafork.data.TmdbJson
import com.aakash.novafork.data.TmdbSearchResult
import com.aakash.novafork.data.awaitUntil
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import com.aakash.novafork.data.db.VideoFavoriteEntity
import com.aakash.novafork.data.db.WatchlistEntity
import com.aakash.novafork.data.grantVideoAccess
import com.aakash.novafork.data.inertScope
import com.aakash.novafork.data.scannedVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Backups of one library ("device A") restored into another ("device B"): each a
 * [RepositoryFixture] with its own in-memory database; the settings file is the app's, cleared
 * in between as a new device would have it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupRestoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fixtures = ArrayList<RepositoryFixture>()
    private val liveScopes = ArrayList<LiveScope>()

    @After
    fun tearDown() {
        try {
            liveScopes.forEach { it.close() }
        } finally {
            fixtures.forEach { it.close() }
        }
    }

    private fun fixture(
        scope: CoroutineScope = inertScope(),
        settings: Settings = Settings(scanDeviceStorage = false),
    ): RepositoryFixture = RepositoryFixture(scope, settings).also { fixtures += it }

    private fun manager(
        fx: RepositoryFixture,
        store: SettingsStore = PrefsSettingsStore(context),
        clock: () -> Long = System::currentTimeMillis,
    ): BackupManager = BackupManager(fx.context, fx.database, store, fx.repository, appVersion = "1.0.test", clock = clock)

    private fun settingsFile(): SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val pendingFile: File get() = File(context.filesDir, "pending_restore.json")

    /** A new device: no settings stored yet. */
    private fun newDeviceSettings(): PrefsSettingsStore {
        settingsFile().edit().clear().commit()
        return PrefsSettingsStore(context)
    }

    private suspend fun exportText(manager: BackupManager, includeSecrets: Boolean): String {
        val out = ByteArrayOutputStream()
        manager.export(out, includeSecrets)
        return out.toString(Charsets.UTF_8.name())
    }

    private suspend fun BackupManager.restoreText(text: String): RestoreReport = restore(read(text.byteInputStream()))

    /** The share "NAS" (nas.local/Media, user alice) serving [videos], scanned; returns the source. */
    private suspend fun share(fx: RepositoryFixture, vararg videos: ScannedVideo, password: String? = SMB_PASSWORD): SourceEntity {
        val id = fx.repository.saveSmbServer(null, "NAS", "nas.local", null, "Media", "", "alice", password, null)
        val source = requireNotNull(fx.database.sourceDao().byId(id))
        fx.shares.serve(source.location, *videos)
        fx.repository.scanSource(id)
        return source
    }

    private fun assertSamePlayback(expected: VideoEntity, actual: VideoEntity) {
        assertEquals(expected.fileName, expected.positionMs, actual.positionMs)
        assertEquals(expected.fileName, expected.watched, actual.watched)
        assertEquals(expected.fileName, expected.lastPlayedAt, actual.lastPlayedAt)
        assertEquals(expected.fileName, expected.playCount, actual.playCount)
        assertEquals(expected.fileName, expected.audioTrackId, actual.audioTrackId)
        assertEquals(expected.fileName, expected.subtitleTrackId, actual.subtitleTrackId)
    }

    @Test
    fun roundTripBringsBackSettingsSourcesHistoryMatchesAndPlaylists() = runBlocking<Unit> {
        val a = fixture()
        val storeA = newDeviceSettings()
        storeA.update { it.copy(themeMode = ThemeMode.LIGHT, seekStepSeconds = 30, tmdbApiKey = "key-of-a") }
        a.tmdb.route("movie/${TmdbJson.INCEPTION_ID}", TmdbJson.inception)
        share(a, DUNE, SEVERANCE_1, CLIP)
        val dune = a.videoAt(DUNE.uri).id
        val episode = a.videoAt(SEVERANCE_1.uri).id
        val clip = a.videoAt(CLIP.uri).id
        a.repository.savePlayback(dune, 600_000L, 9_000_000L)
        // Close enough to the end to count as watched.
        a.repository.savePlayback(episode, 3_050_000L, 3_100_000L)
        a.repository.saveTracks(episode, 2, 5)
        a.repository.setWatched(clip, true)
        a.repository.identify(dune, TmdbSearchResult(TmdbJson.INCEPTION_ID, IdentifyType.MOVIE, "Inception", 2010, null, null))
        val trip = a.repository.createPlaylist("Road trip")
        a.repository.addToPlaylist(trip, listOf(clip, dune))
        val before = listOf(DUNE, SEVERANCE_1, CLIP).map { a.videoAt(it.uri) }
        assertTrue(before[1].watched)

        val text = exportText(manager(a, storeA), includeSecrets = true)

        // Device B: nothing set up, and a layout of its own.
        val storeB = newDeviceSettings()
        storeB.update { it.copy(layoutMode = LayoutMode.TABLET) }
        val b = fixture()
        b.tmdb.route("movie/${TmdbJson.INCEPTION_ID}", TmdbJson.inception)
        val restorer = manager(b, storeB)
        val report = restorer.restoreText(text)

        assertEquals(ThemeMode.LIGHT, storeB.settings.value.themeMode)
        assertEquals(30, storeB.settings.value.seekStepSeconds)
        assertEquals("key-of-a", storeB.settings.value.tmdbApiKey)
        // The backup's layout was "Automatic": this device keeps its own.
        assertEquals(LayoutMode.TABLET, storeB.settings.value.layoutMode)

        assertEquals(1, report.sourcesAdded)
        val restoredShare = requireNotNull(b.database.sourceDao().byId(report.addedSourceIds.single()))
        assertEquals(SourceType.SMB, restoredShare.type)
        assertEquals("NAS", restoredShare.name)
        assertEquals("alice", restoredShare.username)
        assertEquals(SMB_PASSWORD, restoredShare.password)
        // The share isn't scanned yet: everything waits for its videos.
        assertEquals(RestoreCounts(videos = 3, matches = 1, playlistItems = 2), report.waiting)
        assertEquals(1, report.playlistsCreated)
        assertTrue(pendingFile.exists())

        b.shares.serve(restoredShare.location, DUNE, SEVERANCE_1, CLIP)
        b.repository.scanSource(restoredShare.id)

        for (old in before) assertSamePlayback(old, b.videoAt(old.uri))
        val playlist = b.repository.observePlaylists().first().single()
        assertEquals("Road trip", playlist.name)
        assertEquals(listOf(CLIP.uri, DUNE.uri), b.repository.playlistVideos(playlist.id).map { it.video.uri })

        // The scan leaves the match to a background pass (off in this fixture); it goes through Identify.
        assertEquals(RestoreCounts(matches = 1), restorer.waiting())
        val pass = restorer.applyPending()
        assertEquals(1, pass.applied.matches)
        val matched = b.videoAt(DUNE.uri)
        assertEquals(MatchState.MANUAL, matched.matchState)
        assertEquals("movie:${TmdbJson.INCEPTION_ID}", matched.metadataKey)
        assertNotNull(b.database.metadataDao().get("movie:${TmdbJson.INCEPTION_ID}"))
        assertEquals(RestoreCounts(), restorer.waiting())
        assertFalse(pendingFile.exists())
    }

    @Test
    fun videosAreFoundByFolderAndNameThenByNameAndSize() = runBlocking<Unit> {
        val a = fixture()
        val folderA = a.repository.addFolder(TREE, "Movies")
        a.folders.serve(
            TREE,
            scannedVideo("$TREE/document/primary%3AMovies%2FDune.mkv", "Dune.mkv", "Movies", sizeBytes = 4_000_000L),
            scannedVideo("$TREE/document/primary%3AMovies%2FClips%2Fbeach.mp4", "beach.mp4", "Movies/Clips", sizeBytes = 2_000L),
        )
        a.repository.scanSource(folderA)
        val duneA = a.videoAt("$TREE/document/primary%3AMovies%2FDune.mkv")
        val beachA = a.videoAt("$TREE/document/primary%3AMovies%2FClips%2Fbeach.mp4")
        a.repository.savePlayback(duneA.id, 1_200_000L, 9_000_000L)
        a.repository.savePlayback(beachA.id, 5_000L, 60_000L)
        val favourites = a.repository.createPlaylist("Favourites")
        a.repository.addToPlaylist(favourites, listOf(beachA.id, duneA.id))
        val text = exportText(manager(a), includeSecrets = false)

        val b = fixture()
        // The same folder picked again on device B: its documents have other ids there.
        val folderB = b.repository.addFolder(TREE, "Movies")
        b.folders.serve(TREE, scannedVideo("$TREE/document/msf%3A11", "Dune.mkv", "Movies", sizeBytes = 4_000_000L))
        b.repository.scanSource(folderB)
        // The clip lives on a share there, in another folder; another Dune.mkv there is a different file.
        share(
            b,
            scannedVideo("smb://nas.local/Media/Old/beach.mp4", "beach.mp4", "Media/Old", sizeBytes = 2_000L),
            scannedVideo("smb://nas.local/Media/Old/Dune.mkv", "Dune.mkv", "Media/Old", sizeBytes = 9_999L),
        )
        val beachB = b.videoAt("smb://nas.local/Media/Old/beach.mp4").id
        // B has the playlist already, with the clip in it: the restore appends what is missing.
        val favouritesB = b.repository.createPlaylist("Favourites")
        b.repository.addToPlaylist(favouritesB, listOf(beachB))

        val report = manager(b).restoreText(text)

        assertEquals(1, report.sourcesExisting)
        assertEquals(0, report.playlistsCreated)
        assertEquals(RestoreCounts(), report.waiting)
        assertEquals(1_200_000L, b.videoAt("$TREE/document/msf%3A11").positionMs)
        assertEquals(5_000L, b.videoAt("smb://nas.local/Media/Old/beach.mp4").positionMs)
        assertEquals(0L, b.videoAt("smb://nas.local/Media/Old/Dune.mkv").positionMs)
        assertEquals(
            listOf("smb://nas.local/Media/Old/beach.mp4", "$TREE/document/msf%3A11"),
            b.repository.playlistVideos(favouritesB).map { it.video.uri },
        )
        assertEquals(1, b.repository.observePlaylists().first().size)
    }

    @Test
    fun aMediaStoreIdOnAnotherDeviceIsAnotherFile() = runBlocking<Unit> {
        grantVideoAccess()
        val a = fixture(settings = Settings(scanDeviceStorage = true))
        a.tmdb.route("movie/${TmdbJson.INCEPTION_ID}", TmdbJson.inception)
        a.device.serve(DEVICE_LOCATION, scannedVideo(MEDIA_1000034, "Dune.mkv", "Movies", sizeBytes = 4_000_000L))
        a.repository.refreshAll()
        val duneA = a.videoAt(MEDIA_1000034).id
        a.repository.savePlayback(duneA, 4_800_000L, 9_000_000L)
        a.repository.saveTracks(duneA, 2, 4)
        a.repository.identify(duneA, TmdbSearchResult(TmdbJson.INCEPTION_ID, IdentifyType.MOVIE, "Inception", 2010, null, null))
        val sciFi = a.repository.createPlaylist("Sci-fi")
        a.repository.addToPlaylist(sciFi, listOf(duneA))
        val before = a.videoAt(MEDIA_1000034)
        val text = exportText(manager(a), includeSecrets = false)

        // Device B: MediaStore gave that id to another file, and Dune.mkv has an id of its own there.
        val b = fixture(settings = Settings(scanDeviceStorage = true))
        b.tmdb.route("movie/${TmdbJson.INCEPTION_ID}", TmdbJson.inception)
        b.device.serve(
            DEVICE_LOCATION,
            scannedVideo(MEDIA_1000034, "Wedding.mp4", "DCIM/Camera", sizeBytes = 9_000_000L),
            scannedVideo("content://media/external/video/media/52", "Dune.mkv", "Movies", sizeBytes = 4_000_000L),
        )
        b.repository.refreshAll()
        val report = manager(b).restoreText(text)

        assertEquals(RestoreCounts(), report.waiting)
        val duneB = b.videoAt("content://media/external/video/media/52")
        assertSamePlayback(before, duneB)
        assertEquals(MatchState.MANUAL, duneB.matchState)
        assertEquals("movie:${TmdbJson.INCEPTION_ID}", duneB.metadataKey)
        val wedding = b.videoAt(MEDIA_1000034)
        assertEquals(0L, wedding.positionMs)
        assertNull(wedding.lastPlayedAt)
        assertEquals(0, wedding.playCount)
        assertNull(wedding.subtitleTrackId)
        assertNotEquals(MatchState.MANUAL, wedding.matchState)
        val playlist = b.repository.observePlaylists().first().single { it.name == "Sci-fi" }
        assertEquals(listOf(duneB.uri), b.repository.playlistVideos(playlist.id).map { it.video.uri })
    }

    @Test
    fun waitingEntriesAreAppliedWhenTheirSourceIsScanned() = runBlocking<Unit> {
        val a = fixture()
        a.tmdb.route("movie/${TmdbJson.INCEPTION_ID}", TmdbJson.inception)
        share(a, DUNE, CLIP)
        val dune = a.videoAt(DUNE.uri).id
        a.repository.savePlayback(dune, 600_000L, 9_000_000L)
        a.repository.identify(dune, TmdbSearchResult(TmdbJson.INCEPTION_ID, IdentifyType.MOVIE, "Inception", 2010, null, null))
        val list = a.repository.createPlaylist("Later")
        a.repository.addToPlaylist(list, listOf(dune, a.videoAt(CLIP.uri).id))
        val text = exportText(manager(a), includeSecrets = false)

        val live = LiveScope().also { liveScopes += it }
        val b = fixture(live.scope)
        b.tmdb.route("search/movie", TmdbJson.NO_RESULTS)
        b.tmdb.route("search/tv", TmdbJson.NO_RESULTS)
        b.tmdb.route("movie/${TmdbJson.INCEPTION_ID}", TmdbJson.inception)
        val restorer = manager(b)
        val report = restorer.restoreText(text)

        assertEquals(RestoreCounts(videos = 1, matches = 1, playlistItems = 2), report.waiting)
        val restoredShare = requireNotNull(b.database.sourceDao().byId(report.addedSourceIds.single()))
        // A backup without secrets brings the share back without its password.
        assertEquals("alice", restoredShare.username)
        assertNull(restoredShare.password)

        b.shares.serve(restoredShare.location, DUNE, CLIP)
        b.repository.scanSource(restoredShare.id)

        // Playback state and playlist items arrive with the scan; the match follows in the background.
        assertEquals(600_000L, b.videoAt(DUNE.uri).positionMs)
        val later = b.repository.observePlaylists().first().single { it.name == "Later" }
        assertEquals(listOf(DUNE.uri, CLIP.uri), b.repository.playlistVideos(later.id).map { it.video.uri })
        awaitUntil("the restored match") { b.videoAt(DUNE.uri).matchState == MatchState.MANUAL }
        assertEquals("movie:${TmdbJson.INCEPTION_ID}", b.videoAt(DUNE.uri).metadataKey)
        awaitUntil("nothing waiting") { restorer.waiting() == RestoreCounts() }
        assertFalse(pendingFile.exists())
    }

    @Test
    fun waitingEntriesExpireAfterThirtyDays() = runBlocking<Unit> {
        val a = fixture()
        share(a, DUNE)
        a.repository.savePlayback(a.videoAt(DUNE.uri).id, 600_000L, 9_000_000L)
        val text = exportText(manager(a), includeSecrets = false)

        val b = fixture()
        val monthAgo = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000
        val report = manager(b, clock = { monthAgo }).restoreText(text)
        assertEquals(RestoreCounts(videos = 1), report.waiting)

        val restoredShare = requireNotNull(b.database.sourceDao().byId(report.addedSourceIds.single()))
        b.shares.serve(restoredShare.location, DUNE)
        b.repository.scanSource(restoredShare.id)

        assertEquals(0L, b.videoAt(DUNE.uri).positionMs)
        assertFalse(pendingFile.exists())
    }

    @Test
    fun secretsStayOutUnlessAskedFor() = runBlocking<Unit> {
        val a = fixture()
        val storeA = newDeviceSettings()
        storeA.update { it.copy(tmdbApiKey = "tmdb-secret-key", themeMode = ThemeMode.LIGHT) }
        share(a, CLIP, password = "smb-password")
        a.database.sourceDao().insert(
            SourceEntity(
                type = SourceType.JELLYFIN,
                name = "Jelly",
                location = "http://jelly.local:8096",
                username = "bob",
                password = "jelly-password",
                token = "jelly-token",
                userId = "jelly-user-id",
                serverId = "server-1",
            ),
        )
        a.repository.addStream("Door camera", "rtsp://admin:cam-password@192.168.1.9/live")
        a.repository.addStream(null, "https://example.com/live/movie.m3u8")

        val out = ByteArrayOutputStream()
        manager(a, storeA).export(out)
        val plain = out.toString(Charsets.UTF_8.name())
        for (secret in listOf("tmdb-secret-key", "smb-password", "jelly-password", "jelly-token", "jelly-user-id", "cam-password")) {
            assertFalse("$secret is in the backup", secret in plain)
        }
        val backup = BackupCodec.decode(plain)
        assertFalse(backup.includesSecrets)
        val jelly = backup.sources.single { it.type == "JELLYFIN" }
        assertEquals("bob", jelly.username)
        assertEquals("server-1", jelly.serverId)
        // A stream that needs its password can't come back without it.
        assertEquals(listOf("https://example.com/live/movie.m3u8"), backup.sources.filter { it.type == "STREAM" }.map { it.location })
        assertTrue(backup.settings.any { it.key == "theme_mode" })
        assertTrue(backup.settings.none { it.key == "tmdb_api_key" })

        val full = BackupCodec.decode(exportText(manager(a, storeA), includeSecrets = true))
        assertTrue(full.includesSecrets)
        assertEquals("jelly-token", full.sources.single { it.type == "JELLYFIN" }.token)
        assertEquals("smb-password", full.sources.single { it.type == "SMB" }.password)
        assertEquals(2, full.sources.count { it.type == "STREAM" })
        assertTrue(full.settings.any { it.key == "tmdb_api_key" })

        // Restoring the one without secrets: device B keeps its own key, its servers ask to sign in.
        val storeB = newDeviceSettings()
        storeB.update { it.copy(tmdbApiKey = "key-of-b") }
        val b = fixture()
        manager(b, storeB).restore(backup)
        assertEquals("key-of-b", storeB.settings.value.tmdbApiKey)
        assertEquals(ThemeMode.LIGHT, storeB.settings.value.themeMode)
        val jellyB = b.database.sourceDao().all().single { it.type == SourceType.JELLYFIN }
        assertEquals("bob", jellyB.username)
        assertNull(jellyB.password)
        assertNull(jellyB.token)
        assertEquals("https://example.com/live/movie.m3u8", b.database.sourceDao().all().single { it.type == SourceType.STREAM }.location)
    }

    @Test
    fun newerAndForeignFilesAreRefused() = runBlocking<Unit> {
        val restorer = manager(fixture())
        val newer = """{"format":"$BACKUP_FORMAT","formatVersion":${BACKUP_FORMAT_VERSION + 1},"createdAt":1,"somethingNew":[]}"""
        val refused = assertThrows(BackupFormatException::class.java) {
            runBlocking { restorer.read(newer.byteInputStream()) }
        }
        assertTrue(requireNotNull(refused.message).contains("newer version"))

        for (foreign in listOf("""{"hello":"world"}""", "[1, 2, 3]", "not json at all", """{"format":"$BACKUP_FORMAT"}""")) {
            val error = assertThrows(BackupFormatException::class.java) {
                runBlocking { restorer.read(foreign.byteInputStream()) }
            }
            assertEquals(foreign, "This file isn't a Nova Fork backup", error.message)
        }
        val damaged = """{"format":"$BACKUP_FORMAT","formatVersion":1,"videos":[{"positionMs":"soon"}]}"""
        val error = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(damaged) }
        assertTrue(requireNotNull(error.message).contains("damaged"))

        // Fields a later version of this format adds are ignored.
        val later = BackupCodec.decode("""{"format":"$BACKUP_FORMAT","formatVersion":1,"appVersion":"9.9","extra":{"a":1}}""")
        assertEquals("9.9", later.appVersion)
    }

    @Test
    fun filesThatCantBeBackupsAreRefusedEarly() = runBlocking<Unit> {
        val restorer = manager(fixture())

        // A video or an archive picked by mistake: its first byte says enough.
        val binary = EndlessStream(first = 0x1A, fill = 0)
        val notJson = assertThrows(BackupFormatException::class.java) { runBlocking { restorer.read(binary) } }
        assertEquals("This file isn't a Nova Fork backup", notJson.message)
        assertTrue("read ${binary.served} bytes", binary.served <= 1024 * 1024)

        // A file that announces its size is refused without being read.
        val huge = object : InputStream() {
            override fun available(): Int = 200 * 1024 * 1024

            override fun read(): Int = throw AssertionError("read")
        }
        val announced = assertThrows(BackupFormatException::class.java) { runBlocking { restorer.read(huge) } }
        assertTrue(requireNotNull(announced.message).contains("too large"))

        // JSON that goes on and on is cut off at the limit.
        val endless = EndlessStream(first = '{'.code.toByte(), fill = ' '.code.toByte())
        val tooLarge = assertThrows(BackupFormatException::class.java) { runBlocking { restorer.read(endless) } }
        assertTrue(requireNotNull(tooLarge.message).contains("too large"))
        assertTrue("read ${endless.served} bytes", endless.served <= 17L * 1024 * 1024)

        // A byte order mark and white space before the JSON are fine.
        val backup = restorer.read(("\uFEFF \n" + BackupCodec.encode(NovaBackup(appVersion = "2.0"))).byteInputStream())
        assertEquals("2.0", backup.appVersion)
    }

    @Test
    fun settingsOfAnotherTypeAreNotRestored() = runBlocking<Unit> {
        val store = newDeviceSettings()
        store.update { it.copy(seekStepSeconds = 30) }
        val text = """{"format":"$BACKUP_FORMAT","formatVersion":1,"settings":[
            {"key":"secure_dns","type":"string","value":"true"},
            {"key":"seek_step_seconds","type":"long","value":10},
            {"key":"theme_mode","type":"string","value":"SYSTEM"},
            {"key":"from_a_later_version","type":"long","value":7}
        ]}"""

        val report = manager(fixture(), store).restoreText(text)

        assertEquals(2, report.settings)
        assertEquals(ThemeMode.SYSTEM, store.settings.value.themeMode)
        assertEquals(30, store.settings.value.seekStepSeconds)
        assertTrue(store.settings.value.secureDns)
        assertEquals(7L, settingsFile().getLong("from_a_later_version", 0L))

        // A settings file damaged some other way still opens: such values read as their defaults.
        settingsFile().edit().putString("secure_dns", "yes").putLong("seek_step_seconds", 5L).commit()
        val reread = PrefsSettingsStore(context).settings.value
        assertTrue(reread.secureDns)
        assertEquals(10, reread.seekStepSeconds)
        assertEquals(ThemeMode.SYSTEM, reread.themeMode)
    }

    @Test
    fun everyStoredSettingHasItsTypeListed() {
        val store = newDeviceSettings()
        store.update { it.copy(tmdbLanguage = "fr-FR", seekStepSeconds = 20) }
        val stored = SettingsBackup.export(settingsFile(), includeSecrets = true).associate { it.key to it.type }
        assertEquals(PrefsSettingsStore.KEY_TYPES, stored)
    }

    @Test
    fun everySettingTypeSurvivesTheRoundTrip() = runBlocking<Unit> {
        val prefs = settingsFile()
        prefs.edit().clear()
            .putString("a_string", "text")
            .putBoolean("a_flag", true)
            .putInt("an_int", 42)
            .putLong("a_long", 1L shl 40)
            .putFloat("a_float", 1.5f)
            .putStringSet("a_set", setOf("x", "y"))
            .putString("subtitles_api_key", "k")
            .commit()
        val exported = SettingsBackup.export(prefs, includeSecrets = false)
        assertTrue(exported.none { it.key == "subtitles_api_key" })
        val decoded = BackupCodec.decode(BackupCodec.encode(NovaBackup(settings = exported))).settings

        prefs.edit().clear().putString("subtitles_api_key", "kept").commit()
        assertEquals(6, SettingsBackup.restore(prefs, decoded))
        assertEquals("text", prefs.getString("a_string", null))
        assertTrue(prefs.getBoolean("a_flag", false))
        assertEquals(42, prefs.getInt("an_int", 0))
        assertEquals(1L shl 40, prefs.getLong("a_long", 0L))
        assertEquals(1.5f, prefs.getFloat("a_float", 0f), 0f)
        assertEquals(setOf("x", "y"), prefs.getStringSet("a_set", null))
        assertEquals("kept", prefs.getString("subtitles_api_key", null))
    }

    @Test
    fun newerHistoryOnTheDeviceWins() {
        val ref = BackupVideoRef(uri = "smb://nas/a.mkv", fileName = "a.mkv")
        val local = VideoEntity(sourceId = 1, uri = ref.uri, fileName = "a.mkv", folder = "", parsedTitle = "a")
        val state = BackupVideoState(ref, positionMs = 5_000L, watched = false, lastPlayedAt = 100L, playCount = 2, audioTrackId = 3)

        // Nothing played here yet: the backup's state comes whole.
        val fresh = withRestoredPlayback(local, state)
        assertEquals(5_000L, fresh.positionMs)
        assertEquals(100L, fresh.lastPlayedAt)
        assertEquals(2, fresh.playCount)
        assertEquals(3, fresh.audioTrackId)

        // Played here since: the device's own position stays, plays and tracks merge.
        val played = local.copy(positionMs = 9_000L, lastPlayedAt = 200L, playCount = 1, subtitleTrackId = 7)
        val merged = withRestoredPlayback(played, state)
        assertEquals(9_000L, merged.positionMs)
        assertEquals(200L, merged.lastPlayedAt)
        assertEquals(2, merged.playCount)
        assertEquals(3, merged.audioTrackId)
        assertEquals(7, merged.subtitleTrackId)
    }

    /** Endless bytes: [first], then [fill]; counts what was read. */
    private class EndlessStream(private val first: Byte, private val fill: Byte) : InputStream() {
        var served = 0L
            private set

        override fun read(): Int = (if (served++ == 0L) first else fill).toInt() and 0xFF

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            for (i in off until off + len) b[i] = read().toByte()
            return len
        }
    }

    @Test
    fun watchlistAndFavoritesTravelAndWaitForTheirVideos() = runBlocking<Unit> {
        pendingFile.delete()
        val a = fixture()
        share(a, DUNE, CLIP)
        val duneA = a.videoAt(DUNE.uri).id
        val clipA = a.videoAt(CLIP.uri).id
        a.database.watchlistDao().add(WatchlistEntity("v:$duneA", 1_000L))
        a.database.watchlistDao().add(WatchlistEntity("s:severance", 2_000L))
        a.database.videoFavoriteDao().add(VideoFavoriteEntity("v:$clipA", 3_000L))
        val backup = manager(a).read(exportText(manager(a), includeSecrets = true).byteInputStream())
        assertEquals(3, backup.summary().saved)
        assertEquals(setOf("severance"), backup.watchlist.mapNotNull { it.showKey }.toSet())

        // Device B: the share comes back, not scanned yet. The show is saved at once; the
        // movie and the clip wait for their files.
        newDeviceSettings()
        val b = fixture()
        val restorer = manager(b)
        val report = restorer.restore(backup)
        assertEquals(1, report.applied.saved)
        assertEquals(2, report.waiting.saved)
        assertEquals(listOf("s:severance"), b.database.watchlistDao().all().map { it.itemKey })

        val nas = requireNotNull(b.database.sourceDao().byId(report.addedSourceIds.single()))
        b.shares.serve(nas.location, CLIP, DUNE)
        b.repository.scanSource(nas.id)

        val duneB = b.videoAt(DUNE.uri).id
        val clipB = b.videoAt(CLIP.uri).id
        assertEquals(setOf("s:severance", "v:$duneB"), b.database.watchlistDao().all().map { it.itemKey }.toSet())
        assertEquals(1_000L, b.database.watchlistDao().all().single { it.itemKey == "v:$duneB" }.addedAt)
        assertEquals(listOf("v:$clipB"), b.database.videoFavoriteDao().all().map { it.itemKey })
        assertEquals(RestoreCounts(), restorer.waiting())

        // Restoring the same file again adds nothing twice.
        restorer.restore(backup)
        assertEquals(2, b.database.watchlistDao().all().size)
        assertEquals(1, b.database.videoFavoriteDao().all().size)
    }

    private companion object {
        const val SMB_PASSWORD = "hunter2"
        const val DEVICE_LOCATION = "device"
        const val MEDIA_1000034 = "content://media/external/video/media/1000034"
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AMovies"

        val DUNE = scannedVideo("smb://nas.local/Media/Movies/Dune.2021.mkv", "Dune.2021.mkv", "Media/Movies", sizeBytes = 4_000_000_000L)
        val SEVERANCE_1 = scannedVideo(
            "smb://nas.local/Media/TV/Severance/Severance.S01E01.mkv",
            "Severance.S01E01.mkv",
            "Media/TV/Severance",
            sizeBytes = 1_500_000_000L,
        )
        val CLIP = scannedVideo("smb://nas.local/Media/Clips/beach.mp4", "beach.mp4", "Media/Clips", sizeBytes = 50_000_000L)
    }
}
