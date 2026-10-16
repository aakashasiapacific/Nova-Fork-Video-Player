package com.aakash.novafork.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import com.aakash.novafork.CrashReporter
import com.aakash.novafork.core.library.LibraryKeys
import com.aakash.novafork.core.library.NaturalOrder
import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.core.parse.NameParser
import com.aakash.novafork.core.parse.ParsedKind
import com.aakash.novafork.core.parse.ParsedName
import com.aakash.novafork.core.smb.SmbLocation
import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.core.tmdb.TmdbClient
import com.aakash.novafork.core.tmdb.TmdbEpisode
import com.aakash.novafork.core.tmdb.TmdbException
import com.aakash.novafork.core.tmdb.TmdbMovieSummary
import com.aakash.novafork.core.tmdb.TmdbTvSummary
import com.aakash.novafork.data.backup.PendingRestore
import com.aakash.novafork.data.db.FolderEntry
import com.aakash.novafork.data.db.MatchCacheEntity
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.NovaDatabase
import com.aakash.novafork.data.db.PlaylistEntity
import com.aakash.novafork.data.db.PlaylistItemEntity
import com.aakash.novafork.data.db.SQL_CHUNK
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import com.aakash.novafork.data.db.VideoKind
import com.aakash.novafork.data.db.VideoWithMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

private const val TAG = "LibraryRepository"
private const val DEVICE_SOURCE_NAME = "Device storage"
private const val DEVICE_SOURCE_LOCATION = "device"
private const val KEY_SETTLE_MS = 1_500L
private const val DEFAULT_SMB_PORT = 445

/** Quiet time after the last MediaStore change before device storage is scanned again. */
private const val MEDIA_SETTLE_MS = 5_000L

/** The refresh that comes with an app launch skips folders and shares scanned this recently. */
private const val LAUNCH_RESCAN_AFTER_MS = 30L * 60 * 1000

/** A launch's refresh arrives within this time (the main screen starts it as it opens). */
private const val LAUNCH_WINDOW_MS = 10_000L
private const val NO_LAUNCH = Long.MIN_VALUE

/** A device scan reads MediaStore: a few minutes at most, even with tens of thousands of files. */
private const val DEVICE_SCAN_TIMEOUT_MS = 5L * 60 * 1000

/** Folder walks, shares and servers: a first walk of a big NAS over Wi-Fi can take a while. */
private const val WALK_SCAN_TIMEOUT_MS = 30L * 60 * 1000

/** How long one scan of a source may take before it is given up (see LibraryRepositoryImpl.scanBounded). */
internal fun defaultScanTimeoutMs(type: SourceType): Long =
    if (type == SourceType.DEVICE) DEVICE_SCAN_TIMEOUT_MS else WALK_SCAN_TIMEOUT_MS

/** Repository state outside the database: the language the stored metadata was fetched in. */
private const val STATE_PREFS = "library_state"
private const val KEY_METADATA_LANGUAGE = "metadata_language"

/**
 * [progressReporters]: media servers told about playback progress (see [RemoteProgressReporter]);
 * a source type without one reports nothing. [scanTimeoutMs]: how long one scan of a source of a
 * type may take before it is given up (tests shorten it).
 */
