package com.aakash.novafork.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.aakash.novafork.R
import com.aakash.novafork.ui.InputRules
import com.aakash.novafork.ui.common.NovaAlertDialog
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaTextField
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.PlaylistUi
import com.aakash.novafork.ui.model.SourceUi

/**
 * Shows [dialog] (nothing for null). Shared by the phone and tablet UIs; each dialog is modal.
 * [playlists] fill the "Add to Playlist" sheet.
 */
@Composable
fun NovaDialogs(dialog: DialogUi?, actions: AppActions, playlists: List<PlaylistUi> = emptyList()) {
    when (dialog) {
        null -> Unit
        DialogUi.AddStream -> StreamUrlDialog(
            title = "Add Stream",
            confirmLabel = "Save",
            askForName = true,
            onConfirm = { name, url -> actions.addStream(name, url) },
            onDismiss = actions::dismissDialog,
        )
        DialogUi.OpenUrl -> StreamUrlDialog(
            title = "Open Network Stream",
            confirmLabel = "Play",
            askForName = false,
            onConfirm = { _, url -> actions.playUrl(url) },
            onDismiss = actions::dismissDialog,
        )
        is DialogUi.SmbServer -> SmbServerDialog(dialog = dialog, actions = actions)
        DialogUi.AddSource -> AddSourceDialog(actions = actions)
        is DialogUi.NetworkServer -> NetworkServerDialog(dialog = dialog, actions = actions)
        is DialogUi.UpnpServers -> UpnpServersDialog(dialog = dialog, actions = actions)
        is DialogUi.Identify -> IdentifyDialog(dialog = dialog, actions = actions)
        is DialogUi.ChangeArtwork -> ChangeArtworkDialog(dialog = dialog, actions = actions)
        is DialogUi.ConfirmRemoveSource -> ConfirmRemoveSourceDialog(
            source = dialog.source,
            onConfirm = { actions.removeSource(dialog.source.id) },
            onDismiss = actions::dismissDialog,
        )
        is DialogUi.AddToPlaylist -> AddToPlaylistSheet(dialog = dialog, playlists = playlists, actions = actions)
        is DialogUi.NewPlaylist -> PlaylistNameDialog(
            title = "New Playlist",
            confirmLabel = "Create",
            initialName = "",
            onConfirm = { name -> actions.createPlaylist(name, dialog.videoIds) },
            onDismiss = actions::dismissDialog,
        )
        is DialogUi.RenamePlaylist -> PlaylistNameDialog(
            title = "Rename playlist",
            confirmLabel = "Rename",
            initialName = dialog.name,
            onConfirm = { name -> actions.renamePlaylist(dialog.playlistId, name) },
            onDismiss = actions::dismissDialog,
        )
        is DialogUi.ConfirmDeletePlaylist -> ConfirmDeletePlaylistDialog(
            name = dialog.name,
            onConfirm = { actions.deletePlaylist(dialog.playlistId) },
            onDismiss = actions::dismissDialog,
        )
        is DialogUi.ConfirmDeleteVideo -> ConfirmDeleteVideoDialog(
            title = dialog.title,
            onConfirm = { actions.confirmDeleteVideo(dialog.videoId) },
            onDismiss = actions::dismissDialog,
        )
        is DialogUi.LibraryDiagnostics -> LibraryDiagnosticsDialog(dialog = dialog, actions = actions)
    }
}

/** A stream URL (http, rtsp, …), checked before it is handed on, plus an optional name. */
@Composable
private fun StreamUrlDialog(
    title: String,
    confirmLabel: String,
    askForName: Boolean,
    onConfirm: (name: String, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    fun submit() {
        if (InputRules.isStreamUrl(url)) {
            onConfirm(name.trim(), url.trim())
        } else {
            showError = true
        }
    }

    NovaAlertDialog(
        form = true,
        onDismissRequest = onDismiss,
        // A stray tap outside should not throw away a pasted URL.
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { NovaIcon(R.drawable.ic_link, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NovaTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        showError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Stream URL") },
                    placeholder = { Text("https://… or rtsp://…") },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(InputRules.STREAM_URL_ERROR) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = if (askForName) ImeAction.Next else ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                )
                if (askForName) {
                    NovaTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = url.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ConfirmRemoveSourceDialog(source: SourceUi, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NovaAlertDialog(
        onDismissRequest = onDismiss,
        icon = { NovaIcon(R.drawable.ic_delete, contentDescription = null) },
        title = { Text("Remove ${source.name}?") },
        text = { Text("Videos from this source leave your library. Files are not deleted.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Remove") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
