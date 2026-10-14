package com.aakash.novafork.data

import android.app.Application
import android.provider.MediaStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [LibraryRepositoryImpl.watchDeviceStorage]: MediaStore changes (a download finishing, a video
 * deleted) rescan device storage once they have settled. Real time, so runBlocking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeviceStorageWatchTest {
    private val live = LiveScope()
    private lateinit var fx: RepositoryFixture
    private val repo: LibraryRepositoryImpl get() = fx.repository

    @Before
    fun setUp() {
        grantVideoAccess()
        fx = RepositoryFixture(live.scope, Settings(scanDeviceStorage = true))
    }

    @After
    fun tearDown() {
        try {
            live.close()
        } finally {
            if (::fx.isInitialized) fx.close()
        }
    }

    private fun mediaStoreChanged() {
        fx.context.contentResolver.notifyChange(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null)
    }

    /** The repository adds the device source by itself (in the background) while device scanning is on. */
    private suspend fun awaitDeviceSource() {
        awaitUntil("the device source") { fx.database.sourceDao().device() != null }
    }

    @Test
    fun changesRescanDeviceStorageOnceTheySettle() = runBlocking<Unit> {
        awaitDeviceSource()
        fx.device.serve(DEVICE_LOCATION, DOWNLOAD)
        repo.watchDeviceStorage()

        // A download changes MediaStore several times in a row.
        repeat(3) { mediaStoreChanged() }
        delay(1_000)
        assertEquals("Rescanned before the changes settled", 0, fx.device.scanCount)
        assertNull(fx.database.videoDao().byUri(DOWNLOAD.uri))

        awaitUntil("the downloaded video") { fx.database.videoDao().byUri(DOWNLOAD.uri) != null }
        // One rescan for the whole burst.
        assertEquals(1, fx.device.scanCount)
        assertTrue(fx.videoAt(DOWNLOAD.uri).present)
        assertEquals(listOf(DOWNLOAD.uri), repo.observeVideos().first().map { it.video.uri })
    }

    @Test
    fun noRescanWhileDeviceScanningIsOff() = runBlocking<Unit> {
        awaitDeviceSource()
        fx.device.serve(DEVICE_LOCATION, DOWNLOAD)
        fx.settings.update { it.copy(scanDeviceStorage = false) }
        repo.watchDeviceStorage()

        mediaStoreChanged()
        delay(MEDIA_SETTLE_MS + 1_500)

        assertEquals(0, fx.device.scanCount)
        assertNull(fx.database.videoDao().byUri(DOWNLOAD.uri))
    }

    private companion object {
        /** LibraryRepositoryImpl waits this long after the last MediaStore change. */
        const val MEDIA_SETTLE_MS = 5_000L

        /** The DEVICE source's location (one row, scanned through MediaStore). */
        const val DEVICE_LOCATION = "device"

        val DOWNLOAD = scannedVideo("content://media/external/video/media/77", "VID_20240601_090000.mp4", "Download")
    }
}
