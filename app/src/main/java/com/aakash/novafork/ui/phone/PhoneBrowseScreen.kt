package com.aakash.novafork.ui.phone

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.DeviceAccessCard
import com.aakash.novafork.ui.common.DeviceAccessNotice
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.NovaDropdownMenu
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaMenuItem
import com.aakash.novafork.ui.common.ScanBanner
import com.aakash.novafork.ui.common.deviceAccessNotice
import com.aakash.novafork.ui.dialogs.playlistCountLabel
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.BrowseUi
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.PermissionUi
import com.aakash.novafork.ui.model.PlaylistUi
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.model.SourceUi
import com.aakash.novafork.ui.model.VideoCardUi

@Composable
fun PhoneBrowseScreen(
    browse: BrowseUi,
    scan: ScanUi,
    permission: PermissionUi,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    val sources = browse.sources
    val device = remember(sources) { sources.filter { it.type == SourceType.DEVICE } }
    val folders = remember(sources) { sources.filter { it.type == SourceType.FOLDER } }
    val network = remember(sources) { sources.filter { it.type.isNetworkShare || it.type.isMediaServer } }
    val deviceNotice = deviceAccessNotice(permission)

    Column(modifier.fillMaxSize()) {
        PhoneTopBar(
            title = "Sources",
            onBack = actions::back,
            actions = {
                IconButton(onClick = actions::refresh, enabled = !scan.running) {
                    NovaIcon(R.drawable.ic_refresh, contentDescription = "Rescan all")
                }
            },
        )
        ScanBanner(scan)
        PhonePullToRefresh(
            scanning = scan.running,
            onRefresh = actions::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = dims.sectionSpacing + LocalBottomBarSpace.current),
            ) {
                browseItems(
                    browse = browse,
                    device = device,
                    folders = folders,
                    network = network,
                    permission = permission,
                    deviceNotice = deviceNotice,
                    actions = actions,
                )
            }
        }
    }
}

private fun LazyListScope.browseItems(
    browse: BrowseUi,
    device: List<SourceUi>,
    folders: List<SourceUi>,
    network: List<SourceUi>,
    permission: PermissionUi,
    deviceNotice: DeviceAccessNotice?,
    actions: AppActions,
) {
    if (device.isNotEmpty() || !permission.granted) {
        sectionHeader("device", "On this device")
        items(device, key = { "src:${it.id}" }) { source -> SourceRow(source, actions) }
        if (deviceNotice != null) {
            // Missing access hides camera videos, downloads and screen recordings: say so, with the fix.
            item(key = "deviceAccess") {
                val dims = LocalNovaDimens.current
                DeviceAccessCard(
                    notice = deviceNotice,
                    onAllow = actions::requestPermission,
                    onOpenSettings = actions::openAppSettings,
                    modifier = Modifier.padding(horizontal = dims.gutter, vertical = 4.dp),
                )
            }
        } else if (!permission.granted) {
            item(key = "allowDevice") {
                ActionRow(R.drawable.ic_phone_android, "Allow access to videos", actions::requestPermission)
            }
        }
    }

    sectionHeader("folders", "Folders")
    items(folders, key = { "src:${it.id}" }) { source -> SourceRow(source, actions) }
    item(key = "addFolder") {
        ActionRow(R.drawable.ic_create_new_folder, "Add Folder", actions::addFolder)
    }

    sectionHeader("network", "Network")
    items(network, key = { "src:${it.id}" }) { source -> SourceRow(source, actions) }
    item(key = "addNetwork") {
        ActionRow(R.drawable.ic_add, "Add Network Source", actions::addNetworkSource)
    }

    sectionHeader("streams", "Streams")
    items(browse.streams, key = { "stream:${it.id}" }) { stream ->
        StreamRow(
            stream = stream,
            onClick = { actions.play(stream.id) },
            onLongClick = { actions.openVideo(stream.id) },
        )
    }
    item(key = "addStream") {
        ActionRow(R.drawable.ic_add, "Add Stream") { actions.showDialog(DialogUi.AddStream) }
    }
    item(key = "openUrl") {
        ActionRow(R.drawable.ic_link, "Open Network Stream") { actions.showDialog(DialogUi.OpenUrl) }
    }

    sectionHeader("playlists", "Playlists")
    items(browse.playlists, key = { "playlist:${it.id}" }) { playlist ->
        PlaylistRow(playlist = playlist, onClick = { actions.openPlaylist(playlist.id) })
    }
    item(key = "newPlaylist") {
        ActionRow(R.drawable.ic_playlist_add, "New playlist") { actions.showDialog(DialogUi.NewPlaylist()) }
    }
}

private fun LazyListScope.sectionHeader(key: String, title: String) {
    item(key = "header:$key") {
        val dims = LocalNovaDimens.current
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = dims.gutter, end = dims.gutter, top = 24.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun RowIcon(@DrawableRes icon: Int, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        NovaIcon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SourceRow(source: SourceUi, actions: AppActions) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable { actions.openSource(source.id) },
        leadingContent = { RowIcon(source.type.iconRes()) },
        headlineContent = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                if (source.detail.isNotBlank()) {
                    Text(source.detail, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(countLabel(source.videoCount, "video"), maxLines = 1)
                val error = source.error
                if (!error.isNullOrBlank()) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (source.isScanning) {
                    IosSpinner(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        NovaIcon(R.drawable.ic_more_vert, contentDescription = "Options for ${source.name}")
                    }
                    NovaDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        NovaMenuItem(
                            text = { Text("Rescan") },
                            leadingIcon = { NovaIcon(R.drawable.ic_refresh, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                actions.rescanSource(source.id)
                            },
                        )
                        if (source.type.isNetworkShare || source.type.isMediaServer) {
                            NovaMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { NovaIcon(R.drawable.ic_edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    actions.editSource(source)
                                },
                            )
                        }
                        NovaMenuItem(
                            text = { Text("Remove") },
                            destructive = true,
                            leadingIcon = { NovaIcon(R.drawable.ic_delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                actions.showDialog(DialogUi.ConfirmRemoveSource(source))
                            },
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun StreamRow(stream: VideoCardUi, onClick: () -> Unit, onLongClick: () -> Unit) {
    val subtitle = stream.subtitle
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        leadingContent = { RowIcon(R.drawable.ic_live_tv) },
        headlineContent = { Text(stream.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (!subtitle.isNullOrBlank()) {
            { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = {
            NovaIcon(R.drawable.ic_play_arrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun PlaylistRow(playlist: PlaylistUi, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { RowIcon(R.drawable.ic_playlist_play) },
        headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(playlistCountLabel(playlist.itemCount), maxLines = 1) },
        trailingContent = {
            NovaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ActionRow(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { RowIcon(icon) },
        headlineContent = { Text(label, color = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
