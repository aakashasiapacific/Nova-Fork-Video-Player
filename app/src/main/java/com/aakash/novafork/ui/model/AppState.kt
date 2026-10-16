package com.aakash.novafork.ui.model

import androidx.compose.runtime.Immutable
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.db.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The four tabs, in the Apple TV app's order (owner's decision): Video (the Home feed, with the
 * library behind its row headers and "Library" entry), Audio, Settings (with the sources) and
 * Search. Phone shows them in a bottom bar, tablet in a rail.
 */
enum class TopDestination(val label: String) {
    VIDEO("Video"),

    /** Music (a temporary test screen until the Apple Music-style design). */
    AUDIO("Audio"),
    SETTINGS("Settings"),
    SEARCH("Search"),
}

@Immutable
sealed interface Route {
    data object Home : Route
    data object Library : Route
    data object Browse : Route
    data object Settings : Route
    data object Audio : Route
    data object Search : Route
    data class VideoDetails(val videoId: Long) : Route
    data class ShowDetails(val showKey: String) : Route
    data class BrowseDir(val sourceId: Long, val path: String) : Route
    data class Playlist(val playlistId: Long) : Route
}

/** The tab a route belongs to (for highlighting the nav bar / rail). */
val Route.top: TopDestination
    get() = when (this) {
        Route.Home, Route.Library, is Route.VideoDetails, is Route.ShowDetails, is Route.Playlist -> TopDestination.VIDEO
        // Sources are under Settings.
        Route.Browse, is Route.BrowseDir, Route.Settings -> TopDestination.SETTINGS
        Route.Audio -> TopDestination.AUDIO
        Route.Search -> TopDestination.SEARCH
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

/**
 * What the add/edit dialog of an FTP, SFTP, NFS, WebDAV, DLNA/UPnP, Jellyfin, Emby or Plex source
 * holds, as typed (ui NetworkForms turns it into a data NetworkSourceInput).
 */
@Immutable
data class NetworkSourceForm(
    /** The source being edited; null adds a new one. */
    val id: Long? = null,
    val type: SourceType,
    val name: String = "",
    /**
     * Server name, address or URL: "nas.local", "ftps://nas.local", "https://dav.example.com/remote.php/dav",
     * "http://192.168.1.5:8096"; for DLNA/UPnP the device's description URL or UDN.
     */
    val address: String = "",
    val port: String = "",
    /** Folder on the server (FTP, SFTP, NFS, WebDAV); "" = its root. */
    val path: String = "",
    val username: String = "",
    val password: String = "",
    /** Plex: a pasted X-Plex-Token. */
    val token: String = "",
    /** Jellyfin/Emby: ids of the libraries to scan; empty = all of them. */
    val libraryIds: List<String> = emptyList(),
)

/** A Jellyfin or Emby library, offered in the server dialog once a connection test listed them. */
@Immutable
data class ServerLibraryUi(val id: String, val name: String)

/** A Plex server the signed-in account can open. */
@Immutable
data class PlexServerUi(
    /** Its machine identifier ([AppActions.pickPlexServer] takes it). */
    val id: String,
    val name: String,
    /** "Your server · 192.168.1.5", "Shared with you · …". */
    val detail: String,
    /** False when plex.tv lists no address to reach it at. */
    val reachable: Boolean = true,
)

/** Signing in to Plex with a code, inside [DialogUi.NetworkServer]. */
@Immutable
data class PlexSignInUi(
    /** The code to enter at [linkUrl]; null while plex.tv is asked for one. */
    val code: String? = null,
    val linkUrl: String = "https://plex.tv/link",
    /** The account's servers once the code was entered (null = still waiting). */
    val servers: List<PlexServerUi>? = null,
    /** Signing in failed (or the code expired); [AppActions.startPlexSignIn] tries again. */
    val error: String? = null,
)

/** A DLNA/UPnP media server that answered on the local network. */
@Immutable
data class UpnpServerUi(
    val name: String,
    /** Its description URL or UDN: what the source is saved with. */
    val address: String,
    val iconUrl: String? = null,
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

    /** "Add network source": picks the kind of server (SMB, FTP, SFTP, NFS, WebDAV, DLNA/UPnP, Jellyfin, Emby, Plex). */
    data object AddSource : DialogUi

    /** Add or edit an FTP, SFTP, NFS, WebDAV, DLNA/UPnP (by address), Jellyfin, Emby or Plex source. */
    data class NetworkServer(
        val form: NetworkSourceForm,
        val testing: Boolean = false,
        /** null = not tested, "" = OK, else the error (also a failed save). */
        val testResult: String? = null,
        /** Jellyfin/Emby: the server's libraries, listed by the last successful test; null = not listed yet. */
        val libraries: List<ServerLibraryUi>? = null,
        /** Plex: the sign-in with a code, once started; null = not started (or cancelled). */
        val plexSignIn: PlexSignInUi? = null,
    ) : DialogUi

    /** DLNA/UPnP servers found on the local network; picking one adds it. */
    data class UpnpServers(
        val searching: Boolean = false,
        val servers: List<UpnpServerUi> = emptyList(),
        /** The search couldn't run. */
        val error: String? = null,
    ) : DialogUi

    /**
     * Picks the playlist [videoIds] go into, or starts a new one. [label] names what is added
     * ("Dune: Part Two", "Season 2"); empty = not named.
     */
    data class AddToPlaylist(val videoIds: List<Long>, val label: String = "") : DialogUi

    /** Names a new playlist; [videoIds] go into it once it exists. */
    data class NewPlaylist(val videoIds: List<Long> = emptyList()) : DialogUi

    data class RenamePlaylist(val playlistId: Long, val name: String) : DialogUi

    data class ConfirmDeletePlaylist(val playlistId: Long, val name: String) : DialogUi

    /**
     * Settings › About › Library diagnostics: a plain-text report to copy or share; [report] is
     * null while it is being put together.
     */
    data class LibraryDiagnostics(val report: String? = null) : DialogUi
}

/** Settings › Backup & restore. */
@Immutable
data class BackupUi(
    /** When the last backup was saved on this device (epoch ms); null = never. */
    val lastBackupAt: Long? = null,
    /** A backup is being written, read or restored. */
    val working: Boolean = false,
    /** A backup file read and checked, waiting for [AppActions.confirmRestore] or [AppActions.cancelRestore]. */
    val confirm: RestorePreviewUi? = null,
    /** Entries of a restore (watch history, matches, playlist items) still waiting for their videos to be scanned. */
    val waiting: Int = 0,
)

/** What a backup file holds, shown before restoring it. */
@Immutable
data class RestorePreviewUi(
    /** Epoch ms. */
    val createdAt: Long,
    val appVersion: String,
    /** The file holds passwords, sign-ins and API keys. */
    val includesSecrets: Boolean,
    val settings: Int,
    val sources: Int,
    /** Videos with watch history. */
    val videos: Int,
    val matches: Int,
    val playlists: Int,
    /** Songs with plays. */
    val songs: Int = 0,
)

private val IdleBackupState: StateFlow<BackupUi> = MutableStateFlow(BackupUi())

@Immutable
data class AppUiState(
    val route: Route = Route.Home,
    /** The tab the current page was opened from (details opened from Library keep Library highlighted). */
    val tab: TopDestination = TopDestination.VIDEO,
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

    /** Opens the library (under the Video tab) showing [filter]. */
    fun openLibrary(filter: LibraryFilter) {
        navigate(Route.Library)
        setLibraryFilter(filter)
    }

    /** The hero's "+": adds the item ([HeroUi.itemKey]) to the watchlist, or takes it off. */
    fun toggleWatchlist(itemKey: String) {}

    /** Plays songs from search results (see [MusicResultUi]). */
    fun playMusic(songIds: List<Long>, startIndex: Int) {}

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

    // Storage access and diagnostics. Default bodies keep other implementations (previews, tests) compiling.

    /** Opens this app's page in system settings (Permissions › Photos and videos). */
    fun openAppSettings() {}

    /** Puts together the library diagnostics report and shows it ([DialogUi.LibraryDiagnostics]). */
    fun openLibraryDiagnostics() {}

    /** Shares plain [text] through the Android share sheet. */
    fun shareText(subject: String, text: String) {}

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

    // Network sources: FTP, SFTP, NFS, WebDAV, DLNA/UPnP, Jellyfin, Emby and Plex (SMB keeps saveSmb/testSmb).

    /** Opens the "Add network source" type picker ([DialogUi.AddSource]). */
    fun addNetworkSource() = showDialog(DialogUi.AddSource)

    /**
     * Goes on from the type picker: SMB opens [DialogUi.SmbServer], DLNA/UPnP searches the network
     * ([discoverUpnpServers]), the other network types open [DialogUi.NetworkServer].
     */
    fun chooseSourceType(type: SourceType) {}

    /** Connects with what [form] holds (Jellyfin and Emby also list their libraries); reported in [DialogUi.NetworkServer]. */
    fun testNetworkSource(form: NetworkSourceForm) {}

    /** Adds ([NetworkSourceForm.id] null) or updates the source [form] describes, then scans it. */
    fun saveNetworkSource(form: NetworkSourceForm) {}

    /** Searches the local network for DLNA/UPnP servers ([DialogUi.UpnpServers]). */
    fun discoverUpnpServers() {}

    /** Adds a DLNA/UPnP server [discoverUpnpServers] found, then scans it. */
    fun addUpnpServer(server: UpnpServerUi) {}

    /** Plex: asks plex.tv for a code to enter at plex.tv/link and waits until it is ([DialogUi.NetworkServer.plexSignIn]). */
    fun startPlexSignIn() {}

    /** Plex: stops waiting for the code and forgets the sign-in. */
    fun cancelPlexSignIn() {}

    /** Plex: adds (or, editing [form], updates) the signed-in account's server [serverId] ([PlexServerUi.id]). */
    fun pickPlexServer(serverId: String, form: NetworkSourceForm) {}

    /** Plays a video of a live folder listing that the library doesn't hold (yet): [uri] as the library would store it. */
    fun playRemote(uri: String, title: String) {}

    /** Opens [url] in the browser (plex.tv/link). */
    fun openWebPage(url: String) {}

    // Backup & restore

    /** Settings › Backup & restore; an idle state where nothing implements it (previews, tests). */
    fun backupState(): StateFlow<BackupUi> = IdleBackupState

    /**
     * Saves a backup to a file the user picks (system "create document" picker). [includeSecrets]
     * puts passwords, sign-in tokens and API keys into it, in plain text.
     */
    fun exportBackup(includeSecrets: Boolean) {}

    /** Picks a backup file (system picker) and reads it; [BackupUi.confirm] then asks before restoring. */
    fun importBackup() {}

    /** Restores the backup [BackupUi.confirm] describes; the outcome comes as a message. */
    fun confirmRestore() {}

    /** Forgets the backup waiting in [BackupUi.confirm]. */
    fun cancelRestore() {}
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
