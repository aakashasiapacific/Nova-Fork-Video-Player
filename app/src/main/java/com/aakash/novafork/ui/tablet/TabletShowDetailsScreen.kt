package com.aakash.novafork.ui.tablet

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.RatingPill
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.SeasonUi
import com.aakash.novafork.ui.model.ShowDetailsUi

private val EpisodeCellMin = 280.dp

/**
 * TV show page: full-screen artwork behind a heavy scrim, a header with poster, info and Play
 * next-up, a season selector, and the season's episodes as a grid of 16:9 cards.
 */
@Composable
internal fun TabletShowDetailsScreen(
    details: ShowDetailsUi,
    layout: DeviceLayout,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    val seasons = details.seasons
    var selectedSeason by rememberSaveable(details.key) {
        mutableIntStateOf(details.nextUp?.season ?: seasons.firstOrNull()?.number ?: 0)
    }
    val season = seasons.firstOrNull { it.number == selectedSeason } ?: seasons.firstOrNull()
    val nextUpId = details.nextUp?.videoId
    val playId = nextUpId ?: seasons.firstOrNull()?.episodes?.firstOrNull()?.videoId

    Box(modifier.fillMaxSize()) {
        TabletDetailsBackdrop(details.artwork)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(EpisodeCellMin),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = dims.gutter,
                end = dims.gutter,
                top = statusBarTop() + 12.dp,
                bottom = navigationBarBottom() + dims.sectionSpacing,
            ),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                ShowHeader(
                    details = details,
                    layout = layout,
                    onBack = actions::back,
                    onPlay = if (playId != null) {
                        { actions.play(playId) }
                    } else {
                        null
                    },
                )
            }
            if (season != null) {
                item(key = "seasons", span = { GridItemSpan(maxLineSpan) }, contentType = "seasons") {
                    SeasonSelector(
                        seasons = seasons,
                        selected = season.number,
                        onSelect = { selectedSeason = it },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (season.episodes.isNotEmpty()) {
                    item(key = "playSeason", span = { GridItemSpan(maxLineSpan) }, contentType = "playSeason") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(onClick = { actions.playSeason(details.key, season.number) }) {
                                ButtonLabel(R.drawable.ic_play_arrow, if (seasons.size > 1) "Play ${season.title}" else "Play all")
                            }
                            OutlinedButton(onClick = { actions.playSeason(details.key, season.number, shuffle = true) }) {
                                ButtonLabel(R.drawable.ic_shuffle, "Shuffle")
                            }
                        }
                    }
                }
                items(season.episodes, key = { "${it.season}:${it.episode}:${it.videoId}" }, contentType = { "episode" }) { episode ->
                    VideoMenuBox(
                        videoId = episode.videoId,
                        title = metaLine(details.title, episode.code, episode.title),
                        actions = actions,
                    ) { openMenu ->
                        TabletEpisodeCard(
                            episode = episode,
                            onClick = { actions.play(episode.videoId) },
                            onLongClick = openMenu,
                            isNextUp = episode.videoId == nextUpId,
                        )
                    }
                }
            } else {
                item(key = "noEpisodes", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                    Text(
                        text = "No episodes found for this show.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowHeader(
    details: ShowDetailsUi,
    layout: DeviceLayout,
    onBack: () -> Unit,
    onPlay: (() -> Unit)?,
) {
    val dims = LocalNovaDimens.current
    Column {
        TabletBackButton(onClick = onBack)
        Spacer(Modifier.height(20.dp))
        if (layout.isExpanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                TabletDetailsPoster(details.artwork, details.title, dims.detailPosterWidth)
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 760.dp),
                ) {
                    ShowHeadline(details)
                    Spacer(Modifier.height(24.dp))
                    ShowPlay(details, onPlay)
                    Spacer(Modifier.height(24.dp))
                    ShowText(details)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                TabletDetailsPoster(details.artwork, details.title, CompactDetailPosterWidth)
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    ShowHeadline(details)
                    Spacer(Modifier.height(20.dp))
                    ShowPlay(details, onPlay)
                }
            }
            Spacer(Modifier.height(24.dp))
            ShowText(details)
        }
    }
}

@Composable
private fun ShowHeadline(details: ShowDetailsUi) {
    val episodeCount = details.seasons.sumOf { it.episodes.size }
    Column {
        Text(
            text = details.title,
            style = MaterialTheme.typography.displaySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = metaLine(
            details.year?.toString(),
            if (details.seasons.isNotEmpty()) countLabel(details.seasons.size, "season") else null,
            if (episodeCount > 0) countLabel(episodeCount, "episode") else null,
        )
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
private fun ShowPlay(details: ShowDetailsUi, onPlay: (() -> Unit)?) {
    if (onPlay == null) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TabletPlayButton(label = details.playLabel, onClick = onPlay)
        val nextUp = details.nextUp
        if (nextUp != null && (nextUp.progress ?: 0f) > 0f) {
            WatchProgress(progress = nextUp.progress, positionMs = 0L, durationMs = nextUp.durationMs)
        }
    }
}

@Composable
private fun ShowText(details: ShowDetailsUi) {
    Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!details.tagline.isNullOrBlank()) {
            Text(
                text = details.tagline,
                style = MaterialTheme.typography.titleMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!details.overview.isNullOrBlank()) {
            Text(details.overview, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SeasonSelector(
    seasons: List<SeasonUi>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (seasons.size == 1) {
        Text(seasons.first().title, style = MaterialTheme.typography.titleLarge, modifier = modifier)
        return
    }
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        seasons.forEach { season ->
            FilterChip(
                selected = season.number == selected,
                onClick = { onSelect(season.number) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(season.title, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = season.episodes.size.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = LocalContentColor.current.copy(alpha = 0.65f),
                        )
                    }
                },
            )
        }
    }
}
