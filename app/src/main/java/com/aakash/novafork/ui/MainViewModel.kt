package com.aakash.novafork.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aakash.novafork.BuildConfig
import com.aakash.novafork.CrashReporter
import com.aakash.novafork.NovaApp
import com.aakash.novafork.core.jellyfin.ServerSecrets
import com.aakash.novafork.core.library.LibraryKeys
import com.aakash.novafork.core.plex.PlexResource
import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.core.tmdb.TmdbDisabledException
import com.aakash.novafork.core.tmdb.TmdbException
import com.aakash.novafork.data.IdentifyType
import com.aakash.novafork.data.LibraryDiagnostics
import com.aakash.novafork.data.NetworkLocation
import com.aakash.novafork.data.NetworkSourceInput
import com.aakash.novafork.data.NetworkSources
import com.aakash.novafork.data.PlaylistWithCount
import com.aakash.novafork.data.ScanState
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.SmbCredentials
import com.aakash.novafork.data.StoragePermissions
import com.aakash.novafork.data.TmdbSearchResult
import com.aakash.novafork.data.backup.BackupFormatException
import com.aakash.novafork.data.backup.BackupManager
import com.aakash.novafork.data.backup.BackupSummary
import com.aakash.novafork.data.backup.NovaBackup
import com.aakash.novafork.data.backup.RestoreReport
import com.aakash.novafork.data.db.ArtworkChoiceEntity
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoKind
import com.aakash.novafork.data.db.VideoWithMeta
import com.aakash.novafork.data.music.MusicLibraryData
import com.aakash.novafork.sources.DeviceScanner
import com.aakash.novafork.sources.media.MediaServerSources
import com.aakash.novafork.sources.media.PlexSignIn
import com.aakash.novafork.sources.net.FileServerSources
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.BackupUi
import com.aakash.novafork.ui.model.BrowseDirUi
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.withRecentSearch
import com.aakash.novafork.ui.model.IdentifyResultUi
import com.aakash.novafork.ui.model.KeyStatus
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.LibraryUi
import com.aakash.novafork.ui.model.NetworkSourceForm
import com.aakash.novafork.ui.model.PermissionUi
import com.aakash.novafork.ui.model.PlexServerUi
import com.aakash.novafork.ui.model.PlexSignInUi
import com.aakash.novafork.ui.model.RestorePreviewUi
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.SearchUi
import com.aakash.novafork.ui.model.ServerLibraryUi
import com.aakash.novafork.ui.model.SettingsUi
import com.aakash.novafork.ui.model.SmbForm
import com.aakash.novafork.ui.model.SourceUi
import com.aakash.novafork.ui.model.TopDestination
import com.aakash.novafork.ui.model.UpnpServerUi
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MainViewModel"

/** A queue for MainActivity to start the player with (PlayerIntents.forQueue). */
data class QueueStart(val videoIds: List<Long>, val startIndex: Int, val shuffle: Boolean)

/**
 * The app shell's state holder: navigation, what the user picked (filters, search, dialogs) and
 * every [AppActions] that needs no Activity. MainActivity adds the rest (player, pickers,
 * permission prompts) on top.
 */
class MainViewModel(app: Application) : AndroidViewModel(app), AppActions {
    private val graph = NovaApp.graph(app)
    private val library = graph.library
    private val settingsStore = graph.settings

    /** Everything the user (not the database) controls, in one place so updates are atomic. */
    private data class LocalState(
        val backStack: List<Route> = listOf(Route.Home),
        val filter: LibraryFilter = LibraryFilter.ALL,
        val sort: LibrarySort = LibrarySort.RECENT,
        val query: String = "",
        val previewKey: String? = null,
        val dialog: DialogUi? = null,
        val message: String? = null,
        val keyStatus: KeyStatus = KeyStatus.UNKNOWN,
        val permission: PermissionUi = PermissionUi(),
        val allFilesAccess: Boolean = false,
        val crashReport: String? = null,
        /** Folders of network sources (other than SMB) listed live, most recently updated last. */
        val listings: Map<ListingKey, RemoteListing> = emptyMap(),
        val recentSearches: List<String> = emptyList(),
    )

    /** Search's recent searches, kept across launches (they never leave the device). */
    private val searchPrefs = app.getSharedPreferences("search_history", android.content.Context.MODE_PRIVATE)

    private val local = MutableStateFlow(
        LocalState(
            permission = currentPermission(),
            allFilesAccess = StoragePermissions.hasAllFilesAccess(app),
            crashReport = CrashReporter.pending(app),
            recentSearches = searchPrefs.getString(KEY_RECENT_SEARCHES, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
        ),
    )

    private val libraryIndex = combine(
        library.observeVideos(),
        library.observeSources(),
        settingsStore.settings.map { it.preferLocalArtwork }.distinctUntilChanged(),
        graph.database.metadataDao().observeArtworkChoices(),
    ) { videos, sources, preferLocal, choices ->
        LibraryIndex.build(withArtworkChoices(videos, choices), sources, preferLocal)
    }

    // Only touched from the state combine below, which never runs concurrently with itself.
    private val libraryMemo = Memo<Triple<LibraryIndex, LibraryFilter, LibrarySort>, LibraryUi> { (index, filter, sort) ->
        UiMappers.library(index, filter, sort)
    }
    private val searchMemo = Memo<Triple<LibraryIndex, String, MusicLibraryData>, SearchUi> { (index, query, music) ->
        UiMappers.search(index, query, music)
    }

    /** Watchlist keys, most recently added first. */
    private val watchlist: StateFlow<List<String>> =
        graph.music.observeWatchlist().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Starred movies, shows and videos, most recently starred first. */
    private val videoFavorites: StateFlow<List<String>> =
        graph.music.observeVideoFavorites().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val favoritesMemo = Memo<Pair<LibraryIndex, List<String>>, List<LibraryItemUi>> { (index, keys) ->
        if (keys.isEmpty()) {
            emptyList()
        } else {
            val byKey = UiMappers.library(index, LibraryFilter.ALL, LibrarySort.RECENT).items.associateBy { it.key }
            keys.mapNotNull { byKey[it] }
        }
    }

    /** The watchlist row's items, rebuilt when the library or the list changes. */
    private val watchlistMemo = Memo<Pair<LibraryIndex, List<String>>, List<LibraryItemUi>> { (index, keys) ->
        if (keys.isEmpty()) {
            emptyList()
        } else {
            val byKey = UiMappers.library(index, LibraryFilter.ALL, LibrarySort.RECENT).items.associateBy { it.key }
            keys.mapNotNull { byKey[it] }
        }
    }

    /** The music library, for search (one search over videos and music). */
    private val musicLibrary: Flow<MusicLibraryData> = graph.music.observeLibrary().onStart { emit(MusicLibraryData()) }
    private val remoteDirMemo = Memo<RemoteDirInput, BrowseDirUi> { (index, source, path, listings) ->
        UiMappers.remoteDir(index, source, path, listings)
    }

    /** The topmost playlist on the back stack, so it stays loaded under a details page opened from it. */
    private val openPlaylistId: Flow<Long?> = local
        .map { state -> state.backStack.filterIsInstance<Route.Playlist>().lastOrNull()?.playlistId }
        .distinctUntilChanged()

    /** Every playlist, plus the open one's videos, reloaded on any playlist or library change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val playlists: Flow<PlaylistsState> = combine(library.observePlaylists(), openPlaylistId) { all, openId -> all to openId }
        .mapLatest { (all, openId) ->
            val open = openId?.let { id -> all.firstOrNull { it.id == id } }
            PlaylistsState(all = all, open = open, openVideoIds = if (open != null) playlistVideoIds(open.id) else emptyList())
        }

    val state: StateFlow<AppUiState> = combine(
        libraryIndex,
        library.scanState,
        settingsStore.settings,
        local,
        combine(playlists, musicLibrary, watchlist, videoFavorites) { lists, music, saved, starred ->
            Saved(lists, music, saved, starred)
        },
    ) { index, scan, settings, user, extra ->
        assemble(index, scan, settings, user, extra.lists, extra.music, extra.watchlist, extra.favorites)
    }
        .flowOn(Dispatchers.Default)
        // Seeded with the real settings so the first frame already has the right theme and layout.
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState(settings = SettingsUi(settings = settingsStore.settings.value)))

    private val queueStarts = Channel<QueueStart>(Channel.BUFFERED)

    /** Queues to play, each delivered once; MainActivity collects them while it is started. */
    val queueRequests: Flow<QueueStart> = queueStarts.receiveAsFlow()

    private var identifyJob: Job? = null
    private var keyCheckJob: Job? = null
    private var smbTestJob: Job? = null
    private var networkTestJob: Job? = null
    private var upnpJob: Job? = null
    private var plexJob: Job? = null
    private var diagnosticsJob: Job? = null
    private val listingJobs = HashMap<ListingKey, Job>()

    /** The Plex account a code signed in, until one of its servers is picked or the dialog goes away. Memory only. */
    private var plexAccount: PlexAccount? = null
    private val plexSignIn: PlexSignIn by lazy { MediaServerSources.plexSignIn(graph) }

    private val backups: BackupManager by lazy {
        BackupManager(getApplication<Application>(), graph.database, settingsStore, library, BuildConfig.VERSION_NAME)
    }
    private val backup = MutableStateFlow(BackupUi())
    private val backupUi: StateFlow<BackupUi> = backup.asStateFlow()

    /** "Include passwords and sign-ins" of the backup being saved, from the dialog until the picker returns. */
    private var exportSecrets = false

    /** The backup file read and checked, waiting for the user's go ([BackupUi.confirm]). Memory only. */
    private var restoreCandidate: NovaBackup? = null

    init {
        launchSafely("Couldn't scan the library") { library.refreshAll() }
        viewModelScope.launch {
            // A result for the previous key or server says nothing about the new one.
            settingsStore.settings
                .map { it.tmdbApiKey to it.tmdbHost }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    keyCheckJob?.cancel()
                    local.update { state -> state.copy(keyStatus = KeyStatus.UNKNOWN) }
                }
        }
        viewModelScope.launch {
            // Network sources other than SMB are browsed live: a folder is listed when it is opened.
            local
                .map { it.backStack.lastOrNull() as? Route.BrowseDir }
                .distinctUntilChanged()
                .collect { route -> if (route != null) openListing(route.sourceId, route.path, refresh = false) }
        }
        viewModelScope.launch {
            // A finished scan may have given entries of a restored backup their videos.
            library.scanState
                .map { it.running }
                .distinctUntilChanged()
                .collect { running -> if (!running) refreshBackupInfo() }
        }
    }