class LibraryRepositoryImpl(
    context: Context,
    private val database: NovaDatabase,
    private val scanners: Map<SourceType, SourceScanner>,
    private val tmdb: TmdbClient,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val progressReporters: Map<SourceType, RemoteProgressReporter> = emptyMap(),
    private val scanTimeoutMs: (SourceType) -> Long = ::defaultScanTimeoutMs,
) : LibraryRepository {
    private val context: Context = context.applicationContext
    private val sourceDao = database.sourceDao()
    private val videoDao = database.videoDao()
    private val metadataDao = database.metadataDao()
    private val matchCacheDao = database.matchCacheDao()
    private val playlistDao = database.playlistDao()

    private val state = MutableStateFlow(ScanState())
    override val scanState: StateFlow<ScanState> = state.asStateFlow()

    /**
     * One scan at a time: refreshAll coalesces into a running refresh and waits out a single
     * source's scan; scanSource and MediaStore rescans wait their turn.
     */
    private val scanMutex = Mutex()
    private val enricher = MetadataEnricher(database, tmdb, state, scope, Connectivity(this.context))

    /** A restored backup's entries waiting for their videos; applied after each scan of a source. */
    private val pendingRestore = PendingRestore(this.context, database)
    private val statePrefs: SharedPreferences = this.context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

    /** Source id → wall-clock start of its latest scan in this process, failed ones included. */
    private val scanStarts = ConcurrentHashMap<Long, Long>()

    /** elapsedRealtime of an app launch whose refresh hasn't come yet ([NO_LAUNCH] if none). */
    private val launchAt = AtomicLong(SystemClock.elapsedRealtime())

    /** A forced refresh came while a launch refresh ran: that one also scans what it skipped. */
    private val forceRequested = AtomicBoolean(false)

    /**
     * A forced refresh came while a refresh ran: that one scans device storage again at its end
     * (it scanned it first, possibly before what the user refreshed for). A device scan starting
     * clears it.
     */
    private val deviceRescanRequested = AtomicBoolean(false)

    /** The storage access [onStorageAccessChanged] last saw. */
    private val seenAccess = AtomicReference(AccessSnapshot.of(this.context))

    /** The latest scan of each source since the process started (Library diagnostics). */
    private val scanSummaries = ConcurrentHashMap<Long, ScanSummary>()

    /** The source being scanned and the wall-clock time its scan started; null while none is. */
    @Volatile private var runningScan: Pair<String, Long>? = null

    /** True while refreshAll, not a single source's scan, holds [scanMutex]. */
    @Volatile private var refreshing = false

    /** Finished refreshAll runs. Written with [scanMutex] held. */
    @Volatile private var refreshesDone = 0

    private val watchingMedia = AtomicBoolean(false)
    private val mediaChanges = Channel<Unit>(Channel.CONFLATED)

    /** elapsedRealtime of the latest MediaStore change, and of the latest device scan's start. */
    @Volatile private var mediaChangedAt = 0L

    @Volatile private var deviceScanStartedAt = 0L

    /** Progress waiting to be reported to media servers: the latest per video id. */
    private val pendingReports = ConcurrentHashMap<Long, RemoteReport>()
    private val reportsQueued = Channel<Unit>(Channel.CONFLATED)

    init {
        launchLogged("Device source setup") { syncDeviceSource(settings.settings.value.scanDeviceStorage) }
        observeSettings()
        // Picks up lookups an earlier process did not finish.
        enricher.request()
        if (progressReporters.isNotEmpty()) scope.launch(Dispatchers.IO) { deliverReports() }
    }

    override fun observeVideos(): Flow<List<VideoWithMeta>> = videoDao.observePresent().distinctUntilChanged()

    override fun observeSources(): Flow<List<SourceEntity>> = sourceDao.observeEnabled()

    override suspend fun video(id: Long): VideoWithMeta? = withContext(Dispatchers.IO) { videoDao.withMeta(id) }

    override suspend fun source(id: Long): SourceEntity? = withContext(Dispatchers.IO) { sourceDao.byId(id) }

    override suspend fun findByUri(uri: String): VideoWithMeta? =
        withContext(Dispatchers.IO) { videoDao.withMetaByUri(uri) }

    // Scanning

    /** The first call after [markLaunch] (or after this repository was created) is the launch's refresh. */
    override suspend fun refreshAll() {
        refreshAll(force = !takeLaunch())
    }

    /**
     * [force] = false is the refresh that comes with an app launch: it skips folders and shares
     * scanned (or tried) in the last 30 minutes, since walking a big share on every launch is
     * slow and wakes the NAS. Device storage is always scanned; MediaStore makes that cheap.
     */
    internal suspend fun refreshAll(force: Boolean) {
        withContext(Dispatchers.IO) {
            if (!scanMutex.tryLock()) {
                if (refreshing) {
                    // Coalesces into the running refresh; a forced one makes it scan what it skipped
                    // too, and device storage once more (scanned first, maybe before what this is for).
                    if (force) {
                        forceRequested.set(true)
                        deviceRescanRequested.set(true)
                    }
                    return@withContext
                }
                // Only one source is being scanned (a new share, a MediaStore change): refresh after it.
                // A launch refresh is not needed when another refresh got there first.
                val done = refreshesDone
                scanMutex.lock()
                if (!force && refreshesDone != done) {
                    scanMutex.unlock()
                    return@withContext
                }
            }
            refreshing = true
            try {
                forceRequested.set(false)
                state.update { it.copy(running = true, sourceName = null, found = 0, lastError = null) }
                logFailure("Device source setup") { syncDeviceSource(settings.settings.value.scanDeviceStorage) }
                val skipped = ArrayList<Long>()
                for (source in sourceDao.all()) {
                    if (!isScannable(source)) continue
                    if (!force && !forceRequested.get() && scannedRecently(source)) {
                        skipped += source.id
                        continue
                    }
                    // One source going wrong (even its error bookkeeping) never stops the others.
                    logFailure("Scanning a ${source.type.name} source") { scan(source) }
                }
                if (skipped.isNotEmpty() && forceRequested.getAndSet(false)) {
                    for (id in skipped) {
                        logFailure("Scanning a skipped source") {
                            sourceDao.byId(id)?.takeIf { isScannable(it) }?.let { scan(it) }
                        }
                    }
                }
                if (deviceRescanRequested.getAndSet(false)) {
                    logFailure("Scanning device storage again") {
                        sourceDao.device()?.takeIf { isScannable(it) }?.let { scan(it) }
                    }
                }
            } finally {
                refreshing = false
                refreshesDone++
                state.update { it.copy(running = false, sourceName = null, found = 0) }
                scanMutex.unlock()
            }
        }
        enricher.request()
    }

    /** The main screen opened: the refresh it starts right away is a launch refresh (see [refreshAll]). */
    internal fun markLaunch() {
        launchAt.set(SystemClock.elapsedRealtime())
    }

    private fun takeLaunch(): Boolean {
        val at = launchAt.getAndSet(NO_LAUNCH)
        return at != NO_LAUNCH && SystemClock.elapsedRealtime() - at <= LAUNCH_WINDOW_MS
    }

    /** A folder, share or server scanned, or tried, within [LAUNCH_RESCAN_AFTER_MS]. */
    private fun scannedRecently(source: SourceEntity): Boolean {
        val walksFiles = when (source.type) {
            SourceType.FOLDER, SourceType.SMB, SourceType.FTP, SourceType.SFTP, SourceType.NFS, SourceType.UPNP,
            SourceType.WEBDAV, SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX,
            -> true
            SourceType.DEVICE, SourceType.STREAM -> false
        }
        if (!walksFiles) return false
        val last = max(source.lastScanAt ?: 0L, scanStarts[source.id] ?: 0L)
        // A clock set back makes the age negative: scan rather than trust it.
        return last > 0L && System.currentTimeMillis() - last in 0L until LAUNCH_RESCAN_AFTER_MS
    }

    override suspend fun scanSource(sourceId: Long) {
        withContext(Dispatchers.IO) {
            scanMutex.withLock {
                val source = sourceDao.byId(sourceId)
                if (source != null && isScannable(source)) {
                    try {
                        scan(source)
                    } finally {
                        state.update { it.copy(running = false, sourceName = null, found = 0) }
                    }
                }
            }
        }
        enricher.request()
    }

    private fun isScannable(source: SourceEntity): Boolean {
        if (!source.enabled || source.type !in scanners) return false
        return when (source.type) {
            SourceType.DEVICE ->
                settings.settings.value.scanDeviceStorage && StoragePermissions.hasAnyVideoAccess(context)
            SourceType.FOLDER, SourceType.SMB, SourceType.FTP, SourceType.SFTP, SourceType.NFS, SourceType.UPNP,
            SourceType.WEBDAV, SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX,
            -> true
            SourceType.STREAM -> false
        }
    }

    /** Scans one source and merges the result. Called with [scanMutex] held. */
    private suspend fun scan(source: SourceEntity) {
        val scanner = scanners[source.type] ?: return
        if (source.type == SourceType.DEVICE) {
            deviceScanStartedAt = SystemClock.elapsedRealtime()
            // This scan sees whatever a rescan was asked for so far.
            deviceRescanRequested.set(false)
        }
        val startedAt = System.currentTimeMillis()
        val clock = SystemClock.elapsedRealtime()
        scanStarts[source.id] = startedAt
        runningScan = source.name to startedAt
        state.update { it.copy(running = true, sourceName = source.name, found = 0) }
        try {
            val found = scanBounded(scanner, source)
            val merged = merge(source, found)
            sourceDao.markScanned(source.id, System.currentTimeMillis())
            scanSummaries[source.id] = ScanSummary(
                startedAt = startedAt,
                durationMs = SystemClock.elapsedRealtime() - clock,
                found = found.size,
                added = merged.added,
                ownedElsewhere = merged.ownedElsewhere,
                hidden = merged.hidden,
            )
            applyPendingRestore()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailure(source, e, startedAt, clock)
        } catch (e: LinkageError) {
            // A library class missing on this Android version fails this source, not the app.
            recordFailure(source, e, startedAt, clock)
        } finally {
            runningScan = null
        }
    }

    /**
     * Runs [scanner] on [source] for at most [scanTimeoutMs] of its type. A scan stuck in a call
     * that never returns (a document provider or MediaProvider that hangs, a server that stops
     * answering mid-listing) would otherwise hold [scanMutex], and with it every later scan,
     * refresh and permission rescan, until the process ends. The scan runs apart from the caller,
     * so even a blocking call that ignores cancellation is left behind (its result dropped)
     * rather than waited for; the source then shows the time-out as its error.
     */
    private suspend fun scanBounded(scanner: SourceScanner, source: SourceEntity): List<ScannedVideo> {
        val limitMs = scanTimeoutMs(source.type)
        val abandoned = AtomicBoolean(false)
        val work = CoroutineScope(Dispatchers.IO).async {
            scanner.scan(source) { count -> if (!abandoned.get()) state.update { it.copy(found = count) } }
        }
        try {
            return withTimeoutOrNull(limitMs) { work.await() }
                ?: throw IOException("Scanning stopped: no result after ${durationLabel(limitMs)}. Rescan to try again.")
        } catch (e: CancellationException) {
            // This scan being cancelled rethrows here. Otherwise the scanner's own time-out leaked
            // out of it: a failure of this source, not a reason to stop the whole refresh.
            currentCoroutineContext().ensureActive()
            throw IOException(e.message?.takeIf { it.isNotBlank() } ?: "The scan was interrupted", e)
        } finally {
            abandoned.set(true)
            work.cancel()
        }
    }

    /**
     * Gives the videos a scan just found what a restored backup holds for them (see
     * [PendingRestore]). Its manual matches ask TMDB, so they are applied in the background
     * rather than holding up the next source's scan. A failure here never fails the scan.
     */
    private suspend fun applyPendingRestore() {
        if (!pendingRestore.exists()) return
        logFailure("Applying a restored backup") {
            val result = pendingRestore.apply(this, identify = false)
            if (result.matchesReady > 0) {
                launchLogged("Restoring matches") { pendingRestore.apply(this, identify = true) }
            }
        }
    }

    private suspend fun recordFailure(source: SourceEntity, e: Throwable, startedAt: Long, clock: Long) {
        Log.w(TAG, "Scanning ${source.name} failed", e)
        CrashReporter.log("Scanning a ${source.type.name} source failed", e)
        val message = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        scanSummaries[source.id] = ScanSummary(
            startedAt = startedAt,
            durationMs = SystemClock.elapsedRealtime() - clock,
            found = null,
            error = message,
        )
        sourceDao.markFailed(source.id, message)
        state.update { it.copy(lastError = "${source.name}: $message") }
    }

    /**
     * Upserts the files of one scan by URI. Known files keep their id, date added, playback state
     * and match; files of the source the scan did not see are kept (watch history) but hidden.
     * Metadata a media server supplied is stored with them (see [ScannedVideo.serverMetadata]).
     */
    private suspend fun merge(source: SourceEntity, scanned: List<ScannedVideo>): MergeCounts {
        val now = System.currentTimeMillis()
        return database.withTransaction {
            // The source may have been removed while its scan was running.
            if (sourceDao.byId(source.id) == null) return@withTransaction MergeCounts(0, 0, 0)
            val existing = videoDao.bySource(source.id).associateBy { it.uri }
            val seen = HashSet<String>(scanned.size * 2)
            val fresh = ArrayList<ScannedVideo>()
            val inserts = ArrayList<VideoEntity>()
            val updates = ArrayList<VideoEntity>()
            for (file in scanned) {
                if (!seen.add(file.uri)) continue
                val old = existing[file.uri]
                if (old == null) {
                    fresh += file
                } else {
                    val merged = refreshed(old, file, source.id, now)
                    if (merged != old) updates += merged
                }
            }
            var takenOver = 0
            var ownedElsewhere = 0
            if (fresh.isNotEmpty()) {
                // URIs are unique across sources: a file another source already lists (overlapping
                // shares) stays with that source, unless that one is off or no longer sees it.
                val elsewhere = fresh.map { it.uri }.chunked(SQL_CHUNK).flatMap { videoDao.byUris(it) }.associateBy { it.uri }
                val enabledSources = if (elsewhere.isEmpty()) emptySet() else sourceDao.all().filter { it.enabled }.map { it.id }.toSet()
                for (file in fresh) {
                    val other = elsewhere[file.uri]
                    when {
                        other == null -> inserts += newVideo(source.id, file, now)
                        other.sourceId !in enabledSources || !other.present -> {
                            updates += refreshed(other, file, source.id, now).copy(sourceId = source.id)
                            takenOver++
                        }
                        else -> ownedElsewhere++
                    }
                }
            }
            if (inserts.isNotEmpty()) videoDao.insertAll(inserts)
            if (updates.isNotEmpty()) videoDao.updateAll(updates)
            val missing = existing.values.filter { it.present && it.uri !in seen }.map { it.id }
            for (chunk in missing.chunked(SQL_CHUNK)) videoDao.markMissing(chunk)
            storeServerMetadata(source.id, scanned, now)
            MergeCounts(added = inserts.size + takenOver, ownedElsewhere = ownedElsewhere, hidden = missing.size)
        }
    }

    /** What [merge] did: videos new to the source, left with another source, and hidden. */
    private class MergeCounts(val added: Int, val ownedElsewhere: Int, val hidden: Int)

    /** Writes the movies and shows a media server described; unchanged rows are left alone. */
    private suspend fun storeServerMetadata(sourceId: Long, scanned: List<ScannedVideo>, now: Long) {
        for (row in serverMetadataRows(sourceId, scanned, now)) {
            val current = metadataDao.get(row.key)
            if (current == null || row.copy(updatedAt = current.updatedAt) != current) metadataDao.upsert(row)
        }
    }

    private fun newVideo(sourceId: Long, file: ScannedVideo, now: Long): VideoEntity {
        val parsed = parseName(file.fileName, file.parentFolders)
        val video = VideoEntity(
            sourceId = sourceId,
            uri = file.uri,
            fileName = file.fileName,
            folder = file.folder,
            sizeBytes = file.sizeBytes,
            durationMs = file.durationMs?.takeIf { it > 0 },
            width = file.width,
            height = file.height,
            dateAdded = file.dateAdded,
            dateModified = file.dateModified,
            parsedTitle = parsed.title,
            localPoster = file.localPoster,
            localBackdrop = file.localBackdrop,
            localThumb = file.localThumb,
            remoteId = file.remoteId,
        ).withParse(parsed)
        val server = file.serverMetadata?.takeIf { it.isUsable() }
        return if (server != null) video.withServerMetadata(sourceId, server, now) else video
    }

    /** [old] as the scan saw it again, for source [sourceId] (the one scanned). */
    private fun refreshed(old: VideoEntity, file: ScannedVideo, sourceId: Long, now: Long): VideoEntity {
        val updated = old.copy(
            fileName = file.fileName,
            folder = file.folder,
            sizeBytes = file.sizeBytes ?: old.sizeBytes,
            durationMs = file.durationMs?.takeIf { it > 0 } ?: old.durationMs,
            width = file.width ?: old.width,
            height = file.height ?: old.height,
            dateModified = file.dateModified ?: old.dateModified,
            localPoster = file.localPoster,
            localBackdrop = file.localBackdrop,
            localThumb = file.localThumb,
            remoteId = file.remoteId ?: old.remoteId,
            present = true,
        )
        val server = file.serverMetadata?.takeIf { it.isUsable() }
        return when {
            // The user's Identify outranks both the server and the file name.
            old.matchState == MatchState.MANUAL -> updated
            server != null -> updated.withServerMetadata(sourceId, server, now)
            // The server no longer describes it: the file name and TMDB take over.
            ServerMetadata.isServerKey(old.metadataKey) -> updated.withParse(parseName(file.fileName, file.parentFolders))
            // A renamed file (same MediaStore id) may now be something else entirely.
            file.fileName != old.fileName -> updated.withParse(parseName(file.fileName, file.parentFolders))
            else -> updated
        }
    }

    /**
     * Rescans device storage when MediaStore's videos change (downloads, deletions, renames), once
     * the changes have settled for 5 s. Only while device scanning is on and video access is
     * granted; folders and shares are left alone. Called once by AppGraph.
     */
    @OptIn(FlowPreview::class)
    internal fun watchDeviceStorage() {
        if (!watchingMedia.compareAndSet(false, true)) return
        // No handler: onChange runs on a binder thread and only queues a rescan.
        val handler: Handler? = null
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                mediaChangedAt = SystemClock.elapsedRealtime()
                mediaChanges.trySend(Unit)
            }
        }
        try {
            context.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        } catch (e: SecurityException) {
            Log.w(TAG, "Can't watch device storage for changes", e)
            return
        }
        scope.launch {
            mediaChanges.receiveAsFlow()
                .debounce(MEDIA_SETTLE_MS)
                .collect { logFailure("Device storage rescan") { rescanDeviceAfterChange() } }
        }
    }

    /** Scans device storage after a MediaStore change, unless a scan that started since has seen it. */
    private suspend fun rescanDeviceAfterChange() {
        val changedAt = mediaChangedAt
        if (!settings.settings.value.scanDeviceStorage || !StoragePermissions.hasAnyVideoAccess(context)) return
        if (rescanDevice(changedAt)) enricher.request()
    }

    /**
     * Scans device storage unless a device scan that started after [askedAt] (elapsedRealtime)
     * has seen what the request is about. Waits for a running scan instead of overlapping it.
     * True when it scanned.
     */
    private suspend fun rescanDevice(askedAt: Long): Boolean = withContext(Dispatchers.IO) {
        val deviceId = sourceDao.device()?.id ?: return@withContext false
        scanMutex.withLock {
            if (deviceScanStartedAt > askedAt) return@withLock false
            val device = sourceDao.byId(deviceId)?.takeIf { isScannable(it) } ?: return@withLock false
            try {
                scan(device)
            } finally {
                state.update { it.copy(running = false, sourceName = null, found = 0) }
            }
            true
        }
    }

    override fun onStorageAccessChanged(rescan: Boolean) {
        val now = AccessSnapshot.of(context)
        val before = seenAccess.getAndSet(now)
        if (now.videos == VideoAccess.NONE) return
        if (!rescan && !now.widens(before)) return
        val askedAt = SystemClock.elapsedRealtime()
        // In the app's scope: the scan finishes even if the screen that noticed the change goes away.
        launchLogged("Device storage rescan after an access change") {
            if (rescanDevice(askedAt)) enricher.request()
        }
    }

    override suspend fun libraryStats(): LibraryStats = withContext(Dispatchers.IO) {
        val present = HashMap<Long, Int>()
        val missing = HashMap<Long, Int>()
        for (row in videoDao.countsBySource()) {
            val counts = if (row.present) present else missing
            counts[row.sourceId] = (counts[row.sourceId] ?: 0) + row.count
        }
        val running = runningScan
        LibraryStats(
            sources = sourceDao.all().map { source ->
                SourceStats(
                    source = source,
                    present = present[source.id] ?: 0,
                    missing = missing[source.id] ?: 0,
                    lastScan = scanSummaries[source.id],
                )
            },
            kinds = videoDao.shownCountsByKind(),
            deviceScannable = settings.settings.value.scanDeviceStorage && StoragePermissions.hasAnyVideoAccess(context),
            scanningSource = running?.first,
            scanningSince = running?.second,
        )
    }

    /** Ensures the single DEVICE source exists while device scanning is on, and hides it while off. */
    private suspend fun syncDeviceSource(enabled: Boolean): SourceEntity? = database.withTransaction {
        val device = sourceDao.device()
        when {
            device == null && enabled -> {
                val created = SourceEntity(
                    type = SourceType.DEVICE,
                    name = DEVICE_SOURCE_NAME,
                    location = DEVICE_SOURCE_LOCATION,
                    addedAt = System.currentTimeMillis(),
                )
                created.copy(id = sourceDao.insert(created))
            }
            device != null && device.enabled != enabled -> {
                sourceDao.setEnabled(device.id, enabled)
                device.copy(enabled = enabled)
            }
            else -> device
        }
    }

    // Sources

    override suspend fun addFolder(treeUri: String, displayName: String): Long = withContext(Dispatchers.IO) {
        val id = sourceDao.byLocation(SourceType.FOLDER.name, treeUri)?.id
            ?: sourceDao.insert(
                SourceEntity(
                    type = SourceType.FOLDER,
                    name = displayName.trim().ifEmpty { "Folder" },
                    location = treeUri,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        launchLogged("Folder scan") { scanSource(id) }
        id
    }

    override suspend fun saveSmbServer(
        id: Long?,
        name: String,
        host: String,
        port: Int?,
        share: String,
        path: String,
        username: String?,
        password: String?,
        domain: String?,
    ): Long = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        val cleanShare = share.trim('/', ' ')
        require(cleanHost.isNotEmpty()) { "Enter the server name or address" }
        require(cleanShare.isNotEmpty()) { "Enter the share name" }
        val location = SmbUris.build(cleanHost, cleanShare, path.trim('/', ' '), port?.takeIf { it in 1..65535 })
        val label = name.trim().ifEmpty { "$cleanHost/$cleanShare" }
        val user = username?.trim()?.takeIf { it.isNotEmpty() }
        val secret = password?.takeIf { it.isNotEmpty() }
        val workgroup = domain?.trim()?.takeIf { it.isNotEmpty() }

        val existing = id?.let { sourceDao.byId(it) }?.takeIf { it.type == SourceType.SMB }
        val sourceId = if (existing != null) {
            sourceDao.update(
                existing.copy(
                    name = label,
                    location = location,
                    username = user,
                    password = secret,
                    domain = workgroup,
                    lastError = null,
                ),
            )
            existing.id
        } else {
            sourceDao.insert(
                SourceEntity(
                    type = SourceType.SMB,
                    name = label,
                    location = location,
                    username = user,
                    password = secret,
                    domain = workgroup,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
        launchLogged("SMB scan") { scanSource(sourceId) }
        sourceId
    }

    override suspend fun saveNetworkSource(id: Long?, input: NetworkSourceInput): Long {
        val typed = NetworkSources.toSource(input)
        return withContext(Dispatchers.IO) {
            val sourceId = database.withTransaction {
                val existing = if (id != null) {
                    sourceDao.byId(id)
                } else {
                    sourceDao.all().firstOrNull {
                        it.type == typed.type && it.location == typed.location && it.username == typed.username
                    }
                }?.takeIf { it.type == typed.type }
                if (existing != null) {
                    // A token belongs to one account on one server.
                    val sameAccount = existing.location == typed.location && existing.username == typed.username
                    sourceDao.update(
                        existing.copy(
                            name = typed.name,
                            location = typed.location,
                            port = typed.port,
                            username = typed.username,
                            password = typed.password,
                            domain = typed.domain,
                            token = typed.token ?: existing.token.takeIf { sameAccount },
                            userId = typed.userId ?: existing.userId.takeIf { sameAccount },
                            serverId = typed.serverId ?: existing.serverId.takeIf { sameAccount },
                            libraryIds = typed.libraryIds,
                            lastError = null,
                        ),
                    )
                    existing.id
                } else {
                    sourceDao.insert(typed.copy(addedAt = System.currentTimeMillis()))
                }
            }
            launchLogged("${typed.type.name} scan") { scanSource(sourceId) }
            sourceId
        }
    }

    override suspend fun updateSourceAuth(sourceId: Long, token: String?, userId: String?, serverId: String?) {
        withContext(Dispatchers.IO) {
            sourceDao.updateAuth(
                id = sourceId,
                token = token?.trim()?.takeIf { it.isNotEmpty() },
                userId = userId?.trim()?.takeIf { it.isNotEmpty() },
                serverId = serverId?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }

    override suspend fun removeSource(sourceId: Long) {
        withContext(Dispatchers.IO) {
            val source = sourceDao.byId(sourceId) ?: return@withContext
            // While "Scan device storage" is on the next scan brings it straight back, with fresh ids
            // and none of the watch history removing it threw away. The setting hides it instead.
            require(source.type != SourceType.DEVICE) {
                "Device storage can't be removed. Turn off \"Scan device storage\" in Settings instead."
            }
            database.withTransaction {
                when (source.type) {
                    SourceType.SMB -> handOverCoveredVideos(source)
                    SourceType.FTP, SourceType.SFTP, SourceType.NFS, SourceType.WEBDAV -> handOverCoveredFiles(source)
                    SourceType.DEVICE, SourceType.FOLDER, SourceType.STREAM, SourceType.UPNP, SourceType.JELLYFIN,
                    SourceType.EMBY, SourceType.PLEX,
                    -> Unit
                }
                videoDao.deleteBySource(sourceId)
                sourceDao.delete(sourceId)
                metadataDao.deleteUnreferenced()
            }
            if (source.type == SourceType.FOLDER) {
                try {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(source.location),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (e: SecurityException) {
                    Log.i(TAG, "No persisted permission to release for ${source.location}")
                }
            }
        }
    }

    /** Videos another share also covers (nested or overlapping locations) move there, history included. */
    private suspend fun handOverCoveredVideos(source: SourceEntity) {
        val others = sourceDao.smbSources()
            .filter { it.id != source.id }
            .sortedByDescending { it.enabled }
            .mapNotNull { other -> SmbUris.parse(other.location)?.let { other to it } }
        if (others.isEmpty()) return
        val moves = HashMap<Long, MutableList<Long>>()
        for (video in videoDao.bySource(source.id)) {
            val file = SmbUris.parse(video.uri) ?: continue
            val target = others.firstOrNull { (_, root) -> root.covers(file) }?.first ?: continue
            moves.getOrPut(target.id) { ArrayList() } += video.id
        }
        for ((targetId, ids) in moves) {
            for (chunk in ids.chunked(SQL_CHUNK)) videoDao.moveToSource(chunk, targetId)
        }
    }

    /**
     * The same for FTP, SFTP, NFS and WebDAV, whose video URIs are paths below their source's
     * location: another source of the type whose location contains a video takes it over.
     */
    private suspend fun handOverCoveredFiles(source: SourceEntity) {
        val others = sourceDao.all()
            .filter { it.type == source.type && it.id != source.id }
            .sortedByDescending { it.enabled }
            .mapNotNull { other -> NetworkLocation.parse(other.location)?.let { other to it } }
        if (others.isEmpty()) return
        val moves = HashMap<Long, MutableList<Long>>()
        for (video in videoDao.bySource(source.id)) {
            val file = NetworkLocation.parse(video.uri) ?: continue
            val target = others.firstOrNull { (_, root) -> root.covers(file) }?.first ?: continue
            moves.getOrPut(target.id) { ArrayList() } += video.id
        }
        for ((targetId, ids) in moves) {
            for (chunk in ids.chunked(SQL_CHUNK)) videoDao.moveToSource(chunk, targetId)
        }
    }

    override suspend fun addStream(title: String?, url: String): Long = withContext(Dispatchers.IO) {
        val link = url.trim()
        require(link.isNotEmpty()) { "Enter a stream URL" }
        val parsedUri = Uri.parse(link)
        val fileName = parsedUri.lastPathSegment?.takeIf { it.isNotBlank() }
            ?: parsedUri.host?.takeIf { it.isNotBlank() }
            ?: link
        val customTitle = title?.trim()?.takeIf { it.isNotEmpty() }
        val parsed = parseName(if (customTitle != null) asFileName(customTitle) else fileName, emptyList())
        // Only a recognisable "Title (Year)" is worth a TMDB lookup; anything else is a plain stream.
        val isMovie = parsed.kind == ParsedKind.MOVIE && parsed.year != null
        val now = System.currentTimeMillis()

        val videoId = database.withTransaction {
            val existing = videoDao.byUri(link)
            if (existing != null) return@withTransaction existing.id
            val streamSourceId = sourceDao.insert(
                SourceEntity(type = SourceType.STREAM, name = customTitle ?: fileName, location = link, addedAt = now),
            )
            videoDao.insert(
                VideoEntity(
                    sourceId = streamSourceId,
                    uri = link,
                    fileName = fileName,
                    folder = "",
                    dateAdded = now,
                    kind = if (isMovie) VideoKind.MOVIE else VideoKind.VIDEO,
                    parsedTitle = parsed.title,
                    parsedYear = if (isMovie) parsed.year else null,
                    matchState = if (isMovie) MatchState.PENDING else MatchState.UNMATCHED,
                ),
            )
        }
        if (isMovie) enricher.request()
        videoId
    }

    override suspend fun smbCredentials(host: String, share: String): SmbCredentials? = withContext(Dispatchers.IO) {
        sourceDao.smbSources()
            .firstOrNull { source ->
                val location = SmbUris.parse(source.location)
                location != null && location.host.equals(host, ignoreCase = true) &&
                    location.share.equals(share, ignoreCase = true)
            }
            ?.let { SmbCredentials(username = it.username, password = it.password, domain = it.domain) }
    }

    override suspend fun sourceForVideo(videoId: Long): SourceEntity? = withContext(Dispatchers.IO) {
        videoDao.byId(videoId)?.let { sourceDao.byId(it.sourceId) }
    }

    override suspend fun sourceForUri(uri: String): SourceEntity? = withContext(Dispatchers.IO) {
        val owner = videoDao.byUri(uri)?.let { sourceDao.byId(it.sourceId) }
        if (owner != null) return@withContext owner
        val target = NetworkLocation.parse(uri) ?: return@withContext null
        sourceDao.all()
            .filter { it.type.isNetworkShare || it.type.isMediaServer }
            .mapNotNull { source -> NetworkLocation.parse(source.location)?.takeIf { it.covers(target) }?.let { source to it } }
            .sortedWith(
                compareByDescending<Pair<SourceEntity, NetworkLocation>> { it.first.enabled }
                    .thenByDescending { it.second.path.length },
            )
            .firstOrNull()
            ?.first
    }

    // Playback

    override suspend fun savePlayback(videoId: Long, positionMs: Long, durationMs: Long) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val position = positionMs.coerceAtLeast(0)
            val remaining = durationMs - position
            // The halfway check keeps a short clip from counting as watched the moment it opens.
            val finished = durationMs > 0 &&
                remaining <= max(durationMs * 3 / 100, 90_000L) &&
                position >= durationMs / 2
            database.withTransaction {
                if (durationMs > 0) videoDao.fillDuration(videoId, durationMs)
                if (finished) {
                    videoDao.markFinished(videoId, now)
                } else {
                    videoDao.updatePosition(videoId, position, now)
                }
            }
            reportRemote(RemoteReport(videoId, position, durationMs.takeIf { it > 0 }, finished))
        }
    }

    override suspend fun saveTracks(videoId: Long, audioTrackId: Int?, subtitleTrackId: Int?) {
        withContext(Dispatchers.IO) { videoDao.saveTracks(videoId, audioTrackId, subtitleTrackId) }
    }

    override suspend fun setWatched(videoId: Long, watched: Boolean) {
        withContext(Dispatchers.IO) { videoDao.setWatched(videoId, watched) }
        reportRemote(RemoteReport(videoId, positionMs = 0L, durationMs = null, finished = watched))
    }

    // Progress on media servers

    private class RemoteReport(val videoId: Long, val positionMs: Long, val durationMs: Long?, val finished: Boolean)

    /** Queues [report] for the video's server; a newer report of the same video replaces a waiting one. */
    private fun reportRemote(report: RemoteReport) {
        if (progressReporters.isEmpty()) return
        pendingReports[report.videoId] = report
        reportsQueued.trySend(Unit)
    }

    /** Sends queued reports one at a time, for as long as the repository's scope lives. */
    private suspend fun deliverReports() {
        while (true) {
            reportsQueued.receive()
            while (true) {
                val videoId = pendingReports.keys.firstOrNull() ?: break
                val report = pendingReports.remove(videoId) ?: continue
                try {
                    deliver(report)
                } catch (e: CancellationException) {
                    // A reporter's own timeout must not end the delivery; the scope ending does.
                    currentCoroutineContext().ensureActive()
                    Log.w(TAG, "Reporting progress timed out", e)
                } catch (e: Exception) {
                    Log.w(TAG, "Reporting progress failed", e)
                    CrashReporter.log("Reporting progress failed", e)
                }
            }
        }
    }

    private suspend fun deliver(report: RemoteReport) {
        val video = videoDao.byId(report.videoId) ?: return
        val source = sourceDao.byId(video.sourceId) ?: return
        val reporter = progressReporters[source.type] ?: return
        reporter.report(source, video, report.positionMs, report.durationMs ?: video.durationMs, report.finished)
    }

    override suspend fun nextEpisode(videoId: Long): VideoWithMeta? = withContext(Dispatchers.IO) {
        val current = videoDao.byId(videoId) ?: return@withContext null
        val showKey = current.showKey
        if (current.kind != VideoKind.EPISODE || showKey == null) return@withContext null
        // No season (absolute numbering not placed yet) is listed as season 1, as in the UI.
        val season = current.season ?: 1
        val lastEpisode = current.episodeEnd ?: current.episode ?: return@withContext null
        // Strictly later episodes only: a second copy of the same episode is not "next".
        videoDao.episodesOf(showKey)
            .filter {
                val otherSeason = it.video.season ?: 1
                val otherEpisode = it.video.episode ?: Int.MAX_VALUE
                it.video.id != videoId &&
                    (otherSeason > season || (otherSeason == season && otherEpisode > lastEpisode))
            }
            .minWithOrNull(
                compareBy<VideoWithMeta>(
                    { it.video.season ?: 1 },
                    { it.video.episode ?: Int.MAX_VALUE },
                    { it.video.fileName.lowercase() },
                ),
            )
    }

    // TMDB

    override suspend fun searchTmdb(query: String, type: IdentifyType?): List<TmdbSearchResult> =
        withContext(Dispatchers.IO) {
            val text = query.trim()
            if (text.isEmpty()) return@withContext emptyList()
            when (type) {
                IdentifyType.MOVIE -> tmdb.searchMovie(text).map { it.toSearchResult() }
                IdentifyType.TV -> tmdb.searchTv(text).map { it.toSearchResult() }
                null -> coroutineScope {
                    val movies = async { tmdb.searchMovie(text) }
                    val shows = async { tmdb.searchTv(text) }
                    val ranked = movies.await().map { it.popularity to it.toSearchResult() } +
                        shows.await().map { it.popularity to it.toSearchResult() }
                    ranked.sortedByDescending { it.first }.map { it.second }
                }
            }
        }

    override suspend fun identify(videoId: Long, result: TmdbSearchResult) {
        withContext(Dispatchers.IO) {
            val video = videoDao.byId(videoId) ?: return@withContext
            val now = System.currentTimeMillis()
            when (result.type) {
                IdentifyType.MOVIE -> {
                    val metadata = tmdb.movie(result.tmdbId).toMetadata(now)
                    database.withTransaction {
                        metadataDao.upsert(metadata)
                        val current = videoDao.byId(videoId) ?: return@withTransaction
                        videoDao.update(
                            current.copy(
                                kind = VideoKind.MOVIE,
                                metadataKey = metadata.key,
                                showKey = null,
                                season = null,
                                episode = null,
                                episodeEnd = null,
                                episodeTitle = null,
                                episodeOverview = null,
                                episodeStillPath = null,
                                episodeAirDate = null,
                                matchState = MatchState.MANUAL,
                                matchAttemptAt = now,
                            ),
                        )
                    }
                }
                IdentifyType.TV -> identifyShow(video, result.tmdbId, now)
            }
        }
    }

    /**
     * Matches [video] to a TMDB show. An episode takes every episode grouped with it (same showKey)
     * along, so identifying a show never splits it in two. Each keeps its own numbering; absolute
     * anime numbers are placed into the new show's seasons.
     */
    private suspend fun identifyShow(video: VideoEntity, tmdbId: Int, now: Long) {
        val show = tmdb.tv(tmdbId)
        val metadata = show.toMetadata(now)
        val group = video.showKey?.takeIf { video.kind == VideoKind.EPISODE }?.let { videoDao.byShowKey(it) }.orEmpty()
        val targets = if (group.any { it.id == video.id }) group else group + video
        val seasonEpisodes = HashMap<Int, List<TmdbEpisode>>()
        val matches = targets.map { target ->
            // A movie or home video made an episode has no numbers of its own: it becomes S1E1.
            val raw = if (target.kind != VideoKind.EPISODE && target.season == null && target.episode == null) {
                EpisodeNumbers(1, 1, null)
            } else {
                rawNumbers(target)
            }
            val (season, number) = resolveEpisodeNumbers(raw.season, raw.episode, show.seasons)
            val details = if (season != null && number != null) {
                val episodes = seasonEpisodes.getOrPut(season) {
                    try {
                        tmdb.season(show.id, season).episodes
                    } catch (e: TmdbException) {
                        if (e.code == 404) emptyList() else throw e
                    }
                }
                episodes.firstOrNull { it.episodeNumber == number }
            } else {
                null
            }
            ShowMatch(
                videoId = target.id,
                season = season,
                episode = number,
                episodeEnd = raw.episodeEnd?.takeIf { raw.season != null && number != null && it > number },
                details = details,
            )
        }
        database.withTransaction {
            metadataDao.upsert(metadata)
            for (match in matches) {
                val current = videoDao.byId(match.videoId) ?: continue
                videoDao.update(
                    current.copy(
                        kind = VideoKind.EPISODE,
                        metadataKey = metadata.key,
                        showKey = metadata.key,
                        season = match.season,
                        episode = match.episode,
                        episodeEnd = match.episodeEnd,
                        episodeTitle = match.details?.name.nonBlank(),
                        episodeOverview = match.details?.overview.nonBlank(),
                        episodeStillPath = match.details?.stillPath.nonBlank(),
                        episodeAirDate = match.details?.airDate.nonBlank(),
                        matchState = MatchState.MANUAL,
                        matchAttemptAt = now,
                    ),
                )
            }
            // Files of this show added later (same parsed title) join it by themselves.
            targets.filter { it.kind == VideoKind.EPISODE }
                .map { LibraryKeys.matchQueryKey("tv", it.parsedTitle, it.parsedYear) }
                .distinct()
                .forEach { query -> matchCacheDao.put(MatchCacheEntity(query = query, metadataKey = metadata.key, checkedAt = now)) }
        }
    }

    private class ShowMatch(
        val videoId: Long,
        val season: Int?,
        val episode: Int?,
        val episodeEnd: Int?,
        val details: TmdbEpisode?,
    )

    override suspend fun clearMetadata() {
        enricher.invalidate()
        withContext(Dispatchers.IO) {
            database.withTransaction {
                // What media servers described is not TMDB's, and no TMDB language change touches it.
                metadataDao.clearTmdb()
                matchCacheDao.clear()
                videoDao.resetAutomaticMatches()
                for (title in videoDao.automaticEpisodeTitles()) {
                    videoDao.setShowKeyForTitle(title, LibraryKeys.showKey(null, title))
                }
                // Absolute numbers were placed into the old match's seasons; start from the file again.
                for (episode in videoDao.automaticEpisodes()) {
                    val raw = rawNumbers(episode)
                    if (raw.season != episode.season || raw.episode != episode.episode || raw.episodeEnd != episode.episodeEnd) {
                        videoDao.setEpisodeNumbers(episode.id, raw.season, raw.episode, raw.episodeEnd)
                    }
                }
            }
        }
        enricher.request()
    }

    override suspend fun retryMatching() {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                // A new key or host deserves a fresh look at titles that found nothing before.
                matchCacheDao.clearMisses()
                videoDao.resetUnmatched()
            }
        }
        enricher.request()
    }

    // Playlists

    override fun observePlaylists(): Flow<List<PlaylistWithCount>> =
        playlistDao.observeWithCounts().distinctUntilChanged()

    override suspend fun playlistVideos(playlistId: Long): List<VideoWithMeta> =
        withContext(Dispatchers.IO) { playlistDao.shownVideos(playlistId) }

    override suspend fun createPlaylist(name: String): Long {
        val clean = playlistName(name)
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            playlistDao.insert(PlaylistEntity(name = clean, createdAt = now, updatedAt = now))
        }
    }

    override suspend fun renamePlaylist(id: Long, name: String) {
        val clean = playlistName(name)
        withContext(Dispatchers.IO) { playlistDao.rename(id, clean, System.currentTimeMillis()) }
    }

    override suspend fun deletePlaylist(id: Long) {
        withContext(Dispatchers.IO) { playlistDao.delete(id) }
    }

    override suspend fun addToPlaylist(playlistId: Long, videoIds: List<Long>) {
        if (videoIds.isEmpty()) return
        withContext(Dispatchers.IO) {
            database.withTransaction {
                if (playlistDao.byId(playlistId) == null) return@withTransaction
                val items = playlistDao.items(playlistId)
                val taken = items.mapTo(HashSet<Long>()) { it.videoId }
                // Keeps the given order, without what the playlist already has or a second copy.
                val wanted = videoIds.filter { taken.add(it) }
                if (wanted.isEmpty()) return@withTransaction
                // A video removed meanwhile would fail the foreign key.
                val existing = wanted.chunked(SQL_CHUNK).flatMapTo(HashSet<Long>()) { videoDao.existingIds(it) }
                val start = (items.maxOfOrNull { it.position } ?: -1) + 1
                val added = wanted.filter { it in existing }.mapIndexed { index, videoId ->
                    PlaylistItemEntity(playlistId = playlistId, videoId = videoId, position = start + index)
                }
                if (added.isEmpty()) return@withTransaction
                playlistDao.insertItems(added)
                playlistDao.touch(playlistId, System.currentTimeMillis())
            }
        }
    }

    override suspend fun removeFromPlaylist(playlistId: Long, videoId: Long) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val items = playlistDao.items(playlistId)
                if (items.none { it.videoId == videoId }) return@withTransaction
                playlistDao.removeVideo(playlistId, videoId)
                renumber(items.filter { it.videoId != videoId })
                playlistDao.touch(playlistId, System.currentTimeMillis())
            }
        }
    }

    override suspend fun movePlaylistItem(playlistId: Long, from: Int, to: Int) {
        if (from == to) return
        withContext(Dispatchers.IO) {
            database.withTransaction {
                // Indices count shown videos only; hidden items keep their place among the others.
                val shown = playlistDao.shownItemIds(playlistId)
                if (from !in shown.indices || to !in shown.indices) return@withTransaction
                val items = playlistDao.items(playlistId)
                val moving = items.firstOrNull { it.id == shown[from] } ?: return@withTransaction
                val reordered = items.filterTo(ArrayList()) { it.id != moving.id }
                val target = reordered.indexOfFirst { it.id == shown[to] }
                if (target < 0) return@withTransaction
                // Moving down it goes after the item it lands on, moving up before it.
                reordered.add(if (from < to) target + 1 else target, moving)
                renumber(reordered)
                playlistDao.touch(playlistId, System.currentTimeMillis())
            }
        }
    }

    /** Gives [ordered] the positions 0, 1, 2, …, writing only the items that move. */
    private suspend fun renumber(ordered: List<PlaylistItemEntity>) {
        ordered.forEachIndexed { index, item ->
            if (item.position != index) playlistDao.setPosition(item.id, index)
        }
    }

    // Queues

    override suspend fun folderQueue(sourceId: Long, path: String): List<Long> = withContext(Dispatchers.IO) {
        val source = sourceDao.byId(sourceId)?.takeIf { it.enabled } ?: return@withContext emptyList()
        val dir = path.trim('/')
        val smbRoot = if (source.type == SourceType.SMB) SmbUris.parse(source.location)?.path.orEmpty() else ""
        videoDao.folderEntries(sourceId)
            .filter { browseFolder(source.type, smbRoot, it.folder) == dir }
            .sortedWith(compareBy<FolderEntry, String>(NaturalOrder) { it.fileName }.thenBy { it.id })
            .map { it.id }
    }

    override suspend fun seasonQueue(showKey: String, season: Int?): List<Long> = withContext(Dispatchers.IO) {
        val episodes = videoDao.shownEpisodesOf(showKey)
            .filter { season == null || listedSeason(it.season) == season }
        withoutRepeats(episodes.sortedWith(WATCHING_ORDER)).map { it.id }
    }

    override suspend fun nextUpQueue(videoId: Long): List<Long> = withContext(Dispatchers.IO) {
        val current = videoDao.byId(videoId) ?: return@withContext emptyList()
        val showKey = current.showKey
        if (current.kind != VideoKind.EPISODE || showKey == null) return@withContext listOf(current.id)
        // The episode asked for plays even when the library does not show it (a missing file).
        val ordered = (videoDao.shownEpisodesOf(showKey).filter { it.id != current.id } + current)
            .sortedWith(WATCHING_ORDER)
        val start = ordered.indexOfFirst { it.id == current.id }
        withoutRepeats(ordered.subList(start, ordered.size)).map { it.id }
    }

    // Settings reactions

    @OptIn(FlowPreview::class)
    private fun observeSettings() {
        scope.launch {
            settings.settings
                .map { settings.effectiveTmdbKey to it.tmdbHost }
                .distinctUntilChanged()
                .drop(1)
                // A key typed into a text field changes on every keystroke.
                .debounce(KEY_SETTLE_MS)
                .collect { logFailure("Retry matching") { retryMatching() } }
        }
        scope.launch {
            // Metadata is stored in one language: fetch everything again in a new one, also when it
            // changed while the app was closed (the default follows the device's language).
            settings.settings
                .map { it.tmdbLanguage }
                .distinctUntilChanged()
                .collect { language -> logFailure("Metadata reset") { useMetadataLanguage(language) } }
        }
        scope.launch {
            settings.settings
                .map { it.scanDeviceStorage }
                .distinctUntilChanged()
                .drop(1)
                .collect { enabled ->
                    logFailure("Device storage toggle") {
                        val device = syncDeviceSource(enabled)
                        if (enabled && device != null) scanSource(device.id)
                    }
                }
        }
    }

    /** Clears metadata fetched in another language than [language] (see observeSettings). */
    private suspend fun useMetadataLanguage(language: String) {
        val fetchedIn = withContext(Dispatchers.IO) { statePrefs.getString(KEY_METADATA_LANGUAGE, null) }
        if (fetchedIn == language) return
        // Not recorded yet (first start of this version, or a new install): keep what is there.
        if (fetchedIn != null) clearMetadata()
        statePrefs.edit { putString(KEY_METADATA_LANGUAGE, language) }
    }

    /** Background work in the app scope must never take the process down; failures are logged. */
    private fun launchLogged(what: String, block: suspend () -> Unit) {
        scope.launch { logFailure(what, block) }
    }

    private suspend fun logFailure(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what failed", e)
            CrashReporter.log("$what failed", e)
        }
    }
}

