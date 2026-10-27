package com.aakash.novafork.ui.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aakash.novafork.core.tmdb.TmdbClient
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.IosTextButton
import com.aakash.novafork.ui.common.NovaAlertDialog
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi

/** "Change artwork": TMDB's posters and backdrops for a title; a tap uses one. */
@Composable
internal fun ChangeArtworkDialog(dialog: DialogUi.ChangeArtwork, actions: AppActions) {
    NovaAlertDialog(
        form = true,
        onDismissRequest = actions::dismissDialog,
        title = { Text("Artwork for ${dialog.title}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    dialog.loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        IosSpinner()
                    }
                    dialog.error != null -> Text(dialog.error, color = MaterialTheme.colorScheme.error)
                    else -> {
                        Text("Poster", style = MaterialTheme.typography.titleSmall)
                        ImageRow(
                            paths = dialog.posters,
                            selected = dialog.poster,
                            width = 84.dp,
                            height = 126.dp,
                            url = { TmdbClient.posterUrl(it, "w185") },
                            onPick = { actions.chooseArtwork(dialog.metadataKey, it, null) },
                        )
                        Text("Backdrop", style = MaterialTheme.typography.titleSmall)
                        ImageRow(
                            paths = dialog.backdrops,
                            selected = dialog.backdrop,
                            width = 168.dp,
                            height = 95.dp,
                            url = { TmdbClient.backdropUrl(it, "w300") },
                            onPick = { actions.chooseArtwork(dialog.metadataKey, null, it) },
                        )
                        if (dialog.posters.isEmpty() && dialog.backdrops.isEmpty()) Text("TMDB has no other artwork for this title.")
                    }
                }
            }
        },
        confirmButton = { IosTextButton(onClick = actions::dismissDialog) { Text("Done") } },
        dismissButton = if (dialog.customised) {
            { IosTextButton(onClick = { actions.resetArtwork(dialog.metadataKey) }) { Text("Use TMDB's") } }
        } else {
            null
        },
    )
}

@Composable
private fun ImageRow(
    paths: List<String>,
    selected: String?,
    width: Dp,
    height: Dp,
    url: (String) -> String?,
    onPick: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(paths, key = { it }) { path ->
            val shape = RoundedCornerShape(8.dp)
            val isSelected = path == selected
            AsyncImage(
                model = url(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = width, height = height)
                    .clip(shape)
                    .border(if (isSelected) 3.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                    .clickable { onPick(path) },
            )
        }
    }
}
