package com.aakash.novafork.ui.tablet

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.LandscapeArt
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.ProgressStrip
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.common.WatchedBadge
import com.aakash.novafork.ui.dialogs.playlistCountLabel
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.PlaylistDetailsUi
import com.aakash.novafork.ui.model.VideoCardUi

private val PlaylistGutter = 28.dp

/** Browse's right pane for a playlist: name, Play all / Shuffle / Rename / Delete, then its videos in order. */
@Composable
internal fun TabletPlaylistPane(playlist: PlaylistDetailsUi, actions: AppActions, modifier: Modifier = Modifier) {
    val ids = remember(playlist.videos) { playlist.videos.map { it.id } }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = navigationBarBottom() + 24.dp),
    ) {
        item(key = "header", contentType = "header") {
            PlaylistHeader(
                playlist = playlist,
                actions = actions,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = PlaylistGutter, end = PlaylistGutter, top = 20.dp, bottom = 12.dp),
            )
        }
        if (playlist.videos.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = R.drawable.ic_playlist_add,
                        title = "No videos yet",
                        body = "Long-press a video in a folder, the library or a show and pick Add to playlist.",
                    )
                }
            }
        }
        itemsIndexed(playlist.videos, key = { _, video -> "video:${video.id}" }, contentType = { _, _ -> "video" }) { index, video ->
            VideoMenuBox(videoId = video.id, title = video.primaryLine(), actions = actions) { openMenu ->
                PlaylistItemRow(
                    position = index + 1,
                    video = video,
                    onClick = { actions.playQueue(ids, startIndex = index) },
                    onLongClick = openMenu,
                    onRemove = { actions.removeFromPlaylist(playlist.id, video.id) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistHeader(playlist: PlaylistDetailsUi, actions: AppActions, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = playlistCountLabel(playlist.videos.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val hasVideos = playlist.videos.isNotEmpty()
            TabletPlayButton(label = "Play all", onClick = { actions.playPlaylist(playlist.id) }, enabled = hasVideos)
            FilledTonalButton(
                onClick = { actions.playPlaylist(playlist.id, shuffle = true) },
                enabled = hasVideos,
                modifier = Modifier.height(48.dp),
            ) {
                ButtonLabel(R.drawable.ic_shuffle, "Shuffle")
            }
            OutlinedButton(
                onClick = { actions.showDialog(DialogUi.RenamePlaylist(playlist.id, playlist.name)) },
                modifier = Modifier.height(48.dp),
            ) {
                ButtonLabel(R.drawable.ic_edit, "Rename")
            }
            TextButton(
                onClick = { actions.showDialog(DialogUi.ConfirmDeletePlaylist(playlist.id, playlist.name)) },
                modifier = Modifier.height(48.dp),
            ) {
                ButtonLabel(R.drawable.ic_delete, "Delete playlist")
            }
        }
    }
}

/** Position, 16:9 still, title and subtitle, and a remove button. Tap plays from here. */
@Composable
private fun PlaylistItemRow(
    position: Int,
    video: VideoCardUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PlaylistGutter - 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClickLabel = "Play from here", onLongClickLabel = "More options", onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = position.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 28.dp),
        )
        Spacer(Modifier.width(16.dp))
        Box(
            Modifier
                .width(176.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            LandscapeArt(artwork = video.artwork, title = video.title, modifier = Modifier.fillMaxSize())
            if (video.watched) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            ProgressStrip(video.progress, Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = video.primaryLine(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = video.secondaryLine()
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onRemove) {
            NovaIcon(R.drawable.ic_close, contentDescription = "Remove from playlist")
        }
    }
}