/** What storage access looked like at one moment (see LibraryRepositoryImpl.onStorageAccessChanged). */
private data class AccessSnapshot(val videos: VideoAccess, val images: Boolean, val allFiles: Boolean) {
    /** More of device storage is readable now than with [before]. */
    fun widens(before: AccessSnapshot): Boolean =
        videos.ordinal > before.videos.ordinal || (images && !before.images) || (allFiles && !before.allFiles)

    companion object {
        fun of(context: Context): AccessSnapshot = AccessSnapshot(
            videos = StoragePermissions.videoAccess(context),
            images = StoragePermissions.hasImageAccess(context),
            allFiles = StoragePermissions.hasAllFilesAccess(context),
        )
    }
}

/** "5 minutes", "1 minute", "30 seconds". */
private fun durationLabel(ms: Long): String {
    val minutes = ms / 60_000
    if (minutes >= 1) return if (minutes == 1L) "1 minute" else "$minutes minutes"
    val seconds = (ms / 1000).coerceAtLeast(1)
    return if (seconds == 1L) "1 second" else "$seconds seconds"
}

/** Applies a fresh parse and forgets any automatic match, ready for (re)matching. */
private fun VideoEntity.withParse(parsed: ParsedName): VideoEntity {
    val kind = when (parsed.kind) {
        ParsedKind.MOVIE -> VideoKind.MOVIE
        ParsedKind.EPISODE -> VideoKind.EPISODE
        ParsedKind.UNKNOWN -> VideoKind.VIDEO
    }
    val numbers = parsed.numbers()
    return copy(
        kind = kind,
        parsedTitle = parsed.title,
        parsedYear = parsed.year,
        season = numbers.season,
        episode = numbers.episode,
        episodeEnd = numbers.episodeEnd,
        metadataKey = null,
        showKey = if (kind == VideoKind.EPISODE) LibraryKeys.showKey(null, parsed.title) else null,
        episodeTitle = null,
        episodeOverview = null,
        episodeStillPath = null,
        episodeAirDate = null,
        matchState = if (kind == VideoKind.VIDEO) MatchState.UNMATCHED else MatchState.PENDING,
        matchAttemptAt = null,
    )
}

