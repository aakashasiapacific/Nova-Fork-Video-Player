package com.aakash.novafork.ui.tablet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.ActivityIndicator
import com.aakash.novafork.ui.common.ArtImage
import com.aakash.novafork.ui.common.ArtPlaceholder
import com.aakash.novafork.ui.common.GlassMenu
import com.aakash.novafork.ui.common.GlassMenuItem
import com.aakash.novafork.ui.common.NovaRefreshIndicator
import com.aakash.novafork.ui.common.PosterArt
import com.aakash.novafork.ui.common.ProgressBar
import com.aakash.novafork.ui.common.SmallCapsText
import com.aakash.novafork.ui.common.WatchedBadge
import com.aakash.novafork.ui.common.contextLift
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.common.novaClickable
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.EpisodeUi
import com.aakash.novafork.ui.model.HeroTarget
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.ShowCardUi
import com.aakash.novafork.ui.model.SourceUi
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaMotion
import com.aakash.novafork.ui.theme.NovaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// ─────────────────────────────── Insets ───────────────────────────────

/** Height of the status bar. */
@Composable
internal fun statusBarTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** Bottom inset of the navigation bar: scrolling content passes behind it but ends above it. */
@Composable
internal fun navigationBarBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

// ─────────────────────────────── Labels ───────────────────────────────

/** First line under a card: the show for episodes (so "Pilot" never stands alone), else the title. */
internal fun VideoCardUi.primaryLine(): String =
    if (kind == MediaKind.EPISODE && !showTitle.isNullOrBlank()) showTitle else title

/** "S2, E3", "E3", or null when the numbers are unknown. */
internal fun episodeTag(season: Int?, episode: Int?): String? = when {
    season != null && episode != null -> "S$season, E$episode"
    episode != null -> "E$episode"
    season != null -> "Season $season"
    else -> null
}

internal fun VideoCardUi.episodeTag(): String? = episodeTag(season, episode)

/** Caption under a 16:9 card: "S1, E3 · Pilot", "2021 · 2h 35m", "Stream". */
internal fun VideoCardUi.caption(): String? = when (kind) {
    MediaKind.EPISODE ->
        if (!showTitle.isNullOrBlank()) metaLine(episodeTag(), title).ifEmpty { null } else episodeTag() ?: subtitle
    else -> subtitle
}

/** Caption under a 2:3 poster: the year for movies, "S1, E3" for episodes. */
internal fun VideoCardUi.posterCaption(): String? = when (kind) {
    MediaKind.MOVIE -> year?.toString() ?: subtitle
    MediaKind.EPISODE -> episodeTag() ?: subtitle
    MediaKind.VIDEO, MediaKind.STREAM -> subtitle
}

/** Continue Watching caption: "S2, E3 · 20m left". */
internal fun VideoCardUi.continueCaption(): String? =
    metaLine(if (kind == MediaKind.EPISODE) episodeTag() else null, remainingLabel()).ifEmpty { caption() }

/** "42m left" for a partly watched card, null otherwise. */
internal fun VideoCardUi.remainingLabel(): String? = remainingLabel(progress, durationMs)

internal fun remainingLabel(progress: Float?, durationMs: Long?, positionMs: Long = 0L): String? {
    val p = progress ?: return null
    val duration = durationMs ?: return null
    if (p <= 0f || duration <= 0L) return null
    val position = if (positionMs > 0L) positionMs else (duration * p.coerceIn(0f, 1f)).toLong()
    return Formats.remaining(position, duration)
}

/** "2022 · 3 Seasons". */
internal fun ShowCardUi.caption(): String = metaLine(
    year?.toString(),
    when {
        seasonCount > 0 -> countLabel(seasonCount, "Season")
        episodeCount > 0 -> countLabel(episodeCount, "Episode")
        else -> null
    },
)

