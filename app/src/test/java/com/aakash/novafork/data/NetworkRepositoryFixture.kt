package com.aakash.novafork.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.data.db.NovaDatabase
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Like [RepositoryFixture], with one fake scanner ([servers]) for every network source type and
 * [reporter] registered for Jellyfin and Plex progress. With the default inert scope no
 * background work runs (no scan after saveNetworkSource, no metadata pass, no progress delivery).
 */
internal class NetworkRepositoryFixture(scope: CoroutineScope = inertScope()) {
    val context: Context = ApplicationProvider.getApplicationContext<Context>()
    val database: NovaDatabase = Room.inMemoryDatabaseBuilder(context, NovaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val folders = FakeScanner()
    val shares = FakeScanner()

    /** Serves FTP, SFTP, NFS, UPnP, WebDAV, Jellyfin, Emby and Plex sources, by location. */
    val servers = FakeScanner()
    val reporter = RecordingReporter()
    val tmdb = FakeTmdb()
    val settings = FakeSettingsStore(Settings(scanDeviceStorage = false))
    val repository = LibraryRepositoryImpl(
        context = context,
        database = database,
        scanners = mapOf(SourceType.FOLDER to folders, SourceType.SMB to shares) +
            NetworkSources.TYPES.associateWith { servers },
        tmdb = tmdb.client(),
        settings = settings,
        scope = scope,
        progressReporters = mapOf(SourceType.JELLYFIN to reporter, SourceType.PLEX to reporter),
    )

    suspend fun videoAt(uri: String): VideoEntity =
        requireNotNull(database.videoDao().byUri(uri)) { "No video at $uri" }

    suspend fun source(id: Long): SourceEntity = requireNotNull(database.sourceDao().byId(id)) { "No source $id" }

    fun close() {
        database.close()
    }
}

/** Keeps every report; [failNext] makes the next one throw (after counting it in [attempts]). */
internal class RecordingReporter : RemoteProgressReporter {
    class Report(
        val sourceId: Long,
        val videoId: Long,
        val positionMs: Long,
        val durationMs: Long?,
        val finished: Boolean,
        /** [VideoEntity.watched] of the row handed over. */
        val watched: Boolean,
        /** [VideoEntity.positionMs] of the row handed over. */
        val storedPositionMs: Long,
    )

    val reports: MutableList<Report> = CopyOnWriteArrayList()
    val attempts = AtomicInteger()

    @Volatile
    var failNext: Exception? = null

    override suspend fun report(source: SourceEntity, video: VideoEntity, positionMs: Long, durationMs: Long?, finished: Boolean) {
        attempts.incrementAndGet()
        failNext?.let {
            failNext = null
            throw it
        }
        reports += Report(source.id, video.id, positionMs, durationMs, finished, video.watched, video.positionMs)
    }
}
