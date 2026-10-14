package com.aakash.novafork.ui.model

import androidx.compose.runtime.Immutable
import com.aakash.novafork.data.Settings

/** Top-level tabs. Phone shows them in a bottom NavigationBar, tablet in a NavigationRail. */
enum class TopDestination(val label: String) { HOME("Home"), LIBRARY("Library"), BROWSE("Browse"), SETTINGS("Settings") }

@Immutable
sealed interface Route {
    data object Home : Route
    data object Library : Route
    data object Browse : Route
    data object Settings : Route
    data object Search : Route
    data class VideoDetails(val videoId: Long) : Route
    data class ShowDetails(val showKey: String) : Route
    data class BrowseDir(val sourceId: Long, val path: String) : Route
    data class Playlist(val playlistId: Long) : Route
}

/** The tab a route belongs to (for highlighting the nav bar / rail). */
val Route.top: TopDestination
    get() = when (this) {
        Route.Home, is Route.VideoDetails, is Route.ShowDetails, Route.Search -> TopDestination.HOME
        Route.Library -> TopDestination.LIBRARY
        Route.Browse, is Route.BrowseDir, is Route.Playlist -> TopDestination.BROWSE
        Route.Settings -> TopDestination.SETTINGS
    }

@Immutable
data class SmbForm(
    val id: Long? = null,
    val name: String = "",
    val host: String = "",
    val port: String = "",
    val share: String = "",
    val path: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
)

@Immutable
sealed interface DialogUi {
    /** Save a stream URL to the library. */
    data object AddStream : DialogUi

    /** Play a URL once without saving ("Open network stream" in VLC). */
    data object OpenUrl : DialogUi

    data class SmbServer(
        val form: SmbForm = SmbForm(),
        val testing: Boolean = false,
        /** null = not tested, "" = OK, else the error. */
        val testResult: String? = null,
    ) : DialogUi

    data class Identify(
        val videoId: Long,
        val query: String,
        val tv: Boolean,
        val results: List<IdentifyResultUi> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
    ) : DialogUi

    data class ConfirmRemoveSource(val source: SourceUi) : DialogUi

    /**
     * Picks the playlist [videoIds] go into, or starts a new one. [label] names what is added
     * ("Dune: Part Two", "Season 2"); empty = not named.
     */
    data class AddToPlaylist(val videoIds: List<Long>, val label: String = "") : DialogUi

    /** Names a new playlist; [videoIds] go into it once it exists. */
    data class NewPlaylist(val videoIds: List<Long> = emptyList()) : DialogUi

    data class RenamePlaylist(val playlistId: Long, val name: String) : DialogUi

    data class ConfirmDeletePlaylist(val playlistId: Long, val name: String) : DialogUi
}

@Immutable
data class AppUiState(
    val route: Route = Route.Home,
    /** The tab the current page was opened from (details opened from Library keep Library highlighted). */
    val tab: TopDestination = TopDestination.HOME,
    val canGoBack: Boolean = false,
    val home: HomeUi = HomeUi(),
    val library: LibraryUi = LibraryUi(),
    val browse: BrowseUi = BrowseUi(),
    /** Set while route is BrowseDir. */
    val browseDir: BrowseDirUi? = null,
    /** The video being shown in details (route VideoDetails, or the tablet library preview pane). */
    val videoDetails: VideoDetailsUi? = null,
    /** The show being shown in details (route ShowDetails, or the tablet library preview pane). */
    val showDetails: ShowDetailsUi? = null,
    /** Key of the item in the tablet two-pane preview (LibraryItemUi.key), null = none. */
    val previewKey: String? = null,
    /** The playlist being shown (route Playlist); null while it loads or after it was deleted. */
    val playlist: PlaylistDetailsUi? = null,
    val search: SearchUi = SearchUi(),
    val settings: SettingsUi = SettingsUi(),
    val scan: ScanUi = ScanUi(),
    val permission: PermissionUi = PermissionUi(),
    val dialog: DialogUi? = null,
    /** One-shot snackbar text; call AppActions.consumeMessage() after showing it. */
    val message: String? = null,
    /** Stack trace of the last crash (CrashReporter), shown once with Share / Dismiss. */
    val crashReport: String? = null,
)

/**
 * Everything the phone and tablet UIs can ask for. Implemented by MainViewModel/MainActivity.
 * All functions are safe to call from the UI thread and return immediately.
 */
interface AppActions {
    fun navigate(route: Route)
    fun back()
    /** Switches tab: clears the stack to that tab's root. */
    fun selectTab(tab: TopDestination)

    fun play(videoId: Long, startOver: Boolean = false)
    fun openVideo(videoId: Long) = navigate(Route.VideoDetails(videoId))
    fun openShow(showKey: String) = navigate(Route.ShowDetails(showKey))
    /** Tablet two-pane: load an item into the preview pane without navigating. */
    fun preview(item: LibraryItemUi)