/** Episode length as Apple writes it: "47 min", "1 hr 5 min". */
internal fun minutesLabel(durationMs: Long?): String? {
    if (durationMs == null || durationMs <= 0L) return null
    val minutes = ((durationMs + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$minutes min"
        rest == 0 -> "$hours hr"
        else -> "$hours hr $rest min"
    }
}

internal fun countLabel(count: Int, singular: String, plural: String = singular + "s"): String =
    if (count == 1) "1 $singular" else "$count $plural"

/** "EPISODE 3" / "EPISODES 3–4" for the small caps line of an episode card. */
internal fun EpisodeUi.capsLabel(): String {
    val numbers = code.removePrefix("E").replace("–E", "–")
    return when {
        code.isBlank() -> "Episode $episode"
        numbers.contains('–') -> "Episodes $numbers"
        else -> "Episode $numbers"
    }
}

/** "4K" / "HD" from "3840×2160 · 4K"; null for SD or unknown. */
internal fun qualityBadge(resolutionLabel: String?): String? = when {
    resolutionLabel == null -> null
    resolutionLabel.endsWith("4K") -> "4K"
    resolutionLabel.contains("HD") -> "HD"
    else -> null
}

// ─────────────────────────────── Navigation helpers ───────────────────────────────

/** Streams play straight away; everything else opens its details page. */
internal fun AppActions.openCard(card: VideoCardUi) {
    if (card.kind == MediaKind.STREAM) play(card.id) else openVideo(card.id)
}

internal fun AppActions.openItem(item: LibraryItemUi) {
    when (item) {
        is LibraryItemUi.Video -> openCard(item.card)
        is LibraryItemUi.Show -> openShow(item.card.key)
    }
}

internal fun AppActions.openHero(target: HeroTarget) {
    when (target) {
        is HeroTarget.Video -> openVideo(target.id)
        is HeroTarget.Show -> openShow(target.key)
    }
}

/** Saved streams are sources of their own; removing one goes through the usual confirmation. */
internal fun AppActions.confirmRemoveStream(sourceId: Long, title: String) {
    showDialog(DialogUi.ConfirmRemoveSource(SourceUi(id = sourceId, type = SourceType.STREAM, name = title, detail = "")))
}

internal val LibraryItemUi.artwork: Artwork
    get() = when (this) {
        is LibraryItemUi.Video -> card.artwork
        is LibraryItemUi.Show -> card.artwork
    }

// ─────────────────────────────── Context menus ───────────────────────────────

/** Long-press menu of a video card: play, details, watched state, Identify (or Remove for streams). */
internal fun videoMenu(card: VideoCardUi, actions: AppActions, includeDetails: Boolean = true): List<GlassMenuItem> {
    val stream = card.kind == MediaKind.STREAM
    val started = (card.progress ?: 0f) > 0f
    return buildList {
        add(GlassMenuItem(if (started) "Resume" else "Play", icon = R.drawable.ic_play_arrow) { actions.play(card.id) })
        if (started) {
            add(GlassMenuItem("Play from Beginning", icon = R.drawable.ic_restart_alt) { actions.play(card.id, startOver = true) })
        }
        if (includeDetails) {
            add(GlassMenuItem("Details", icon = R.drawable.ic_info) { actions.openVideo(card.id) })
        }
        if (!stream) {
            add(
                GlassMenuItem(
                    label = if (card.watched) "Mark as Unwatched" else "Mark as Watched",
                    icon = if (card.watched) R.drawable.ic_visibility_off else R.drawable.ic_check_circle,
                    startsSection = true,
                ) { actions.setWatched(card.id, !card.watched) },
            )
            add(GlassMenuItem("Identify…", icon = R.drawable.ic_edit) { actions.identify(card.id) })
        } else {
            val sourceId = card.sourceId
            if (sourceId != null) {
                add(
                    GlassMenuItem("Remove Stream", icon = R.drawable.ic_delete, destructive = true, startsSection = true) {
                        actions.confirmRemoveStream(sourceId, card.title)
                    },
                )
            }
        }
    }
}

internal fun showMenu(key: String, actions: AppActions): List<GlassMenuItem> =
    listOf(GlassMenuItem("Go to Show", icon = R.drawable.ic_tv) { actions.openShow(key) })

internal fun episodeMenu(episode: EpisodeUi, actions: AppActions): List<GlassMenuItem> {
    val started = (episode.progress ?: 0f) > 0f
    return buildList {
        add(GlassMenuItem(if (started) "Resume" else "Play", icon = R.drawable.ic_play_arrow) { actions.play(episode.videoId) })
        if (started) {
            add(GlassMenuItem("Play from Beginning", icon = R.drawable.ic_restart_alt) { actions.play(episode.videoId, startOver = true) })
        }
        add(GlassMenuItem("Details", icon = R.drawable.ic_info) { actions.openVideo(episode.videoId) })
        add(
            GlassMenuItem(
                label = if (episode.watched) "Mark as Unwatched" else "Mark as Watched",
                icon = if (episode.watched) R.drawable.ic_visibility_off else R.drawable.ic_check_circle,
                startsSection = true,
            ) { actions.setWatched(episode.videoId, !episode.watched) },
        )
    }
}

internal fun libraryItemMenu(item: LibraryItemUi, actions: AppActions): List<GlassMenuItem> = when (item) {
    is LibraryItemUi.Video -> videoMenu(item.card, actions)
    is LibraryItemUi.Show -> showMenu(item.card.key, actions)
}

// ─────────────────────────────── Artwork and cards ───────────────────────────────

/** 16:9 card art: still, backdrop, the poster cropped, then a frame of the video. */
@Composable
internal fun WideArt(artwork: Artwork, title: String, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    val candidates = remember(artwork) {
        listOfNotNull(artwork.stillUrl, artwork.backdropUrl, artwork.posterUrl, artwork.frameSource).distinct()
    }
    ArtImage(
        candidates = candidates,
        contentDescription = title,
        modifier = modifier,
        frameSource = artwork.frameSource,
    ) {
        ArtPlaceholder(seed = artwork.seed.ifEmpty { title }, title = if (showTitle) title else null)
    }
}

/** Progress capsule inside the artwork (Continue Watching), or a check for watched items. */
@Composable
private fun BoxScope.CardOverlay(progress: Float?, watched: Boolean) {
    if (progress != null && progress > 0f) {
        ProgressBar(
            progress = progress,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
        )
    } else if (watched) {
        WatchedBadge(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        )
    }
}

/** Title (Subhead semibold) and caption (Footnote secondary) under a card's artwork. */
@Composable
private fun CardText(title: String, caption: String?) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = title,
        style = NovaType.subheadSemibold,
        color = NovaColors.label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (!caption.isNullOrBlank()) {
        Text(
            text = caption,
            style = NovaType.footnote,
            color = NovaColors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Long press opens the card's context menu; null when it has none. */
private fun menuOpener(menu: List<GlassMenuItem>, open: () -> Unit): (() -> Unit)? =
    if (menu.isEmpty()) null else open

/**
 * The Apple TV card: 16:9 artwork (no border, no shadow) with the title and caption BELOW it.
 * A long press lifts the card and opens [menu]. Width comes from [modifier].
 */
@Composable
internal fun TabletWideCard(
    artwork: Artwork,
    title: String,
    caption: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    watched: Boolean = false,
    menu: List<GlassMenuItem> = emptyList(),
    onClickLabel: String? = null,
) {
    val dims = LocalNovaDimens.current
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier
            .contextLift(menuOpen)
            .novaClickable(
                onClickLabel = onClickLabel,
                onLongClick = menuOpener(menu) { menuOpen = true },
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(dims.cardRadius)),
        ) {
            WideArt(artwork = artwork, title = title, modifier = Modifier.matchParentSize())
            CardOverlay(progress, watched)
        }
        CardText(title, caption)
        GlassMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, items = menu)
    }
}

