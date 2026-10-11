package com.aakash.novafork.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aakash.novafork.BuildConfig
import com.aakash.novafork.NovaApp
import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.core.tmdb.TmdbDisabledException
import com.aakash.novafork.core.tmdb.TmdbException
import com.aakash.novafork.data.IdentifyType
import com.aakash.novafork.data.ScanState
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.SmbCredentials
import com.aakash.novafork.data.StoragePermissions
import com.aakash.novafork.data.TmdbSearchResult
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoKind
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.IdentifyResultUi
import com.aakash.novafork.ui.model.KeyStatus
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.LibraryUi
import com.aakash.novafork.ui.model.PermissionUi
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.SearchUi
import com.aakash.novafork.ui.model.SettingsUi
import com.aakash.novafork.ui.model.SmbForm
import com.aakash.novafork.ui.model.SourceUi
import com.aakash.novafork.ui.model.TopDestination
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "MainViewModel"

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
    )

    private val local = MutableStateFlow(
        LocalState(permission = currentPermission(), allFilesAccess = StoragePermissions.hasAllFilesAccess(app)),
    )

    private val libraryIndex = combine(
        library.observeVideos(),
        library.observeSources(),
        settingsStore.settings.map { it.preferLocalArtwork }.distinctUntilChanged(),
    ) { videos, sources, preferLocal ->
        LibraryIndex.build(videos, sources, preferLocal)
    }

    // Only touched from the state combine below, which never runs concurrently with itself.
    private val libraryMemo = Memo<Triple<LibraryIndex, LibraryFilter, LibrarySort>, LibraryUi> { (index, filter, sort) ->
        UiMappers.library(index, filter, sort)
    }
    private val searchMemo = Memo<Pair<LibraryIndex, String>, SearchUi> { (index, query) -> UiMappers.search(index, query) }

    val state: StateFlow<AppUiState> = combine(
        libraryIndex,
        library.scanState,
        settingsStore.settings,
        local,
    ) { index, scan, settings, user ->
        assemble(index, scan, settings, user)
    }
        .flowOn(Dispatchers.Default)
        // Seeded with the real settings so the first frame already has the right theme and layout.
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState(settings = SettingsUi(settings = settingsStore.settings.value)))

    private var identifyJob: Job? = null
    private var keyCheckJob: Job? = null
    private var smbTestJob: Job? = null

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
    }

    private fun assemble(index: LibraryIndex, scan: ScanState, settings: Settings, user: LocalState): AppUiState {
        val route = user.backStack.lastOrNull() ?: Route.Home
        val previewKey = user.previewKey
        // Details follow the route; on a tablet's library page they follow the preview pane.
        val videoId = (route as? Route.VideoDetails)?.videoId
            ?: previewKey?.takeIf { it.startsWith("v:") }?.removePrefix("v:")?.toLongOrNull()
        val showKey = (route as? Route.ShowDetails)?.showKey
            ?: previewKey?.takeIf { it.startsWith("s:") }?.removePrefix("s:")
        val browse = UiMappers.browse(index, scan)
        return AppUiState(
            route = route,
            canGoBack = user.backStack.size > 1,
            home = index.home,
            library = libraryMemo[Triple(index, user.filter, user.sort)],
            browse = browse,
            browseDir = (route as? Route.BrowseDir)?.let { UiMappers.browseDir(index, it.sourceId, it.path, scan) },
            videoDetails = videoId?.let { UiMappers.videoDetails(index, it) },
            showDetails = showKey?.let { UiMappers.showDetails(index, it) },
            previewKey = previewKey,
            search = searchMemo[index to user.query],
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
            permission = user.permission,
            dialog = user.dialog,
            message = user.message,
        )
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
            TopDestination.HOME -> Route.Home
            TopDestination.LIBRARY -> Route.Library
            TopDestination.BROWSE -> Route.Browse
            TopDestination.SETTINGS -> Route.Settings
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
        launchSafely("Couldn't scan the library") { library.refreshAll() }
    }

    override fun rescanSource(sourceId: Long) {
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
        local.update { it.copy(dialog = dialog) }
    }

    fun showMessage(text: String) {
        local.update { it.copy(message = text) }
    }

    override fun consumeMessage() {
        local.update { it.copy(message = null) }
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
        launchSafely("Couldn't open the share") {
            val entity = library.source(source.id)
            if (entity == null || entity.type != SourceType.SMB) {
                showMessage("Only network shares can be edited")
                return@launchSafely
            }
            val location = SmbUris.parse(entity.location)
            val form = SmbForm(
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
            showDialog(DialogUi.SmbServer(form = form))
        }
    }

    private fun updateSmbDialog(transform: (DialogUi.SmbServer) -> DialogUi.SmbServer) {
        local.update { state ->
            val dialog = state.dialog as? DialogUi.SmbServer ?: return@update state
            state.copy(dialog = transform(dialog))
        }
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
     * Gaining access rescans device storage; [rescan] forces that, e.g. after the user picked more
     * videos in Android 14's limited-access picker, which does not change any permission.
     */
    fun onPermissionsChanged(rescan: Boolean = false) {
        val permission = currentPermission()
        val allFiles = StoragePermissions.hasAllFilesAccess(getApplication<Application>())
        val before = local.value
        local.update { it.copy(permission = permission, allFilesAccess = allFiles) }
        val gainedMedia = permission.granted && (!before.permission.granted || (before.permission.partial && !permission.partial))
        val gainedAllFiles = allFiles && !before.allFilesAccess
        if (gainedMedia || gainedAllFiles || (rescan && permission.granted)) rescanDevice()
    }

    /**
     * refreshAll() returns at once while another scan runs, which would skip the device library
     * that just became readable; scanning the device source directly waits for its turn.
     */
    private fun rescanDevice() {
        launchSafely("Couldn't scan device storage") {
            val device = library.observeSources().first().firstOrNull { it.type == SourceType.DEVICE }
            if (device != null) library.scanSource(device.id) else library.refreshAll()
        }
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
        val TRAILING_YEAR = Regex("""\s*\(?(19|20)\d{2}\)?$""")
    }
}

/** Caches the last result: the combined state is rebuilt on every change, most of it unchanged. */
private class Memo<K, V>(private val compute: (K) -> V) {
    private var last: Pair<K, V>? = null

    operator fun get(key: K): V {
        val cached = last
        if (cached != null && cached.first == key) return cached.second
        return compute(key).also { last = key to it }
    }
}