private class EpisodeNumbers(val season: Int?, val episode: Int?, val episodeEnd: Int?)

/**
 * The parser gives absolute anime numbers as season 1 plus [ParsedName.absoluteEpisode]; they are
 * stored without a season so matching can place them into the show's TMDB seasons.
 */
private fun ParsedName.numbers(): EpisodeNumbers {
    val absolute = absoluteEpisode
    return if (absolute != null) EpisodeNumbers(null, absolute, null) else EpisodeNumbers(season, episode, episodeEnd)
}

/**
 * An episode's numbering as its file name gives it. A match may have placed an absolute number
 * into a season of the show it found; this takes it back to the absolute number.
 */
private fun rawNumbers(video: VideoEntity): EpisodeNumbers {
    val stored = EpisodeNumbers(video.season, video.episode, video.episodeEnd)
    if (video.kind != VideoKind.EPISODE) return stored
    val parsed = parseName(video.fileName, video.folder.split('/').filter { it.isNotEmpty() }.asReversed())
    val absolute = parsed.absoluteEpisode?.takeIf { parsed.kind == ParsedKind.EPISODE } ?: return stored
    return EpisodeNumbers(null, absolute, null)
}

private fun SmbLocation.covers(file: SmbLocation): Boolean =
    host.equals(file.host, ignoreCase = true) &&
        (port ?: DEFAULT_SMB_PORT) == (file.port ?: DEFAULT_SMB_PORT) &&
        share.equals(file.share, ignoreCase = true) &&
        (path.isEmpty() || file.path.startsWith("$path/", ignoreCase = true))

