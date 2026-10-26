package com.aakash.novafork.ui.phone

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aakash.novafork.R
import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EndOfListGap
import com.aakash.novafork.ui.common.GlassCircle
import com.aakash.novafork.ui.common.Hairline
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.LandscapeArt
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.PosterArt
import com.aakash.novafork.ui.common.ProgressStrip
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.common.WatchedBadge
import com.aakash.novafork.ui.common.iosScrollIndicator
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.common.pressDim
import com.aakash.novafork.ui.common.pressScale
import com.aakash.novafork.ui.common.searchHighlighted
import com.aakash.novafork.ui.common.sourceTypeIcon
import com.aakash.novafork.ui.common.staggeredAppear
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.EpisodeUi
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.ShowCardUi
import com.aakash.novafork.ui.model.TimelineMonth
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.theme.NovaColors
import kotlinx.coroutines.delay

private val ThumbShape = RoundedCornerShape(8.dp)

// ---------------------------------------------------------------------------------------------
// Labels
// ---------------------------------------------------------------------------------------------

/** Episodes read better under their show's name ("The Bear" / "S2 · E3 · Fishes"). */
internal fun VideoCardUi.displayTitle(): String {
    val show = showTitle
    return if (kind == MediaKind.EPISODE && !show.isNullOrBlank()) show else title
}

internal fun VideoCardUi.displaySubtitle(): String? {
    val show = showTitle
    return if (kind == MediaKind.EPISODE && !show.isNullOrBlank()) {
        metaLine(subtitle, title).ifEmpty { null }
    } else {
        subtitle
    }
}

/** "1 video", "12 videos". */
internal fun countLabel(count: Int, singular: String, plural: String = singular + "s"): String =
    "$count ${if (count == 1) singular else plural}"

/** "45m", "1h 32m"; clock format for clips shorter than a minute. */
internal fun durationLabel(durationMs: Long?): String? {
    if (durationMs == null || durationMs <= 0) return null
    return if (durationMs < 60_000) Formats.clock(durationMs) else Formats.runtime(((durationMs + 30_000) / 60_000).toInt())
}

/** "1h 12m left" for a partly watched card, else null. */
internal fun remainingLabel(card: VideoCardUi): String? {
    val duration = card.durationMs ?: return null
    val progress = card.progress ?: return null
    if (duration <= 0 || progress <= 0f) return null
    return Formats.remaining((duration * progress.coerceIn(0f, 1f)).toLong(), duration)
}

@DrawableRes
internal fun SourceType.iconRes(): Int = when (this) {
    SourceType.DEVICE -> R.drawable.ic_phone_android
    SourceType.FOLDER, SourceType.SMB, SourceType.STREAM, SourceType.FTP, SourceType.SFTP, SourceType.NFS,
    SourceType.UPNP, SourceType.WEBDAV, SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX,
    -> sourceTypeIcon(this)
}

// ---------------------------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------------------------

/**
 * 2:3 poster with the watched badge and progress on the art, then title and subtitle. No rating
 * on posters: Apple TV keeps it for the title's own page (owner).
 */
