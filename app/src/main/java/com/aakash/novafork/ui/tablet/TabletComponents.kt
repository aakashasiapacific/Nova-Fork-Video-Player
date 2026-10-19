package com.aakash.novafork.ui.tablet

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.ArtPlaceholder
import com.aakash.novafork.ui.common.BackdropArt
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.LandscapeArt
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.PosterArt
import com.aakash.novafork.ui.common.ProgressStrip
import com.aakash.novafork.ui.common.RatingPill
import com.aakash.novafork.ui.common.WatchedBadge
import com.aakash.novafork.ui.common.bottomScrim
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.common.startScrim
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.EpisodeUi
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.ShowCardUi
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.theme.NovaColors
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// ---------------------------------------------------------------------------------------------
// Insets and small helpers
// ---------------------------------------------------------------------------------------------

/** Height of the status bar; pages that draw art behind it pad their text by this much. */
@Composable
internal fun statusBarTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** Bottom padding that lets scrolling content pass behind the navigation bar but end above it. */
@Composable
internal fun navigationBarBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Card scale: shrinks slightly while pressed, grows a little on hover or keyboard focus. */
@Composable
internal fun animateCardScale(interactionSource: InteractionSource): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val target = when {
        pressed -> 0.96f
        hovered || focused -> 1.04f
        else -> 1f
    }
    return animateFloatAsState(targetValue = target, label = "cardScale")
}

/** First line of a card: the show for episodes (so "Pilot" never stands alone), else the title. */
internal fun VideoCardUi.primaryLine(): String =
    if (kind == MediaKind.EPISODE && !showTitle.isNullOrBlank()) showTitle else title

/** Second line of a card: "S1 · E3 · Pilot" for episodes, else the provided subtitle. */
internal fun VideoCardUi.secondaryLine(): String? =
    if (kind == MediaKind.EPISODE && !showTitle.isNullOrBlank()) metaLine(subtitle, title) else subtitle

/** "42m left" for a partly watched card, null otherwise. */
internal fun VideoCardUi.remainingLabel(): String? {
    val p = progress ?: return null
    val duration = durationMs ?: return null
    if (p <= 0f || duration <= 0L) return null
    return Formats.remaining((duration * p.coerceIn(0f, 1f)).toLong(), duration)
}

/** "45m" / "1h 2m"; null when unknown. */
internal fun durationLabel(durationMs: Long?): String? {
    if (durationMs == null || durationMs <= 0L) return null
    return Formats.runtime(((durationMs + 30_000L) / 60_000L).toInt().coerceAtLeast(1))
}

internal fun countLabel(count: Int, singular: String, plural: String = singular + "s"): String =
    if (count == 1) "1 $singular" else "$count $plural"

internal fun ShowCardUi.subtitleLine(): String = metaLine(
    year?.toString(),
    if (seasonCount > 0) countLabel(seasonCount, "season") else countLabel(episodeCount, "episode"),
)

/**
 * Pull-to-refresh whose spinner shows from the pull until the scan it started is over. Scans that
 * start on their own only show the scan banner, not this spinner.
 */
@Composable
internal fun TabletPullToRefresh(
    scanning: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
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
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = pulled,
        onRefresh = {
            pulled = true
            onRefresh()
        },
        modifier = modifier,
        state = refreshState,
        // iOS's refresh control: the spinner fades in with the pull and spins while the scan runs.
        indicator = {
            val fraction = refreshState.distanceFraction.coerceIn(0f, 1f)
            if (pulled || fraction > 0f) {
                IosSpinner(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 18.dp)
                        .size(28.dp)
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

private const val PULL_REFRESH_GRACE_MS = 900L

// ---------------------------------------------------------------------------------------------
// Buttons and labels
// ---------------------------------------------------------------------------------------------

/** Primary "Play / Resume …" button, a little taller than the default for lean-back screens. */
@Composable
internal fun TabletPlayButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        NovaIcon(R.drawable.ic_play_arrow, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Icon + text content for secondary buttons. */
@Composable
internal fun ButtonLabel(@DrawableRes icon: Int, text: String) {
    NovaIcon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(text, maxLines = 1)
}

/** Round back button that stays readable over artwork. */
@Composable
internal fun TabletBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        NovaIcon(R.drawable.ic_arrow_back, contentDescription = "Back")
    }
}

/** Non-interactive genre tag (genres are labels, not filters). */
@Composable
internal fun TabletTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            maxLines = 1,
        )
    }
}

/** Small label drawn on top of artwork ("Next up", "45m"). */
@Composable
internal fun ImageLabel(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (emphasized) MaterialTheme.colorScheme.onPrimary else Color.White,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (emphasized) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Centered spinner, used while details or folder listings load. */
@Composable
internal fun TabletLoading(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        IosSpinner()
    }
}

