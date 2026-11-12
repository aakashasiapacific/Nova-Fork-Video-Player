package com.aakash.novafork.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.ui.common.IosTextButton
import com.aakash.novafork.ui.common.NovaAlertDialog

/** Fix Name: the title and artist shown for [song]; the file itself is left as it is. */
@Composable
fun FixSongNameDialog(song: SongEntity, onSave: (title: String, artist: String) -> Unit, onDismiss: () -> Unit) {
    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(song.artist.orEmpty()) }
    NovaAlertDialog(
        form = true,
        onDismissRequest = onDismiss,
        title = { Text("Fix Name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") }, singleLine = true)
                Text("Only Nova shows these; the music file is not changed. Clear both to use the file's own.")
            }
        },
        confirmButton = { IosTextButton(onClick = { onSave(title, artist) }) { Text("Save") } },
        dismissButton = { IosTextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
