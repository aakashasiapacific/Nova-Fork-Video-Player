package com.aakash.novafork.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.aakash.novafork.core.library.LibraryKeys
import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.core.parse.NameParser
import com.aakash.novafork.core.parse.ParsedKind
import com.aakash.novafork.core.parse.ParsedName
import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.core.tmdb.TmdbClient
import com.aakash.novafork.core.tmdb.TmdbException
import com.aakash.novafork.core.tmdb.TmdbMovieSummary
import com.aakash.novafork.core.tmdb.TmdbTvSummary
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

private const val TAG = "LibraryRepository"
private const val DEVICE_SOURCE_NAME = "Device storage"
private const val DEVICE_SOURCE_LOCATION = "device"
private const val KEY_SETTLE_MS = 1_500L

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

    /** One scan at a time: refreshAll coalesces into a running scan, scanSource waits for it. */
    private val scanMutex = Mutex()
    private val enricher = MetadataEnricher(database, tmdb, state, scope)

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

    override suspend fun refreshAll() {
        withContext(Dispatchers.IO) {
            if (!scanMutex.tryLock()) return@withContext
            try {
                state.update { it.copy(running = true, sourceName = null, found = 0, lastError = null) }
                syncDeviceSource(settings.settings.value.scanDeviceStorage)
                for (source in sourceDao.all()) {
                    if (isScannable(source)) scan(source)
                }
            } finally {
                state.update { it.copy(running = false, sourceName = null, found = 0) }
                scanMutex.unlock()
            }
        }
        enricher.request()
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
        state.update { it.copy(running = true, sourceName = source.name, found = 0) }
        try {
            val found = scanner.scan(source) { count -> state.update { it.copy(found = count) } }
            merge(source, found)
            sourceDao.markScanned(source.id, System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Scanning ${source.name} failed", e)
            val message = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            sourceDao.markFailed(source.id, message)
            state.update { it.copy(lastError = "${source.name}: $message") }
        }
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
            val inserts = ArrayList<VideoEntity>()
            val updates = ArrayList<VideoEntity>()
            for (file in scanned) {
                if (!seen.add(file.uri)) continue
                val old = existing[file.uri]
                if (old == null) {
                    inserts += newVideo(source.id, file)
                } else {
                    val merged = refreshed(old, file)
                    if (merged != old) updates += merged
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
            database.withTransaction {
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
        val season = current.season ?: 0
        val lastEpisode = current.episodeEnd ?: current.episode ?: return@withContext null
        // Strictly later episodes only: a second copy of the same episode is not "next".
        videoDao.episodesOf(showKey)
            .filter {
                val otherSeason = it.video.season ?: 0
                val otherEpisode = it.video.episode ?: Int.MAX_VALUE
                it.video.id != videoId &&
                    (otherSeason > season || (otherSeason == season && otherEpisode > lastEpisode))
            }
            .minWithOrNull(
                compareBy<VideoWithMeta>(
                    { it.video.season ?: 0 },
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
                IdentifyType.TV -> {
                    val show = tmdb.tv(result.tmdbId)
                    val metadata = show.toMetadata(now)
                    val resolved = resolveEpisodeNumbers(video.season, video.episode, show.seasons)
                    val season = resolved.first ?: 1
                    val number = resolved.second ?: 1
                    val episode = try {
                        tmdb.season(show.id, season).episodes.firstOrNull { it.episodeNumber == number }
                    } catch (e: TmdbException) {
                        if (e.code == 404) null else throw e
                    }
                    database.withTransaction {
                        metadataDao.upsert(metadata)
                        val current = videoDao.byId(videoId) ?: return@withTransaction
                        videoDao.update(
                            current.copy(
                                kind = VideoKind.EPISODE,
                                metadataKey = metadata.key,
                                showKey = metadata.key,
                                season = season,
                                episode = number,
                                episodeEnd = current.episodeEnd?.takeIf { video.season != null && it > number },
                                episodeTitle = episode?.name.nonBlank(),
                                episodeOverview = episode?.overview.nonBlank(),
                                episodeStillPath = episode?.stillPath.nonBlank(),
                                episodeAirDate = episode?.airDate.nonBlank(),
                                matchState = MatchState.MANUAL,
                                matchAttemptAt = now,
                            ),
                        )
                    }
                }
            }
        }
    }

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
            // Metadata is stored in one language: fetch everything again in the new one.
            settings.settings
                .map { it.tmdbLanguage }
                .distinctUntilChanged()
                .drop(1)
                .collect { logFailure("Metadata reset") { clearMetadata() } }
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
    // Absolute anime numbering is placed into a TMDB season once the show is matched.
    val absolute = parsed.absoluteEpisode?.takeIf { parsed.season == null || parsed.episode == null }
    return copy(
        kind = kind,
        parsedTitle = parsed.title,
        parsedYear = parsed.year,
        season = if (absolute != null) null else parsed.season,
        episode = absolute ?: parsed.episode,
        episodeEnd = if (absolute != null) null else parsed.episodeEnd,
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

/** A bug in the parser must not fail a whole scan: such a file becomes a plain video. */
private fun parseName(fileName: String, parentFolders: List<String>): ParsedName =
    try {
        NameParser.parse(fileName, parentFolders)
    } catch (e: Exception) {
        Log.w(TAG, "Could not parse \"$fileName\"", e)
        ParsedName(kind = ParsedKind.UNKNOWN, title = VideoFiles.baseName(fileName).ifBlank { fileName })
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
