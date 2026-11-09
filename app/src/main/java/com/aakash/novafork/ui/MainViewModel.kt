package com.aakash.novafork.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.text.format.DateFormat
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
import com.aakash.novafork.core.tmdb.CastMember
import com.aakash.novafork.core.tmdb.NextEpisodeLabel
import com.aakash.novafork.core.tmdb.TmdbClient
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
import com.aakash.novafork.data.backup.AutoBackup
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
import com.aakash.novafork.ui.common.FileRenames
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.BackupUi
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.model.HomeUi
import com.aakash.novafork.ui.model.UpcomingUi
import com.aakash.novafork.ui.model.ChannelsUi
import com.aakash.novafork.ui.model.BrowseDirUi
import com.aakash.novafork.ui.model.CastUi
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.IdentifyResultUi
import com.aakash.novafork.ui.model.KeyStatus
import com.aakash.novafork.ui.model.LibraryCounts
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibraryHealthUi
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.LibraryUi
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.NetworkSourceForm
import com.aakash.novafork.ui.model.PermissionUi
import com.aakash.novafork.ui.model.PlayRecord
import com.aakash.novafork.ui.model.PlexServerUi
import com.aakash.novafork.ui.model.PlexSignInUi
import com.aakash.novafork.ui.model.RestorePreviewUi
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.SearchUi
import com.aakash.novafork.ui.model.ServerLibraryUi
import com.aakash.novafork.ui.model.SettingsUi
import com.aakash.novafork.ui.model.ShowDetailsUi
import com.aakash.novafork.ui.model.SmbForm
import com.aakash.novafork.ui.model.SourceUi
import com.aakash.novafork.ui.model.SpaceUsage
import com.aakash.novafork.ui.model.StoredFile
import com.aakash.novafork.ui.model.TopDestination
import com.aakash.novafork.ui.model.UpnpServerUi
import com.aakash.novafork.ui.model.VideoDetailsUi
import com.aakash.novafork.ui.model.WatchStats
import com.aakash.novafork.ui.model.spaceByFolder
import com.aakash.novafork.ui.model.watchStats
import com.aakash.novafork.ui.model.withRecentSearch
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate
import java.util.Locale
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MainViewModel"

