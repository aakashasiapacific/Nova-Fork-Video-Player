package com.aakash.novafork.ui.tablet

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.ScanBanner
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.common.sourceTypeIcon
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.BrowseDirUi
import com.aakash.novafork.ui.model.BrowseUi
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.FolderUi
import com.aakash.novafork.ui.model.PermissionUi
import com.aakash.novafork.ui.model.PlaylistDetailsUi
import com.aakash.novafork.ui.model.PlaylistUi
import com.aakash.novafork.ui.model.RemoteFileUi
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.model.SourceUi
import com.aakash.novafork.ui.model.VideoCardUi

private val PaneGutter = 28.dp

/** What the right pane shows: [route] is a [Route.BrowseDir], a [Route.Playlist] or null (welcome). */
@Immutable
private data class DirPane(val route: Route?, val dir: BrowseDirUi?, val playlist: PlaylistDetailsUi?)

/** Two panes: sources and playlists on the left, the open folder or playlist (or a welcome panel) on the right. */
@Composable
internal fun TabletBrowseScreen(
    state: AppUiState,
    layout: DeviceLayout,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val dirRoute = state.route as? Route.BrowseDir
    // A listing left over from another folder must not show under this one's breadcrumbs.
    val dir = state.browseDir?.takeIf { dirRoute != null && it.isListingOf(dirRoute) }
    val playlistRoute = state.route as? Route.Playlist
    val playlist = state.playlist?.takeIf { playlistRoute != null && it.id == playlistRoute.playlistId }
    // Portrait uses a narrower sources pane and smaller cells so the folder grid keeps two columns.
    val dirCellMin = if (layout.isExpanded) 220.dp else 160.dp
    val dirStates = rememberSaveableStateHolder()
    Row(modifier.fillMaxSize()) {
        SourcesPane(
            browse = state.browse,
            scan = state.scan,
            permission = state.permission,
            selectedSourceId = dirRoute?.sourceId,
            selectedPlaylistId = playlistRoute?.playlistId,
            actions = actions,
            modifier = Modifier
                .width(if (layout.isExpanded) 340.dp else 300.dp)
                .fillMaxHeight(),
        )
        AnimatedContent(
            targetState = DirPane(dirRoute ?: playlistRoute, dir, playlist),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            transitionSpec = { fadeIn(tween(durationMillis = 220, delayMillis = 30)) togetherWith fadeOut(tween(durationMillis = 120)) },
            contentKey = { it.route },
            label = "browsePane",
        ) { pane ->
            when (val route = pane.route) {
                // Each folder and playlist keeps its own scroll position when coming back to it.
                is Route.BrowseDir -> dirStates.SaveableStateProvider(route.toString()) {
                    val shown = pane.dir
                    if (shown == null) {
                        TabletLoading(Modifier.fillMaxSize())
                    } else {
                        BrowseDirPane(dir = shown, cellMin = dirCellMin, actions = actions, modifier = Modifier.fillMaxSize())
                    }
                }
                is Route.Playlist -> dirStates.SaveableStateProvider(route.toString()) {
                    val shown = pane.playlist
                    if (shown == null) {
                        TabletLoading(Modifier.fillMaxSize())
                    } else {
                        TabletPlaylistPane(playlist = shown, actions = actions, modifier = Modifier.fillMaxSize())
                    }
                }
                else -> BrowseWelcome(actions = actions, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private fun BrowseDirUi.isListingOf(route: Route.BrowseDir): Boolean =
    sourceId == route.sourceId && path.trim('/') == route.path.trim('/')

// ---------------------------------------------------------------------------------------------
// Sources pane
// ---------------------------------------------------------------------------------------------

@Composable
private fun SourcesPane(
    browse: BrowseUi,
    scan: ScanUi,
    permission: PermissionUi,
    selectedSourceId: Long?,
    selectedPlaylistId: Long?,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val sources = browse.sources
    val device = remember(sources) { sources.filter { it.type == SourceType.DEVICE } }
    val folders = remember(sources) { sources.filter { it.type == SourceType.FOLDER } }
    val network = remember(sources) { sources.filter { it.type.isNetworkShare || it.type.isMediaServer } }
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = statusBarTop() + 20.dp,
                bottom = navigationBarBottom() + 24.dp,
            ),
        ) {
            item(key = "title", contentType = "title") {
                Column {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Browse", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = actions::refresh, enabled = !scan.running) {
                            NovaIcon(R.drawable.ic_refresh, contentDescription = "Rescan all")
                        }
                    }
                    ScanBanner(scan, Modifier.padding(top = 4.dp))
                }
            }

            if (device.isNotEmpty() || !permission.granted) {
                sectionHeader("device", "On this device")
                items(device, key = { "src:${it.id}" }, contentType = { "source" }) { source ->
                    SourceRow(source = source, selected = source.id == selectedSourceId, actions = actions)
                }
                if (!permission.granted) {
                    item(key = "allowDevice", contentType = "action") {
                        ActionRow(R.drawable.ic_video_library, "Allow access to videos", actions::requestPermission)
                    }
                }
            }

            sectionHeader("folders", "Folders")
            items(folders, key = { "src:${it.id}" }, contentType = { "source" }) { source ->
                SourceRow(source = source, selected = source.id == selectedSourceId, actions = actions)
            }
            item(key = "addFolder", contentType = "action") {
                ActionRow(R.drawable.ic_create_new_folder, "Add folder", actions::addFolder)
            }

            sectionHeader("network", "Network")
            items(network, key = { "src:${it.id}" }, contentType = { "source" }) { source ->
                SourceRow(source = source, selected = source.id == selectedSourceId, actions = actions)
            }
            item(key = "addNetwork", contentType = "action") {
                ActionRow(R.drawable.ic_add, "Add network source", actions::addNetworkSource)
            }

            sectionHeader("streams", "Streams")
            items(browse.streams, key = { "stream:${it.id}" }, contentType = { "stream" }) { stream ->
                StreamRow(
                    stream = stream,
                    onClick = { actions.play(stream.id) },
                    onLongClick = { actions.openVideo(stream.id) },
                )
            }
            item(key = "addStream", contentType = "action") {
                ActionRow(R.drawable.ic_add, "Add stream") { actions.showDialog(DialogUi.AddStream) }
            }
            item(key = "openUrl", contentType = "action") {
                ActionRow(R.drawable.ic_link, "Open network stream") { actions.showDialog(DialogUi.OpenUrl) }
            }

            sectionHeader("playlists", "Playlists")
            items(browse.playlists, key = { "playlist:${it.id}" }, contentType = { "playlist" }) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    selected = playlist.id == selectedPlaylistId,
                    onClick = { actions.openPlaylist(playlist.id) },
                )
            }
            item(key = "newPlaylist", contentType = "action") {
                ActionRow(R.drawable.ic_playlist_add, "New playlist") { actions.showDialog(DialogUi.NewPlaylist()) }
            }
        }
    }
}

