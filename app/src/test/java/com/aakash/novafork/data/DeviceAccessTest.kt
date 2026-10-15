package com.aakash.novafork.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Device storage must be scanned with any access that lets MediaStore list videos (the video
 * permission, Android 14's limited access, or All files access alone), a permission granted
 * while the app runs must start a scan, and nothing may hold the library's scans forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeviceAccessTest {
    private val live = LiveScope()
    private var liveUsed = false
    private val fixtures = ArrayList<RepositoryFixture>()

    private fun fixture(
        settings: Settings = Settings(scanDeviceStorage = true),
        live: Boolean = false,
        scanTimeoutMs: Long? = null,
    ): RepositoryFixture {
        if (live) liveUsed = true
        return RepositoryFixture(
            scope = if (live) this.live.scope else inertScope(),
            initialSettings = settings,
            scanTimeoutMs = scanTimeoutMs,
        ).also { fixtures += it }
    }

    @After
    fun tearDown() {
        try {
            if (liveUsed) live.close()
        } finally {
            fixtures.forEach { it.close() }
        }
    }

    // Which access counts

    @Test
    fun allFilesAccessCountsAsAccessToEveryVideo() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        setAllFilesAccess(false)
        assertEquals(VideoAccess.NONE, StoragePermissions.videoAccess(context))
        assertFalse(StoragePermissions.hasAnyVideoAccess(context))

        setAllFilesAccess(true)
        assertEquals(VideoAccess.FULL, StoragePermissions.videoAccess(context))
        assertTrue(StoragePermissions.hasVideoAccess(context))
        assertTrue(StoragePermissions.hasImageAccess(context))
        assertTrue(StoragePermissions.hasAllFilesAccess(context))
        // Not "limited": with All files access every video is visible even without the media permission.
        grantLimitedAccess()
        assertFalse(StoragePermissions.hasPartialAccess(context))
        assertEquals(
            "allowed",
            StoragePermissions.describe(context).toMap()["All files access (MANAGE_EXTERNAL_STORAGE)"],
        )
    }

    @Test
    fun limitedAccessAndTheVideoPermission() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        setAllFilesAccess(false)
        grantLimitedAccess()
        assertEquals(VideoAccess.PARTIAL, StoragePermissions.videoAccess(context))
        assertTrue(StoragePermissions.hasAnyVideoAccess(context))

        grantVideoAccess()
        assertEquals(VideoAccess.FULL, StoragePermissions.videoAccess(context))
        assertFalse(StoragePermissions.hasPartialAccess(context))
        val states = StoragePermissions.describe(context).toMap()
        assertEquals("granted", states["Videos (READ_MEDIA_VIDEO)"])
        assertEquals("denied", states["Images (READ_MEDIA_IMAGES)"])
        assertEquals("not on this Android version", states["Storage (READ_EXTERNAL_STORAGE)"])
    }

    @Test
    fun allFilesAccessAloneScansDeviceStorage() = runTest {
        val fx = fixture()
        fx.device.serve(DEVICE_LOCATION, CAMERA_CLIP, SCREEN_RECORDING)
        setAllFilesAccess(false)
        fx.repository.refreshAll()
        assertEquals("No access: device storage is not asked", 0, fx.device.scanCount)

        setAllFilesAccess(true)
        fx.repository.refreshAll()

        assertEquals(1, fx.device.scanCount)
        assertTrue(fx.videoAt(CAMERA_CLIP.uri).present)
        assertTrue(fx.videoAt(SCREEN_RECORDING.uri).present)
        assertEquals(VideoKind.VIDEO, fx.videoAt(SCREEN_RECORDING.uri).kind)
    }

    @Test
    fun limitedAccessScansTheVideosThatWerePicked() = runTest {
        val fx = fixture()
        setAllFilesAccess(false)
        grantLimitedAccess()
        fx.device.serve(DEVICE_LOCATION, CAMERA_CLIP)

        fx.repository.refreshAll()

        assertEquals(1, fx.device.scanCount)
        assertTrue(fx.videoAt(CAMERA_CLIP.uri).present)
    }

    // Access granted while the app runs

    @Test
    fun accessGrantedWhileTheAppRunsScansDeviceStorageInTheBackground() = runBlocking<Unit> {
        setAllFilesAccess(false)
        val fx = fixture(live = true)
        awaitUntil("the device source") { fx.database.sourceDao().device() != null }
        fx.device.serve(DEVICE_LOCATION, CAMERA_CLIP, WHATSAPP_VIDEO)

        // Back in the app without access: nothing to scan.
        fx.repository.onStorageAccessChanged()
        delay(QUIET_MS)
        assertEquals(0, fx.device.scanCount)

        // "Allow all" in system settings, then back in the app: scanned without a restart.
        grantVideoAccess()
        fx.repository.onStorageAccessChanged()
        awaitUntil("the device videos") { fx.database.videoDao().byUri(WHATSAPP_VIDEO.uri) != null }
        assertEquals(1, fx.device.scanCount)
        assertTrue(fx.videoAt(CAMERA_CLIP.uri).present)

        // Resuming again with the same access changes nothing.
        fx.repository.onStorageAccessChanged()
        delay(QUIET_MS)
        assertEquals(1, fx.device.scanCount)

        // More videos picked in the limited-access picker: a rescan is asked for explicitly.
        fx.repository.onStorageAccessChanged(rescan = true)
        awaitUntil("a second scan") { fx.device.scanCount == 2 }
    }

    @Test
    fun allFilesAccessGrantedWhileTheAppRunsScansDeviceStorage() = runBlocking<Unit> {
        setAllFilesAccess(false)
        val fx = fixture(live = true)
        awaitUntil("the device source") { fx.database.sourceDao().device() != null }
        fx.device.serve(DEVICE_LOCATION, CAMERA_CLIP)

        setAllFilesAccess(true)
        fx.repository.onStorageAccessChanged()

        awaitUntil("the camera video") { fx.database.videoDao().byUri(CAMERA_CLIP.uri) != null }
        assertEquals(1, fx.device.scanCount)
    }

    // Refresh

    @Test
    fun pullToRefreshDuringARefreshScansDeviceStorageAgain() = runTest {
        grantVideoAccess()
        val fx = fixture()
        val folderId = fx.repository.addFolder(TREE, "Library")
        fx.device.serve(DEVICE_LOCATION, CAMERA_CLIP)
        fx.folders.serve(TREE, INCEPTION)
        val gate = fx.folders.hold(TREE)

        val launchRefresh = async { fx.repository.refreshAll() }
        try {
            gate.entered.await()
            // Device storage went first; a screen recording is saved after its scan.
            assertEquals(1, fx.device.scanCount)
            fx.device.serve(DEVICE_LOCATION, CAMERA_CLIP, SCREEN_RECORDING)
            // Pull to refresh joins the running refresh instead of starting another.
            fx.repository.refreshAll()
        } finally {
            gate.open()
        }
        launchRefresh.await()

        assertEquals("The running refresh scanned device storage once more", 2, fx.device.scanCount)
        assertTrue(fx.videoAt(SCREEN_RECORDING.uri).present)
        assertTrue(fx.videoAt(INCEPTION.uri).present)
        assertEquals(folderId, fx.videoAt(INCEPTION.uri).sourceId)
        assertFalse(fx.repository.scanState.value.running)
    }

    // Stuck scans

    @Test
    fun aScanThatNeverFinishesIsGivenUpAndTheLibraryMovesOn() = runBlocking<Unit> {
        grantVideoAccess()
        val fx = fixture(scanTimeoutMs = 300)
        val stuckId = fx.repository.addFolder(TREE, "Stuck")
        fx.folders.serve(TREE, INCEPTION)
        // A document provider that never answers.
        fx.folders.hold(TREE)
        fx.device.serve(DEVICE_LOCATION, CAMERA_CLIP)

        fx.repository.scanSource(stuckId)

        val stuck = requireNotNull(fx.repository.source(stuckId))
        assertTrue(stuck.lastError.orEmpty(), stuck.lastError.orEmpty().startsWith("Scanning stopped"))
        assertFalse(fx.repository.scanState.value.running)
        assertNull(fx.database.videoDao().byUri(INCEPTION.uri))

        // Nothing waits behind it: the next refresh scans device storage.
        fx.repository.refreshAll()
        assertEquals(1, fx.device.scanCount)
        assertTrue(fx.videoAt(CAMERA_CLIP.uri).present)
    }

    @Test
    fun aScannersOwnTimeOutFailsOnlyItsSource() = runTest {
        val fx = fixture(settings = Settings(scanDeviceStorage = false))
        val folderId = fx.repository.addFolder(TREE, "Library")
        val shareId = fx.repository.saveSmbServer(
            id = null, name = "NAS", host = "nas.local", port = null, share = "media", path = "",
            username = null, password = null, domain = null,
        )
        // A client library's own time-out escaping as a CancellationException.
        fx.folders.failure = CancellationException("Timed out waiting for the folder")
        fx.shares.serve(SHARE_ROOT, SHARE_MOVIE)

        fx.repository.refreshAll()

        assertEquals("Timed out waiting for the folder", fx.repository.source(folderId)?.lastError)
        assertTrue("The refresh went on to the share", fx.videoAt(SHARE_MOVIE.uri).present)
        assertNull(fx.repository.source(shareId)?.lastError)
    }

    // Library diagnostics

    @Test
    fun libraryStatsCountEachSourceAndWhatTheLastScanDid() = runTest {
        grantVideoAccess()
        val fx = fixture()
        fx.device.serve(DEVICE_LOCATION, CAMERA_CLIP, SCREEN_RECORDING, WHATSAPP_VIDEO)
        val folderId = fx.repository.addFolder(TREE, "Library")
        fx.folders.serve(TREE, INCEPTION)
        fx.repository.refreshAll()
        // The screen recording was deleted; the folder's provider then fails.
        fx.device.serve(DEVICE_LOCATION, CAMERA_CLIP, WHATSAPP_VIDEO)
        fx.folders.failure = IOException("Permission revoked")
        fx.repository.refreshAll()

        val stats = fx.repository.libraryStats()

        assertTrue(stats.deviceScannable)
        assertNull(stats.scanningSource)
        val device = stats.sources.single { it.source.type == SourceType.DEVICE }
        assertEquals(2, device.present)
        assertEquals(1, device.missing)
        val deviceScan = requireNotNull(device.lastScan)
        assertEquals(2, deviceScan.found)
        assertEquals(0, deviceScan.added)
        assertEquals(1, deviceScan.hidden)
        assertNull(deviceScan.error)

        val folder = stats.sources.single { it.source.id == folderId }
        assertEquals(1, folder.present)
        assertEquals("Permission revoked", folder.lastScan?.error)
        assertNull(folder.lastScan?.found)
        assertEquals("Permission revoked", folder.source.lastError)

        // Shown videos by kind: two camera/WhatsApp clips (plain videos) and a movie waiting for TMDB.
        val byKind = stats.kinds.groupBy({ it.kind }, { it.count }).mapValues { it.value.sum() }
        assertEquals(2, byKind[VideoKind.VIDEO])
        assertEquals(1, byKind[VideoKind.MOVIE])
        assertNotNull(stats.kinds.firstOrNull { it.kind == VideoKind.MOVIE && it.matchState == MatchState.PENDING })
    }

    private companion object {
        /** Long enough for a background scan that should not happen to have happened. */
        const val QUIET_MS = 400L

        /** The DEVICE source's location (one row, scanned through MediaStore). */
        const val DEVICE_LOCATION = "device"
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AVideos"

        // Device videos as MediaStore lists them: camera, screen recorder and WhatsApp, no metadata.
        val CAMERA_CLIP = scannedVideo("content://media/external/video/media/101", "20240601_101500.mp4", "DCIM/Camera", sizeBytes = null)
        val SCREEN_RECORDING = scannedVideo(
            "content://media/external/video/media/102",
            "Screen_Recording_20240602-101010_Chrome.mp4",
            "DCIM/Screen recordings",
            sizeBytes = null,
        )
        val WHATSAPP_VIDEO = scannedVideo(
            "content://media/external/video/media/103",
            "VID-20240603-WA0001.mp4",
            "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video",
            sizeBytes = null,
        )
        val INCEPTION = scannedVideo("$TREE/document/inception", "Inception (2010).mkv", "Movies")

        val SHARE_ROOT = SmbUris.build("nas.local", "media")
        val SHARE_MOVIE = scannedVideo(
            SmbUris.build("nas.local", "media", "Movies/Heat (1995).mkv"),
            "Heat (1995).mkv",
            "Movies",
        )
    }
}