    private fun assemble(
        index: LibraryIndex,
        scan: ScanState,
        settings: Settings,
        user: LocalState,
        lists: PlaylistsState,
        music: MusicLibraryData,
        saved: List<String>,
        starred: List<String>,
    ): AppUiState {
        val route = user.backStack.lastOrNull() ?: Route.Home
        val previewKey = user.previewKey
        // Details follow the route; on a tablet's library page they follow the preview pane.
        val videoId = (route as? Route.VideoDetails)?.videoId
            ?: previewKey?.takeIf { it.startsWith("v:") }?.removePrefix("v:")?.toLongOrNull()
        val showKey = (route as? Route.ShowDetails)?.showKey
            ?: previewKey?.takeIf { it.startsWith("s:") }?.removePrefix("s:")
        val browse = UiMappers.browse(index, scan, lists.all)
        val openPlaylist = lists.open
        return AppUiState(
            route = route,
            canGoBack = user.backStack.size > 1,
            home = if (saved.isEmpty() && starred.isEmpty()) {
                index.home
            } else {
                val keys = saved.toHashSet()
                val stars = starred.toHashSet()
                index.home.copy(
                    hero = index.home.hero.map { hero ->
                        hero.copy(onWatchlist = hero.itemKey in keys, favorite = hero.itemKey in stars)
                    },
                    watchlist = watchlistMemo[index to saved],
                )
            },
            library = when (user.filter) {
                LibraryFilter.WATCHLIST -> LibraryUi(filter = user.filter, sort = user.sort, items = watchlistMemo[index to saved])
                LibraryFilter.FAVORITES -> LibraryUi(filter = user.filter, sort = user.sort, items = favoritesMemo[index to starred])
                else -> libraryMemo[Triple(index, user.filter, user.sort)]
            },
            browse = browse,
            browseDir = (route as? Route.BrowseDir)?.let { browseDirFor(index, it, user.listings, scan) },
            videoDetails = videoId?.let { UiMappers.videoDetails(index, it) },
            showDetails = showKey?.let { UiMappers.showDetails(index, it) },
            previewKey = previewKey,
            playlist = openPlaylist?.let { UiMappers.playlistDetails(index, it.id, it.name, lists.openVideoIds) },
            search = searchMemo[Triple(index, user.query, music)].copy(recent = user.recentSearches),
            settings = SettingsUi(
                settings = settings,
                hasBuiltInKey = settingsStore.hasBuiltInTmdbKey,
                keyStatus = if (settingsStore.effectiveTmdbKey.isBlank()) KeyStatus.MISSING else user.keyStatus,
                versionName = BuildConfig.VERSION_NAME,
                sources = browse.sources,
                allFilesAccess = user.allFilesAccess,
                canRequestAllFilesAccess = StoragePermissions.canRequestAllFilesAccess,
            ),
            scan = UiMappers.scan(scan),
            permission = user.permission.copy(deviceScanning = settings.scanDeviceStorage),
            dialog = user.dialog,
            message = user.message,
            crashReport = user.crashReport,
        )
    }

    /** Library folders for device, picked folders and SMB; the other network sources as listed live. */
    private fun browseDirFor(
        index: LibraryIndex,
        route: Route.BrowseDir,
        listings: Map<ListingKey, RemoteListing>,
        scan: ScanState,
    ): BrowseDirUi? {
        val source = index.sourcesById[route.sourceId]
        return if (source != null && source.type in NetworkSources.TYPES) {
            remoteDirMemo[RemoteDirInput(index, source, route.path, listings)]
        } else {
            UiMappers.browseDir(index, route.sourceId, route.path, scan)
        }
    }

    // Navigation

    override fun navigate(route: Route) {
        local.update { state ->
            if (state.backStack.lastOrNull() == route) state else state.copy(backStack = state.backStack + route)
        }
    }

    override fun back() {
        local.update { state ->
            if (state.backStack.size > 1) state.copy(backStack = state.backStack.dropLast(1)) else state
        }
    }

    override fun selectTab(tab: TopDestination) {
        val root = when (tab) {
            TopDestination.VIDEO -> Route.Home
            TopDestination.AUDIO -> Route.Audio
            TopDestination.SETTINGS -> Route.Settings
            TopDestination.SEARCH -> Route.Search
        }
        // Other tabs sit on top of Home, so system back from a tab's root returns Home first.
        val stack = if (root == Route.Home) listOf(Route.Home) else listOf(Route.Home, root)
        local.update { it.copy(backStack = stack) }
    }

    override fun preview(item: LibraryItemUi) {
        local.update { it.copy(previewKey = item.key) }
    }

    // Library

    override fun refresh() {
        // The refresh still scans folders and shares; it just can't see device storage.
        deviceScanBlocked()?.let(::showMessage)
        launchSafely("Couldn't scan the library") { library.refreshAll() }
    }

