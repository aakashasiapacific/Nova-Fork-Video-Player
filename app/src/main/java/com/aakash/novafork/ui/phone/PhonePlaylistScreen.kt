package com.aakash.novafork.ui.phone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.dialogs.playlistCountLabel
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.PlaylistDetailsUi

/** One playlist: Play all / Shuffle, then its videos (tap plays from there, × removes). */
@Composable
fun PhonePlaylistScreen(
    playlist: PlaylistDetailsUi?,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PhoneTopBar(
            title = playlist?.name.orEmpty(),
            onBack = actions::back,
            actions = {
                if (playlist != null) PlaylistMenu(playlist = playlist, actions = actions)
            },
        )
        if (playlist == null) {
            LoadingBox()
        } else if (playlist.videos.isEmpty()) {
            CenteredScrollable {
                EmptyState(
                    icon = R.drawable.ic_playlist_add,
                    title = "No videos yet",
                    body = "Long-press a video in Browse, Library or a show and pick Add to playlist.",
                )
            }
        } else {
            PlaylistContent(playlist = playlist, actions = actions)
        }
    }
}

@Composable
private fun PlaylistContent(playlist: PlaylistDetailsUi, actions: AppActions) {
    val dims = LocalNovaDimens.current
    val ids = remember(playlist.videos) { playlist.videos.map { it.id } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = dims.sectionSpacing),
    ) {
        item(key = "header") {
            Column(Modifier.padding(start = dims.gutter, end = dims.gutter, bottom = 8.dp)) {
                Text(
                    text = playlistCountLabel(playlist.videos.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                PhonePlayAllRow(
                    onPlayAll = { actions.playPlaylist(playlist.id) },
                    onShuffle = { actions.playPlaylist(playlist.id, shuffle = true) },
                )
            }
        }
        itemsIndexed(playlist.videos, key = { _, video -> "video:${video.id}" }) { index, video ->
            VideoMenuBox(videoId = video.id, title = video.displayTitle(), actions = actions) { openMenu ->
                PhoneVideoRow(
                    card = video,
                    onClick = { actions.playQueue(ids, startIndex = index) },
                    onLongClick = openMenu,
                    trailing = {
                        IconButton(onClick = { actions.removeFromPlaylist(playlist.id, video.id) }) {
                            NovaIcon(R.drawable.ic_close, contentDescription = "Remove from playlist")
                        }
                    },
                )
            }
        }
    }
}

/** Rename and Delete, behind the top bar's overflow button. */
@Composable
private fun PlaylistMenu(playlist: PlaylistDetailsUi, actions: AppActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            NovaIcon(R.drawable.ic_more_vert, contentDescription = "Playlist options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { NovaIcon(R.drawable.ic_edit, contentDescription = null) },
                onClick = {
                    open = false
                    actions.showDialog(DialogUi.RenamePlaylist(playlist.id, playlist.name))
                },
            )
            DropdownMenuItem(
                text = { Text("Delete playlist") },
                leadingIcon = { NovaIcon(R.drawable.ic_delete, contentDescription = null) },
                onClick = {
                    open = false
                    actions.showDialog(DialogUi.ConfirmDeletePlaylist(playlist.id, playlist.name))
                },
            )
        }
    }
}
