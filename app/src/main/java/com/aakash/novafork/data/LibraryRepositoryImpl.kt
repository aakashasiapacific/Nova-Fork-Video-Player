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
import com.aakash.novafork.data.db.MatchCacheEntity
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.NovaDatabase
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
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

/** Repository state outside the database: the language the stored metadata was fetched in. */
private const val STATE_PREFS = "library_state"
private const val KEY_METADATA_LANGUAGE = "metadata_language"

class LibraryRepositoryImpl(
    context: Context,
    private val database: NovaDatabase,
    private val scanners: Map<SourceType, SourceScanner>,
    private val tmdb: TmdbClient,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) : LibraryRepository {
    private val context: Context = context.applicationContext
    private val sourceDao = database.sourceDao()
    private val videoDao = database.videoDao()
    private val metadataDao = database.metadataDao()
    private val matchCacheDao = database.matchCacheDao()

    private val state = MutableStateFlow(ScanState())
    override val scanState: StateFlow<ScanState> = state.asStateFlow()

    /**
     * One scan at a time: refreshAll coalesces into a running refresh and waits out a single
     * source's scan; scanSource and MediaStore rescans wait their turn.
     */
    private val scanMutex = Mutex()
    private val enricher = MetadataEnricher(database, tmdb, state, scope, Connectivity(this.context))
    private val statePrefs: SharedPreferences = this.context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

    /** Source id → wall-clock start of its latest scan in this process, failed ones included. */
    private val scanStarts = ConcurrentHashMap<Long, Long>()

    /** elapsedRealtime of an app launch whose refresh hasn't come yet ([NO_LAUNCH] if none). */
    private val launchAt = AtomicLong(SystemClock.elapsedRealtime())

    /** A forced refresh came while a launch refresh ran: that one also scans what it skipped. */
    private val forceRequested = AtomicBoolean(false)

    /** True while refreshAll, not a single source's scan, holds [scanMutex]. */
    @Volatile private var refreshing = false

    /** Finished refreshAll runs. Written with [scanMutex] held. */
    @Volatile private var refreshesDone = 0

    private val watchingMedia = AtomicBoolean(false)
    private val mediaChanges = Channel<Unit>(Channel.CONFLATED)

    /** elapsedRealtime of the latest MediaStore change, and of the latest device scan's start. */
    @Volatile private var mediaChangedAt = 0L

    @Volatile private var deviceScanStartedAt = 0L

    init {
        launchLogged("Device source setup") { syncDeviceSource(settings.settings.value.scanDeviceStorage) }
        observeSettings()
        // Picks up lookups an earlier process did not finish.
        enricher.request()
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
                    // Coalesces into the running refresh; a forced one makes it scan what it skipped too.
                    if (force) forceRequested.set(true)
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

    /** A folder or share scanned, or tried, within [LAUNCH_RESCAN_AFTER_MS]. */
    private fun scannedRecently(source: SourceEntity): Boolean {
        val walksFiles = when (source.type) {
            SourceType.FOLDER, SourceType.SMB -> true
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
            SourceType.FOLDER, SourceType.SMB -> true
            SourceType.STREAM -> false
        }
    }

    /** Scans one source and merges the result. Called with [scanMutex] held. */
    private suspend fun scan(source: SourceEntity) {
        val scanner = scanners[source.type] ?: return
        if (source.type == SourceType.DEVICE) deviceScanStartedAt = SystemClock.elapsedRealtime()
        scanStarts[source.id] = System.currentTimeMillis()
        state.update { it.copy(running = true, sourceName = source.name, found = 0) }
        try {
            val found = scanner.scan(source) { count -> state.update { it.copy(found = count) } }
            merge(source, found)
            sourceDao.markScanned(source.id, System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailure(source, e)
        } catch (e: LinkageError) {
            // A library class missing on this Android version fails this source, not the app.
            recordFailure(source, e)
        }
    }

    private suspend fun recordFailure(source: SourceEntity, e: Throwable) {
        Log.w(TAG, "Scanning ${source.name} failed", e)
        CrashReporter.log("Scanning a ${source.type.name} source failed", e)
        val message = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        sourceDao.markFailed(source.id, message)
        state.update { it.copy(lastError = "${source.name}: $message") }
    }

    /**
     * Upserts the files of one scan by URI. Known files keep their id, date added, playback state
     * and match; files of the source the scan did not see are kept (watch history) but hidden.
     */
    private suspend fun merge(source: SourceEntity, scanned: List<ScannedVideo>) {
        database.withTransaction {
            // The source may have been removed while its scan was running.
            if (sourceDao.byId(source.id) == null) return@withTransaction
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
                    val merged = refreshed(old, file)
                    if (merged != old) updates += merged
                }
            }
            if (fresh.isNotEmpty()) {
                // URIs are unique across sources: a file another source already lists (overlapping
                // shares) stays with that source, unless that one is off or no longer sees it.
                val elsewhere = fresh.map { it.uri }.chunked(SQL_CHUNK).flatMap { videoDao.byUris(it) }.associateBy { it.uri }
                val enabledSources = if (elsewhere.isEmpty()) emptySet() else sourceDao.all().filter { it.enabled }.map { it.id }.toSet()
                for (file in fresh) {
                    val other = elsewhere[file.uri]
                    when {
                        other == null -> inserts += newVideo(source.id, file)
                        other.sourceId !in enabledSources || !other.present ->
                            updates += refreshed(other, file).copy(sourceId = source.id)
                        else -> Unit
                    }
                }
            }
            if (inserts.isNotEmpty()) videoDao.insertAll(inserts)
            if (updates.isNotEmpty()) videoDao.updateAll(updates)
            val missing = existing.values.filter { it.present && it.uri !in seen }.map { it.id }
            for (chunk in missing.chunked(SQL_CHUNK)) videoDao.markMissing(chunk)
        }
    }

    private fun newVideo(sourceId: Long, file: ScannedVideo): VideoEntity {
        val parsed = parseName(file.fileName, file.parentFolders)
        return VideoEntity(
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
        ).withParse(parsed)
    }

    private fun refreshed(old: VideoEntity, file: ScannedVideo): VideoEntity {
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
            present = true,
        )
        // A renamed file (same MediaStore id) may now be something else entirely.
        val renamed = file.fileName != old.fileName && old.matchState != MatchState.MANUAL
        return if (renamed) updated.withParse(parseName(file.fileName, file.parentFolders)) else updated
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
        val scanned = withContext(Dispatchers.IO) {
            val deviceId = sourceDao.device()?.id ?: return@withContext false
            // Waits for a running scan instead of overlapping it.
            scanMutex.withLock {
                if (deviceScanStartedAt > changedAt) return@withLock false
                val device = sourceDao.byId(deviceId)?.takeIf { isScannable(it) } ?: return@withLock false
                try {
                    scan(device)
                } finally {
                    state.update { it.copy(running = false, sourceName = null, found = 0) }
                }
                true
            }
        }
        if (scanned) enricher.request()
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

    override suspend fun removeSource(sourceId: Long) {
        withContext(Dispatchers.IO) {
            val source = sourceDao.byId(sourceId) ?: return@withContext
            // While "Scan device storage" is on the next scan brings it straight back, with fresh ids
            // and none of the watch history removing it threw away. The setting hides it instead.
            require(source.type != SourceType.DEVICE) {
                "Device storage can't be removed. Turn off \"Scan device storage\" in Settings instead."
            }
            database.withTransaction {
                if (source.type == SourceType.SMB) handOverCoveredVideos(source)
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
        }
    }

    override suspend fun saveTracks(videoId: Long, audioTrackId: Int?, subtitleTrackId: Int?) {
        withContext(Dispatchers.IO) { videoDao.saveTracks(videoId, audioTrackId, subtitleTrackId) }
    }

    override suspend fun setWatched(videoId: Long, watched: Boolean) {
        withContext(Dispatchers.IO) { videoDao.setWatched(videoId, watched) }
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
                metadataDao.clear()
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
