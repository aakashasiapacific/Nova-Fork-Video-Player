package com.aakash.novafork.ui.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.NovaDropdownMenu
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaMenuItem
import com.aakash.novafork.ui.common.SectionHeader
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.ShowDetailsUi

@Composable
fun PhoneShowDetailsScreen(
    details: ShowDetailsUi,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    val seasons = details.seasons
    val nextUp = details.nextUp
    var selectedSeason by rememberSaveable(details.key) {
        mutableIntStateOf(nextUp?.season ?: seasons.firstOrNull()?.number ?: 0)
    }
    val seasonIndex = seasons.indexOfFirst { it.number == selectedSeason }.coerceAtLeast(0)
    val season = seasons.getOrNull(seasonIndex)
    // Lazy keys must be unique; a file listed twice in a season must not crash the list.
    val episodes = remember(season) { season?.episodes.orEmpty().distinctBy { it.videoId } }
    val episodeCount = remember(seasons) { seasons.sumOf { it.episodes.size } }
    // Identify works on a file; any episode of the show leads to the right show search.
    val identifyVideoId = nextUp?.videoId ?: seasons.firstNotNullOfOrNull { it.episodes.firstOrNull()?.videoId }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val backdropHeight = detailsBackdropHeight(maxHeight)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = dims.sectionSpacing + LocalBottomBarSpace.current),
        ) {
            item(key = "header") {
                PhoneDetailsHeader(
                    artwork = details.artwork,
                    title = details.title,
                    meta = metaLine(
                        details.year?.toString(),
                        if (seasons.isNotEmpty()) countLabel(seasons.size, "season") else null,
                        if (episodeCount > 0) countLabel(episodeCount, "episode") else null,
                    ),
                    rating = details.rating,
                    genres = details.genres,
                    maxBackdropHeight = backdropHeight,
                )
            }
            item(key = "play") {
                Column(Modifier.padding(start = dims.gutter, end = dims.gutter, top = 20.dp)) {
                    Button(
                        onClick = { if (nextUp != null) actions.play(nextUp.videoId) },
                        enabled = nextUp != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        NovaIcon(R.drawable.ic_play_arrow, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            text = details.playLabel,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val progress = nextUp?.progress
                    if (progress != null && progress > 0f) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (nextUp != null && nextUp.title.isNotBlank()) {
                        Text(
                            text = metaLine(nextUp.code, nextUp.title),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            item(key = "overview") {
                OverviewBlock(
                    tagline = details.tagline,
                    overview = details.overview,
                    modifier = Modifier.padding(start = dims.gutter, end = dims.gutter, top = 16.dp),
                )
            }
            item(key = "seasons") {
                Column(Modifier.padding(top = dims.sectionSpacing)) {
                    if (seasons.size > 1) {
                        // Apple TV's season picker: "Season 1 ⌄" opening a menu of the seasons.
                        var seasonMenu by remember { mutableStateOf(false) }
                        Box(Modifier.padding(horizontal = dims.gutter)) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClickLabel = "Choose a season") { seasonMenu = true }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(season?.title ?: "Episodes", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                                NovaIcon(
                                    R.drawable.ic_expand_more,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 6.dp).size(18.dp),
                                )
                            }
                            NovaDropdownMenu(expanded = seasonMenu, onDismissRequest = { seasonMenu = false }) {
                                seasons.forEach { s ->
                                    NovaMenuItem(
                                        text = { Text(s.title) },
                                        leadingIcon = {
                                            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                                                if (s.number == season?.number) {
                                                    NovaIcon(R.drawable.ic_check, contentDescription = "Selected", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            seasonMenu = false
                                            selectedSeason = s.number
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        SectionHeader(
                            title = season?.title ?: "Episodes",
                            modifier = Modifier.padding(horizontal = dims.gutter),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (episodes.isEmpty()) {
                item(key = "noEpisodes") {
                    Text(
                        text = "No episodes in this season.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = dims.gutter, vertical = 16.dp),
                    )
                }
            } else {
                if (season != null) {
                    item(key = "playSeason") {
                        PhonePlayAllRow(
                            onPlayAll = { actions.playSeason(details.key, season.number) },
                            onShuffle = { actions.playSeason(details.key, season.number, shuffle = true) },
                            playLabel = if (seasons.size > 1) "Play Season" else "Play All",
                            modifier = Modifier.padding(start = dims.gutter, end = dims.gutter, bottom = 8.dp),
                        )
                    }
                }
                items(episodes, key = { "ep:${it.videoId}" }) { episode ->
                    VideoMenuBox(
                        videoId = episode.videoId,
                        title = metaLine(details.title, episode.code, episode.title),
                        actions = actions,
                    ) { openMenu ->
                        PhoneEpisodeRow(
                            episode = episode,
                            onClick = { actions.play(episode.videoId) },
                            onLongClick = openMenu,
                        )
                    }
                }
            }
        }
        PhoneDetailsTopBar(
            onBack = actions::back,
            modifier = Modifier.align(Alignment.TopStart),
            menu = if (identifyVideoId != null) {
                { close ->
                    NovaMenuItem(
                        text = { Text("Identify Show…") },
                        leadingIcon = { NovaIcon(R.drawable.ic_search, contentDescription = null) },
                        onClick = {
                            close()
                            actions.identify(identifyVideoId)
                        },
                    )
                    NovaMenuItem(
                        text = { Text("Change Artwork…") },
                        leadingIcon = { NovaIcon(R.drawable.ic_image, contentDescription = null) },
                        onClick = {
                            close()
                            actions.changeArtwork(identifyVideoId)
                        },
                    )
                }
            } else {
                null
            },
        )
    }
}
