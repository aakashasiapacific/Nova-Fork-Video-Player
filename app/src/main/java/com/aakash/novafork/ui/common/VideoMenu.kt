package com.aakash.novafork.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aakash.novafork.R
import com.aakash.novafork.ui.model.AppActions

/**
 * Long-press menu of a library video's card or row: [content] gets an `openMenu` callback to use as
 * its long-click. The menu offers Details and Add to playlist, then [extraItems] (each gets a
 * `close` callback), and opens under the card.
 */
@Composable
fun VideoMenuBox(
    videoId: Long,
    title: String,
    actions: AppActions,
    modifier: Modifier = Modifier,
    showDetails: Boolean = true,
    extraItems: (@Composable (close: () -> Unit) -> Unit)? = null,
    content: @Composable (openMenu: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        content { open = true }
        NovaDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (showDetails) {
                NovaMenuItem(
                    text = { Text("Details") },
                    leadingIcon = { NovaIcon(R.drawable.ic_info, contentDescription = null) },
                    onClick = {
                        open = false
                        actions.openVideo(videoId)
                    },
                )
            }
            NovaMenuItem(
                text = { Text("Add to playlist") },
                leadingIcon = { NovaIcon(R.drawable.ic_playlist_add, contentDescription = null) },
                onClick = {
                    open = false
                    actions.addToPlaylist(listOf(videoId), title)
                },
            )
            extraItems?.invoke { open = false }
        }
    }
}