    override fun rescanSource(sourceId: Long) {
        if (state.value.browse.sources.any { it.id == sourceId && it.type == SourceType.DEVICE }) {
            val blocked = deviceScanBlocked()
            if (blocked != null) {
                showMessage(blocked)
                return
            }
        }
        // Refresh on a live folder lists it again as well.
        val open = local.value.backStack.lastOrNull() as? Route.BrowseDir
        if (open != null && open.sourceId == sourceId) openListing(open.sourceId, open.path, refresh = true)
        launchSafely("Couldn't scan this source") { library.scanSource(sourceId) }
    }

    override fun setLibraryFilter(filter: LibraryFilter) {
        local.update { it.copy(filter = filter) }
    }

    override fun setLibrarySort(sort: LibrarySort) {
        local.update { it.copy(sort = sort) }
    }

    override fun search(query: String) {
        local.update { it.copy(query = query) }
    }

    override fun rememberSearch(query: String) {
        val recent = withRecentSearch(local.value.recentSearches, query)
        local.update { it.copy(recentSearches = recent) }
        searchPrefs.edit().putString(KEY_RECENT_SEARCHES, recent.joinToString("\n")).apply()
    }

    override fun clearRecentSearches() {
        local.update { it.copy(recentSearches = emptyList()) }
        searchPrefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    override fun changeArtwork(videoId: Long) {
        launchSafely("Couldn't load artwork") {
            val row = library.video(videoId) ?: return@launchSafely
            val meta = row.meta
            val tmdbId = meta?.key?.takeUnless { it.startsWith("server:") }?.let { LibraryKeys.tmdbId(it) }
            if (meta == null || tmdbId == null) {
                showMessage("Artwork can only be changed for titles matched with TMDB")
                return@launchSafely
            }
            val key = meta.key
            val type = if (key.startsWith("tv:")) "tv" else "movie"
            val choice = graph.database.metadataDao().artworkChoices().firstOrNull { it.metadataKey == key }
            val opened = DialogUi.ChangeArtwork(
                metadataKey = key,
                title = meta.title,
                poster = choice?.posterPath ?: meta.posterPath,
                backdrop = choice?.backdropPath ?: meta.backdropPath,
                customised = choice != null,
            )
            showDialog(opened)
            val loaded = try {
                val images = graph.tmdb.images(type, tmdbId)
                opened.copy(
                    loading = false,
                    posters = images.posters.sortedByDescending { it.voteAverage }.map { it.filePath }.filter { it.isNotBlank() }.distinct().take(ARTWORK_LIMIT),
                    backdrops = images.backdrops.sortedByDescending { it.voteAverage }.map { it.filePath }.filter { it.isNotBlank() }.distinct().take(ARTWORK_LIMIT),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "TMDB images failed", e)
                opened.copy(loading = false, error = "Couldn't reach TMDB. Check the connection and try again.")
            }
            local.update { state ->
                val shown = state.dialog as? DialogUi.ChangeArtwork
                if (shown != null && shown.metadataKey == key) state.copy(dialog = loaded.copy(poster = shown.poster, backdrop = shown.backdrop, customised = shown.customised)) else state
            }
        }
    }

    override fun chooseArtwork(metadataKey: String, posterPath: String?, backdropPath: String?) {
        launchSafely("Couldn't change the artwork") {
            val dao = graph.database.metadataDao()
            val old = dao.artworkChoices().firstOrNull { it.metadataKey == metadataKey }
            dao.setArtworkChoice(
                ArtworkChoiceEntity(
                    metadataKey = metadataKey,
                    posterPath = posterPath ?: old?.posterPath,
                    backdropPath = backdropPath ?: old?.backdropPath,
                ),
            )
            local.update { state ->
                val shown = state.dialog as? DialogUi.ChangeArtwork ?: return@update state
                if (shown.metadataKey != metadataKey) return@update state
                state.copy(
                    dialog = shown.copy(
                        poster = posterPath ?: shown.poster,
                        backdrop = backdropPath ?: shown.backdrop,
                        customised = true,
                    ),
                )
            }
        }
    }

    override fun resetArtwork(metadataKey: String) {
        launchSafely("Couldn't change the artwork") {
            graph.database.metadataDao().clearArtworkChoice(metadataKey)
            dismissDialog()
            showMessage("Back to TMDB's artwork")
        }
    }

    override fun toggleFavorite(itemKey: String) {
        val on = itemKey in videoFavorites.value
        launchSafely("Couldn't change your favorites") { graph.music.setVideoFavorite(itemKey, favorite = !on) }
    }

    override fun toggleWatchlist(itemKey: String) {
        val on = itemKey in watchlist.value
        launchSafely("Couldn't change the watchlist") {
            if (on) graph.music.removeFromWatchlist(itemKey) else graph.music.addToWatchlist(itemKey)
        }
    }

    override fun playMusic(songIds: List<Long>, startIndex: Int) {
        if (songIds.isNotEmpty()) graph.musicPlayer.playSongs(songIds, startIndex)
    }

    override fun setWatched(videoId: Long, watched: Boolean) {
        launchSafely("Couldn't update the video") { library.setWatched(videoId, watched) }
    }

    /** Called by MainActivity once the picked folder's permission is persisted. */
    fun addFolder(treeUri: String, displayName: String) {
        launchSafely("Couldn't add the folder") {
            library.addFolder(treeUri, displayName)
            showMessage("Added $displayName — scanning")
        }
    }

    override fun removeSource(sourceId: Long) {
        val name = (local.value.dialog as? DialogUi.ConfirmRemoveSource)?.source?.takeIf { it.id == sourceId }?.name
        launchSafely("Couldn't remove the source") {
            library.removeSource(sourceId)
            forgetListings(sourceId, relist = false)
            local.update { state ->
                // Folders of a removed source have nothing left to show.
                val stack = state.backStack.filterNot { it is Route.BrowseDir && it.sourceId == sourceId }
                state.copy(
                    backStack = stack.ifEmpty { listOf(Route.Home) },
                    dialog = state.dialog.takeUnless { it is DialogUi.ConfirmRemoveSource },
                    message = if (name != null) "Removed $name" else "Source removed",
                )
            }
        }
    }

    // Dialogs and messages

    override fun showDialog(dialog: DialogUi?) {
        if (dialog !is DialogUi.Identify) identifyJob?.cancel()
        if (dialog !is DialogUi.SmbServer) smbTestJob?.cancel()
        if (dialog !is DialogUi.NetworkServer) {
            networkTestJob?.cancel()
            plexJob?.cancel()
            plexAccount = null
        }
        if (dialog !is DialogUi.UpnpServers) upnpJob?.cancel()
        if (dialog !is DialogUi.LibraryDiagnostics) diagnosticsJob?.cancel()
        local.update { it.copy(dialog = dialog) }
    }

    fun showMessage(text: String) {
        local.update { it.copy(message = text) }
    }

    // Deleting a video (MainActivity does the deleting, as Android's prompt needs the activity)

    /** What deleting a video needs: its file, whether MediaStore holds it, and a name to show. */
    class DeleteTarget(val uri: String, val mediaStore: Boolean, val title: String)

    suspend fun deleteTarget(videoId: Long): DeleteTarget? {
        val row = library.video(videoId) ?: return null
        val uri = row.video.uri.takeIf { it.startsWith("content://") } ?: return null
        val title = row.meta?.title?.takeIf { it.isNotBlank() } ?: row.video.parsedTitle
        return DeleteTarget(uri, mediaStore = uri.startsWith("content://media/"), title = title)
    }

    /** The file is gone (or in the bin): the video leaves the library and its page closes. */
    fun onVideoDeleted(videoId: Long, title: String, toBin: Boolean) {
        launchSafely("Couldn't update the library") {
            library.forgetDeletedVideo(videoId)
            local.update { state ->
                val stack = state.backStack.filterNot { it is Route.VideoDetails && it.videoId == videoId }
                state.copy(
                    backStack = stack.ifEmpty { listOf(Route.Home) },
                    dialog = state.dialog.takeUnless { it is DialogUi.ConfirmDeleteVideo },
                    message = if (toBin) "Moved $title to Recently Deleted" else "Deleted $title",
                )
            }
        }
    }

    /** A video came back from the bin: device storage is scanned so it shows again. */
    fun onVideoRestored() {
        showMessage("Restored. It shows again once the library updates.")
        refresh()
    }

    override fun consumeMessage() {
        local.update { it.copy(message = null) }
    }

    // Crash report (MainActivity overrides shareCrashReport with the share sheet)

    override fun shareCrashReport() {}

    override fun dismissCrashReport() {
        CrashReporter.clear(getApplication())
        local.update { it.copy(crashReport = null) }
    }

    override fun addStream(title: String, url: String) {
        val link = url.trim()
        if (!InputRules.isStreamUrl(link)) {
            showMessage(InputRules.STREAM_URL_ERROR)
            return
        }
        launchSafely("Couldn't save the stream") {
            library.addStream(title.trim().ifEmpty { null }, link)
            local.update { state ->
                state.copy(dialog = state.dialog.takeUnless { it is DialogUi.AddStream }, message = "Saved to Streams")
            }
        }
    }

    // Network shares

    override fun saveSmb(form: SmbForm) {
        val clean = InputRules.normalizeSmb(form)
        val invalid = InputRules.smbFormError(clean)
        if (invalid != null) {
            updateSmbDialog { it.copy(testing = false, testResult = invalid) }
            return
        }
        viewModelScope.launch {
            try {
                library.saveSmbServer(
                    id = clean.id,
                    name = clean.name,
                    host = clean.host,
                    port = InputRules.smbPort(clean),
                    share = clean.share,
                    path = clean.path,
                    username = clean.username.ifEmpty { null },
                    password = clean.password.ifEmpty { null },
                    domain = clean.domain.ifEmpty { null },
                )
                val name = clean.name.ifEmpty { "${clean.host}/${clean.share}" }
                smbTestJob?.cancel()
                local.update { state ->
                    state.copy(
                        dialog = state.dialog.takeUnless { it is DialogUi.SmbServer },
                        message = if (clean.id == null) "Added $name — scanning" else "Saved $name — scanning",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Saving the network share failed", e)
                updateSmbDialog { it.copy(testing = false, testResult = e.message.nonBlank() ?: "Couldn't save the share") }
            }
        }
    }

    override fun testSmb(form: SmbForm) {
        val clean = InputRules.normalizeSmb(form)
        val invalid = InputRules.smbFormError(clean)
        if (invalid != null) {
            updateSmbDialog { it.copy(testing = false, testResult = invalid) }
            return
        }
        smbTestJob?.cancel()
        updateSmbDialog { it.copy(testing = true, testResult = null) }
        smbTestJob = viewModelScope.launch {
            val result = try {
                graph.smb.test(
                    host = clean.host,
                    port = InputRules.smbPort(clean),
                    share = clean.share,
                    path = clean.path,
                    credentials = SmbCredentials(
                        username = clean.username.ifEmpty { null },
                        password = clean.password.ifEmpty { null },
                        domain = clean.domain.ifEmpty { null },
                    ),
                ).fold(
                    onSuccess = { "" },
                    onFailure = { it.message.nonBlank() ?: "Couldn't connect" },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Testing the network share failed", e)
                e.message.nonBlank() ?: "Couldn't connect"
            }
            updateSmbDialog { it.copy(testing = false, testResult = result) }
        }
    }

    override fun editSource(source: SourceUi) {
        launchSafely("Couldn't open the source") {
            val entity = library.source(source.id)
            when {
                entity == null -> showMessage("This source is gone")
                entity.type == SourceType.SMB -> showDialog(DialogUi.SmbServer(form = smbForm(entity)))
                entity.type in NetworkSources.TYPES -> showDialog(DialogUi.NetworkServer(form = NetworkForms.formFor(entity)))
                else -> showMessage("Only network sources can be edited")
            }
        }
    }

    private fun smbForm(entity: SourceEntity): SmbForm {
        val location = SmbUris.parse(entity.location)
        return SmbForm(
            id = entity.id,
            name = entity.name,
            host = location?.host.orEmpty(),
            port = location?.port?.toString().orEmpty(),
            share = location?.share.orEmpty(),
            path = location?.path.orEmpty(),
            username = entity.username.orEmpty(),
            password = entity.password.orEmpty(),
            domain = entity.domain.orEmpty(),
        )
    }

    private fun updateSmbDialog(transform: (DialogUi.SmbServer) -> DialogUi.SmbServer) {
        local.update { state ->
            val dialog = state.dialog as? DialogUi.SmbServer ?: return@update state
            state.copy(dialog = transform(dialog))
        }
    }

    // Network sources other than SMB: FTP, SFTP, NFS, WebDAV, DLNA/UPnP, Jellyfin, Emby, Plex

    override fun chooseSourceType(type: SourceType) {
        when (type) {
            SourceType.SMB -> showDialog(DialogUi.SmbServer())
            SourceType.UPNP -> discoverUpnpServers()
            SourceType.FTP, SourceType.SFTP, SourceType.NFS, SourceType.WEBDAV, SourceType.JELLYFIN, SourceType.EMBY,
            SourceType.PLEX,
            -> showDialog(DialogUi.NetworkServer(form = NetworkSourceForm(type = type)))
            SourceType.STREAM -> showDialog(DialogUi.AddStream)
            // Not network sources: the picker doesn't offer them.
            SourceType.DEVICE, SourceType.FOLDER -> showDialog(null)
        }
    }

    override fun testNetworkSource(form: NetworkSourceForm) {
        val (_, source) = checkedNetworkSource(form) ?: return
        val browser = graph.browsers[form.type]
        if (browser == null) {
            updateNetworkDialog { it.copy(testing = false, testResult = "${NetworkForms.typeLabel(form.type)} sources can't be checked") }
            return
        }
        val listsLibraries = form.type == SourceType.JELLYFIN || form.type == SourceType.EMBY
        networkTestJob?.cancel()
        updateNetworkDialog { it.copy(testing = true, testResult = null) }
        networkTestJob = viewModelScope.launch {
            var libraries: List<ServerLibraryUi>? = null
            val outcome = try {
                val target = withStoredSignIn(source, form.id)
                val failure = browser.test(target).exceptionOrNull()
                if (failure == null && listsLibraries) {
                    // libraryIds null: every library a scan could take, not just the ones chosen before.
                    libraries = browser.list(target.copy(libraryIds = null), "")
                        .filter { it.isDirectory }
                        .map { ServerLibraryUi(id = it.path, name = it.name.ifBlank { it.path }) }
                        .distinctBy { it.id }
                }
                if (failure == null) "" else userText(failure, "Couldn't connect")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logNetwork("Testing the server failed", e)
                userText(e, "Couldn't connect")
            }
            updateNetworkDialog { it.copy(testing = false, testResult = outcome, libraries = libraries) }
        }
    }

    override fun saveNetworkSource(form: NetworkSourceForm) {
        val (input, source) = checkedNetworkSource(form) ?: return
        networkTestJob?.cancel()
        saveNetworkInput(form.id, input, source.name)
    }

    override fun discoverUpnpServers() {
        upnpJob?.cancel()
        showDialog(DialogUi.UpnpServers(searching = true))
        upnpJob = viewModelScope.launch {
            val outcome = try {
                Result.success(FileServerSources.discoverUpnpServers(graph))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logNetwork("Looking for DLNA servers failed", e)
                Result.failure(e)
            }
            updateUpnpDialog { dialog ->
                outcome.fold(
                    onSuccess = { found ->
                        dialog.copy(
                            searching = false,
                            servers = found
                                .distinctBy { it.path }
                                .map { UpnpServerUi(name = it.name.ifBlank { "DLNA server" }, address = it.path, iconUrl = it.imageUrl) },
                            error = null,
                        )
                    },
                    onFailure = { dialog.copy(searching = false, error = userText(it, "Couldn't search this network")) },
                )
            }
        }
    }

    override fun addUpnpServer(server: UpnpServerUi) {
        val input = NetworkSourceInput(type = SourceType.UPNP, name = server.name, address = server.address)
        val name = try {
            NetworkSources.toSource(input).name
        } catch (e: IllegalArgumentException) {
            showMessage(userText(e, "Couldn't add ${server.name}"))
            return
        }
        saveNetworkInput(null, input, name)
    }

    override fun startPlexSignIn() {
        plexJob?.cancel()
        plexAccount = null
        updateNetworkDialog { it.copy(plexSignIn = PlexSignInUi(linkUrl = plexSignIn.linkUrl)) }
        plexJob = viewModelScope.launch {
            try {
                val pin = plexSignIn.createCode()
                updatePlexSignIn { it.copy(code = pin.code) }
                val token = plexSignIn.awaitToken(pin)
                val servers = plexSignIn.servers(token)
                plexAccount = PlexAccount(token, servers)
                updatePlexSignIn { it.copy(servers = servers.map(::plexServerUi)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logNetwork("Signing in to Plex failed", e)
                updatePlexSignIn { it.copy(error = userText(e, "Couldn't sign in to Plex")) }
            }
        }
    }

    override fun cancelPlexSignIn() {
        plexJob?.cancel()
        plexAccount = null
        updateNetworkDialog { it.copy(plexSignIn = null) }
    }

    override fun pickPlexServer(serverId: String, form: NetworkSourceForm) {
        val account = plexAccount ?: return
        val server = account.servers.firstOrNull { it.clientIdentifier == serverId } ?: return
        // A failure with another server (or an earlier try) says nothing about this one.
        updatePlexSignIn { it.copy(error = null) }
        val input = plexSignIn.sourceInput(server, account.token, form.name)
        if (input == null) {
            updatePlexSignIn { it.copy(error = "plex.tv lists no address for ${server.name.nonBlank() ?: "this server"}") }
            return
        }
        val name = try {
            NetworkSources.toSource(input).name
        } catch (e: IllegalArgumentException) {
            updatePlexSignIn { it.copy(error = userText(e, "Couldn't add this server")) }
            return
        }
        saveNetworkInput(form.id, input, name)
    }

    /** [form] as a source, checked; null (the problem shown in the dialog) when it can't be one. */
    private fun checkedNetworkSource(form: NetworkSourceForm): Pair<NetworkSourceInput, SourceEntity>? =
        try {
            val input = NetworkForms.toInput(form)
            input to NetworkSources.toSource(input)
        } catch (e: IllegalArgumentException) {
            updateNetworkDialog { it.copy(testing = false, testResult = userText(e, "Check the server address")) }
            null
        }

    /** An edit that keeps the server and the user keeps the stored sign-in too, so a test needn't sign in again. */
    private suspend fun withStoredSignIn(source: SourceEntity, id: Long?): SourceEntity {
        val stored = id?.let { library.source(it) } ?: return source
        if (stored.type != source.type || stored.location != source.location || stored.username != source.username) return source
        return source.copy(
            token = source.token ?: stored.token,
            userId = source.userId ?: stored.userId,
            serverId = source.serverId ?: stored.serverId,
        )
    }

    private fun saveNetworkInput(id: Long?, input: NetworkSourceInput, name: String) {
        viewModelScope.launch {
            try {
                val savedId = library.saveNetworkSource(id, input)
                forgetListings(savedId, relist = true)
                plexJob?.cancel()
                plexAccount = null
                local.update { state ->
                    state.copy(
                        dialog = state.dialog.takeUnless { it is DialogUi.NetworkServer || it is DialogUi.UpnpServers },
                        message = if (id == null) "Added $name — scanning" else "Saved $name — scanning",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logNetwork("Saving the server failed", e)
                val text = userText(e, "Couldn't save the server")
                val dialog = local.value.dialog
                when {
                    dialog is DialogUi.NetworkServer && dialog.plexSignIn?.servers != null ->
                        updatePlexSignIn { it.copy(error = text) }
                    dialog is DialogUi.NetworkServer -> updateNetworkDialog { it.copy(testing = false, testResult = text) }
                    dialog is DialogUi.UpnpServers -> updateUpnpDialog { it.copy(error = text) }
                    else -> showMessage(text)
                }
            }
        }
    }

    private fun plexServerUi(server: PlexResource): PlexServerUi {
        val address = PlexSignIn.address(server)
        val host = address?.let { NetworkLocation.parse(it)?.host }
        return PlexServerUi(
            id = server.clientIdentifier,
            name = server.name.nonBlank() ?: "Plex server",
            detail = joinMeta(if (server.owned) "Your server" else "Shared with you", host ?: "No address on plex.tv").orEmpty(),
            reachable = address != null,
        )
    }

    private fun updateNetworkDialog(transform: (DialogUi.NetworkServer) -> DialogUi.NetworkServer) {
        local.update { state ->
            val dialog = state.dialog as? DialogUi.NetworkServer ?: return@update state
            state.copy(dialog = transform(dialog))
        }
    }

    private fun updatePlexSignIn(transform: (PlexSignInUi) -> PlexSignInUi) {
        updateNetworkDialog { dialog ->
            val signIn = dialog.plexSignIn ?: return@updateNetworkDialog dialog
            dialog.copy(plexSignIn = transform(signIn))
        }
    }

    private fun updateUpnpDialog(transform: (DialogUi.UpnpServers) -> DialogUi.UpnpServers) {
        local.update { state ->
            val dialog = state.dialog as? DialogUi.UpnpServers ?: return@update state
            state.copy(dialog = transform(dialog))
        }
    }

    /** A server's error for people: its message without access tokens (servers may quote URLs). */
    private fun userText(error: Throwable, fallback: String): String = ServerSecrets.redact(error.message).nonBlank() ?: fallback

    /** Server errors may quote URLs with access tokens: logged scrubbed and without the stack trace. */
    private fun logNetwork(what: String, error: Throwable) {
        Log.w(TAG, "$what: ${error.javaClass.simpleName}: ${ServerSecrets.redact(error.message)}")
    }

    // Live folders of network sources (Browse)

    /**
     * Lists folder [path] of source [sourceId] with its NetworkBrowser, unless that listing is
     * there already (or on its way) and no [refresh] is asked for. Sources browsed from the
     * library (device, folders, SMB) are left alone.
     */
    private fun openListing(sourceId: Long, path: String, refresh: Boolean) {
        val key = ListingKey(sourceId, path)
        val known = local.value.listings[key]
        if (!refresh && known != null && (known.loading || known.error == null)) return
        listingJobs.remove(key)?.cancel()
        // Started lazily so it is in listingJobs before its body runs (and removes it) on the main thread.
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                listFolder(key)
            } finally {
                if (listingJobs[key] === coroutineContext[Job]) listingJobs.remove(key)
            }
        }
        listingJobs[key] = job
        job.start()
    }

    private suspend fun listFolder(key: ListingKey) {
        val source = try {
            library.source(key.sourceId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reading source ${key.sourceId} failed", e)
            null
        }
        if (source == null || source.type !in NetworkSources.TYPES) return
        updateListing(key) { it.copy(loading = true, error = null) }
        val outcome = try {
            val browser = graph.browsers[source.type]
                ?: throw IOException("${NetworkForms.typeLabel(source.type)} sources can't be browsed")
            Result.success(browser.list(source, key.path))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logNetwork("Listing a folder failed", e)
            Result.failure(e)
        }
        updateListing(key) { listing ->
            outcome.fold(
                onSuccess = { entries -> RemoteListing(entries = entries) },
                onFailure = { listing.copy(loading = false, error = userText(it, "Couldn't open this folder")) },
            )
        }
    }

    private fun updateListing(key: ListingKey, transform: (RemoteListing) -> RemoteListing) {
        local.update { state ->
            val listings = LinkedHashMap(state.listings)
            // Taken out and put back: the most recently updated listing goes last, the oldest is dropped first.
            listings[key] = transform(listings.remove(key) ?: RemoteListing())
            // The folder on screen stays, however old: nothing would list it again while it is open.
            val open = (state.backStack.lastOrNull() as? Route.BrowseDir)?.let { ListingKey(it.sourceId, it.path) }
            while (listings.size > MAX_LISTINGS) {
                val oldest = listings.keys.firstOrNull { it != open } ?: break
                listings.remove(oldest)
            }
            state.copy(listings = listings)
        }
    }

    /**
     * A removed or edited source's folders are listed afresh when opened again; with [relist], the
     * one on screen right away.
     */
    private fun forgetListings(sourceId: Long, relist: Boolean) {
        listingJobs.keys.filter { it.sourceId == sourceId }.forEach { key -> listingJobs.remove(key)?.cancel() }
        local.update { state -> state.copy(listings = state.listings.filterKeys { it.sourceId != sourceId }) }
        val open = local.value.backStack.lastOrNull() as? Route.BrowseDir
        if (relist && open != null && open.sourceId == sourceId) openListing(open.sourceId, open.path, refresh = true)
    }

    // Identify

    override fun identify(videoId: Long) {
        launchSafely("Couldn't open Identify") {
            val video = library.video(videoId)?.video ?: return@launchSafely
            val query = listOfNotNull(video.parsedTitle.trim().ifEmpty { null }, video.parsedYear?.toString())
                .joinToString(" ")
            val tv = video.kind == VideoKind.EPISODE
            showDialog(DialogUi.Identify(videoId = videoId, query = query, tv = tv))
            identifySearch(query, tv)
        }
    }

    override fun identifySearch(query: String, tv: Boolean) {
        val text = query.trim()
        identifyJob?.cancel()
        if (text.isEmpty()) {
            updateIdentifyDialog { it.copy(query = query, tv = tv, results = emptyList(), loading = false, error = null) }
            return
        }
        updateIdentifyDialog { it.copy(query = query, tv = tv, loading = true, error = null) }
        identifyJob = viewModelScope.launch {
            val type = if (tv) IdentifyType.TV else IdentifyType.MOVIE
            val outcome = try {
                var found = library.searchTmdb(text, type)
                // TMDB matches words, so "Title 2010" can come back empty where "Title" does not.
                val withoutYear = text.replace(TRAILING_YEAR, "")
                if (found.isEmpty() && withoutYear.isNotEmpty() && withoutYear != text) {
                    found = library.searchTmdb(withoutYear, type)
                }
                Result.success(found)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "TMDB search failed", e)
                Result.failure(e)
            }
            updateIdentifyDialog { dialog ->
                outcome.fold(
                    onSuccess = { found ->
                        dialog.copy(
                            results = found.map { it.toUi() },
                            loading = false,
                            error = if (found.isEmpty()) "Nothing on TMDB matches “$text”" else null,
                        )
                    },
                    onFailure = { dialog.copy(results = emptyList(), loading = false, error = tmdbErrorText(it)) },
                )
            }
        }
    }

    override fun identifyPick(videoId: Long, result: IdentifyResultUi) {
        identifyJob?.cancel()
        updateIdentifyDialog { it.copy(loading = true, error = null) }
        identifyJob = viewModelScope.launch {
            try {
                library.identify(videoId, result.toSearchResult())
                local.update { state ->
                    state.copy(
                        dialog = state.dialog.takeUnless { it is DialogUi.Identify },
                        message = "Matched to ${result.title}",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Identify failed", e)
                val text = tmdbErrorText(e)
                local.update { state ->
                    val dialog = state.dialog as? DialogUi.Identify
                    if (dialog != null) {
                        state.copy(dialog = dialog.copy(loading = false, error = text))
                    } else {
                        state.copy(message = text)
                    }
                }
            }
        }
    }

    private fun updateIdentifyDialog(transform: (DialogUi.Identify) -> DialogUi.Identify) {
        local.update { state ->
            val dialog = state.dialog as? DialogUi.Identify ?: return@update state
            state.copy(dialog = transform(dialog))
        }
    }

    private fun TmdbSearchResult.toUi() = IdentifyResultUi(
        tmdbId = tmdbId,
        isTv = type == IdentifyType.TV,
        title = title,
        year = year,
        overview = overview.nonBlank(),
        posterUrl = posterUrl,
    )

    private fun IdentifyResultUi.toSearchResult() = TmdbSearchResult(
        tmdbId = tmdbId,
        type = if (isTv) IdentifyType.TV else IdentifyType.MOVIE,
        title = title,
        year = year,
        overview = overview,
        posterUrl = posterUrl,
    )

    private fun tmdbErrorText(error: Throwable): String = when {
        error is TmdbDisabledException -> "Add a TMDB API key in Settings to search."
        error is TmdbException && error.code == 401 -> "TMDB didn't accept the API key. Check it in Settings."
        error is TmdbException -> error.message.nonBlank() ?: "TMDB error ${error.code}"
        error is IOException -> "Couldn't reach TMDB. Check your connection, or try Secure DNS or api.tmdb.org in Settings."
        else -> error.message.nonBlank() ?: "Something went wrong"
    }

    // Settings

    override fun updateSettings(transform: (Settings) -> Settings) {
        settingsStore.update(transform)
    }

    override fun checkTmdbKey() {
        keyCheckJob?.cancel()
        if (settingsStore.effectiveTmdbKey.isBlank()) {
            local.update { it.copy(keyStatus = KeyStatus.MISSING) }
            return
        }
        local.update { it.copy(keyStatus = KeyStatus.CHECKING) }
        keyCheckJob = viewModelScope.launch {
            val status = try {
                graph.tmdb.testCredentials().fold(onSuccess = { KeyStatus.VALID }, onFailure = ::keyStatusFor)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                keyStatusFor(e)
            }
            local.update { it.copy(keyStatus = status) }
        }
    }

    private fun keyStatusFor(error: Throwable): KeyStatus = when {
        error is TmdbDisabledException -> KeyStatus.MISSING
        error is TmdbException && error.code == 401 -> KeyStatus.INVALID
        else -> KeyStatus.UNREACHABLE
    }

    override fun clearMetadata() {
        launchSafely("Couldn't clear metadata") {
            library.clearMetadata()
            showMessage("Metadata cleared — fetching it again")
        }
    }

    // Permissions

    private fun currentPermission(): PermissionUi {
        val app = getApplication<Application>()
        return PermissionUi(
            granted = StoragePermissions.hasAnyVideoAccess(app),
            partial = StoragePermissions.hasPartialAccess(app),
        )
    }

    /**
     * Re-reads storage access (MainActivity calls this on resume and after a permission prompt).
     * The library rescans device storage when more of it became readable, whether the change was
     * made here or in system settings; [rescan] forces that, e.g. after the user picked more
     * videos in Android 14's limited-access picker, which does not change any permission.
     */
    fun onPermissionsChanged(rescan: Boolean = false) {
        val permission = currentPermission()
        val allFiles = StoragePermissions.hasAllFilesAccess(getApplication<Application>())
        local.update { it.copy(permission = permission, allFilesAccess = allFiles) }
        library.onStorageAccessChanged(rescan = rescan && permission.granted)
    }

    /** Why device storage can't be scanned right now; null when it can (or the user turned it off). */
    private fun deviceScanBlocked(): String? {
        if (!settingsStore.settings.value.scanDeviceStorage) return null
        if (StoragePermissions.hasAnyVideoAccess(getApplication<Application>())) return null
        return "Device storage isn't scanned: Nova has no access to videos. Allow it under Browse › Device storage."
    }

    // Library diagnostics

    override fun openLibraryDiagnostics() {
        showDialog(DialogUi.LibraryDiagnostics())
        diagnosticsJob?.cancel()
        diagnosticsJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val report = try {
                LibraryDiagnostics.collect(
                    context = app,
                    library = library,
                    settings = settingsStore.settings.value,
                    appVersion = BuildConfig.VERSION_NAME,
                    census = { DeviceScanner(app).census() },
                    music = { graph.music.diagnostics() },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Library diagnostics failed", e)
                "Library diagnostics failed: ${e.message.nonBlank() ?: e.javaClass.simpleName}"
            }
            local.update { state ->
                if (state.dialog is DialogUi.LibraryDiagnostics) state.copy(dialog = DialogUi.LibraryDiagnostics(report)) else state
            }
        }
    }

    // Queues: resolved here, started by MainActivity (see queueRequests).

    override fun playQueue(videoIds: List<Long>, startIndex: Int, shuffle: Boolean) {
        if (videoIds.isEmpty()) return
        queueStarts.trySend(QueueStart(videoIds, startIndex.coerceIn(0, videoIds.lastIndex), shuffle))
    }

    override fun playFolder(sourceId: Long, path: String, shuffle: Boolean) {
        // A live folder plays the library videos it lists, in its order (media servers' folders are no paths).
        val live = ListingKey(sourceId, path) in local.value.listings
        val listed = state.value.browseDir?.takeIf { it.sourceId == sourceId && it.path == path }?.videos?.map { it.id }
        playResolved("Couldn't play this folder", "No videos to play in this folder", shuffle) {
            if (live) listed.orEmpty() else library.folderQueue(sourceId, path)
        }
    }

    override fun playSeason(showKey: String, season: Int?, shuffle: Boolean) {
        playResolved("Couldn't play these episodes", "No episodes to play", shuffle) {
            library.seasonQueue(showKey, season)
        }
    }

    override fun playPlaylist(playlistId: Long, startIndex: Int, shuffle: Boolean) {
        playResolved("Couldn't play the playlist", "This playlist has no videos to play", shuffle, startIndex) {
            library.playlistVideos(playlistId).map { it.video.id }
        }
    }

    /** Shuffled queues start anywhere, not always on their first video. */
    private fun playResolved(
        failure: String,
        empty: String,
        shuffle: Boolean,
        startIndex: Int = 0,
        resolve: suspend () -> List<Long>,
    ) {
        launchSafely(failure) {
            val ids = resolve()
            if (ids.isEmpty()) {
                showMessage(empty)
                return@launchSafely
            }
            val start = if (shuffle) ids.indices.random() else startIndex.coerceIn(0, ids.lastIndex)
            queueStarts.send(QueueStart(ids, start, shuffle))
        }
    }

    // Playlists

    override fun addVideosToPlaylist(playlistId: Long, videoIds: List<Long>) {
        val name = playlistName(playlistId)
        launchSafely("Couldn't add to the playlist") {
            library.addToPlaylist(playlistId, videoIds)
            local.update { state ->
                state.copy(
                    dialog = state.dialog.takeUnless { it is DialogUi.AddToPlaylist },
                    message = if (name != null) "Added to $name" else "Added to the playlist",
                )
            }
        }
    }

    override fun createPlaylist(name: String, videoIds: List<Long>) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            showMessage("Give the playlist a name")
            return
        }
        launchSafely("Couldn't create the playlist") {
            val id = library.createPlaylist(clean)
            if (videoIds.isNotEmpty()) library.addToPlaylist(id, videoIds)
            local.update { state ->
                state.copy(
                    dialog = state.dialog.takeUnless { it is DialogUi.NewPlaylist || it is DialogUi.AddToPlaylist },
                    message = if (videoIds.isEmpty()) "Created $clean" else "Added to $clean",
                )
            }
        }
    }

    override fun renamePlaylist(playlistId: Long, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            showMessage("Give the playlist a name")
            return
        }
        launchSafely("Couldn't rename the playlist") {
            library.renamePlaylist(playlistId, clean)
            local.update { state -> state.copy(dialog = state.dialog.takeUnless { it is DialogUi.RenamePlaylist }) }
        }
    }

    override fun deletePlaylist(playlistId: Long) {
        val name = playlistName(playlistId)
        launchSafely("Couldn't delete the playlist") {
            library.deletePlaylist(playlistId)
            local.update { state ->
                // A deleted playlist's page has nothing left to show.
                val stack = state.backStack.filterNot { it is Route.Playlist && it.playlistId == playlistId }
                state.copy(
                    backStack = stack.ifEmpty { listOf(Route.Home) },
                    dialog = state.dialog.takeUnless { it is DialogUi.ConfirmDeletePlaylist },
                    message = if (name != null) "Deleted $name" else "Playlist deleted",
                )
            }
        }
    }

    override fun removeFromPlaylist(playlistId: Long, videoId: Long) {
        launchSafely("Couldn't remove the video") { library.removeFromPlaylist(playlistId, videoId) }
    }

    override fun movePlaylistItem(playlistId: Long, from: Int, to: Int) {
        launchSafely("Couldn't move the video") { library.movePlaylistItem(playlistId, from, to) }
    }

    private fun playlistName(playlistId: Long): String? =
        state.value.browse.playlists.firstOrNull { it.id == playlistId }?.name

    private suspend fun playlistVideoIds(playlistId: Long): List<Long> =
        try {
            library.playlistVideos(playlistId).map { it.video.id }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Loading playlist $playlistId failed", e)
            emptyList()
        }

    // Backup & restore: MainActivity runs the file pickers (exportBackup, importBackup) and hands
    // the picked files to writeBackup and readBackup.

    override fun backupState(): StateFlow<BackupUi> = backupUi

    /** Remembers the backup dialog's choice while MainActivity's "create document" picker is open. */
    fun prepareExport(includeSecrets: Boolean) {
        exportSecrets = includeSecrets
    }

    /** Writes a backup into [uri], a document the picker created. */
    fun writeBackup(uri: Uri) {
        val includeSecrets = exportSecrets
        exportSecrets = false
        val resolver = getApplication<Application>().contentResolver
        runBackupJob("Couldn't save the backup", viewModelScope) {
            val summary = withContext(Dispatchers.IO) { backups.export(openForWriting(resolver, uri), includeSecrets) }
            backup.update { it.copy(lastBackupAt = summary.createdAt) }
            showMessage(backupMessage(summary))
        }
    }

    /** Reads and checks the backup file at [uri]; [BackupUi.confirm] then asks before restoring it. */
    fun readBackup(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        runBackupJob("Couldn't read the backup", viewModelScope) {
            val read = withContext(Dispatchers.IO) {
                val input = resolver.openInputStream(uri) ?: throw IOException("The file couldn't be opened")
                input.use { backups.read(it) }
            }
            restoreCandidate = read
            backup.update { it.copy(confirm = read.summary().toPreview()) }
        }
    }

    override fun confirmRestore() {
        // The summary stays up meanwhile, so the restore can be confirmed again once the job is done.
        if (backup.value.working) {
            showMessage(BACKUP_BUSY)
            return
        }
        val candidate = restoreCandidate
        restoreCandidate = null
        backup.update { it.copy(confirm = null) }
        if (candidate == null) return
        // In the app's scope: leaving the screen must not stop a restore halfway.
        runBackupJob("Couldn't restore the backup", graph.appScope) {
            val report = backups.restore(candidate)
            backup.update { it.copy(waiting = report.waiting.total) }
            showMessage(restoreMessage(report))
            if (report.addedSourceIds.isNotEmpty()) scanRestoredSources(report.addedSourceIds)
        }
    }

    override fun cancelRestore() {
        restoreCandidate = null
        backup.update { it.copy(confirm = null) }
    }

    /** One backup job at a time; [BackupUi.working] shows it. */
    private fun runBackupJob(failure: String, scope: CoroutineScope, block: suspend () -> Unit) {
        if (backup.value.working) {
            showMessage(BACKUP_BUSY)
            return
        }
        backup.update { it.copy(working = true) }
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupFormatException) {
                showMessage(e.message.nonBlank() ?: failure)
            } catch (e: Exception) {
                Log.w(TAG, failure, e)
                showMessage(e.message.nonBlank()?.let { "$failure: $it" } ?: failure)
            } finally {
                backup.update { it.copy(working = false) }
            }
        }
    }

    /** Sources a restore added are scanned one after the other; their videos then get their history. */
    private fun scanRestoredSources(sourceIds: List<Long>) {
        graph.appScope.launch {
            for (id in sourceIds) {
                try {
                    library.scanSource(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Scanning a restored source failed", e)
                }
            }
        }
    }

    private suspend fun refreshBackupInfo() {
        try {
            val last = backups.lastBackupAt()
            val waiting = backups.waiting().total
            backup.update { it.copy(lastBackupAt = last, waiting = waiting) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reading the backup state failed", e)
        }
    }

    private fun backupMessage(summary: BackupSummary): String {
        val what = listOfNotNull(
            plural(summary.sources, "source", "sources"),
            plural(summary.videos, "video with history", "videos with history"),
            summary.songs.takeIf { it > 0 }?.let { plural(it, "song with plays", "songs with plays") },
            summary.saved.takeIf { it > 0 }?.let { plural(it, "watchlist or favorites entry", "watchlist and favorites entries") },
            plural(summary.playlists, "playlist", "playlists"),
        ).joinToString(", ")
        return if (summary.includesSecrets) {
            "Backup saved: $what. It holds your passwords, so keep it private."
        } else {
            "Backup saved: $what"
        }
    }

    private fun restoreMessage(report: RestoreReport): String {
        val restored = buildList {
            if (report.settings > 0) add("settings")
            if (report.sourcesAdded > 0) add(plural(report.sourcesAdded, "source", "sources"))
            if (report.applied.videos > 0) add("history of ${plural(report.applied.videos, "video", "videos")}")
            if (report.applied.matches > 0) add(plural(report.applied.matches, "match", "matches"))
            if (report.applied.songs > 0) add("plays of ${plural(report.applied.songs, "song", "songs")}")
            if (report.applied.saved > 0) add("your watchlist and favorites")
            if (report.playlistsCreated > 0 || report.applied.playlistItems > 0) add("playlists")
        }
        val sentences = ArrayList<String>()
        sentences += if (restored.isEmpty()) "Nothing new to restore" else "Restored ${restored.joinToString(", ")}"
        val waiting = report.waiting.total
        if (waiting == 1) sentences += "1 item waits for its video or song to be scanned"
        if (waiting > 1) sentences += "$waiting items wait for their videos and songs to be scanned"
        if (report.foldersToPick.isNotEmpty()) {
            sentences += "Add ${report.foldersToPick.joinToString(", ")} again under Folders"
        }
        if (report.matchesFailed > 0) {
            sentences += "Skipped ${plural(report.matchesFailed, "match", "matches")} TMDB no longer has"
        }
        return sentences.joinToString(". ") + "."
    }

    private fun plural(count: Int, one: String, many: String): String = if (count == 1) "1 $one" else "$count $many"

    private fun BackupSummary.toPreview() = RestorePreviewUi(
        createdAt = createdAt,
        appVersion = appVersion,
        includesSecrets = includesSecrets,
        settings = settings,
        sources = sources,
        videos = videos,
        matches = matches,
        playlists = playlists,
        songs = songs,
        saved = saved,
    )

    /** "wt" truncates what a provider hands back; not every provider knows it, "w" is the fallback. */
    private fun openForWriting(resolver: ContentResolver, uri: Uri): OutputStream {
        val truncating = try {
            resolver.openOutputStream(uri, "wt")
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
        return truncating ?: resolver.openOutputStream(uri, "w") ?: throw IOException("The file couldn't be opened")
    }

    // Activity-only actions: MainActivity overrides these.

    override fun play(videoId: Long, startOver: Boolean) {}

    override fun playUrl(url: String) {}

    override fun requestPermission() {}

    override fun requestAllFilesAccess() {}

    override fun addFolder() {}

    private fun launchSafely(failure: String, block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, failure, e)
            showMessage(e.message.nonBlank()?.let { "$failure: $it" } ?: failure)
        }
    }

    private companion object {
        const val KEY_RECENT_SEARCHES = "recent"
        val TRAILING_YEAR = Regex("""\s*\(?(19|20)\d{2}\)?$""")

        /** Live folder listings kept for going back without listing again. */
        const val MAX_LISTINGS = 40

        const val BACKUP_BUSY = "Wait for the backup or restore that is running"

        /** "Change artwork" shows TMDB's best-voted images, up to this many of each. */
        const val ARTWORK_LIMIT = 30
    }
}

/** What a live folder's screen model is built from (scan progress alone doesn't rebuild it). */
private data class RemoteDirInput(
    val index: LibraryIndex,
    val source: SourceEntity,
    val path: String,
    val listings: Map<ListingKey, RemoteListing>,
)

/** A Plex account signed in with a code: its token and the servers it can open. */
private class PlexAccount(val token: String, val servers: List<PlexResource>)

/** Every playlist, and the videos of the one the back stack shows ([open] null = none). */
private data class PlaylistsState(
    val all: List<PlaylistWithCount> = emptyList(),
    val open: PlaylistWithCount? = null,
    val openVideoIds: List<Long> = emptyList(),
)

/** Caches the last result: the combined state is rebuilt on every change, most of it unchanged. */
private class Memo<K, V>(private val compute: (K) -> V) {
    private var last: Pair<K, V>? = null

    operator fun get(key: K): V {
        val cached = last
        if (cached != null && cached.first == key) return cached.second
        return compute(key).also { last = key to it }
    }
}

/** The posters and backdrops the user picked in "Change artwork", over TMDB's defaults. */
/** The playlists, music library, watchlist and favorites, combined as one input of the UI state. */
private class Saved(
    val lists: PlaylistsState,
    val music: MusicLibraryData,
    val watchlist: List<String>,
    val favorites: List<String>,
)

private fun withArtworkChoices(rows: List<VideoWithMeta>, choices: List<ArtworkChoiceEntity>): List<VideoWithMeta> {
    if (choices.isEmpty()) return rows
    val byKey = choices.associateBy { it.metadataKey }
    return rows.map { row ->
        val meta = row.meta ?: return@map row
        val choice = byKey[meta.key] ?: return@map row
        row.copy(
            meta = meta.copy(
                posterPath = choice.posterPath ?: meta.posterPath,
                backdropPath = choice.backdropPath ?: meta.backdropPath,
                // A picked poster replaces TMDB's textless one wherever that would show.
                textlessPosterPath = choice.posterPath ?: meta.textlessPosterPath,
            ),
        )
    }
}