/** A device video for MainActivity to rename in MediaStore: [uri] is its content:// address, [newFileName] the whole new name. */
data class DeviceRename(val videoId: Long, val uri: String, val newFileName: String)

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
        /** Videos taken off the History page, each with the lastPlayedAt it was hidden at. */
        val historyHidden: Map<Long, Long> = emptyMap(),
        /** The IPTV list being shown (Route.Channels). */
        val channels: ChannelsUi? = null,
        /** Titles kept off Home ("v:<id>" / "s:<showKey>"); they stay in the library. */
        val homeHidden: Set<String> = emptySet(),
        /** The title pinned to the front of Home's hero, if any. */
        val homePinned: String? = null,
    )

    /** Search's recent searches, kept across launches (they never leave the device). */
    private val searchPrefs = app.getSharedPreferences("search_history", android.content.Context.MODE_PRIVATE)

    /** History's hidden videos (video id to when it had last been played); only the list is hidden, never a file. */
    private val historyPrefs = app.getSharedPreferences("history_hidden", android.content.Context.MODE_PRIVATE)

    /** Home's picks: titles kept off Home, and the one pinned to its front. */
    private val homePrefs = app.getSharedPreferences("home_picks", android.content.Context.MODE_PRIVATE)

    /** Hidden Videos: ids of videos kept out of the library's screens (never deleted; Browse still lists them). */
    private val hiddenPrefs = app.getSharedPreferences("library_hidden", android.content.Context.MODE_PRIVATE)
    private val hiddenVideoIds = MutableStateFlow(
        hiddenPrefs.getStringSet(KEY_HIDDEN_VIDEOS, null).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
    )

    private val local = MutableStateFlow(
        LocalState(
            permission = currentPermission(),
            allFilesAccess = StoragePermissions.hasAllFilesAccess(app),
            crashReport = CrashReporter.pending(app),
            recentSearches = searchPrefs.getString(KEY_RECENT_SEARCHES, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            historyHidden = readHistoryHidden(),
            homeHidden = homePrefs.getStringSet(HOME_HIDDEN_KEY, null).orEmpty().toSet(),
            homePinned = homePrefs.getString(HOME_PINNED_KEY, null),
            // Preferences › Appearance › Open On: the tab the app starts on.
            backStack = when (settingsStore.settings.value.startTab) {
                "AUDIO" -> listOf(Route.Home, Route.Audio)
                "SEARCH" -> listOf(Route.Home, Route.Search)
                else -> listOf(Route.Home)
            },
        ),
    )

    /** Every folder with videos and how many (hidden folders too), for Preferences › Hidden Folders. */
    @Volatile
    private var allVideoFolders: List<Pair<String, Int>> = emptyList()

    private val libraryIndex = combine(
        library.observeVideos(),
        library.observeSources(),
        settingsStore.settings.map { it.preferLocalArtwork to it.hiddenFolders }.distinctUntilChanged(),
        graph.database.metadataDao().observeArtworkChoices(),
        combine(hiddenVideoIds, graph.titleOverrides.titles) { hidden, titles -> hidden to titles },
    ) { videos, sources, (preferLocal, hiddenFolders), choices, (hidden, titles) ->
        allVideoFolders = videos.groupingBy { it.video.folder }.eachCount().toList().sortedBy { it.first.lowercase() }
        // Hidden Folders: their videos stay out of the library's screens (Browse still lists them).
        val folders = hiddenFolders.split('\n').filter { it.isNotBlank() }.toSet()
        val shown = if (folders.isEmpty()) videos else videos.filter { it.video.folder !in folders }
        LibraryIndex.build(withArtworkChoices(shown, choices), sources, preferLocal, hidden, titles)
    }

    // Only touched from the state combine below, which never runs concurrently with itself.
    private val libraryMemo = Memo<Triple<LibraryIndex, LibraryFilter, LibrarySort>, LibraryUi> { (index, filter, sort) ->
        UiMappers.library(index, filter, sort)
    }
    /** Preferences' Storage and Watch Stats, sums over the whole library; worked out only on a settings page. */
    private val statsMemo = Memo<Pair<LibraryIndex, MusicLibraryData>, Pair<SpaceUsage, WatchStats>> { (index, music) ->
        val names = index.sources.associate { it.id to it.name }
        val files = index.allVideos.map { StoredFile(it.video.sourceId, it.video.folder, it.video.sizeBytes) } +
            music.songs.map { StoredFile(it.sourceId, it.folder, it.sizeBytes) }
        val records = index.allVideos.map {
            PlayRecord(
                isMovie = it.kind == MediaKind.MOVIE,
                isEpisode = it.kind == MediaKind.EPISODE,
                showKey = it.showKey,
                showTitle = it.showTitle,
                durationMs = it.video.durationMs,
                positionMs = it.video.positionMs,
                watched = it.video.watched,
                playCount = it.video.playCount,
                lastPlayedAt = it.video.lastPlayedAt,
            )
        }
        spaceByFolder(files, names) to watchStats(records)
    }
    /** Preferences' "Duplicates use …" note; worked out only on a settings page. */
    private val duplicateBytesMemo = Memo<LibraryIndex, Long> { index -> UiMappers.duplicateBytes(index) }
    private val searchMemo = Memo<Triple<LibraryIndex, String, MusicLibraryData>, SearchUi> { (index, query, music) ->
        UiMappers.search(index, query, music)
    }

    /** The library as last shown, for what a long-press menu saves (an episode saves its show). */
    @Volatile private var latestIndex: LibraryIndex? = null

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

    private val duplicatesMemo = Memo<LibraryIndex, List<LibraryItemUi>> { index -> UiMappers.duplicates(index) }

    /** The music library, for search (one search over videos and music). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val musicLibrary: Flow<MusicLibraryData> = graph.music.observeLibrary()
        // Home first: the music library is read a moment after start (the Audio tab has its own).
        .onStart {
            emit(MusicLibraryData())
            delay(MUSIC_LOAD_DELAY_MS)
        }
        .transformLatest { music ->
            // Lyrics are read after start-up settles, then kept, so search can find a sung line.
            emit(music.copy(lyrics = lastLyrics))
            val withLyrics = music.songs.filter { it.lyricsUri != null }
            kotlinx.coroutines.delay(if (lastLyrics.isEmpty()) LYRICS_FIRST_INDEX_MS else LYRICS_REINDEX_MS)
            val lyrics = graph.music.lyricsText(withLyrics)
            if (lyrics != lastLyrics) {
                lastLyrics = lyrics
                emit(music.copy(lyrics = lyrics))
            }
        }

    @Volatile private var lastLyrics: Map<Long, List<String>> = emptyMap()
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
        // The last input only makes the page rebuild when a rating or air date arrives (see TitleExtras).
        combine(playlists, musicLibrary, watchlist, videoFavorites, graph.titleExtras.changes) { lists, music, saved, starred, _ ->
            Saved(lists, music, saved, starred)
        },
    ) { index, scan, settings, user, extra ->
        assemble(index, scan, settings, user, extra.lists, extra.music, extra.watchlist, extra.favorites)
    }
        .flowOn(Dispatchers.Default)
        // Seeded with the real settings so the first frame already has the right theme and layout,
        // and with the last Home page, so the app opens on something at once (see HomeSnapshot).
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialState())

    private fun initialState(): AppUiState {
        val settings = SettingsUi(settings = settingsStore.settings.value)
        val cached = HomeSnapshot.read(getApplication<Application>().filesDir)
        return if (cached != null) {
            AppUiState(libraryLoaded = true, home = cached, settings = settings)
        } else {
            AppUiState(settings = settings)
        }
    }

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
        BackupManager(
            getApplication<Application>(),
            graph.database,
            settingsStore,
            library,
            BuildConfig.VERSION_NAME,
            titleOverrides = graph.titleOverrides,
        )
    }
    private val autoBackup: AutoBackup by lazy { AutoBackup(getApplication<Application>(), backups) }
    private val backup = MutableStateFlow(BackupUi())
    private val backupUi: StateFlow<BackupUi> = backup.asStateFlow()

    /** "Include passwords and sign-ins" of the backup being saved, from the dialog until the picker returns. */
    private var exportSecrets = false

    /** The backup file read and checked, waiting for the user's go ([BackupUi.confirm]). Memory only. */
    private var restoreCandidate: NovaBackup? = null

    init {
        launchSafely("Couldn't scan the library") { library.refreshAll() }
        // Weekly automatic backup: on start, if the last one is a week old, write one quietly.
        graph.appScope.launch {
            try {
                withContext(Dispatchers.IO) { publishAutoBackup() }
                if (autoBackup.runIfDue() != null) withContext(Dispatchers.IO) { publishAutoBackup() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Automatic backup check failed", e)
            }
        }
        viewModelScope.launch {
            // Keep the latest Home on disk for the next start (a few seconds after it settles).
            state.map { it.home }.distinctUntilChanged().collectLatest { home ->
                delay(HOME_SNAPSHOT_DELAY_MS)
                withContext(Dispatchers.IO) { HomeSnapshot.write(getApplication<Application>().filesDir, home) }
            }
        }
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
        latestIndex = index
        val route = user.backStack.lastOrNull() ?: Route.Home
        val previewKey = user.previewKey
        // Details follow the route; on a tablet's library page they follow the preview pane.
        val videoId = (route as? Route.VideoDetails)?.videoId
            ?: previewKey?.takeIf { it.startsWith("v:") }?.removePrefix("v:")?.toLongOrNull()
        val showKey = (route as? Route.ShowDetails)?.showKey
            ?: previewKey?.takeIf { it.startsWith("s:") }?.removePrefix("s:")
        val browse = UiMappers.browse(index, scan, lists.all)
        val stats = if (route == Route.Settings || route is Route.SettingsPage) statsMemo[index to music] else null
        val openPlaylist = lists.open
        val baseHome = withHomePicks(index, index.home, user.homeHidden, user.homePinned)
        val home = if (saved.isEmpty() && starred.isEmpty()) {
            baseHome
        } else {
            val keys = saved.toHashSet()
            val stars = starred.toHashSet()
            baseHome.copy(
                hero = baseHome.hero.map { hero ->
                    hero.copy(onWatchlist = hero.itemKey in keys, favorite = hero.itemKey in stars)
                },
                watchlist = watchlistMemo[index to saved],
            )
        }
        return AppUiState(
            libraryLoaded = true,
            route = route,
            canGoBack = user.backStack.size > 1,
            stackDepth = user.backStack.size,
            previousRoute = user.backStack.getOrNull(user.backStack.size - 2),
            home = home.copy(
                continueWatching = withSeenNewDots(index, route, home.continueWatching),
                libraryCounts = LibraryCounts(
                    favorites = if (starred.isEmpty()) 0 else favoritesMemo[index to starred].size,
                    all = index.movies.size + index.shows.size + index.otherVideos.size,
                ),
            ),
            library = when (user.filter) {
                LibraryFilter.WATCHLIST -> LibraryUi(filter = user.filter, sort = user.sort, items = watchlistMemo[index to saved])
                LibraryFilter.FAVORITES -> LibraryUi(filter = user.filter, sort = user.sort, items = favoritesMemo[index to starred])
                LibraryFilter.DUPLICATES -> LibraryUi(filter = user.filter, sort = user.sort, items = duplicatesMemo[index])
                else -> libraryMemo[Triple(index, user.filter, user.sort)]
            },
            browse = browse,
            browseDir = (route as? Route.BrowseDir)?.let { browseDirFor(index, it, user.listings, scan) },
            videoDetails = videoId?.let { withStreamBadges(index, it, withAgeRating(index, it, UiMappers.videoDetails(index, it))) },
            showDetails = showKey?.let { withShowExtras(index, it, UiMappers.showDetails(index, it)) },
            previewKey = previewKey,
            history = if (route == Route.History) UiMappers.history(index, user.historyHidden) else emptyList(),
            upcoming = if (route == Route.Upcoming) upcomingOf(index) else emptyList(),
            channels = user.channels?.takeIf { route is Route.Channels && route.url == it.url },
            person = (route as? Route.Person)?.let { UiMappers.person(index, it.name) }.orEmpty(),
            health = if (route == Route.LibraryHealth) UiMappers.libraryHealth(index) else LibraryHealthUi(),
            hidden = if (route == Route.HiddenVideos) UiMappers.hiddenVideos(index, hiddenVideoIds.value) else emptyList(),
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
                space = stats?.first ?: SpaceUsage(),
                watch = stats?.second ?: WatchStats(),
                duplicateBytes = if (stats != null) duplicateBytesMemo[index] else 0L,
                videoCount = index.allVideos.size,
                songCount = music.songs.size,
                lastScanAt = index.sources.mapNotNull { it.lastScanAt }.maxOrNull(),
                videoFolders = if (stats != null) allVideoFolders else emptyList(),
            ),
            scan = UiMappers.scan(scan),
            permission = user.permission.copy(deviceScanning = settings.scanDeviceStorage),
            dialog = user.dialog,
            message = user.message,
            crashReport = user.crashReport,
        )
    }

    /** A movie's page with its cached age rating; asks TMDB for it when it isn't cached. */
    /** The "5.1" / "7.1" badge, once the video has been played and its tracks are known. */
    private fun withStreamBadges(index: LibraryIndex, videoId: Long, details: VideoDetailsUi?): VideoDetailsUi? {
        val traits = graph.streamTraits
        val languageLine = listOfNotNull(
            traits.audioLanguages(videoId)?.let { "Audio: $it" },
            traits.subtitleLanguages(videoId)?.let { "Subtitles: $it" },
        ).joinToString(" · ").ifEmpty { null }
        val withLanguages = details?.copy(languageLine = languageLine)
        val channels = traits.channels(videoId) ?: return withLanguages
        val video = index.videosById[videoId]?.video ?: return withLanguages
        return withLanguages?.copy(
            qualityBadges = com.aakash.novafork.core.format.QualityBadges.of(
                video.width,
                video.height,
                com.aakash.novafork.core.format.StreamTraits(channels = channels),
            ),
        )
    }

    private fun withAgeRating(index: LibraryIndex, videoId: Long, details: VideoDetailsUi?): VideoDetailsUi? {
        val key = index.videosById[videoId]?.takeIf { it.kind == MediaKind.MOVIE }?.meta?.key
        if (details == null || key == null) return details
        val extras = graph.titleExtras
        extras.request(key)
        return details.copy(
            ageRating = extras.certification(key),
            trailerUrl = trailerUrlOf(extras.trailerKey(key)),
            cast = castOf(extras.cast(key)),
        )
    }

    private fun trailerUrlOf(youtubeKey: String?): String? = youtubeKey?.let { "https://www.youtube.com/watch?v=$it" }

    private fun castOf(cast: List<CastMember>): List<CastUi> =
        cast.map { CastUi(it.name, it.character, TmdbClient.imageUrl(it.profilePath, "w185")) }

    /** A show's page with its cached age rating and next episode date; asks TMDB for what isn't cached. */
    private fun withShowExtras(index: LibraryIndex, showKey: String, details: ShowDetailsUi?): ShowDetailsUi? {
        val key = index.showsByKey[showKey]?.meta?.key
        if (details == null || key == null) return details
        val extras = graph.titleExtras
        extras.request(key)
        val locale = Locale.getDefault()
        return details.copy(
            ageRating = extras.certification(key),
            trailerUrl = trailerUrlOf(extras.trailerKey(key)),
            cast = castOf(extras.cast(key)),
            nextEpisodeLabel = NextEpisodeLabel.of(extras.nextAirDate(key), LocalDate.now(), locale) { skeleton ->
                DateFormat.getBestDateTimePattern(locale, skeleton)
            },
        )
    }

    /** Coming Up: shows with a next episode dated today or later (TMDB, cached), soonest first. */
    private fun upcomingOf(index: LibraryIndex): List<UpcomingUi> {
        val extras = graph.titleExtras
        val today = LocalDate.now()
        val locale = Locale.getDefault()
        val day = java.time.format.DateTimeFormatter.ofPattern("EEEE", locale)
        val short = java.time.format.DateTimeFormatter.ofPattern("d MMM", locale)
        val full = java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale)
        return index.shows.asSequence()
            .take(UPCOMING_SHOW_LIMIT)
            .mapNotNull { show ->
                val key = show.meta?.key ?: return@mapNotNull null
                extras.request(key)
                val date = extras.nextAirDate(key)?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
                    ?.takeIf { !it.isBefore(today) } ?: return@mapNotNull null
                date to show
            }
            .sortedBy { it.first }
            .map { (date, show) ->
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
                UpcomingUi(
                    showKey = show.key,
                    title = show.title,
                    artwork = show.card.artwork,
                    dayLabel = when {
                        days == 0L -> "Today"
                        days == 1L -> "Tomorrow"
                        days < 7L -> date.format(day)
                        else -> date.format(short)
                    },
                    dateLabel = date.format(full),
                )
            }
            .toList()
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

    /** Continue Watching cards whose new-episode dot was seen (their show's page was opened since). */
    private val seenNewDots: MutableSet<Long> by lazy {
        prefsOf(SEEN_DOTS_PREFS).getStringSet(SEEN_DOTS_KEY, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toMutableSet()
    }

    private fun prefsOf(name: String) = getApplication<Application>().getSharedPreferences(name, android.content.Context.MODE_PRIVATE)

    /**
     * Apple TV's blue dot goes once you've looked: opening a show's page marks the dot on its
     * Continue Watching card as seen, and it stays gone for that episode.
     */
    private fun withSeenNewDots(index: LibraryIndex, route: Route, cards: List<VideoCardUi>): List<VideoCardUi> {
        val openShow = (route as? Route.ShowDetails)?.showKey
        if (openShow != null) {
            val fresh = cards.filter { it.newEpisode && index.videosById[it.id]?.showKey == openShow && it.id !in seenNewDots }
            if (fresh.isNotEmpty()) {
                synchronized(seenNewDots) { seenNewDots += fresh.map { it.id } }
                prefsOf(SEEN_DOTS_PREFS).edit().putStringSet(SEEN_DOTS_KEY, seenNewDots.map { it.toString() }.toSet()).apply()
            }
        }
        if (cards.none { it.newEpisode && it.id in seenNewDots }) return cards
        return cards.map { if (it.newEpisode && it.id in seenNewDots) it.copy(newEpisode = false) else it }
    }

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

    override fun scanSource(sourceId: Long) {
        launchSafely("Couldn't scan that source") { library.scanSource(sourceId) }
    }

    override fun refreshQuietly() {
        viewModelScope.launch {
            try {
                library.refreshAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "A background refresh failed", e)
            }
        }
    }

    /**
     * Back in the app (owner: every opening rescans, silently): the launch's light refresh again,
     * which skips folders and shares scanned in the last half hour.
     */
    fun onAppReturned() {
        graph.onMainScreenCreated()
        refreshQuietly()
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

    private fun readHistoryHidden(): Map<Long, Long> =
        historyPrefs.all.mapNotNull { (key, value) ->
            val id = key.toLongOrNull() ?: return@mapNotNull null
            val at = value as? Long ?: return@mapNotNull null
            id to at
        }.toMap()

    override fun hideFromHistory(videoId: Long, playedAt: Long) {
        local.update { it.copy(historyHidden = it.historyHidden + (videoId to playedAt)) }
        historyPrefs.edit().putLong(videoId.toString(), playedAt).apply()
    }

    private fun setHidden(ids: Set<Long>) {
        hiddenVideoIds.value = ids
        hiddenPrefs.edit().putStringSet(KEY_HIDDEN_VIDEOS, ids.mapTo(HashSet()) { it.toString() }).apply()
    }

    /** Home without the titles kept off it, and the pinned one first in the hero. */
    private fun withHomePicks(index: LibraryIndex, home: HomeUi, hidden: Set<String>, pinned: String?): HomeUi {
        if (hidden.isEmpty() && pinned == null) return home
        fun off(card: VideoCardUi): Boolean =
            "v:${card.id}" in hidden || index.videosById[card.id]?.showKey?.let { "s:$it" in hidden } == true
        var hero = home.hero.filter { it.itemKey !in hidden }
        if (pinned != null && pinned !in hidden) {
            val pinnedHero = hero.firstOrNull { it.itemKey == pinned } ?: UiMappers.pinnedHero(index, pinned)
            if (pinnedHero != null) hero = listOf(pinnedHero) + hero.filter { it.itemKey != pinned }
        }
        return home.copy(
            hero = hero,
            continueWatching = home.continueWatching.filterNot(::off),
            upNext = home.upNext.filterNot(::off),
        )
    }

    override fun hideFromHome(itemKey: String) {
        local.update { it.copy(homeHidden = it.homeHidden + itemKey, homePinned = it.homePinned.takeIf { pin -> pin != itemKey }) }
        saveHomePicks()
    }

    override fun toggleHomePin(itemKey: String) {
        local.update { it.copy(homePinned = if (it.homePinned == itemKey) null else itemKey, homeHidden = it.homeHidden - itemKey) }
        saveHomePicks()
    }

    /** Opens an IPTV list's page and reads its channels. */
    fun openChannelList(url: String) {
        local.update { it.copy(channels = ChannelsUi(url)) }
        navigate(Route.Channels(url))
        viewModelScope.launch {
            val result = try {
                val list = withContext(Dispatchers.IO) { com.aakash.novafork.data.IptvPlaylist.load(url) }
                ChannelsUi(url, loading = false, channels = list, error = if (list.isEmpty()) "No channels in this list" else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ChannelsUi(url, loading = false, error = "Couldn't read the list: ${e.message ?: e.javaClass.simpleName}")
            }
            local.update { state -> if (state.channels?.url == url) state.copy(channels = result) else state }
        }
    }

    override fun isPinnedToHome(itemKey: String): Boolean = local.value.homePinned == itemKey

    override fun isSoftwareDecoded(videoId: Long): Boolean =
        latestIndex?.videosById?.get(videoId)?.video?.uri?.let(com.aakash.novafork.player.MediaSource::isSoftwareForVideo) == true

    override fun toggleSoftwareDecoding(videoId: Long) {
        val uri = latestIndex?.videosById?.get(videoId)?.video?.uri ?: return
        val on = !com.aakash.novafork.player.MediaSource.isSoftwareForVideo(uri)
        // No message (owner: no pop-ups): the menu item says which it is next time.
        com.aakash.novafork.player.MediaSource.setSoftwareForVideo(getApplication(), uri, on)
    }

    override fun restoreHomeTitles() {
        local.update { it.copy(homeHidden = emptySet()) }
        saveHomePicks()
    }

    private fun saveHomePicks() {
        val state = local.value
        homePrefs.edit()
            .putStringSet(HOME_HIDDEN_KEY, state.homeHidden)
            .putString(HOME_PINNED_KEY, state.homePinned)
            .apply()
    }

    override fun hideVideo(videoId: Long) {
        setHidden(hiddenVideoIds.value + videoId)
    }

    override fun hideShow(showKey: String) {
        val episodes = latestIndex?.showsByKey?.get(showKey)?.episodes ?: return
        setHidden(hiddenVideoIds.value + episodes.map { it.id })
    }

    override fun unhideVideos(videoIds: List<Long>) {
        setHidden(hiddenVideoIds.value - videoIds.toSet())
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

    override fun forgetSearch(query: String) {
        val recent = local.value.recentSearches - query
        local.update { it.copy(recentSearches = recent) }
        if (recent.isEmpty()) {
            searchPrefs.edit().remove(KEY_RECENT_SEARCHES).apply()
        } else {
            searchPrefs.edit().putString(KEY_RECENT_SEARCHES, recent.joinToString("\n")).apply()
        }
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

    override fun saveKeyFor(videoId: Long): String? {
        val video = latestIndex?.videosById?.get(videoId) ?: return null
        return if (video.kind == MediaKind.EPISODE) video.showKey?.let { "s:$it" } else "v:$videoId"
    }

    override fun isFavorite(itemKey: String): Boolean = itemKey in videoFavorites.value

    override fun isOnWatchlist(itemKey: String): Boolean = itemKey in watchlist.value

    override fun playMusic(songIds: List<Long>, startIndex: Int) {
        if (songIds.isNotEmpty()) graph.musicPlayer.playSongs(songIds, startIndex)
    }

    override fun setTitleOverride(key: String, title: String?) {
        if (key.isBlank()) return
        if (title.isNullOrBlank()) graph.titleOverrides.reset(key) else graph.titleOverrides.set(key, title)
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

    override fun showMessage(text: String) {
        local.update { it.copy(message = text) }
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

    // Renaming files: a share is renamed here; a device file needs the system's say-so, so
    // MainActivity does it (see deviceRenameRequests, finishDeviceRename).

    private val deviceRenames = Channel<DeviceRename>(Channel.BUFFERED)

    /** Device videos to rename, for MainActivity to ask permission for and rename in MediaStore. */
    val deviceRenameRequests: Flow<DeviceRename> = deviceRenames.receiveAsFlow()

    override fun renameVideoFile(videoId: Long, newBaseName: String) {
        viewModelScope.launch {
            try {
                val video = library.video(videoId)?.video
                val type = library.sourceForVideo(videoId)?.type
                val newName = video?.let { FileRenames.newFileName(it.fileName, newBaseName) }
                if (video == null || newName == null || newName == video.fileName) return@launch
                when {
                    type == SourceType.SMB -> {
                        val newUri = graph.smb.rename(video.uri, newName)
                        library.renameVideoFile(videoId, newName, newUri)
                        showMessage("Renamed")
                    }
                    type == SourceType.DEVICE && video.uri.startsWith("content://") ->
                        deviceRenames.trySend(DeviceRename(videoId, video.uri, newName))
                    else -> showMessage(FileRenames.FAILED)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't rename video $videoId", e)
                showMessage(FileRenames.FAILED)
            }
        }
    }

    /** MediaStore renamed the device file of [videoId]: the library row follows. */
    fun finishDeviceRename(videoId: Long, newFileName: String) {
        launchSafely(FileRenames.FAILED) {
            library.renameVideoFile(videoId, newFileName)
            showMessage("Renamed")
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

    /**
     * The watch history as CSV at [uri]: every video ever played, last played first, with its
     * show, season, episode, play count, watched state and file name. Nothing is changed.
     */
    /** Preferences › Library › Export Library: every title as a CSV row (the files are untouched). */
    fun writeLibraryCsv(uri: Uri) {
        val index = latestIndex ?: return
        val resolver = getApplication<Application>().contentResolver
        launchSafely("Couldn't save the library list") {
            val rows = index.allVideos.sortedWith(compareBy({ it.kind.name }, { (it.showTitle ?: it.title).lowercase() }, { it.video.season ?: 0 }, { it.video.episode ?: 0 }))
            val text = buildString {
                appendLine("Kind,Title,Year,Show,Season,Episode,Length (min),Watched,Source,File")
                for (item in rows) {
                    val video = item.video
                    appendLine(
                        listOf(
                            item.kind.name.lowercase().replaceFirstChar { it.uppercase() },
                            item.title,
                            item.card.year?.toString().orEmpty(),
                            item.showTitle.orEmpty(),
                            video.season?.toString().orEmpty(),
                            video.episode?.toString().orEmpty(),
                            video.durationMs?.let { (it / 60_000L).toString() }.orEmpty(),
                            if (video.watched) "yes" else "no",
                            item.source?.name.orEmpty(),
                            video.fileName,
                        ).joinToString(",") { csvField(it) },
                    )
                }
            }
            withContext(Dispatchers.IO) {
                openForWriting(resolver, uri).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }
            showMessage(if (rows.size == 1) "1 title exported" else "${rows.size} titles exported")
        }
    }

    fun writeHistoryCsv(uri: Uri) {
        val index = latestIndex ?: return
        val resolver = getApplication<Application>().contentResolver
        launchSafely("Couldn't save the history") {
            val rows = index.allVideos
                .filter { it.video.lastPlayedAt != null || it.video.playCount > 0 }
                .sortedByDescending { it.video.lastPlayedAt ?: 0L }
            val text = buildString {
                appendLine("Title,Show,Season,Episode,Last played,Play count,Watched,File")
                for (item in rows) {
                    val video = item.video
                    val played = video.lastPlayedAt?.let { java.time.Instant.ofEpochMilli(it).toString() }.orEmpty()
                    appendLine(
                        listOf(
                            item.title, item.showTitle.orEmpty(), video.season?.toString().orEmpty(), video.episode?.toString().orEmpty(),
                            played, video.playCount.toString(), if (video.watched) "yes" else "no", video.fileName,
                        ).joinToString(",") { csvField(it) },
                    )
                }
            }
            withContext(Dispatchers.IO) {
                openForWriting(resolver, uri).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }
            showMessage(if (rows.size == 1) "1 video exported" else "${rows.size} videos exported")
        }
    }

    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

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

    private fun publishAutoBackup() {
        val state = autoBackup.state()
        backup.update { it.copy(autoWeekly = state.weekly, autoFolderName = state.folderName, lastAutoAt = state.lastAt) }
    }

    override fun setAutoBackup(weekly: Boolean) {
        autoBackup.setWeekly(weekly)
        publishAutoBackup()
        if (weekly) runAutoBackupSoon()
    }

    /** MainActivity took the grant for [tree], the folder picked for automatic backups. */
    fun setBackupFolder(tree: Uri) {
        autoBackup.setFolder(tree)
        publishAutoBackup()
        runAutoBackupSoon()
    }

    private fun runAutoBackupSoon() {
        graph.appScope.launch {
            try {
                if (autoBackup.runIfDue() != null) withContext(Dispatchers.IO) { publishAutoBackup() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Automatic backup failed", e)
            }
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
        /** Lyrics search: the first read waits for start-up, later ones for a burst of changes. */
        const val LYRICS_FIRST_INDEX_MS = 4_000L
        const val HOME_SNAPSHOT_DELAY_MS = 3_000L
        const val MUSIC_LOAD_DELAY_MS = 2_000L
        const val SEEN_DOTS_PREFS = "seen_new_dots"
        const val SEEN_DOTS_KEY = "ids"
        const val LYRICS_REINDEX_MS = 1_000L

        const val KEY_RECENT_SEARCHES = "recent"
        const val KEY_HIDDEN_VIDEOS = "videos"
        const val HOME_HIDDEN_KEY = "hidden"
        const val UPCOMING_SHOW_LIMIT = 80
        const val HOME_PINNED_KEY = "pinned"
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