/**
 * A video's folder as Browse lists it, relative to its source's root: device folders as they are,
 * a picked folder's paths without the folder's own name, share paths below the source's folder
 * ([smbRoot], the path inside the share); the other network sources report their folders below
 * their location already. The same rules as the UI's relativeFolder, so
 * [LibraryRepository.folderQueue] takes Browse's paths.
 */
internal fun browseFolder(type: SourceType, smbRoot: String, folder: String): String {
    val clean = folder.trim('/')
    return when (type) {
        SourceType.DEVICE -> clean
        SourceType.FOLDER -> if ('/' in clean) clean.substringAfter('/') else ""
        SourceType.SMB -> {
            val root = smbRoot.trim('/')
            when {
                root.isEmpty() -> clean
                clean.equals(root, ignoreCase = true) -> ""
                clean.startsWith("$root/", ignoreCase = true) -> clean.substring(root.length + 1)
                else -> clean
            }
        }
        SourceType.STREAM -> ""
        SourceType.FTP, SourceType.SFTP, SourceType.NFS, SourceType.UPNP, SourceType.WEBDAV, SourceType.JELLYFIN,
        SourceType.EMBY, SourceType.PLEX,
        -> clean
    }
}

/** The season an episode is listed under: absolute-numbered ones (no season yet) sit in season 1. */
private fun listedSeason(season: Int?): Int = season ?: 1

