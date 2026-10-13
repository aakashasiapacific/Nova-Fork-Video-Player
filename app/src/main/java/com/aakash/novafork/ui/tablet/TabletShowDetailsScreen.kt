package com.aakash.novafork.ui.tablet

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.GlassCircleButton
import com.aakash.novafork.ui.common.GlassMenuItem
import com.aakash.novafork.ui.common.GlassStyle
import com.aakash.novafork.ui.common.PrimaryCapsuleButton
import com.aakash.novafork.ui.common.PullDownButton
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.SeasonUi
import com.aakash.novafork.ui.model.ShowDetailsUi
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaType

/**
 * TV show page: full-screen artwork, title, meta, "▶ Resume S1, E3" with Start Over, the
 * description; under the fold "Episodes" (season capsule + a carousel of 360dp episode cards
 * scrolled to the next-up episode), About and Information.
 */
@Composable
internal fun TabletShowDetailsScreen(
    details: ShowDetailsUi,
    layout: DeviceLayout,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val seasons = details.seasons
    val nextUp = details.nextUp
    var selectedSeason by rememberSaveable(details.key) {
        mutableIntStateOf(nextUp?.season ?: seasons.firstOrNull()?.number ?: 0)
    }
    val season = seasons.firstOrNull { it.number == selectedSeason } ?: seasons.firstOrNull()
    val playId = nextUp?.videoId ?: seasons.firstOrNull()?.episodes?.firstOrNull()?.videoId
    val started = (nextUp?.progress ?: 0f) > 0f
    val episodeCount = seasons.sumOf { it.episodes.size }
    val seasonsLabel = when {
        seasons.size > 1 -> countLabel(seasons.size, "Season")
        episodeCount > 0 -> countLabel(episodeCount, "Episode")
        else -> null
    }
    TabletDetailsPage(
        artwork = details.artwork,
        title = details.title,
        onBack = actions::back,
        menu = showDetailsMenu(details, actions),
        modifier = modifier,
        fold = {
            DetailsTitle(details.title)
            DetailsMeta(
                meta = metaLine(details.genres.firstOrNull(), details.year?.toString(), seasonsLabel),
                rating = details.rating,
            )
            if (playId != null) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryCapsuleButton(text = details.playLabel, onClick = { actions.play(playId) })
                    if (started && nextUp != null) {
                        Spacer(Modifier.width(12.dp))
                        GlassCircleButton(
                            onClick = { actions.play(nextUp.videoId, startOver = true) },
                            icon = R.drawable.ic_restart_alt,
                            contentDescription = "Start Over",
                            style = GlassStyle.Clear,
                        )
                    }
                }
                if (nextUp != null) {
                    ResumeLine(
                        progress = nextUp.progress,
                        label = metaLine(episodeTag(nextUp.season, nextUp.episode), remainingLabel(nextUp.progress, nextUp.durationMs)),
                    )
                }
            }
            DetailsOverview(details.overview, details.title)
        },
        below = {
            if (season != null) {
                detailsSection(key = "episodes", title = null) {
                    EpisodesHeader(seasons = seasons, season = season, onSeason = { selectedSeason = it })
                    EpisodeCarousel(season = season, nextUpId = nextUp?.videoId, actions = actions)
                }
            } else {
                detailsSection(key = "noEpisodes", title = "Episodes") {
                    Text(
                        text = "No episodes found for this show.",
                        style = NovaType.subhead,
                        color = NovaColors.OnMediaSecondary,
                        modifier = Modifier.padding(horizontal = LocalNovaDimens.current.gutter),
                    )
                }
            }
            aboutSection(
                title = details.title,
                overview = details.overview,
                subtitle = metaLine(details.tagline, details.genres.joinToString(", ")),
            )
            informationSection(
                listOf(
                    "Released" to details.year?.toString(),
                    "Seasons" to seasons.size.takeIf { it > 0 }?.toString(),
                    "Episodes" to episodeCount.takeIf { it > 0 }?.toString(),
                    "Genres" to details.genres.joinToString(", "),
                ),
            )
        },
    )
}

/** "Episodes" with the "Season 1 ▾" capsule beside it (a plain label for one season). */
@Composable
private fun EpisodesHeader(seasons: List<SeasonUi>, season: SeasonUi, onSeason: (Int) -> Unit) {
    val dims = LocalNovaDimens.current
    Row(
        Modifier.padding(start = dims.gutter, end = dims.gutter, bottom = dims.rowHeaderBottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Episodes", style = NovaType.title2, color = NovaColors.OnMedia, maxLines = 1)
        Spacer(Modifier.width(16.dp))
        if (seasons.size > 1) {
            PullDownButton(
                label = season.title,
                items = seasons.map { option ->
                    GlassMenuItem(option.title, checked = option.number == season.number) { onSeason(option.number) }
                },
                style = GlassStyle.Clear,
            )
        } else {
            Text(text = season.title, style = NovaType.subheadSemibold, color = NovaColors.OnMediaSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun EpisodeCarousel(season: SeasonUi, nextUpId: Long?, actions: AppActions) {
    val dims = LocalNovaDimens.current
    val state = rememberLazyListState()
    // Bring the next-up episode into view whenever a season is shown.
    LaunchedEffect(season.number, nextUpId) {
        val index = season.episodes.indexOfFirst { it.videoId == nextUpId }
        state.scrollToItem(index.coerceAtLeast(0))
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = dims.gutter),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
    ) {
        items(season.episodes, key = { "${it.season}:${it.episode}:${it.videoId}" }) { episode ->
            TabletEpisodeCard(
                episode = episode,
                onClick = { actions.play(episode.videoId) },
                menu = episodeMenu(episode, actions),
                modifier = Modifier.width(dims.episodeCardWidth),
            )
        }
    }
}

/**
 * "…": mark the next-up episode watched. No Identify here: it re-matches a single file, which
 * would split the show; each episode's own page has it.
 */
private fun showDetailsMenu(details: ShowDetailsUi, actions: AppActions): List<GlassMenuItem> {
    val nextUp = details.nextUp ?: return emptyList()
    val tag = episodeTag(nextUp.season, nextUp.episode)
    return listOf(
        GlassMenuItem(
            label = if (tag != null) "Mark $tag as Watched" else "Mark as Watched",
            icon = R.drawable.ic_check_circle,
        ) { actions.setWatched(nextUp.videoId, true) },
    )
}