private fun LazyListScope.sectionHeader(key: String, title: String) {
    item(key = "header:$key", contentType = "header") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 6.dp),
        )
    }
}


@Composable
private fun RowIcon(@DrawableRes icon: Int, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        NovaIcon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SourceRow(source: SourceUi, selected: Boolean, actions: AppActions) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = { actions.openSource(source.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIcon(sourceTypeIcon(source.type))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(source.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (source.detail.isNotBlank()) {
                    Text(
                        text = source.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val error = source.error
                if (!error.isNullOrBlank()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (source.isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = source.videoCount.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    NovaIcon(R.drawable.ic_more_vert, contentDescription = "Options for ${source.name}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rescan") },
                        leadingIcon = { NovaIcon(R.drawable.ic_refresh, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            actions.rescanSource(source.id)
                        },
                    )
                    if (source.type.isNetworkShare || source.type.isMediaServer) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { NovaIcon(R.drawable.ic_edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                actions.editSource(source)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        leadingIcon = { NovaIcon(R.drawable.ic_delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            actions.showDialog(DialogUi.ConfirmRemoveSource(source))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamRow(stream: VideoCardUi, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClickLabel = "Play", onLongClickLabel = "Details", onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(R.drawable.ic_live_tv)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stream.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = stream.subtitle
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        NovaIcon(
            R.drawable.ic_play_arrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun PlaylistRow(playlist: PlaylistUi, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIcon(R.drawable.ic_playlist_play)
            Spacer(Modifier.width(14.dp))
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = playlist.itemCount.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ActionRow(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIcon(icon)
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Right pane
// ---------------------------------------------------------------------------------------------

@Composable
private fun BrowseWelcome(actions: AppActions, modifier: Modifier = Modifier) {
    CenteredScrollColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = PaneGutter,
            end = PaneGutter,
            top = statusBarTop() + 24.dp,
            bottom = navigationBarBottom() + 24.dp,
        ),
    ) {
        Column(Modifier.widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(size = 64.dp)
            Spacer(Modifier.height(20.dp))
            Text("Pick a source", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Choose a source on the left to browse its folders, or add a new one.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ActionTile(
                        icon = R.drawable.ic_create_new_folder,
                        title = "Add folder",
                        body = "Any folder on this tablet or an SD card.",
                        onClick = actions::addFolder,
                        modifier = Modifier.weight(1f),
                    )
                    ActionTile(
                        icon = R.drawable.ic_lan,
                        title = "Add network source",
                        body = "SMB, FTP, SFTP, NFS, WebDAV, DLNA, Jellyfin, Emby or Plex.",
                        onClick = actions::addNetworkSource,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ActionTile(
                        icon = R.drawable.ic_live_tv,
                        title = "Add stream",
                        body = "Save a live channel or video URL to your library.",
                        onClick = { actions.showDialog(DialogUi.AddStream) },
                        modifier = Modifier.weight(1f),
                    )
                    ActionTile(
                        icon = R.drawable.ic_link,
                        title = "Open network stream",
                        body = "Play a link once without saving it.",
                        onClick = { actions.showDialog(DialogUi.OpenUrl) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionTile(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(168.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                NovaIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BrowseDirPane(dir: BrowseDirUi, cellMin: Dp, actions: AppActions, modifier: Modifier = Modifier) {
    val isEmpty = dir.isEmpty
    Column(modifier) {
        BrowseDirHeader(
            dir = dir,
            actions = actions,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = PaneGutter, end = PaneGutter - 8.dp, top = 20.dp, bottom = 8.dp),
        )
        if (dir.isLoading && !isEmpty) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PaneGutter)
                    .height(2.dp),
            )
        }
        val error = dir.error
        when {
            isEmpty && dir.isLoading -> TabletLoading(Modifier.fillMaxSize())
            isEmpty && !error.isNullOrBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = R.drawable.ic_error,
                    title = "Couldn't open this folder",
                    body = error,
                    actionLabel = "Try again",
                    onAction = { actions.rescanSource(dir.sourceId) },
                )
            }
            isEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = R.drawable.ic_folder_open,
                    title = "No videos here",
                    body = "This folder has no videos or subfolders with videos.",
                )
            }
            else -> DirGrid(dir = dir, error = error, cellMin = cellMin, actions = actions)
        }
    }
}

@Composable
private fun BrowseDirHeader(dir: BrowseDirUi, actions: AppActions, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dir.title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (dir.videos.isNotEmpty()) {
                // Plays the videos directly in this folder, not those in its subfolders.
                FilledTonalButton(onClick = { actions.playFolder(dir.sourceId, dir.path) }) {
                    ButtonLabel(R.drawable.ic_play_arrow, "Play all")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { actions.playFolder(dir.sourceId, dir.path, shuffle = true) }) {
                    ButtonLabel(R.drawable.ic_shuffle, "Shuffle")
                }
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = { actions.rescanSource(dir.sourceId) }, enabled = !dir.isLoading) {
                NovaIcon(R.drawable.ic_refresh, contentDescription = "Refresh")
            }
        }
        if (dir.crumbs.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dir.crumbs.forEachIndexed { index, crumb ->
                    if (index > 0) {
                        NovaIcon(
                            R.drawable.ic_chevron_right,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(18.dp),
                        )
                    }
                    if (index == dir.crumbs.lastIndex) {
                        Text(
                            text = crumb.label,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    } else {
                        AssistChip(
                            onClick = { actions.navigate(Route.BrowseDir(dir.sourceId, crumb.path)) },
                            label = { Text(crumb.label) },
                            leadingIcon = if (index == 0) {
                                {
                                    NovaIcon(
                                        sourceTypeIcon(dir.sourceType),
                                        contentDescription = null,
                                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirGrid(dir: BrowseDirUi, error: String?, cellMin: Dp, actions: AppActions) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cellMin),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = PaneGutter,
            end = PaneGutter,
            top = 12.dp,
            bottom = navigationBarBottom() + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (!error.isNullOrBlank()) {
            fullWidthItem("error") {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (dir.folders.isNotEmpty()) {
            fullWidthItem("foldersHeader") { GridHeader("Folders") }
            items(dir.folders, key = { "folder:${it.path}" }, contentType = { "folder" }) { folder ->
                FolderTile(folder = folder, onClick = { actions.openFolder(folder) })
            }
        }
        if (dir.videos.isNotEmpty() || dir.files.isNotEmpty()) {
            fullWidthItem("videosHeader") { GridHeader("Videos") }
            items(dir.videos, key = { "video:${it.id}" }, contentType = { "video" }) { video ->
                VideoMenuBox(videoId = video.id, title = video.primaryLine(), actions = actions) { openMenu ->
                    TabletStillCard(
                        artwork = video.artwork,
                        title = video.primaryLine(),
                        subtitle = video.secondaryLine(),
                        cornerLabel = durationLabel(video.durationMs),
                        progress = video.progress,
                        watched = video.watched,
                        onClick = { actions.play(video.id) },
                        onLongClick = openMenu,
                    )
                }
            }
            // Not scanned yet: played straight from the server.
            items(dir.files, key = { "file:${it.uri}" }, contentType = { "file" }) { file ->
                RemoteFileCard(file = file, onPlay = { actions.playRemote(file.uri, file.name) })
            }
        }
    }
}

@Composable
private fun RemoteFileCard(file: RemoteFileUi, onPlay: () -> Unit) {
    TabletStillCard(
        artwork = file.artwork,
        title = file.name,
        subtitle = file.subtitle,
        onClick = onPlay,
        onLongClick = onPlay,
    )
}

private fun LazyGridScope.fullWidthItem(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key) { content() }
}

@Composable
private fun GridHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun FolderTile(folder: FolderUi, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                NovaIcon(R.drawable.ic_folder, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = folder.note ?: metaLine(countLabel(folder.videoCount, "video")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            NovaIcon(
                R.drawable.ic_chevron_right,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