@Composable
private fun PosterCardLayout(
    title: String,
    subtitle: String?,
    artwork: Artwork,
    onClick: () -> Unit,
    modifier: Modifier,
    progress: Float?,
    watched: Boolean,
    onLongClick: (() -> Unit)?,
) {
    val dims = LocalNovaDimens.current
    val shape = RoundedCornerShape(dims.cardRadius)
    // Apple TV's press: the card sinks in and darkens while held, no ripple.
    val press = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(press, pressed = CardPressScale)
            .clip(shape)
            .combinedClickable(interactionSource = press, indication = null, onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pressDim(press),
        ) {
            PosterArt(artwork = artwork, title = title, modifier = Modifier.fillMaxSize())
            if (watched) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            ProgressStrip(progress, Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = searchHighlighted(title),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Always present so cards in a row or grid line up.
        Text(
            text = subtitle.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** Poster card for a video. Width comes from [modifier] (row) or the grid cell. */
@Composable
internal fun PhonePosterCard(
    card: VideoCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    PosterCardLayout(
        title = card.displayTitle(),
        subtitle = card.displaySubtitle(),
        artwork = card.artwork,
        onClick = onClick,
        modifier = modifier,
        progress = card.progress,
        watched = card.watched,
        onLongClick = onLongClick,
    )
}

/** Poster card for a TV show ("24 episodes"). */
@Composable
internal fun PhoneShowCard(
    show: ShowCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PosterCardLayout(
        title = show.title,
        subtitle = countLabel(show.episodeCount, "episode"),
        artwork = show.artwork,
        onClick = onClick,
        modifier = modifier,
        progress = null,
        watched = show.episodeCount > 0 && show.unwatchedCount == 0,
        onLongClick = null,
    )
}

/** 16:9 card (continue watching, next episode): art with progress, text below, as in Apple TV. */
@Composable
internal fun PhoneLandscapeCard(
    card: VideoCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    title: String = card.displayTitle(),
    subtitle: String? = card.displaySubtitle(),
    width: Dp = LocalNovaDimens.current.landscapeCardWidth,
) {
    WideCardLayout(
        title = title,
        subtitle = subtitle,
        artwork = card.artwork,
        artTitle = card.title,
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        width = width,
        progress = card.progress,
        watched = card.watched,
    )
}

/**
 * Apple TV's row card for a movie or video: 16:9 art (backdrop, still or a frame) with the title
 * and caption below, opening its page rather than playing.
 */
@Composable
internal fun PhoneWideVideoCard(
    card: VideoCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    WideCardLayout(
        title = card.displayTitle(),
        subtitle = card.displaySubtitle(),
        artwork = card.artwork,
        artTitle = card.title,
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        progress = card.progress,
        watched = card.watched,
    )
}

/** [PhoneWideVideoCard] for a TV show ("24 episodes"). */
@Composable
internal fun PhoneWideShowCard(
    show: ShowCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WideCardLayout(
        title = show.title,
        subtitle = countLabel(show.episodeCount, "episode"),
        artwork = show.artwork,
        artTitle = show.title,
        onClick = onClick,
        modifier = modifier,
        progress = null,
        watched = show.episodeCount > 0 && show.unwatchedCount == 0,
    )
}

@Composable
private fun WideCardLayout(
    title: String,
    subtitle: String?,
    artwork: Artwork,
    artTitle: String,
    onClick: () -> Unit,
    modifier: Modifier,
    progress: Float?,
    watched: Boolean,
    onLongClick: (() -> Unit)? = null,
    width: Dp = LocalNovaDimens.current.landscapeCardWidth,
) {
    val shape = RoundedCornerShape(LocalNovaDimens.current.cardRadius)
    val press = remember { MutableInteractionSource() }
    Column(
        modifier
            .width(width)
            .pressScale(press, pressed = CardPressScale)
            .clip(shape)
            .combinedClickable(interactionSource = press, indication = null, onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pressDim(press),
        ) {
            // A card without any 16:9 picture names its title on the placeholder.
            LandscapeArt(artwork = artwork, title = artTitle, modifier = Modifier.fillMaxSize())
            if (watched) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            ProgressStrip(progress, Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** How far a poster or card sinks in while pressed. */
private const val CardPressScale = 0.96f

/** List row for a video in a folder: 16:9 thumbnail, title, subtitle, then [trailing] (a button). */
@Composable
internal fun PhoneVideoRow(
    card: VideoCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val dims = LocalNovaDimens.current
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = dims.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(ThumbShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            LandscapeArt(artwork = card.artwork, title = card.title, modifier = Modifier.fillMaxSize())
            if (card.watched) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(4.dp))
            ProgressStrip(card.progress, Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = card.displayTitle(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = card.displaySubtitle()
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}

/** "Play All" and "Shuffle" side by side, for a folder, a season or a playlist. */
@Composable
internal fun PhonePlayAllRow(
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    playLabel: String = "Play All",
) {
    // Apple's Play and Shuffle: two equal grey pills with the accent on icon and label.
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GreyActionPill(R.drawable.ic_play_arrow, playLabel, onPlayAll, Modifier.weight(1f))
        GreyActionPill(R.drawable.ic_shuffle, "Shuffle", onShuffle, Modifier.weight(1f))
    }
}

@Composable
private fun GreyActionPill(@DrawableRes icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NovaIcon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = accent, modifier = Modifier.padding(start = 6.dp))
    }
}

/**
 * Episode in a season list: still with progress, code and title, duration, then a short
 * overview. The one to watch next ([upNext]) says so in blue above its title.
 */
@Composable
internal fun PhoneEpisodeRow(
    episode: EpisodeUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    upNext: Boolean = false,
) {
    val dims = LocalNovaDimens.current
    Column(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = dims.gutter, vertical = 10.dp)
            // Finished episodes step back a little, so the ones still to watch stand out.
            .graphicsLayer { alpha = if (episode.watched && !upNext) WatchedEpisodeAlpha else 1f },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(132.dp)
                    .aspectRatio(16f / 9f)
                    .clip(ThumbShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                LandscapeArt(artwork = episode.artwork, title = episode.title, modifier = Modifier.fillMaxSize())
                if (episode.watched) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(4.dp))
                ProgressStrip(episode.progress, Modifier.align(Alignment.BottomStart))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                // Apple TV's small grey caps above an episode's title: "EPISODE 3".
                val label = if (episode.episode > 0) "EPISODE ${episode.episode}" else episode.code.uppercase()
                if (label.isNotBlank() || upNext) {
                    Text(
                        text = buildAnnotatedString {
                            if (upNext) {
                                withStyle(SpanStyle(color = NovaColors.IosBlue)) { append("UP NEXT") }
                                if (label.isNotBlank()) append(" · ")
                            }
                            append(label)
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = metaLine(durationLabel(episode.durationMs), episode.airDate)
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }
        val overview = episode.overview
        if (!overview.isNullOrBlank()) {
            Text(
                text = overview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Poster grid for library items (Library and Search). */
@Composable
internal fun PhoneLibraryGrid(
    items: List<LibraryItemUi>,
    actions: AppActions,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    /** More full-width content after the cards (search's music results). */
    footer: LazyGridScope.() -> Unit = {},
    /** Full-width content before the cards (search's Top Hit). */
    header: LazyGridScope.() -> Unit = {},
) {
    val dims = LocalNovaDimens.current
    val seen = remember { mutableSetOf<Any>() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(pinchedCell ?: dims.gridMinCell),
        modifier = modifier.fillMaxSize().pinchColumns(state).iosScrollIndicator(state),
        state = state,
        contentPadding = PaddingValues(start = dims.gutter, end = dims.gutter, top = 8.dp, bottom = EndOfListGap + LocalBottomBarSpace.current),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        header()
        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
            val appear = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).staggeredAppear(item.key, index, seen)
            when (item) {
                is LibraryItemUi.Video -> VideoMenuBox(
                    videoId = item.card.id,
                    title = item.card.displayTitle(),
                    actions = actions,
                    showDetails = false,
                    modifier = appear,
                ) { openMenu ->
                    PhonePosterCard(item.card, onClick = { actions.openVideo(item.card.id) }, onLongClick = openMenu)
                }
                is LibraryItemUi.Show -> PhoneShowCard(item.card, onClick = { actions.openShow(item.card.key) }, modifier = appear)
            }
        }
        footer()
    }
}

/** The Recently Added timeline: a month heading ("October", "March 2024") over each month's videos, as Photos groups. */
@Composable
internal fun PhoneTimelineGrid(
    months: List<TimelineMonth>,
    actions: AppActions,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    /** Full-width content above the first month (Search's recent searches). */
    header: (@Composable () -> Unit)? = null,
) {
    val dims = LocalNovaDimens.current
    val seen = remember { mutableSetOf<Any>() }
    // Where each month's heading sits in the grid, to know which month is under the top edge.
    val headings = remember(months, header != null) {
        var index = if (header != null) 1 else 0
        months.map { month -> index.also { index += 1 + month.cards.size } }
    }
    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(pinchedCell ?: dims.gridMinCell),
            modifier = Modifier.fillMaxSize().pinchColumns(state).iosScrollIndicator(state),
            state = state,
            contentPadding = PaddingValues(start = dims.gutter, end = dims.gutter, top = 0.dp, bottom = EndOfListGap + LocalBottomBarSpace.current),
            horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
            months.forEach { month ->
                item(key = "month:" + month.title, span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        month.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                itemsIndexed(month.cards, key = { _, card -> "v:" + card.id }) { index, card ->
                    val appear = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).staggeredAppear("v:" + card.id, index, seen)
                    VideoMenuBox(videoId = card.id, title = card.displayTitle(), actions = actions, showDetails = false, modifier = appear) { openMenu ->
                        PhonePosterCard(card, onClick = { actions.openVideo(card.id) }, onLongClick = openMenu)
                    }
                }
            }
        }
        PinnedMonth(months, headings, state)
    }
}

/**
 * The month being scrolled through stays pinned at the top, as Photos does; the next month's
 * heading pushes it up and off as it arrives.
 */
@Composable
private fun PinnedMonth(months: List<TimelineMonth>, headings: List<Int>, state: LazyGridState) {
    val dims = LocalNovaDimens.current
    val current by remember(headings) {
        derivedStateOf {
            val first = state.firstVisibleItemIndex
            val top = headings.firstOrNull() ?: 0
            // At rest on the first heading the heading itself shows (and above it, nothing to pin).
            if (first < top || (first == top && state.firstVisibleItemScrollOffset == 0)) -1 else headings.indexOfLast { it <= first }
        }
    }
    val month = months.getOrNull(current) ?: return
    var height by remember { mutableIntStateOf(0) }
    Text(
        month.title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { height = it.height }
            .graphicsLayer {
                // The next heading, once it reaches the pinned one, pushes it up.
                val next = headings.getOrNull(current + 1)
                val arriving = next?.let { index -> state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } }
                translationY = arriving?.let { (it.offset.y - height).coerceAtMost(0).toFloat() } ?: 0f
            }
            .background(MaterialTheme.colorScheme.background)
            // A hairline under it, so it reads as a header with the posters passing beneath.
            .drawBehind {
                val line = Hairline.toPx()
                drawRect(IosColors.Separator, topLeft = Offset(0f, size.height - line), size = Size(size.width, line))
            }
            .padding(horizontal = dims.gutter)
            .padding(top = 12.dp, bottom = 6.dp),
    )
}

/**
 * The poster size picked by pinching a poster grid (All Videos, Recently Added), shared by both and
 * kept while the app runs; null = the default size. It is a size, not a count, so turning the
 * phone still fits as many as there is room for.
 */
private var pinchedCell by mutableStateOf<Dp?>(null)

/**
 * Pinching a poster grid, as in Photos: spreading two fingers makes the posters bigger (one less
 * per row), pinching them together smaller (one more), one step per pinch and with a tick; the
 * posters glide to their new places. One finger still scrolls as before.
 */
@Composable
private fun Modifier.pinchColumns(state: LazyGridState): Modifier {
    val dims = LocalNovaDimens.current
    val haptics = LocalHapticFeedback.current
    return pointerInput(state, dims) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var startDistance = 0f
            var stepped = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val fingers = event.changes.filter { it.pressed }
                if (fingers.size >= 2) {
                    val distance = (fingers[0].position - fingers[1].position).getDistance()
                    if (startDistance == 0f) {
                        startDistance = distance.coerceAtLeast(1f)
                    } else if (!stepped) {
                        val ratio = distance / startDistance
                        val step = when {
                            ratio > 1.3f -> -1
                            ratio < 0.77f -> 1
                            else -> 0
                        }
                        if (step != 0) {
                            stepped = true
                            val info = state.layoutInfo
                            val columns = (info.visibleItemsInfo.maxOfOrNull { it.column } ?: -1) + 1
                            val target = (columns + step).coerceIn(MinGridColumns, maxOf(MaxGridColumns, columns))
                            if (columns > 0 && target != columns) {
                                val spacing = dims.rowSpacing.toPx()
                                val available = info.viewportSize.width - 2 * dims.gutter.toPx()
                                // The largest cell that still fits exactly [target] in a row.
                                val cell = (available + spacing) / target - spacing - 1f
                                pinchedCell = cell.coerceAtLeast(1f).toDp()
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                    // Two fingers belong to the pinch, not to scrolling.
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

private const val MinGridColumns = 2
private const val MaxGridColumns = 4

// ---------------------------------------------------------------------------------------------
// Page scaffolding
// ---------------------------------------------------------------------------------------------

/**
 * iOS 26's navigation bar: a round glass back button, the title centred in 17pt semibold and the
 * page's buttons on the right. A root page (no [onBack]) gets a bold title on the left instead.
 */
@Composable
internal fun PhoneTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 12.dp),
    ) {
        if (onBack != null) {
            GlassCircle(onClick = onBack, description = "Back", size = 40.dp, modifier = Modifier.align(Alignment.CenterStart)) {
                NovaIcon(R.drawable.ic_arrow_back, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 100.dp),
            )
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp, end = 100.dp),
            )
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * Centres [content] in the available space but still scrolls when it does not fit (landscape),
 * and keeps pull-to-refresh working on empty pages.
 */
@Composable
internal fun CenteredScrollable(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val minHeight = if (constraints.hasBoundedHeight) maxHeight else 0.dp
        Box(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
internal fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        IosSpinner()
    }
}

/** Icon + label content for buttons. */
@Composable
internal fun RowScope.ButtonContent(@DrawableRes icon: Int, label: String) {
    NovaIcon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** How far a finished episode's row fades back. */
private const val WatchedEpisodeAlpha = 0.55f