/**
 * 2:3 poster with the title below (Library grid). [selected] draws the iPad selection ring
 * around the artwork (the item shown in the preview pane).
 */
@Composable
internal fun TabletPosterCard(
    artwork: Artwork,
    title: String,
    caption: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    progress: Float? = null,
    watched: Boolean = false,
    menu: List<GlassMenuItem> = emptyList(),
) {
    val dims = LocalNovaDimens.current
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier
            .contextLift(menuOpen)
            .novaClickable(
                onLongClick = menuOpener(menu) { menuOpen = true },
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .selectionRing(selected, dims.cardRadius)
                .clip(RoundedCornerShape(dims.cardRadius)),
        ) {
            PosterArt(artwork = artwork, title = title, modifier = Modifier.matchParentSize())
            CardOverlay(progress, watched)
        }
        CardText(title, caption)
        GlassMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, items = menu)
    }
}

/** A 2.5dp label-coloured ring 4dp outside the artwork, fading in and out. */
@Composable
private fun Modifier.selectionRing(selected: Boolean, radius: Dp): Modifier {
    val color = NovaColors.label
    val ringAlpha by animateFloatAsState(if (selected) 1f else 0f, NovaMotion.fade(NovaMotion.FastMs), label = "ring")
    return drawWithContent {
        drawContent()
        if (ringAlpha > 0f) {
            val stroke = 2.5.dp.toPx()
            val outset = 4.dp.toPx() + stroke / 2f
            drawRoundRect(
                color = color.copy(alpha = color.alpha * ringAlpha),
                topLeft = Offset(-outset, -outset),
                size = Size(size.width + outset * 2f, size.height + outset * 2f),
                cornerRadius = CornerRadius(radius.toPx() + outset),
                style = Stroke(width = stroke),
            )
        }
    }
}

