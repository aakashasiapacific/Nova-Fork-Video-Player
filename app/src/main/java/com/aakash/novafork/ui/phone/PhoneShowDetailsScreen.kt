package com.aakash.novafork.ui.phone

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.AppShortcuts
import androidx.compose.ui.platform.LocalContext
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.CastRow
import com.aakash.novafork.ui.common.EndOfListGap
import com.aakash.novafork.ui.common.GreyCaption
import com.aakash.novafork.ui.common.NovaMenuDivider
import com.aakash.novafork.ui.common.RenameInLibraryMenuItems
import com.aakash.novafork.ui.common.RenameTitleDialog
import com.aakash.novafork.ui.common.TrailerPill
import com.aakash.novafork.ui.common.IosButton
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.Motion
import com.aakash.novafork.ui.common.NovaDropdownMenu
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaMenuItem
import com.aakash.novafork.ui.common.SectionHeader
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.EpisodeUi
import com.aakash.novafork.ui.model.ShowDetailsUi
import kotlin.math.abs

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
    val haptics = LocalHapticFeedback.current
    // Lazy keys must be unique; a file listed twice in a season must not crash the list.
    val episodes = remember(season) { season?.episodes.orEmpty().distinctBy { it.videoId } }
    val rows = remember(episodes) { episodeRowsWithGaps(episodes) }
    val episodeCount = remember(seasons) { seasons.sumOf { it.episodes.size } }
    // Identify works on a file; any episode of the show leads to the right show search.
    val identifyVideoId = nextUp?.videoId ?: seasons.firstNotNullOfOrNull { it.episodes.firstOrNull()?.videoId }

    var renamingTitle by remember { mutableStateOf(false) }
    if (renamingTitle) {
        RenameTitleDialog(
            title = details.title,
            onRename = { actions.setTitleOverride(details.titleKey, it) },
            onDismiss = { renamingTitle = false },
        )
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val backdropHeight = detailsBackdropHeight(maxHeight)
        val listState = rememberLazyListState()
        val titleCollapse = rememberTitleCollapse(listState, backdropHeight)
        DetailsStatusBar(details.artwork, listState, backdropHeight)
        val tint = rememberDetailsTint(details.artwork)
        val hasOverview = !details.tagline.isNullOrBlank() || !details.overview.isNullOrBlank()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = EndOfListGap + LocalBottomBarSpace.current),
        ) {
            item(key = "header") {
                PhoneDetailsHeader(
                    scrolled = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f },
                    artwork = details.artwork,
                    title = details.title,
                    meta = metaLine(
                        details.year?.toString(),
                        if (seasons.isNotEmpty()) countLabel(seasons.size, "season") else null,
                        if (episodeCount > 0) countLabel(episodeCount, "episode") else null,
                    ),
                    badges = listOfNotNull(details.ageRating),
                    note = details.nextEpisodeLabel,
                    rating = details.rating,
                    genres = details.genres,
                    maxBackdropHeight = backdropHeight,
                    modifier = Modifier.tintBand(tint, from = 1f, to = 0.8f),
                )
            }
            item(key = "play") {
                Column(Modifier.tintBand(tint, from = 0.8f, to = if (hasOverview) 0.55f else 0f).padding(start = dims.gutter, end = dims.gutter, top = 20.dp)) {
                    IosButton(
                        onClick = { if (nextUp != null) actions.play(nextUp.videoId) },
                        enabled = nextUp != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = rememberPlayColor(details.artwork, MaterialTheme.colorScheme.primary),
                            contentColor = Color.White,
                        ),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        NovaIcon(R.drawable.ic_play_arrow, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            text = details.playLabel,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (nextUp != null && nextUp.title.isNotBlank()) {
                        Text(
                            text = metaLine(nextUp.code, nextUp.title),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            val trailer = details.trailerUrl
            if (trailer != null) {
                item(key = "trailer") {
                    Row(Modifier.padding(start = dims.gutter, end = dims.gutter, top = 12.dp)) {
                        TrailerPill(trailer, onOpen = actions::openWebPage)
                    }
                }
            }
            item(key = "overview") {
                Column(Modifier.tintBand(tint, from = 0.55f, to = 0f).padding(start = dims.gutter, end = dims.gutter, top = 16.dp)) {
                    OverviewBlock(tagline = details.tagline, overview = details.overview)
                    Credits(
                        lines = listOf("Starring" to details.starring, "Created by" to details.createdBy),
                        modifier = Modifier.padding(top = 12.dp),
                        onName = actions::openPerson,
                    )
                }
            }
            if (details.cast.isNotEmpty()) {
                item(key = "cast") {
                    CastRow(details.cast, onPerson = actions::openPerson, gutter = dims.gutter, modifier = Modifier.padding(top = dims.sectionSpacing))
                }
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
                                // The name crossfades to the new season; the chevron turns while open.
                                Crossfade(targetState = season?.title ?: "Episodes", animationSpec = Motion.content(), label = "seasonTitle") { title ->
                                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                                }
                                val turn by animateFloatAsState(if (seasonMenu) 180f else 0f, Motion.content(), label = "seasonChevron")
                                NovaIcon(
                                    R.drawable.ic_expand_more,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .size(18.dp)
                                        .graphicsLayer { rotationZ = turn },
                                )
                            }
                            NovaDropdownMenu(expanded = seasonMenu, onDismissRequest = { seasonMenu = false }, title = "Seasons") {
                                seasons.forEach { s ->
                                    NovaMenuItem(
                                        text = { Text(s.title) },
                                        checked = s.number == season?.number,
                                        onClick = {
                                            seasonMenu = false
                                            selectedSeason = s.number
                                        },
                                    )
                                }
                                if (season != null && season.episodes.isNotEmpty()) {
                                    NovaMenuDivider()
                                    val allWatched = season.episodes.all { it.watched }
                                    NovaMenuItem(
                                        text = { Text(if (allWatched) "Mark Season Unwatched" else "Mark Season Watched") },
                                        onClick = {
                                            seasonMenu = false
                                            season.episodes.forEach { actions.setWatched(it.videoId, !allWatched) }
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
                    season?.let { GreyCaption(it.summaryCaption, Modifier.padding(horizontal = dims.gutter)) }
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
                items(rows, key = { it.key }) { row ->
                    if (row is EpisodeListRow.Missing) {
                        Text(
                            text = row.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = IosColors.SecondaryLabel,
                            modifier = Modifier.padding(horizontal = dims.gutter, vertical = 6.dp),
                        )
                        return@items
                    }
                    val episode = (row as EpisodeListRow.Row).episode
                    VideoMenuBox(
                        videoId = episode.videoId,
                        title = metaLine(details.title, episode.code, episode.title),
                        actions = actions,
                        // Another season's episodes fade in as it is picked.
                        modifier = Modifier
                            .animateItem(fadeInSpec = Motion.content(), placementSpec = null, fadeOutSpec = null)
                            .swipeSeasons(
                                hasPrevious = seasonIndex > 0,
                                hasNext = seasonIndex < seasons.lastIndex,
                            ) { step ->
                                seasons.getOrNull(seasonIndex + step)?.let {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedSeason = it.number
                                }
                            },
                    ) { openMenu ->
                        PhoneEpisodeRow(
                            episode = episode,
                            onClick = { actions.play(episode.videoId) },
                            onLongClick = openMenu,
                            upNext = episode.videoId == nextUp?.videoId,
                        )
                    }
                }
            }
        }
        PhoneDetailsTopBar(
            onBack = actions::back,
            modifier = Modifier.align(Alignment.TopStart),
            title = details.title,
            collapsed = titleCollapse,
            menu = { close ->
                    val context = LocalContext.current
                    NovaMenuItem(
                        text = { Text("Add to Home Screen") },
                        leadingIcon = { NovaIcon(R.drawable.ic_add, contentDescription = null) },
                        onClick = {
                            close()
                            AppShortcuts.pinShow(context, details.key, details.title)
                        },
                    )
                    val allEpisodes = seasons.flatMap { it.episodes }
                    val showWatched = allEpisodes.isNotEmpty() && allEpisodes.all { it.watched }
                    NovaMenuItem(
                        text = { Text(if (showWatched) "Mark Show Unwatched" else "Mark Show Watched") },
                        leadingIcon = {
                            NovaIcon(
                                if (showWatched) R.drawable.ic_visibility_off else R.drawable.ic_check_circle,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            close()
                            allEpisodes.forEach { actions.setWatched(it.videoId, !showWatched) }
                        },
                    )
                    RenameInLibraryMenuItems(
                        titleKey = details.titleKey,
                        customised = details.titleCustomised,
                        close = close,
                        onRename = { renamingTitle = true },
                        onReset = { actions.setTitleOverride(details.titleKey, null) },
                    )
                    if (identifyVideoId != null) NovaMenuItem(
                        text = { Text("Identify Show…") },
                        leadingIcon = { NovaIcon(R.drawable.ic_search, contentDescription = null) },
                        onClick = {
                            close()
                            actions.identify(identifyVideoId)
                        },
                    )
                    if (identifyVideoId != null) NovaMenuItem(
                        text = { Text("Change Artwork…") },
                        leadingIcon = { NovaIcon(R.drawable.ic_image, contentDescription = null) },
                        onClick = {
                            close()
                            actions.changeArtwork(identifyVideoId)
                        },
                    )
            },
        )
    }
}

/**
 * Swiping sideways across the episodes changes season: left to the next, right to the one before,
 * with a tick. A swipe with no season that way is left alone, so a swipe right on the first
 * season still takes the page back; up and down still scroll.
 */
private fun Modifier.swipeSeasons(hasPrevious: Boolean, hasNext: Boolean, onSwipe: (step: Int) -> Unit): Modifier =
    pointerInput(hasPrevious, hasNext) {
        val slop = viewConfiguration.touchSlop
        val commit = SeasonSwipeCommit.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var dx = 0f
            var dy = 0f
            var step = 0
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val delta = change.positionChange()
                dx += delta.x
                dy += delta.y
                if (step == 0) {
                    // A scroll, or a swipe this way with no season to go to: not ours.
                    if (abs(dy) > slop && abs(dy) >= abs(dx)) return@awaitEachGesture
                    if (abs(dx) > slop && abs(dx) > abs(dy)) {
                        val way = if (dx < 0f) 1 else -1
                        if ((way > 0 && hasNext) || (way < 0 && hasPrevious)) step = way else return@awaitEachGesture
                    }
                }
                if (step != 0) change.consume()
            }
            if (step != 0 && abs(dx) > commit) onSwipe(step)
        }
    }

/** How far a swipe across the episodes must go to change season. */
private val SeasonSwipeCommit = 64.dp

/** A line of a season's list: an episode, or a quiet note that some numbers between two are missing. */
private sealed interface EpisodeListRow {
    val key: String

    class Row(val episode: EpisodeUi) : EpisodeListRow {
        override val key get() = "ep:${episode.videoId}"
    }

    class Missing(val first: Int, val last: Int) : EpisodeListRow {
        override val key get() = "missing:$first"
        val label get() = if (first == last) "Episode $first missing" else "Episodes $first–$last missing"
    }
}

/**
 * [episodes] with a [EpisodeListRow.Missing] between neighbours whose numbers skip (E3 then E5).
 * Only gaps inside the numbers the season has: unnumbered episodes get none, and a double episode
 * ("E3-E4", read from its code) counts as covering both numbers.
 */
private fun episodeRowsWithGaps(episodes: List<EpisodeUi>): List<EpisodeListRow> {
    val out = ArrayList<EpisodeListRow>(episodes.size)
    var previousEnd: Int? = null
    for (episode in episodes) {
        val start = episode.episode.takeIf { it > 0 }
        if (start != null) {
            val end = Regex("\\d+").findAll(episode.code).lastOrNull()?.value?.toIntOrNull()?.coerceAtLeast(start) ?: start
            val before = previousEnd
            if (before != null && start > before + 1) out += EpisodeListRow.Missing(before + 1, start - 1)
            previousEnd = maxOf(before ?: end, end)
        }
        out += EpisodeListRow.Row(episode)
    }
    return out
}