/** Episodes in watching order, as the show's page lists them: season (specials last), episode, file name. */
private val WATCHING_ORDER: Comparator<VideoEntity> =
    compareBy<VideoEntity>(
        { if (it.season == 0) Int.MAX_VALUE else listedSeason(it.season) },
        { it.episode ?: Int.MAX_VALUE },
    )
        .thenBy(NaturalOrder) { it.fileName }
        .thenBy { it.id }

/**
 * Drops what a queue would play twice, keeping the first of each in [ordered]: another copy of an
 * episode (720p and 1080p), or an episode a double episode before it (S01E01E02) already covers.
 * Episodes without a number are all kept.
 */
private fun withoutRepeats(ordered: List<VideoEntity>): List<VideoEntity> {
    val kept = ArrayList<VideoEntity>(ordered.size)
    var season: Int? = null
    var coveredUpTo = Int.MIN_VALUE
    for (video in ordered) {
        val episode = video.episode
        if (episode == null) {
            kept += video
            continue
        }
        val listed = listedSeason(video.season)
        if (listed != season) {
            season = listed
            coveredUpTo = Int.MIN_VALUE
        } else if (episode <= coveredUpTo) {
            continue
        }
        kept += video
        coveredUpTo = max(coveredUpTo, max(episode, video.episodeEnd ?: episode))
    }
    return kept
}

