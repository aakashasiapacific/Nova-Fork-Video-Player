package com.aakash.novafork.ui.tablet

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.RatingPill
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.VideoDetailsUi

/** Compact (portrait) poster width; landscape uses [com.aakash.novafork.ui.adaptive.NovaDimens.detailPosterWidth]. */
internal val CompactDetailPosterWidth = 200.dp

/**
 * Movie / episode / video page: the artwork fills the screen behind a heavy scrim. Landscape puts
 * the poster and file facts in a left column next to the text; portrait stacks them.
 */
@Composable
internal fun TabletVideoDetailsScreen(
    details: VideoDetailsUi,
    layout: DeviceLayout,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    Box(modifier.fillMaxSize()) {
        TabletDetailsBackdrop(details.artwork)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(
                    start = dims.gutter,
                    end = dims.gutter,
                    top = 12.dp,
                    bottom = navigationBarBottom() + dims.sectionSpacing,
                ),
        ) {
            TabletBackButton(onClick = actions::back)
            Spacer(Modifier.height(20.dp))
            if (layout.isExpanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Column(
                        Modifier.width(dims.detailPosterWidth),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        TabletDetailsPoster(details.artwork, details.title, dims.detailPosterWidth)
                        VideoFileCard(details, Modifier.fillMaxWidth())
                    }
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .widthIn(max = 760.dp),
                    ) {
                        VideoHeadline(details)
                        Spacer(Modifier.height(28.dp))
                        VideoBody(details, actions)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    TabletDetailsPoster(details.artwork, details.title, CompactDetailPosterWidth)
                    Spacer(Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) { VideoHeadline(details) }
                }
                Spacer(Modifier.height(28.dp))
                VideoBody(details, actions)
                Spacer(Modifier.height(28.dp))
                VideoFileCard(details, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun VideoHeadline(details: VideoDetailsUi) {
    Column {
        Text(
            text = details.title,
            style = MaterialTheme.typography.displaySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val original = details.originalTitle
        if (!original.isNullOrBlank() && !original.equals(details.title, ignoreCase = true)) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = original,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val meta = metaLine(details.year?.toString(), details.runtimeLabel, details.episodeCode)
        if (meta.isNotEmpty() || (details.rating ?: 0f) > 0f) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RatingPill(details.rating)
            }
        }
        if (details.genres.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                details.genres.forEach { TabletTag(it) }
            }
        }
    }
}

@Composable
private fun VideoBody(details: VideoDetailsUi, actions: AppActions) {
    val dims = LocalNovaDimens.current
    val isStream = details.kind == MediaKind.STREAM
    val started = (details.progress ?: 0f) > 0f
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TabletPlayButton(label = details.playLabel, onClick = { actions.play(details.id) })
            if (started) {
                FilledTonalButton(onClick = { actions.play(details.id, startOver = true) }, modifier = Modifier.height(48.dp)) {
                    ButtonLabel(R.drawable.ic_restart_alt, "Start Over")
                }
            }
            if (!isStream) {
                OutlinedButton(onClick = { actions.identify(details.id) }, modifier = Modifier.height(48.dp)) {
                    ButtonLabel(R.drawable.ic_search, "Identify")
                }
                OutlinedButton(onClick = { actions.setWatched(details.id, !details.watched) }, modifier = Modifier.height(48.dp)) {
                    if (details.watched) {
                        ButtonLabel(R.drawable.ic_visibility_off, "Mark unwatched")
                    } else {
                        ButtonLabel(R.drawable.ic_check, "Mark watched")
                    }
                }
            }
            OutlinedButton(
                onClick = { actions.addToPlaylist(listOf(details.id), details.title) },
                modifier = Modifier.height(48.dp),
            ) {
                ButtonLabel(R.drawable.ic_playlist_add, "Add to Playlist")
            }
        }
        WatchProgress(progress = details.progress, positionMs = details.positionMs, durationMs = details.durationMs)
        val showKey = details.showKey
        if (details.kind == MediaKind.EPISODE && showKey != null && !details.showTitle.isNullOrBlank()) {
            TextButton(onClick = { actions.openShow(showKey) }) {
                ButtonLabel(R.drawable.ic_tv, "From ${details.showTitle}")
                Spacer(Modifier.width(4.dp))
                NovaIcon(R.drawable.ic_chevron_right, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        if (!details.tagline.isNullOrBlank()) {
            Text(
                text = details.tagline,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!details.overview.isNullOrBlank()) Text(details.overview, style = MaterialTheme.typography.bodyLarge)
        val next = details.nextEpisode
        if (next != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Next Episode", style = MaterialTheme.typography.titleLarge)
                TabletLandscapeCard(
                    card = next,
                    onClick = { actions.play(next.id) },
                    onLongClick = { actions.openVideo(next.id) },
                    modifier = Modifier.width(dims.landscapeCardWidth),
                )
            }
        }
    }
}

private class FileFact(@DrawableRes val icon: Int, val label: String, val value: String)

@Composable
private fun VideoFileCard(details: VideoDetailsUi, modifier: Modifier = Modifier) {
    val facts = listOfNotNull(
        details.fileName.takeIf { it.isNotBlank() }?.let { FileFact(R.drawable.ic_video_file, "File", it) },
        details.location.takeIf { it.isNotBlank() }?.let { FileFact(R.drawable.ic_folder, "Location", it) },
        details.sizeLabel?.takeIf { it.isNotBlank() }?.let { FileFact(R.drawable.ic_sd_storage, "Size", it) },
        details.resolutionLabel?.takeIf { it.isNotBlank() }?.let { FileFact(R.drawable.ic_hd, "Video", it) },
        details.sourceLabel.takeIf { it.isNotBlank() }?.let { FileFact(R.drawable.ic_storage, "Source", it) },
    )
    if (facts.isEmpty()) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("File", style = MaterialTheme.typography.titleSmall)
            facts.forEach { fact ->
                Row {
                    NovaIcon(
                        fact.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = fact.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = fact.value,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