/** The brand mark: the launcher icon on its iOS-blue rounded square. */
@Composable
internal fun BrandMark(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(NovaColors.IosBlue),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(size * 1.5f),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------------------------

/**
 * 2:3 poster card with the title below. The whole card is the touch target; the ripple is clipped
 * to the poster and the card scales on press/hover. Width comes from [modifier].
 */
@Composable
internal fun TabletPosterCard(
    artwork: Artwork,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    rating: Float? = null,
    progress: Float? = null,
    watched: Boolean = false,
    badge: String? = null,
    selected: Boolean = false,
) {
    val dims = LocalNovaDimens.current
    val shape = RoundedCornerShape(dims.cardRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateCardScale(interactionSource)
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(if (selected) 12.dp else 4.dp, shape)
                .outlineIf(selected, MaterialTheme.colorScheme.primary, shape)
                .indication(interactionSource, LocalIndication.current),
        ) {
            PosterArt(artwork = artwork, title = title, modifier = Modifier.fillMaxSize())
            RatingPill(rating, onImage = true, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
            when {
                watched -> WatchedBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
                badge != null -> ImageLabel(badge, Modifier.align(Alignment.TopEnd).padding(8.dp), emphasized = true)
            }
            ProgressStrip(progress, Modifier.align(Alignment.BottomCenter), height = 4.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Poster card for a video: tap opens details (streams play), long-press plays (streams open details). */
@Composable
internal fun TabletVideoPosterCard(
    card: VideoCardUi,
    onOpen: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val stream = card.kind == MediaKind.STREAM
    TabletPosterCard(
        artwork = card.artwork,
        title = card.primaryLine(),
        subtitle = card.secondaryLine(),
        onClick = { if (stream) onPlay(card.id) else onOpen(card.id) },
        onLongClick = { if (stream) onOpen(card.id) else onPlay(card.id) },
        onLongClickLabel = if (stream) "Details" else "Play",
        modifier = modifier,
        rating = card.rating,
        progress = card.progress,
        watched = card.watched,
        selected = selected,
    )
}

@Composable
internal fun TabletShowPosterCard(
    card: ShowCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    TabletPosterCard(
        artwork = card.artwork,
        title = card.title,
        subtitle = card.subtitleLine(),
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = if (onLongClick != null) "Details" else null,
        modifier = modifier,
        rating = card.rating,
        watched = card.episodeCount > 0 && card.unwatchedCount == 0,
        badge = if (card.unwatchedCount > 0) "${card.unwatchedCount} new" else null,
        selected = selected,
    )
}

/** Poster card for any library item. */
@Composable
internal fun TabletLibraryItemCard(
    item: LibraryItemUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    selected: Boolean = false,
) {
    when (item) {
        is LibraryItemUi.Video -> TabletPosterCard(
            artwork = item.card.artwork,
            title = item.card.primaryLine(),
            subtitle = item.card.secondaryLine(),
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = onLongClickLabel,
            modifier = modifier,
            rating = item.card.rating,
            progress = item.card.progress,
            watched = item.card.watched,
            selected = selected,
        )
        is LibraryItemUi.Show -> TabletPosterCard(
            artwork = item.card.artwork,
            title = item.card.title,
            subtitle = item.card.subtitleLine(),
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = onLongClickLabel,
            modifier = modifier,
            rating = item.card.rating,
            watched = item.card.episodeCount > 0 && item.card.unwatchedCount == 0,
            badge = if (item.card.unwatchedCount > 0) "${item.card.unwatchedCount} new" else null,
            selected = selected,
        )
    }
}

/**
 * 16:9 card with the title inside the image (continue watching, next episode). Tap plays,
 * long-press opens details. Width comes from [modifier].
 */
@Composable
internal fun TabletLandscapeCard(
    card: VideoCardUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    val shape = RoundedCornerShape(dims.cardRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateCardScale(interactionSource)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .aspectRatio(16f / 9f)
            .shadow(4.dp, shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClickLabel = "Play",
                onLongClickLabel = "Details",
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) {
        LandscapeArt(
            artwork = card.artwork,
            title = card.title,
            modifier = Modifier
                .fillMaxSize()
                .bottomScrim(Color.Black.copy(alpha = 0.88f), startFraction = 0.3f),
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = card.primaryLine(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val secondary = metaLine(card.secondaryLine(), card.remainingLabel())
                if (secondary.isNotEmpty()) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                NovaIcon(R.drawable.ic_play_arrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        if (card.watched) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(10.dp))
        ProgressStrip(card.progress, Modifier.align(Alignment.BottomCenter), height = 4.dp)
    }
}

/**
 * 16:9 still with text below (episodes, files in a folder). Tap plays, long-press opens details.
 * [cornerLabel] sits on the image (duration), [badge] marks it ("Next up").
 */
@Composable
internal fun TabletStillCard(
    artwork: Artwork,
    title: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    description: String? = null,
    footnote: String? = null,
    cornerLabel: String? = null,
    progress: Float? = null,
    watched: Boolean = false,
    badge: String? = null,
    highlighted: Boolean = false,
    onClickLabel: String = "Play",
) {
    val dims = LocalNovaDimens.current
    val shape = RoundedCornerShape(dims.cardRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateCardScale(interactionSource)
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = onClickLabel,
                onLongClickLabel = "Details",
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shadow(4.dp, shape)
                .outlineIf(highlighted, MaterialTheme.colorScheme.primary, shape)
                .indication(interactionSource, LocalIndication.current),
        ) {
            LandscapeArt(artwork = artwork, title = title, modifier = Modifier.fillMaxSize())
            if (badge != null) ImageLabel(badge, Modifier.align(Alignment.TopStart).padding(10.dp), emphasized = true)
            if (watched) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(10.dp))
            if (cornerLabel != null) {
                ImageLabel(cornerLabel, Modifier.align(Alignment.BottomEnd).padding(start = 10.dp, end = 10.dp, bottom = 12.dp))
            }
            ProgressStrip(progress, Modifier.align(Alignment.BottomCenter), height = 4.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!footnote.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = footnote,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Episode tile for the show grid: still, "E3 · Title", two lines of overview, duration. */
@Composable
internal fun TabletEpisodeCard(
    episode: EpisodeUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isNextUp: Boolean = false,
) {
    TabletStillCard(
        artwork = episode.artwork,
        title = metaLine(episode.code.ifBlank { "E${episode.episode}" }, episode.title),
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        description = episode.overview,
        footnote = metaLine(durationLabel(episode.durationMs), episode.airDate),
        progress = episode.progress,
        watched = episode.watched,
        badge = if (isNextUp) "Next up" else null,
        highlighted = isNextUp,
    )
}

/** "Resume" progress bar with the time left, for details pages. Nothing when unwatched. */
@Composable
internal fun WatchProgress(progress: Float?, positionMs: Long, durationMs: Long?, modifier: Modifier = Modifier) {
    if (progress == null || progress <= 0f) return
    val fraction = progress.coerceIn(0f, 1f)
    val label = if (durationMs != null && durationMs > 0L) {
        val position = if (positionMs > 0L) positionMs else (durationMs * fraction).toLong()
        Formats.remaining(position, durationMs)
    } else {
        "${(fraction * 100).roundToInt()}% watched"
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .width(240.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** 2dp outline for the selected / highlighted card, drawn over the art. */
internal fun Modifier.outlineIf(selected: Boolean, color: Color, shape: Shape): Modifier =
    if (selected) border(2.dp, color, shape) else this

// ---------------------------------------------------------------------------------------------
// Details backdrop and poster
// ---------------------------------------------------------------------------------------------

/**
 * Full-screen art behind the details pages, darkened heavily so text anywhere stays readable.
 * Fixed in place while the content scrolls over it.
 */
@Composable
internal fun TabletDetailsBackdrop(artwork: Artwork, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize()) {
        BackdropArt(
            artwork = artwork,
            modifier = Modifier
                .fillMaxSize()
                .startScrim(background.copy(alpha = 0.85f), endFraction = 0.75f),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(background.copy(alpha = 0.55f), background))),
        )
    }
}

/** Large rounded poster with a soft shadow, for details headers. */
@Composable
internal fun TabletDetailsPoster(artwork: Artwork, title: String, width: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    PosterArt(
        artwork = artwork,
        title = title,
        modifier = modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .shadow(20.dp, shape)
            .clip(shape),
    )
}

/** Fanned stack of placeholder posters: decoration for empty and welcome panels. */
@Composable
internal fun PosterCollage(modifier: Modifier = Modifier) {
    Box(modifier.size(width = 340.dp, height = 300.dp), contentAlignment = Alignment.Center) {
        CollagePoster("Nova · drama", rotation = -12f, offsetX = -96f)
        CollagePoster("Nova · space", rotation = 12f, offsetX = 96f)
        CollagePoster("Nova · night", rotation = 0f, offsetX = 0f, elevated = true)
        BrandMark(Modifier.align(Alignment.BottomCenter), size = 64.dp)
    }
}

@Composable
private fun CollagePoster(seed: String, rotation: Float, offsetX: Float, elevated: Boolean = false) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .graphicsLayer {
                rotationZ = rotation
                translationX = offsetX * density
                translationY = if (elevated) -18f * density else 0f
            }
            .width(if (elevated) 150.dp else 130.dp)
            .aspectRatio(2f / 3f)
            .shadow(if (elevated) 18.dp else 8.dp, shape)
            .clip(shape),
    ) {
        ArtPlaceholder(seed = seed, title = null)
    }
}