/** Trimmed playlist name; blank is refused. */
private fun playlistName(name: String): String {
    val clean = name.trim()
    require(clean.isNotEmpty()) { "Enter a playlist name" }
    return clean
}

/** A bug in the parser must not fail a whole scan: such a file becomes a plain video. */
private fun parseName(fileName: String, parentFolders: List<String>): ParsedName =
    try {
        NameParser.parse(fileName, parentFolders)
    } catch (e: Exception) {
        Log.w(TAG, "Could not parse \"$fileName\"", e)
        ParsedName(kind = ParsedKind.UNKNOWN, title = VideoFiles.baseName(fileName).ifBlank { fileName })
    } catch (e: StackOverflowError) {
        // A pathological name can overflow java.util.regex's recursion; the scan goes on.
        Log.w(TAG, "Could not parse \"$fileName\"", e)
        ParsedName(kind = ParsedKind.UNKNOWN, title = fileName)
    }

/**
 * NameParser takes file names; give a typed title a video extension so a dot in it
 * ("Mr. Robot") is not mistaken for one.
 */
private fun asFileName(title: String): String =
    if (VideoFiles.extension(title) in VideoFiles.EXTENSIONS) title else "$title.mkv"

private fun TmdbMovieSummary.toSearchResult() = TmdbSearchResult(
    tmdbId = id,
    type = IdentifyType.MOVIE,
    title = title.ifBlank { originalTitle.orEmpty() },
    year = year,
    overview = overview.nonBlank(),
    posterUrl = TmdbClient.posterUrl(posterPath, "w342"),
)

private fun TmdbTvSummary.toSearchResult() = TmdbSearchResult(
    tmdbId = id,
    type = IdentifyType.TV,
    title = name.ifBlank { originalName.orEmpty() },
    year = year,
    overview = overview.nonBlank(),
    posterUrl = TmdbClient.posterUrl(posterPath, "w342"),
)