    fun refresh()
    fun requestPermission()
    /** Opens the system "All files access" screen (Android 11+). */
    fun requestAllFilesAccess()

    fun setLibraryFilter(filter: LibraryFilter)
    fun setLibrarySort(sort: LibrarySort)
    fun search(query: String)

    fun openSource(sourceId: Long) = navigate(Route.BrowseDir(sourceId, ""))
    fun openFolder(folder: FolderUi) = navigate(Route.BrowseDir(folder.sourceId, folder.path))
    fun rescanSource(sourceId: Long)

    /** Launches the system folder picker (SAF). */
    fun addFolder()
    fun showDialog(dialog: DialogUi?)
    fun dismissDialog() = showDialog(null)
    fun addStream(title: String, url: String)
    fun playUrl(url: String)
    fun saveSmb(form: SmbForm)
    fun testSmb(form: SmbForm)
    fun editSource(source: SourceUi)
    fun removeSource(sourceId: Long)

    /** Opens the Identify dialog prefilled with the parsed title. */
    fun identify(videoId: Long)
    fun identifySearch(query: String, tv: Boolean)
    fun identifyPick(videoId: Long, result: IdentifyResultUi)
    fun setWatched(videoId: Long, watched: Boolean)

    fun updateSettings(transform: (Settings) -> Settings)
    fun checkTmdbKey()
    fun clearMetadata()

    fun consumeMessage()

    /** Shares the crash report text (Android share sheet). */
    fun shareCrashReport()
    /** Forgets the crash report. */
    fun dismissCrashReport()

    // Queues and playlists. Default bodies keep other implementations (previews, tests) compiling.

    /** Plays library videos one after the other from [startIndex]; [shuffle] plays the rest in random order. */
    fun playQueue(videoIds: List<Long>, startIndex: Int = 0, shuffle: Boolean = false) {}

    /** Plays the videos directly in a Browse folder ([path] as in [Route.BrowseDir], "" = the source root). */
    fun playFolder(sourceId: Long, path: String, shuffle: Boolean = false) {}

    /** Plays a show's episodes in order: one season, or the whole show for a null [season]. */
    fun playSeason(showKey: String, season: Int?, shuffle: Boolean = false) {}

    /** Plays a playlist from its [startIndex]th video. */
    fun playPlaylist(playlistId: Long, startIndex: Int = 0, shuffle: Boolean = false) {}

    fun openPlaylist(playlistId: Long) = navigate(Route.Playlist(playlistId))

    /** Opens the "Add to playlist" sheet for [videoIds]; [label] names them in the sheet's title. */
    fun addToPlaylist(videoIds: List<Long>, label: String = "") = showDialog(DialogUi.AddToPlaylist(videoIds, label))

    /** Appends [videoIds] to the playlist (videos already in it are skipped). */
    fun addVideosToPlaylist(playlistId: Long, videoIds: List<Long>) {}

    /** Creates a playlist named [name], then adds [videoIds] to it. */
    fun createPlaylist(name: String, videoIds: List<Long> = emptyList()) {}

    fun renamePlaylist(playlistId: Long, name: String) {}

    fun deletePlaylist(playlistId: Long) {}

    fun removeFromPlaylist(playlistId: Long, videoId: Long) {}

    /** Moves the playlist's video at index [from] to index [to] (indices of [PlaylistDetailsUi.videos]). */
    fun movePlaylistItem(playlistId: Long, from: Int, to: Int) {}
}

/** No-op actions for previews and screenshot tests. */
open class NoOpAppActions : AppActions {
    override fun navigate(route: Route) {}
    override fun back() {}
    override fun selectTab(tab: TopDestination) {}
    override fun play(videoId: Long, startOver: Boolean) {}
    override fun preview(item: LibraryItemUi) {}
    override fun refresh() {}
    override fun requestPermission() {}
    override fun requestAllFilesAccess() {}
    override fun setLibraryFilter(filter: LibraryFilter) {}
    override fun setLibrarySort(sort: LibrarySort) {}
    override fun search(query: String) {}
    override fun rescanSource(sourceId: Long) {}
    override fun addFolder() {}
    override fun showDialog(dialog: DialogUi?) {}
    override fun addStream(title: String, url: String) {}
    override fun playUrl(url: String) {}
    override fun saveSmb(form: SmbForm) {}
    override fun testSmb(form: SmbForm) {}
    override fun editSource(source: SourceUi) {}
    override fun removeSource(sourceId: Long) {}
    override fun identify(videoId: Long) {}
    override fun identifySearch(query: String, tv: Boolean) {}
    override fun identifyPick(videoId: Long, result: IdentifyResultUi) {}
    override fun setWatched(videoId: Long, watched: Boolean) {}
    override fun updateSettings(transform: (Settings) -> Settings) {}
    override fun checkTmdbKey() {}
    override fun clearMetadata() {}
    override fun consumeMessage() {}
    override fun shareCrashReport() {}
    override fun dismissCrashReport() {}
}
