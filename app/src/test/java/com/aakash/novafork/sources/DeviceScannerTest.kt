package com.aakash.novafork.sources

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.data.FakeSettingsStore
import com.aakash.novafork.data.FakeTmdb
import com.aakash.novafork.data.LibraryRepositoryImpl
import com.aakash.novafork.data.ScanState
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.db.NovaDatabase
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoKind
import com.aakash.novafork.data.grantVideoAccess
import com.aakash.novafork.data.inertScope
import com.aakash.novafork.data.setAllFilesAccess
import com.aakash.novafork.ui.LibraryIndex
import com.aakash.novafork.ui.UiMappers
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.MediaKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * [DeviceScanner] against a MediaStore that lists what a phone's does (camera videos, screen
 * recordings, WhatsApp videos, no size or duration yet), through the real repository and the
 * Browse, Library and Home models: every one of them shows up, with or without a poster.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeviceScannerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var mediaStore: FakeMediaStore
    private lateinit var database: NovaDatabase

    @Before
    fun setUp() {
        mediaStore = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
        mediaStore.videos = PHONE_VIDEOS
        database = Room.inMemoryDatabaseBuilder(context, NovaDatabase::class.java).allowMainThreadQueries().build()
        setAllFilesAccess(false)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository() = LibraryRepositoryImpl(
        context = context,
        database = database,
        scanners = mapOf(SourceType.DEVICE to DeviceScanner(context)),
        tmdb = FakeTmdb().client(),
        settings = FakeSettingsStore(Settings(scanDeviceStorage = true)),
        scope = inertScope(),
    )

    @Test
    fun phoneVideosWithoutPostersReachBrowseLibraryAndHome() = runTest {
        grantVideoAccess()
        val repo = repository()

        repo.refreshAll()

        val device = repo.observeSources().first().single { it.type == SourceType.DEVICE }
        assertNull(device.lastError)
        val index = LibraryIndex.build(repo.observeVideos().first(), repo.observeSources().first(), preferLocalArtwork = false)
        // Everything but the video in a hidden folder.
        assertEquals(5, index.videos.size)
        assertEquals(5, index.videoCountBySource[device.id])

        val root = requireNotNull(UiMappers.browseDir(index, device.id, "", ScanState()))
        assertEquals(listOf("Android", "DCIM", "Movies"), root.folders.map { it.name })
        val dcim = requireNotNull(UiMappers.browseDir(index, device.id, "DCIM", ScanState()))
        assertEquals(listOf("Camera" to 2, "Screen recordings" to 1), dcim.folders.map { it.name to it.videoCount })
        val camera = requireNotNull(UiMappers.browseDir(index, device.id, "DCIM/Camera", ScanState()))
        assertEquals(setOf("20240601_101500", "VID_20240604_090000"), camera.videos.map { it.title }.toSet())
        assertTrue(camera.videos.all { it.kind == MediaKind.VIDEO })
        val whatsApp = requireNotNull(
            UiMappers.browseDir(index, device.id, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video", ScanState()),
        )
        assertEquals(listOf("VID-20240603-WA0001"), whatsApp.videos.map { it.title })

        val library = UiMappers.library(index, LibraryFilter.VIDEOS, LibrarySort.RECENT)
        assertEquals(4, library.items.size)
        assertTrue(library.items.all { it is LibraryItemUi.Video })
        assertEquals(4, index.home.videos.size)
        // The movie waits for TMDB with a placeholder poster; it is listed all the same.
        val movie = index.videos.single { it.video.fileName == "Inception (2010).mkv" }
        assertEquals(VideoKind.MOVIE, movie.video.kind)
        assertEquals(1, UiMappers.library(index, LibraryFilter.MOVIES, LibrarySort.RECENT).items.size)
        assertEquals("content://media/external/video/media/1", index.videos.single { it.video.fileName == "20240601_101500.mp4" }.video.uri)
    }

    @Test
    fun allFilesAccessAloneIsEnoughToScan() = runTest {
        setAllFilesAccess(true)

        val videos = DeviceScanner(context).scan(DEVICE_SOURCE)

        assertEquals(5, videos.size)
    }

    @Test
    fun withoutAccessTheScanFailsVisibly() = runTest {
        try {
            DeviceScanner(context).scan(DEVICE_SOURCE)
            fail("Scanned without access")
        } catch (e: IOException) {
            assertEquals("Allow access to videos to scan this device", e.message)
        }
        assertEquals("Allow access to videos to scan this device", DeviceScanner(context).census().error)
    }

    @Test
    fun aMediaProviderThatRefusesAColumnStillListsEveryVideo() = runTest {
        grantVideoAccess()
        mediaStore.refusedColumn = MediaStore.Video.Media.WIDTH

        val videos = DeviceScanner(context).scan(DEVICE_SOURCE)

        assertEquals(5, videos.size)
        assertTrue(videos.all { it.width == null && it.durationMs == null })
        assertEquals("DCIM/Camera", videos.single { it.fileName == "20240601_101500.mp4" }.folder)
    }

    @Test
    fun censusCountsWhatMediaStoreListsByFolder() = runTest {
        grantVideoAccess()

        val census = DeviceScanner(context).census()

        assertNull(census.error)
        assertEquals(6, census.total)
        assertEquals(5, census.kept)
        assertEquals(mapOf("hidden or system folder" to 1), census.skipped)
        assertEquals("DCIM/Camera" to 2, census.folders.first())
        assertEquals(6, census.folders.sumOf { it.second })
        assertEquals(mapOf("external_primary" to 5, "1234-5678" to 1), census.volumes)
    }

    @Test
    fun skipReasonsKeepCameraScreenAndMessagingFolders() {
        assertNull(deviceSkipReason("20240601_101500.mp4", "DCIM/Camera"))
        assertNull(deviceSkipReason("Screen_Recording_20240602-101010_Chrome.mp4", "DCIM/Screen recordings"))
        assertNull(deviceSkipReason("VID-20240603-WA0001.mp4", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"))
        assertNull(deviceSkipReason("clip.mp4", ""))
        assertEquals("hidden or system folder", deviceSkipReason("clip.mp4", "DCIM/.thumbnails"))
        assertEquals("hidden or system folder", deviceSkipReason("clip.mp4", "Android/data/com.example"))
        assertEquals("sample clip", deviceSkipReason("movie-sample.mkv", "Movies"))
        assertEquals("macOS metadata file (._)", deviceSkipReason("._clip.mp4", "Movies"))
        assertEquals("no file name", deviceSkipReason(" ", "Movies"))
    }

    /** MediaStore's tables for authority "media": the video rows below, no images. */
    class FakeMediaStore : ContentProvider() {
        @Volatile var videos: List<Map<String, Any?>> = emptyList()

        /** A column this provider refuses, as an unusual MediaProvider might. */
        @Volatile var refusedColumn: String? = null

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            queryArgs: Bundle?,
            cancellationSignal: CancellationSignal?,
        ): Cursor {
            val columns = projection?.map { it }?.toTypedArray() ?: arrayOf(MediaStore.Video.Media._ID)
            refusedColumn?.let { refused -> require(refused !in columns) { "Invalid column $refused" } }
            val rows = if ("video" in uri.pathSegments) videos else emptyList()
            return MatrixCursor(columns).apply {
                for (row in rows) addRow(columns.map { row[it] }.toTypedArray())
            }
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = query(uri, projection, null as Bundle?, null)

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    private companion object {
        val DEVICE_SOURCE = SourceEntity(id = 1, type = SourceType.DEVICE, name = "Device storage", location = "device")

        /** A row as MediaStore gives it: a camera file just saved has no size, duration or dimensions yet. */
        fun row(
            id: Long,
            name: String,
            relativePath: String,
            volume: String = "external_primary",
            sizeBytes: Long? = null,
            durationMs: Long? = null,
        ): Map<String, Any?> = mapOf(
            MediaStore.Video.Media._ID to id,
            MediaStore.Video.Media.DISPLAY_NAME to name,
            MediaStore.Video.Media.SIZE to sizeBytes,
            MediaStore.Video.Media.DURATION to durationMs,
            MediaStore.Video.Media.WIDTH to null,
            MediaStore.Video.Media.HEIGHT to null,
            MediaStore.Video.Media.DATE_ADDED to 1_717_236_900L,
            MediaStore.Video.Media.DATE_MODIFIED to 1_717_236_900L,
            MediaStore.MediaColumns.RELATIVE_PATH to relativePath,
            MediaStore.MediaColumns.VOLUME_NAME to volume,
        )

        val PHONE_VIDEOS = listOf(
            row(1, "20240601_101500.mp4", "DCIM/Camera/"),
            row(2, "Screen_Recording_20240602-101010_Chrome.mp4", "DCIM/Screen recordings/"),
            row(3, "VID-20240603-WA0001.mp4", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/"),
            row(4, "Inception (2010).mkv", "Movies/", sizeBytes = 2_000_000_000L, durationMs = 8_880_000L),
            row(5, "clip.mp4", "DCIM/.thumbnails/"),
            row(6, "VID_20240604_090000.mp4", "DCIM/Camera/", volume = "1234-5678"),
        )
    }
}
