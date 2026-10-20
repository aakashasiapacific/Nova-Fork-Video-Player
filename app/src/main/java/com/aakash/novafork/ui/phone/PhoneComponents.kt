package com.aakash.novafork.ui.phone

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aakash.novafork.R
import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.GlassCircle
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.LandscapeArt
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.PosterArt
import com.aakash.novafork.ui.common.ProgressStrip
import com.aakash.novafork.ui.common.RatingPill
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.common.WatchedBadge
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.common.sourceTypeIcon
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.EpisodeUi
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.ShowCardUi
import com.aakash.novafork.ui.model.VideoCardUi
import kotlinx.coroutines.delay

private val ThumbShape = RoundedCornerShape(8.dp)

/** How long a pull waits for the refresh to start a scan before the spinner gives up. */
private const val PULL_START_GRACE_MS = 1_500L

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

/** 2:3 poster with rating, watched badge and progress on the art, then title and subtitle. */
@Composable
private fun PosterCardLayout(
    title: String,
    subtitle: String?,
    artwork: Artwork,
    onClick: () -> Unit,
    modifier: Modifier,
    rating: Float?,
    progress: Float?,
    watched: Boolean,
    onLongClick: (() -> Unit)?,
) {
    val dims = LocalNovaDimens.current
    val shape = RoundedCornerShape(dims.cardRadius)
    Column(
        modifier
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            PosterArt(artwork = artwork, title = title, modifier = Modifier.fillMaxSize())
            RatingPill(rating, Modifier.align(Alignment.TopStart).padding(6.dp), onImage = true)
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
        rating = card.rating,
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
        rating = show.rating,
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
    Column(
        modifier
            .width(width)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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

/** Episode in a season list: still with progress, code and title, duration, then a short overview. */
@Composable
internal fun PhoneEpisodeRow(
    episode: EpisodeUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    Column(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = dims.gutter, vertical = 10.dp),
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
                if (label.isNotBlank()) {
                    Text(
                        text = label,
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
) {
    val dims = LocalNovaDimens.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(dims.gridMinCell),
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = dims.gutter, end = dims.gutter, top = 8.dp, bottom = dims.sectionSpacing + LocalBottomBarSpace.current),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.key }) { item ->
            when (item) {
                is LibraryItemUi.Video -> VideoMenuBox(
                    videoId = item.card.id,
                    title = item.card.displayTitle(),
                    actions = actions,
                    showDetails = false,
                ) { openMenu ->
                    PhonePosterCard(item.card, onClick = { actions.openVideo(item.card.id) }, onLongClick = openMenu)
                }
                is LibraryItemUi.Show -> PhoneShowCard(item.card, onClick = { actions.openShow(item.card.key) })
            }
        }
        footer()
    }
}

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

/**
 * Pull-to-refresh that spins only for a refresh the user pulled for, until that scan finishes.
 * Scans started elsewhere (app start, a new source) are reported by the ScanBanner instead: a
 * spinner pinned over the page for a long poster fetch would just be noise.
 */
@Composable
internal fun PhonePullToRefresh(
    scanning: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    var pulled by remember { mutableStateOf(false) }
    var scanSeen by remember { mutableStateOf(false) }
    LaunchedEffect(pulled, scanning) {
        when {
            !pulled -> scanSeen = false
            scanning -> scanSeen = true
            scanSeen -> pulled = false
            else -> {
                delay(PULL_START_GRACE_MS)
                pulled = false
            }
        }
    }
    PullToRefreshBox(
        isRefreshing = pulled,
        onRefresh = {
            pulled = true
            onRefresh()
        },
        modifier = modifier,
        state = state,
        // iOS's refresh control: the spinner fades in as the page is pulled and spins while the
        // scan runs.
        indicator = {
            val fraction = state.distanceFraction.coerceIn(0f, 1f)
            if (pulled || fraction > 0f) {
                IosSpinner(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .then(indicatorModifier)
                        .padding(top = 18.dp)
                        .size(26.dp)
                        .graphicsLayer {
                            alpha = if (pulled) 1f else fraction
                            rotationZ = if (pulled) 0f else fraction * 180f
                        },
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        },
        content = content,
    )
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
