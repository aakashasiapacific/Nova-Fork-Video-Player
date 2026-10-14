package com.aakash.novafork.data

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.aakash.novafork.core.library.LibraryKeys
import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.MetadataEntity
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import com.aakash.novafork.data.db.VideoKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** Scanning, merging and source management of [LibraryRepositoryImpl] against a real Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LibraryScanTest {
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

    @Test
    fun scanAddsNewFilesParsedFromTheirNames() = runTest {
        val folderId = repo.addFolder(TREE, "Library")
        fx.folders.serve(TREE, INCEPTION, OFFICE_S1E2, CAMERA_CLIP)

        repo.scanSource(folderId)

        val movie = fx.videoAt(INCEPTION.uri)
        assertEquals(folderId, movie.sourceId)
        assertEquals("Inception (2010).mkv", movie.fileName)
        assertEquals("Movies", movie.folder)
        assertEquals(VideoKind.MOVIE, movie.kind)
        assertEquals("Inception", movie.parsedTitle)
        assertEquals(2010, movie.parsedYear)
        assertNull(movie.showKey)
        assertEquals(MatchState.PENDING, movie.matchState)
        assertEquals(1_000L, movie.dateAdded)
        assertTrue(movie.present)

        val episode = fx.videoAt(OFFICE_S1E2.uri)
        assertEquals(VideoKind.EPISODE, episode.kind)
        assertEquals("The Office", episode.parsedTitle)
        assertEquals(1, episode.season)
        assertEquals(2, episode.episode)
        assertEquals(LibraryKeys.showKey(null, "The Office"), episode.showKey)
        assertEquals(MatchState.PENDING, episode.matchState)

        // Nothing to look up for a home video.
        val clip = fx.videoAt(CAMERA_CLIP.uri)
        assertEquals(VideoKind.VIDEO, clip.kind)
        assertEquals(MatchState.UNMATCHED, clip.matchState)

        val source = requireNotNull(repo.source(folderId))
        assertEquals(SourceType.FOLDER, source.type)
        assertNotNull(source.lastScanAt)
        assertNull(source.lastError)
        assertEquals(1, fx.folders.scanCount)
        assertEquals(3, repo.observeVideos().first().size)
    }

    @Test
    fun rescanKeepsPlaybackStateAndMatch() = runTest {
        val folderId = repo.addFolder(TREE, "Library")
        fx.folders.serve(TREE, INCEPTION)
        repo.scanSource(folderId)
        val id = fx.videoAt(INCEPTION.uri).id
        repo.savePlayback(id, 60_000, 8_880_000)
        repo.saveTracks(id, 2, 5)
        fx.edit(INCEPTION.uri) { it.copy(metadataKey = "movie:27205", matchState = MatchState.MATCHED, matchAttemptAt = 42L) }

        // The same file again, now with a new size and a later date from the scanner.
        fx.folders.serve(TREE, INCEPTION.copy(sizeBytes = 900_000_000L, dateAdded = 9_000L))
        repo.scanSource(folderId)

        val video = fx.videoAt(INCEPTION.uri)
        assertEquals(id, video.id)
        assertEquals(1, fx.videosOf(folderId).size)
        assertEquals(900_000_000L, video.sizeBytes)
        assertEquals(1_000L, video.dateAdded)
        assertEquals(60_000L, video.positionMs)
        assertEquals(8_880_000L, video.durationMs)
        assertNotNull(video.lastPlayedAt)
        assertEquals(2, video.audioTrackId)
        assertEquals(5, video.subtitleTrackId)
        assertEquals("movie:27205", video.metadataKey)
        assertEquals(MatchState.MATCHED, video.matchState)
        assertEquals(42L, video.matchAttemptAt)
    }

    @Test
    fun missingFilesAreHiddenNotDeleted() = runTest {
        val folderId = repo.addFolder(TREE, "Library")
        fx.folders.serve(TREE, INCEPTION, CAMERA_CLIP)
        repo.scanSource(folderId)
        val clipId = fx.videoAt(CAMERA_CLIP.uri).id
        repo.savePlayback(clipId, 5_000, 600_000)

        fx.folders.serve(TREE, INCEPTION)
        repo.scanSource(folderId)

        val gone = fx.videoAt(CAMERA_CLIP.uri)
        assertFalse(gone.present)
        assertEquals(5_000L, gone.positionMs)
        assertTrue(fx.videoAt(INCEPTION.uri).present)
        assertEquals(listOf(INCEPTION.uri), repo.observeVideos().first().map { it.video.uri })

        // Back on the next scan, with its id and resume point.
        fx.folders.serve(TREE, INCEPTION, CAMERA_CLIP)
        repo.scanSource(folderId)

        val back = fx.videoAt(CAMERA_CLIP.uri)
        assertTrue(back.present)
        assertEquals(clipId, back.id)
        assertEquals(5_000L, back.positionMs)
        assertEquals(2, repo.observeVideos().first().size)
    }

    @Test
    fun renamedFileIsParsedAgain() = runTest {
        val folderId = repo.addFolder(TREE, "Library")
        fx.folders.serve(TREE, INCEPTION, OFFICE_S1E2)
        repo.scanSource(folderId)
        val episodeId = fx.videoAt(OFFICE_S1E2.uri).id
        repo.savePlayback(episodeId, 120_000, 1_300_000)
        fx.edit(OFFICE_S1E2.uri) {
            it.copy(
                metadataKey = "tv:2316",
                showKey = "tv:2316",
                episodeTitle = "Sexual Harassment",
                matchState = MatchState.MATCHED,
                matchAttemptAt = 42L,
            )
        }
        fx.edit(INCEPTION.uri) { it.copy(metadataKey = "movie:27205", matchState = MatchState.MANUAL) }

        // Same files (same URIs) under new names.
        fx.folders.serve(
            TREE,
            INCEPTION.copy(fileName = "Inception.2010.1080p.BluRay.x264.mkv"),
            OFFICE_S1E2.copy(fileName = "The.Office.S01E03.mkv"),
        )
        repo.scanSource(folderId)

        val episode = fx.videoAt(OFFICE_S1E2.uri)
        assertEquals(episodeId, episode.id)
        assertEquals("The.Office.S01E03.mkv", episode.fileName)
        assertEquals(1, episode.season)
        assertEquals(3, episode.episode)
        assertEquals(MatchState.PENDING, episode.matchState)
        assertNull(episode.metadataKey)
        assertNull(episode.episodeTitle)
        assertNull(episode.matchAttemptAt)
        assertEquals(LibraryKeys.showKey(null, "The Office"), episode.showKey)
        assertEquals(120_000L, episode.positionMs)

        // A manual match is the user's call: a new name does not undo it.
        val movie = fx.videoAt(INCEPTION.uri)
        assertEquals("Inception.2010.1080p.BluRay.x264.mkv", movie.fileName)
        assertEquals(MatchState.MANUAL, movie.matchState)
        assertEquals("movie:27205", movie.metadataKey)
    }

    @Test
    fun failedScanKeepsTheLibraryAndReportsTheError() = runTest {
        val folderId = repo.addFolder(TREE, "Library")
        fx.folders.serve(TREE, INCEPTION)
        repo.scanSource(folderId)

        fx.folders.failure = IOException("Permission revoked")
        repo.scanSource(folderId)

        assertTrue(fx.videoAt(INCEPTION.uri).present)
        assertEquals("Permission revoked", repo.source(folderId)?.lastError)
        assertEquals("Library: Permission revoked", repo.scanState.value.lastError)
        assertFalse(repo.scanState.value.running)

        fx.folders.failure = null
        repo.scanSource(folderId)
        assertNull(repo.source(folderId)?.lastError)
    }

    @Test
    fun launchRefreshSkipsFoldersScannedRecently() = runTest {
        val folderId = repo.addFolder(TREE, "Library")
        fx.folders.serve(TREE, INCEPTION)
        repo.scanSource(folderId)
        assertEquals(1, fx.folders.scanCount)

        // The first refresh of a launch leaves a folder scanned minutes ago alone…
        fx.folders.serve(TREE, INCEPTION, CAMERA_CLIP)
        repo.refreshAll()
        assertEquals(1, fx.folders.scanCount)
        assertNull(fx.database.videoDao().byUri(CAMERA_CLIP.uri))

        // …a later one (pull to refresh) walks it again.
        repo.refreshAll()
        assertEquals(2, fx.folders.scanCount)
        assertTrue(fx.videoAt(CAMERA_CLIP.uri).present)
        assertFalse(repo.scanState.value.running)
    }

    @Test
    fun deviceStorageFollowsTheSettingAndThePermission() = runTest {
        fx.device.serve(DEVICE_LOCATION, DEVICE_CLIP)

        // Off (the fixture's setting): there is no device source at all.
        repo.refreshAll()
        assertNull(fx.database.sourceDao().device())

        // On, but without video access MediaStore is not asked.
        fx.settings.update { it.copy(scanDeviceStorage = true) }
        repo.refreshAll()
        val device = requireNotNull(fx.database.sourceDao().device())
        assertTrue(device.enabled)
        assertEquals(DEVICE_LOCATION, device.location)
        assertEquals(0, fx.device.scanCount)

        grantVideoAccess()
        repo.refreshAll()
        assertEquals(1, fx.device.scanCount)
        val clip = fx.videoAt(DEVICE_CLIP.uri)
        assertEquals(device.id, clip.sourceId)
        repo.savePlayback(clip.id, 5_000, 600_000)
        assertEquals(listOf(DEVICE_CLIP.uri), repo.observeVideos().first().map { it.video.uri })

        // Turned off it is hidden, history and all, rather than deleted.
        fx.settings.update { it.copy(scanDeviceStorage = false) }
        repo.refreshAll()
        assertFalse(requireNotNull(fx.database.sourceDao().device()).enabled)
        assertEquals(1, fx.device.scanCount)
        assertTrue(repo.observeVideos().first().isEmpty())
        assertTrue(repo.observeSources().first().none { it.type == SourceType.DEVICE })
        assertEquals(5_000L, fx.videoAt(DEVICE_CLIP.uri).positionMs)

        // Back on: the same source and video, resume point included.
        fx.settings.update { it.copy(scanDeviceStorage = true) }
        repo.refreshAll()
        assertEquals(device.id, fx.database.sourceDao().device()?.id)
        assertEquals(2, fx.device.scanCount)
        val back = fx.videoAt(DEVICE_CLIP.uri)
        assertEquals(clip.id, back.id)
        assertEquals(5_000L, back.positionMs)
        assertEquals(listOf(DEVICE_CLIP.uri), repo.observeVideos().first().map { it.video.uri })
    }

    @Test
    fun addStreamKeepsOneSourceAndVideoPerUrl() = runTest {
        val url = "https://cdn.example.com/live/channel.m3u8"

        val id = repo.addStream(null, url)
        val again = repo.addStream("Renamed", "  $url  ")

        assertEquals(id, again)
        val streams = fx.database.sourceDao().all().filter { it.type == SourceType.STREAM }
        assertEquals(1, streams.size)
        assertEquals(url, streams.single().location)
        assertEquals("channel.m3u8", streams.single().name)
        val video = fx.videoAt(url)
        assertEquals(id, video.id)
        assertEquals(streams.single().id, video.sourceId)
        assertEquals("channel.m3u8", video.fileName)
        assertEquals(VideoKind.VIDEO, video.kind)
        assertEquals(MatchState.UNMATCHED, video.matchState)

        // A typed "Title (Year)" is worth a TMDB lookup.
        val movieId = repo.addStream("Big Buck Bunny (2008)", "http://media.example.com/bbb_sunflower_1080p.mp4")
        val movie = requireNotNull(repo.video(movieId)).video
        assertEquals(VideoKind.MOVIE, movie.kind)
        assertEquals("Big Buck Bunny", movie.parsedTitle)
        assertEquals(2008, movie.parsedYear)
        assertEquals(MatchState.PENDING, movie.matchState)
        assertEquals(2, fx.database.sourceDao().all().count { it.type == SourceType.STREAM })

        assertTrue(runCatching { repo.addStream(null, "   ") }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun deviceStorageCannotBeRemoved() = runTest {
        val deviceId = fx.database.sourceDao().insert(
            SourceEntity(type = SourceType.DEVICE, name = "Device storage", location = "device"),
        )
        fx.database.videoDao().insert(
            VideoEntity(
                sourceId = deviceId,
                uri = "content://media/external/video/media/1",
                fileName = "VID_20240501_123456.mp4",
                folder = "DCIM/Camera",
                parsedTitle = "VID_20240501_123456",
                kind = VideoKind.VIDEO,
                matchState = MatchState.UNMATCHED,
            ),
        )

        val error = runCatching { repo.removeSource(deviceId) }.exceptionOrNull()

        assertTrue("Expected IllegalArgumentException, got $error", error is IllegalArgumentException)
        assertNotNull(repo.source(deviceId))
        assertEquals(1, fx.videosOf(deviceId).size)
    }

    @Test
    fun removingAFolderDeletesItsVideosAndTheirMetadata() = runTest {
        val tree = Uri.parse(TREE)
        fx.context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val folderId = repo.addFolder(TREE, "Library")
        val otherId = repo.addFolder(OTHER_TREE, "Other")
        fx.folders.serve(TREE, INCEPTION, CAMERA_CLIP)
        fx.folders.serve(OTHER_TREE, SINTEL)
        repo.scanSource(folderId)
        repo.scanSource(otherId)
        fx.database.metadataDao().upsert(MetadataEntity(key = "movie:27205", tmdbId = 27205, type = "movie", title = "Inception"))
        fx.database.metadataDao().upsert(MetadataEntity(key = "movie:45745", tmdbId = 45745, type = "movie", title = "Sintel"))
        fx.edit(INCEPTION.uri) { it.copy(metadataKey = "movie:27205", matchState = MatchState.MATCHED) }
        fx.edit(SINTEL.uri) { it.copy(metadataKey = "movie:45745", matchState = MatchState.MATCHED) }

        repo.removeSource(folderId)

        assertNull(repo.source(folderId))
        assertTrue(fx.videosOf(folderId).isEmpty())
        assertNull(fx.database.videoDao().byUri(INCEPTION.uri))
        assertNull(fx.database.videoDao().byUri(CAMERA_CLIP.uri))
        assertNull(fx.database.metadataDao().get("movie:27205"))
        assertNotNull(fx.database.metadataDao().get("movie:45745"))
        assertEquals(listOf(SINTEL.uri), repo.observeVideos().first().map { it.video.uri })
        assertTrue(fx.context.contentResolver.persistedUriPermissions.none { it.uri == tree })

        // Removing it again is a no-op.
        repo.removeSource(folderId)
        assertNotNull(repo.source(otherId))
    }

    @Test
    fun fileTwoSharesCoverStaysWithTheShareThatFoundItFirst() = runTest {
        val tvId = repo.saveSmbServer(
            id = null, name = "NAS TV", host = "nas.local", port = null, share = "media", path = "TV",
            username = null, password = null, domain = null,
        )
        val wholeId = repo.saveSmbServer(
            id = null, name = "NAS", host = "nas.local", port = null, share = "media", path = "",
            username = "alice", password = "secret", domain = "WORKGROUP",
        )
        assertEquals(SMB_TV, repo.source(tvId)?.location)
        assertEquals(SMB_ROOT, repo.source(wholeId)?.location)
        fx.shares.serve(SMB_TV, SMB_EPISODE)
        fx.shares.serve(SMB_ROOT, SMB_MOVIE, SMB_EPISODE)

        repo.scanSource(tvId)
        val episodeId = fx.videoAt(SMB_EPISODE.uri).id
        repo.savePlayback(episodeId, 300_000, 3_000_000)
        repo.scanSource(wholeId)

        assertEquals(tvId, fx.videoAt(SMB_EPISODE.uri).sourceId)
        assertEquals(listOf(SMB_MOVIE.uri), fx.videosOf(wholeId).map { it.uri })

        // Scanning the narrower share again moves nothing either.
        repo.scanSource(tvId)
        val episode = fx.videoAt(SMB_EPISODE.uri)
        assertEquals(tvId, episode.sourceId)
        assertEquals(episodeId, episode.id)
        assertEquals(300_000L, episode.positionMs)
        assertTrue(episode.present)
        assertEquals(2, repo.observeVideos().first().size)
    }

    @Test
    fun fileItsShareNoLongerSeesMovesToTheShareThatDoes() = runTest {
        val tvId = repo.saveSmbServer(
            id = null, name = "NAS TV", host = "nas.local", port = null, share = "media", path = "TV",
            username = null, password = null, domain = null,
        )
        val wholeId = repo.saveSmbServer(
            id = null, name = "NAS", host = "nas.local", port = null, share = "media", path = "",
            username = null, password = null, domain = null,
        )
        fx.shares.serve(SMB_TV, SMB_EPISODE)
        fx.shares.serve(SMB_ROOT, SMB_MOVIE, SMB_EPISODE)
        repo.scanSource(tvId)
        val episodeId = fx.videoAt(SMB_EPISODE.uri).id
        repo.savePlayback(episodeId, 300_000, 3_000_000)
        repo.scanSource(wholeId)
        assertEquals(tvId, fx.videoAt(SMB_EPISODE.uri).sourceId)

        // The narrower share stops seeing it (moved on the NAS, or the share's path changed)…
        fx.shares.serve(SMB_TV)
        repo.scanSource(tvId)
        assertFalse(fx.videoAt(SMB_EPISODE.uri).present)

        // …so the share that still lists it takes it over, history included.
        repo.scanSource(wholeId)
        val episode = fx.videoAt(SMB_EPISODE.uri)
        assertEquals(wholeId, episode.sourceId)
        assertEquals(episodeId, episode.id)
        assertEquals(300_000L, episode.positionMs)
        assertTrue(episode.present)
        assertTrue(fx.videosOf(tvId).isEmpty())
        assertEquals(setOf(SMB_MOVIE.uri, SMB_EPISODE.uri), repo.observeVideos().first().map { it.video.uri }.toSet())
    }

    @Test
    fun removingAShareHandsCoveredVideosToTheOverlappingShare() = runTest {
        val wholeId = repo.saveSmbServer(
            id = null, name = "NAS", host = "nas.local", port = null, share = "media", path = "",
            username = "alice", password = "secret", domain = "WORKGROUP",
        )
        val tvId = repo.saveSmbServer(
            id = null, name = "NAS TV", host = "nas.local", port = null, share = "media", path = "TV",
            username = null, password = null, domain = null,
        )
        fx.shares.serve(SMB_ROOT, SMB_MOVIE, SMB_EPISODE)
        fx.shares.serve(SMB_TV, SMB_EPISODE)
        repo.scanSource(wholeId)
        val episodeId = fx.videoAt(SMB_EPISODE.uri).id
        repo.savePlayback(episodeId, 300_000, 3_000_000)
        repo.scanSource(tvId)
        assertEquals(wholeId, fx.videoAt(SMB_EPISODE.uri).sourceId)
        assertEquals(SmbCredentials("alice", "secret", "WORKGROUP"), repo.smbCredentials("NAS.LOCAL", "media"))

        repo.removeSource(wholeId)

        assertNull(repo.source(wholeId))
        // Only the whole share had the movie.
        assertNull(fx.database.videoDao().byUri(SMB_MOVIE.uri))
        val episode = fx.videoAt(SMB_EPISODE.uri)
        assertEquals(tvId, episode.sourceId)
        assertEquals(episodeId, episode.id)
        assertEquals(300_000L, episode.positionMs)
        assertTrue(episode.present)
        assertEquals(listOf(SMB_EPISODE.uri), repo.observeVideos().first().map { it.video.uri })
        // Credentials now come from the share that is left.
        assertEquals(SmbCredentials(null, null, null), repo.smbCredentials("nas.local", "Media"))
    }

    @Test
    fun smbServerNeedsHostAndShare() = runTest {
        val noHost = runCatching {
            repo.saveSmbServer(
                id = null, name = "", host = "  ", port = null, share = "media", path = "",
                username = null, password = null, domain = null,
            )
        }.exceptionOrNull()
        val noShare = runCatching {
            repo.saveSmbServer(
                id = null, name = "", host = "nas.local", port = null, share = "/", path = "",
                username = null, password = null, domain = null,
            )
        }.exceptionOrNull()

        assertTrue(noHost is IllegalArgumentException)
        assertTrue(noShare is IllegalArgumentException)
        assertTrue(fx.database.sourceDao().smbSources().isEmpty())
    }

    private companion object {
        val TREE = "content://com.android.externalstorage.documents/tree/primary%3AVideos"
        val OTHER_TREE = "content://com.android.externalstorage.documents/tree/primary%3AOther"

        val INCEPTION = scannedVideo("$TREE/document/inception", "Inception (2010).mkv", "Movies")
        val OFFICE_S1E2 = scannedVideo("$TREE/document/office-1-2", "The.Office.S01E02.mkv", "TV/The Office/Season 1")
        val CAMERA_CLIP = scannedVideo("$TREE/document/clip", "VID_20240501_123456.mp4", "Camera")
        val SINTEL = scannedVideo("$OTHER_TREE/document/sintel", "Sintel (2010).mkv", "Movies")

        /** The DEVICE source's location (one row, scanned through MediaStore). */
        const val DEVICE_LOCATION = "device"
        val DEVICE_CLIP = scannedVideo("content://media/external/video/media/42", "VID_20240501_123456.mp4", "DCIM/Camera")

        val SMB_ROOT = SmbUris.build("nas.local", "media")
        val SMB_TV = SmbUris.build("nas.local", "media", "TV")
        val SMB_EPISODE = scannedVideo(
            SmbUris.build("nas.local", "media", "TV/Severance/Severance.S01E01.mkv"),
            "Severance.S01E01.mkv",
            "TV/Severance",
        )
        val SMB_MOVIE = scannedVideo(SmbUris.build("nas.local", "media", "Movies/Heat (1995).mkv"), "Heat (1995).mkv", "Movies")
    }
}
