package com.aakash.novafork.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aakash.novafork.R
import com.aakash.novafork.download.VideoDownloads
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.IosProgressBar
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaMenuItem
import com.aakash.novafork.ui.common.SectionHeader
import com.aakash.novafork.ui.common.fadeEndEdge
import com.aakash.novafork.ui.common.rememberRowSnap
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.VideoDetailsUi

/**
 * A movie's TMDB collection ("Dune Collection"): the library's films in it, oldest first, as a
 * row of posters; the film on screen is in it too, so the order reads at a glance.
 */
@Composable
internal fun CollectionRow(details: VideoDetailsUi, actions: AppActions, modifier: Modifier = Modifier) {
    val dims = LocalNovaDimens.current
    val rowState = rememberLazyListState()
    Column(modifier.padding(top = dims.sectionSpacing)) {
        SectionHeader(
            title = details.collectionName ?: "Collection",
            modifier = Modifier.padding(start = dims.gutter, end = dims.gutter, bottom = 10.dp),
        )
        LazyRow(
            state = rowState,
            flingBehavior = rememberRowSnap(rowState),
            modifier = Modifier.fadeEndEdge(rowState),
            contentPadding = PaddingValues(horizontal = dims.gutter),
            horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
        ) {
            items(details.collection, key = { it.id }) { card ->
                PhonePosterCard(
                    card = card,
                    onClick = { if (card.id != details.id) actions.openVideo(card.id) },
                    modifier = Modifier.width(dims.posterWidth),
                )
            }
        }
    }
}

/** A movie's trailers, featurettes and other bonus videos as landscape cards; tapping one plays it. */
@Composable
internal fun ExtrasRow(details: VideoDetailsUi, actions: AppActions, modifier: Modifier = Modifier) {
    val dims = LocalNovaDimens.current
    val rowState = rememberLazyListState()
    Column(modifier.padding(top = dims.sectionSpacing)) {
        SectionHeader(
            title = "Extras",
            modifier = Modifier.padding(start = dims.gutter, end = dims.gutter, bottom = 10.dp),
        )
        LazyRow(
            state = rowState,
            flingBehavior = rememberRowSnap(rowState),
            modifier = Modifier.fadeEndEdge(rowState),
            contentPadding = PaddingValues(horizontal = dims.gutter),
            horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
        ) {
            items(details.extras, key = { it.id }) { card ->
                PhoneLandscapeCard(
                    card = card,
                    onClick = { actions.play(card.id) },
                    onLongClick = { actions.openVideo(card.id) },
                    title = card.title,
                    subtitle = card.subtitle,
                )
            }
        }
    }
}

/** "Downloading 42%" and its bar while this video copies to the phone; nothing otherwise. */
@Composable
internal fun DownloadStatus(details: VideoDetailsUi, modifier: Modifier = Modifier) {
    val downloads by VideoDownloads.progress.collectAsStateWithLifecycle()
    val fraction = downloads[details.id] ?: return
    Column(modifier) {
        Text(
            if (fraction >= 0f) "Downloading ${(fraction * 100).toInt()}%" else "Downloading…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IosProgressBar(
            progress = if (fraction >= 0f) ({ fraction }) else null,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The title page's menu entry: copies a share or server video into Movies/Nova Fork. */
@Composable
internal fun DownloadMenuItem(details: VideoDetailsUi, close: () -> Unit) {
    if (!details.downloadable) return
    val context = LocalContext.current
    val downloads by VideoDownloads.progress.collectAsStateWithLifecycle()
    val running = details.id in downloads
    NovaMenuItem(
        text = { Text(if (running) "Downloading…" else "Download") },
        leadingIcon = { NovaIcon(R.drawable.ic_download, contentDescription = null) },
        enabled = !running,
        onClick = {
            close()
            VideoDownloads.start(context, details.id)
        },
    )
}