/** Library grid cell for any item. */
@Composable
internal fun TabletLibraryCard(
    item: LibraryItemUi,
    onClick: () -> Unit,
    menu: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    when (item) {
        is LibraryItemUi.Video -> TabletPosterCard(
            artwork = item.card.artwork,
            title = item.card.primaryLine(),
            caption = item.card.posterCaption(),
            onClick = onClick,
            modifier = modifier,
            selected = selected,
            progress = item.card.progress,
            watched = item.card.watched,
            menu = menu,
        )
        is LibraryItemUi.Show -> TabletPosterCard(
            artwork = item.card.artwork,
            title = item.card.title,
            caption = item.card.caption(),
            onClick = onClick,
            modifier = modifier,
            selected = selected,
            watched = item.card.episodeCount > 0 && item.card.unwatchedCount == 0,
            menu = menu,
        )
    }
}

/**
 * Episode card of the details carousel: 16:9 still with the progress capsule inside, "EPISODE 3"
 * in small caps, the title, a 3-line description and the length. Tap plays.
 */
@Composable
internal fun TabletEpisodeCard(
    episode: EpisodeUi,
    onClick: () -> Unit,
    menu: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier
            .contextLift(menuOpen)
            .novaClickable(
                onClickLabel = "Play",
                onLongClick = menuOpener(menu) { menuOpen = true },
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(dims.cardRadius)),
        ) {
            WideArt(artwork = episode.artwork, title = episode.title, modifier = Modifier.matchParentSize(), showTitle = false)
            CardOverlay(episode.progress, episode.watched)
        }
        Spacer(Modifier.height(10.dp))
        SmallCapsText(episode.capsLabel())
        Spacer(Modifier.height(2.dp))
        Text(
            text = episode.title,
            style = NovaType.subheadSemibold,
            color = NovaColors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = episode.overview.orEmpty(),
            style = NovaType.footnote,
            color = NovaColors.secondaryLabel,
            maxLines = 3,
            minLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val length = metaLine(minutesLabel(episode.durationMs), remainingLabel(episode.progress, episode.durationMs))
        if (length.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(text = length, style = NovaType.footnote, color = NovaColors.secondaryLabel, maxLines = 1)
        }
        GlassMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, items = menu)
    }
}

// ─────────────────────────────── Page helpers ───────────────────────────────

/** Large activity indicator in the middle of [modifier]. */
@Composable
internal fun TabletLoading(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        ActivityIndicator(size = 36.dp)
    }
}

/** Vertically scrollable column whose content is centred while it fits the viewport. */
@Composable
internal fun CenteredScrollColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val minHeight = if (constraints.hasBoundedHeight) maxHeight else 0.dp
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(contentPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/**
 * Pull-to-refresh with the iOS spinner. It spins from the pull until the scan it started is
 * over; scans that start on their own (or poster fetching) only show the scan banner. Only pass
 * ScanUi.running as [scanning].
 */
@Composable
internal fun TabletRefreshBox(
    scanning: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorTop: Dp = 0.dp,
    indicatorColor: Color = NovaColors.secondaryLabel,
    content: @Composable BoxScope.() -> Unit,
) {
    var pulled by remember { mutableStateOf(false) }
    val currentScanning by rememberUpdatedState(scanning)
    if (pulled) {
        LaunchedEffect(Unit) {
            // Give the scan a moment to start, then keep spinning until it is over.
            withTimeoutOrNull(PULL_REFRESH_GRACE_MS) { snapshotFlow { currentScanning }.first { it } }
            snapshotFlow { currentScanning }.first { !it }
            pulled = false
        }
    }
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = pulled,
        onRefresh = {
            pulled = true
            onRefresh()
        },
        modifier = modifier,
        state = state,
        indicator = {
            NovaRefreshIndicator(
                state = state,
                isRefreshing = pulled,
                color = indicatorColor,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = indicatorTop),
            )
        },
        content = content,
    )
}

private const val PULL_REFRESH_GRACE_MS = 900L
