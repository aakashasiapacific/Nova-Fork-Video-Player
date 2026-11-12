package com.aakash.novafork.ui.dialogs

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.IosSheet
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.IosTextButton
import com.aakash.novafork.ui.common.LandscapeArt
import com.aakash.novafork.ui.common.NovaAlertDialog
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaTextField
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.PlaylistUi

/** Playlist names are short labels; longer input is cut rather than refused. */
private const val MAX_NAME_LENGTH = 80

/** "Add to Playlist": every playlist (most recently changed first) and New playlist on top. */
@Composable
internal fun AddToPlaylistSheet(dialog: DialogUi.AddToPlaylist, playlists: List<PlaylistUi>, actions: AppActions) {
    // Centred like an iOS sheet's title, with what is being added under it; picking a playlist
    // (or swiping the sheet away) closes it, so no tick.
    IosSheet(onDismissRequest = actions::dismissDialog, title = "Add to Playlist", subtitle = dialog.label, done = false) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "new") {
                ListItem(
                    modifier = Modifier.clickable { actions.showDialog(DialogUi.NewPlaylist(dialog.videoIds)) },
                    leadingContent = {
                        PlaylistThumb(artwork = null, icon = R.drawable.ic_add)
                    },
                    headlineContent = { Text("New Playlist…", color = MaterialTheme.colorScheme.primary) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            items(playlists, key = { "playlist:${it.id}" }) { playlist ->
                ListItem(
                    modifier = Modifier.clickable { actions.addVideosToPlaylist(playlist.id, dialog.videoIds) },
                    leadingContent = { PlaylistThumb(artwork = playlist.artwork) },
                    headlineContent = { Text(playlist.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(playlistCountLabel(playlist.itemCount)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

/** 16:9 thumbnail of a playlist's first video, or an icon tile while it has none. */
@Composable
internal fun PlaylistThumb(
    artwork: Artwork?,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_playlist_play,
) {
    Box(
        modifier
            .width(72.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork != null) {
            LandscapeArt(artwork = artwork, title = "", modifier = Modifier.fillMaxSize())
        } else {
            NovaIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
    }
}

/** "No videos", "1 video", "12 videos". */
internal fun playlistCountLabel(count: Int): String = when (count) {
    0 -> "No videos"
    1 -> "1 video"
    else -> "$count videos"
}

/** Names a new playlist ([DialogUi.NewPlaylist]) or renames one ([DialogUi.RenamePlaylist]). */
@Composable
internal fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val canSave = name.isNotBlank()
    NovaAlertDialog(
        form = true,
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { NovaIcon(R.drawable.ic_playlist_add, contentDescription = null) },
        title = { Text(title) },
        text = {
            NovaTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (canSave) onConfirm(name) }),
            )
        },
        confirmButton = {
            IosTextButton(onClick = { onConfirm(name) }, enabled = canSave) { Text(confirmLabel) }
        },
        dismissButton = {
            IosTextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun ConfirmDeletePlaylistDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NovaAlertDialog(
        onDismissRequest = onDismiss,
        icon = { NovaIcon(R.drawable.ic_delete, contentDescription = null) },
        title = { Text("Delete $name?") },
        text = { Text("The playlist goes away. Its videos stay in your library.") },
        confirmButton = {
            IosTextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            IosTextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
