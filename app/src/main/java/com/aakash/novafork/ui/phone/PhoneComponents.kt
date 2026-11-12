package com.aakash.novafork.ui.phone

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.aakash.novafork.ui.common.IosCheckCircle
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.LocalPosterColumns
import com.aakash.novafork.ui.common.LocalLibraryLayout
import com.aakash.novafork.data.LibraryLayout
import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EndOfListGap
import com.aakash.novafork.ui.common.GlassCircle
import com.aakash.novafork.ui.common.Hairline
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.IosTextButton
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.LandscapeArt
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.PosterArt
import com.aakash.novafork.ui.common.ProgressStrip
import com.aakash.novafork.ui.common.RollingText
import com.aakash.novafork.ui.common.ShowMenuBox
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.common.WatchedBadge
import com.aakash.novafork.ui.common.WatchedMark
import com.aakash.novafork.ui.common.fadeLastLine
import com.aakash.novafork.ui.common.iosScrollIndicator
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.common.pressDim
import com.aakash.novafork.ui.common.pressScale
import com.aakash.novafork.ui.common.pressTilt
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
import com.aakash.novafork.ui.theme.SoftRowIndication
import com.aakash.novafork.ui.theme.Spacing
import kotlinx.coroutines.delay

private val ThumbShape = RoundedCornerShape(12.dp)

// ---------------------------------------------------------------------------------------------
// Labels
// ---------------------------------------------------------------------------------------------

/** Episodes read better under their show's name ("The Bear" / "S2 · E3 · Fishes"). */
/** "★ 8.2 · 2024 · 2h 47m" for a poster's long-press menu; null when nothing is known. */
internal fun VideoCardUi.infoLine(): String? = listOfNotNull(
    rating?.let { "★ " + String.format(java.util.Locale.US, "%.1f", it) },
    year?.toString(),
    durationMs?.takeIf { it > 0 }?.let { Formats.runtime((it / 60_000L).toInt()) },
).joinToString(" · ").ifEmpty { null }

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
 * on posters: Apple TV keeps it for the title's own page (owner). With [bare] it is the poster
 * alone, nothing written under it (Search's wall), and is read out by its title instead.
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
    bare: Boolean = false,
    /** The video's source, for the source colour dot (Preferences › Appearance). */
    sourceId: Long? = null,
    uhd: Boolean = false,
) {
    val dims = LocalNovaDimens.current
    val shape = RoundedCornerShape(dims.cardRadius)
    // Apple TV's press: the card sinks in and darkens while held, no ripple.
    val press = remember { MutableInteractionSource() }
    // Keyboard, D-pad or remote focus: Apple TV's lift, the card grows a touch and its art gets
    // a white ring. Touch never focuses it, so it looks unchanged there.
    val focused by press.collectIsFocusedAsState()
    val focusScale by androidx.compose.animation.core.animateFloatAsState(
        if (focused) 1.05f else 1f,
        com.aakash.novafork.ui.common.Motion.content(),
        label = "focusScale",
    )
    Column(
        modifier
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
            }
            .pressScale(press, pressed = CardPressScale)
            .pressTilt()
            .clip(shape)
            .combinedClickable(interactionSource = press, indication = null, onClick = onClick, onLongClick = onLongClick)
            .then(if (bare) Modifier.semantics(mergeDescendants = true) { contentDescription = title } else Modifier),
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
            if (focused) Box(Modifier.matchParentSize().border(2.5.dp, Color.White, shape))
            WatchedMark(watched, Modifier.align(Alignment.TopEnd).padding(6.dp))
            if (sourceId != null && com.aakash.novafork.ui.common.LocalSourceDots.current) {
                com.aakash.novafork.ui.common.SourceDot(sourceId, Modifier.align(Alignment.TopStart).padding(7.dp))
            }
            if (uhd) UhdTag(Modifier.align(Alignment.BottomEnd).padding(6.dp))
            ProgressStrip(progress, Modifier.align(Alignment.BottomStart))
        }
        if (!bare) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = searchHighlighted(title),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Always present, one line each (Apple TV), so cards in a row or grid line up tidily.
            Text(
                text = subtitle.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

/** Poster card for a video. Width comes from [modifier] (row) or the grid cell. */
@Composable
internal fun PhonePosterCard(
    card: VideoCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    bare: Boolean = false,
    /** Library grids: the card's own short caption ("S1 · E4", "2021 · 2h 35m"), without the episode's title. */
    terse: Boolean = false,
) {
    PosterCardLayout(
        title = card.displayTitle(),
        subtitle = if (terse) card.subtitle else card.displaySubtitle(),
        artwork = card.artwork,
        onClick = onClick,
        modifier = modifier,
        progress = card.progress,
        watched = card.watched,
        onLongClick = onLongClick,
        bare = bare,
        sourceId = card.sourceId,
        uhd = card.uhd,
    )
}

/** Apple TV's tiny "4K" tag in a poster's corner: white letters on a dark frosted chip. */
@Composable
private fun UhdTag(modifier: Modifier = Modifier) {
    Text(
        "4K",
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
        color = Color.White.copy(alpha = 0.92f),
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** Poster card for a TV show ("24 episodes"). */
@Composable
internal fun PhoneShowCard(
    show: ShowCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    PosterCardLayout(
        title = show.title,
        // "3 seasons · 24 episodes" (one season says only its episodes).
        subtitle = show.watchedCaption ?: if (show.seasonCount > 1) {
            "${countLabel(show.seasonCount, "season")} · ${countLabel(show.episodeCount, "episode")}"
        } else {
            countLabel(show.episodeCount, "episode")
        },
        artwork = show.artwork,
        onClick = onClick,
        modifier = modifier,
        progress = null,
        watched = show.episodeCount > 0 && show.unwatchedCount == 0,
        onLongClick = onLongClick,
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
    /** The caption's digits roll when it changes (Continue Watching's time left). */
    rollingSubtitle: Boolean = false,
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
        rollingSubtitle = rollingSubtitle,
        newEpisode = card.newEpisode,
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
    onLongClick: (() -> Unit)? = null,
) {
    WideCardLayout(
        title = show.title,
        // "3 seasons · 24 episodes" (one season says only its episodes).
        subtitle = show.watchedCaption ?: if (show.seasonCount > 1) {
            "${countLabel(show.seasonCount, "season")} · ${countLabel(show.episodeCount, "episode")}"
        } else {
            countLabel(show.episodeCount, "episode")
        },
        artwork = show.artwork,
        artTitle = show.title,
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
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
    rollingSubtitle: Boolean = false,
    newEpisode: Boolean = false,
) {
    val shape = RoundedCornerShape(LocalNovaDimens.current.cardRadius)
    val press = remember { MutableInteractionSource() }
    Column(
        modifier
            .width(width)
            .pressScale(press, pressed = CardPressScale)
            .pressTilt()
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
            // A card without any 16:9 picture names its title on the placeholder. In a row that
            // drives it, the picture drifts against the scroll inside its frame (Apple TV).
            val parallax = LocalCardParallax.current
            LandscapeArt(
                artwork = artwork,
                title = artTitle,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (parallax == null) {
                            Modifier
                        } else {
                            Modifier.graphicsLayer {
                                scaleX = ParallaxZoom
                                scaleY = ParallaxZoom
                                translationX = -parallax() * size.width * ParallaxShift
                            }
                        },
                    ),
            )
            // A half-watched card is a paused frame: a soft shade at its foot keeps the progress
            // line readable over a bright picture, and a faint edge sets it off the black page.
            if (progress != null && progress > 0f && progress < 1f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .fillMaxHeight(0.35f)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))),
                )
            }
            Box(Modifier.matchParentSize().border(0.5.dp, Color.White.copy(alpha = 0.08f), shape))
            WatchedMark(watched, Modifier.align(Alignment.TopEnd).padding(6.dp))
            if (newEpisode) NewEpisodeDot(Modifier.align(Alignment.TopStart).padding(8.dp))
            ProgressStrip(progress, Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.height(8.dp))
        // One line of title and one of caption (Apple TV): every card in a row ends at the same
        // height, with no empty line between a short title and its caption.
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            minLines = 1,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            // A long title fades out at the card's edge instead of ending in "…" (iOS 27).
            modifier = Modifier.fadeLastLine(1),
        )
        if (rollingSubtitle) {
            // iOS's rolling digits: "42m left" counts down to "30m left" on coming back from playing.
            RollingText(
                text = subtitle.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = with(LocalDensity.current) { MaterialTheme.typography.bodySmall.lineHeight.toDp() })
                    .clipToBounds(),
            )
        } else {
            Text(
                text = subtitle.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Apple TV's small blue dot: this show has a new episode to watch. */
@Composable
internal fun NewEpisodeDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .background(NovaColors.IosBlue, CircleShape),
    )
}

/** How far a poster or card sinks in while pressed. */
private const val CardPressScale = 0.97f

/**
 * Where a card sits across its row, -1 (left edge) to 1 (right edge), read while drawing: a row
 * that provides it makes its cards' pictures drift against the scroll. Null: no drift.
 */
internal val LocalCardParallax = compositionLocalOf<(() -> Float)?> { null }

/** How far a drifting picture moves (a share of its width), and how much larger it is drawn so its edges never show. */
private const val ParallaxShift = 0.08f
private const val ParallaxZoom = 1f + 2f * ParallaxShift

/**
 * [LocalCardParallax] for the item at [index] of this row: its centre against the row's centre,
 * as a share of the row's width. Reads the layout, so it belongs in a draw or layer block.
 */
internal fun LazyListState.parallaxAt(index: Int): () -> Float = {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        0f
    } else {
        val width = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
        ((item.offset + item.size / 2f - center) / width).coerceIn(-1f, 1f)
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
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SoftRowIndication,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = dims.gutter, vertical = Spacing.sm),
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
            WatchedMark(card.watched, Modifier.align(Alignment.TopEnd).padding(4.dp))
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
            // Capsules, as iOS 26/27 draws its buttons.
            .clip(RoundedCornerShape(percent = 50))
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
 * Episode in a season list, as Apple TV draws it: the 16:9 still on the left (about 40% of the
 * row) with its progress along the bottom edge; on the right "3. Title" in 15sp semibold, a
 * two-line grey summary, then "45m" or "28m left". The one to watch next ([upNext]) says so in blue.
 */
@Composable
internal fun PhoneEpisodeRow(
    episode: EpisodeUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    upNext: Boolean = false,
) {
    // Spoiler-safe: an episode not started yet keeps its picture and summary to itself.
    val spoilerHidden = com.aakash.novafork.ui.common.LocalSpoilerSafe.current && !episode.watched && (episode.progress ?: 0f) <= 0f
    val dims = LocalNovaDimens.current
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SoftRowIndication,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = dims.gutter, vertical = Spacing.md)
            // Finished episodes step back a little, so the ones still to watch stand out.
            .graphicsLayer { alpha = if (episode.watched && !upNext) WatchedEpisodeAlpha else 1f },
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .weight(0.4f)
                .aspectRatio(16f / 9f)
                .clip(ThumbShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            LandscapeArt(
                artwork = episode.artwork,
                title = episode.title,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (spoilerHidden) Modifier.blur(18.dp) else Modifier),
            )
            WatchedMark(episode.watched, Modifier.align(Alignment.TopEnd).padding(Spacing.xs + 2.dp))
            ProgressStrip(episode.progress, Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(0.6f)) {
            // The episode's number in large grey figures before its title (Apple TV).
            val heading = buildAnnotatedString {
                if (episode.episode > 0) {
                    withStyle(SpanStyle(color = IosColors.SecondaryLabel, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)) {
                        append("${episode.episode}")
                    }
                    append("  ")
                    append(episode.title)
                } else {
                    append(episode.title.ifBlank { episode.code })
                }
            }
            Text(
                text = heading,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val overview = episode.overview.takeUnless { spoilerHidden }
            if (!overview.isNullOrBlank()) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            val length = episodeLengthLabel(episode)
            if (upNext || length != null) {
                Text(
                    text = buildAnnotatedString {
                        if (upNext) {
                            withStyle(SpanStyle(color = NovaColors.IosBlue, fontWeight = FontWeight.SemiBold)) { append("Up Next") }
                            if (length != null) append(" · ")
                        }
                        if (length != null) append(length)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

/** "45m" for an episode not started, "28m left" once part-way; null when the length is unknown. */
private fun episodeLengthLabel(episode: EpisodeUi): String? {
    val duration = episode.durationMs?.takeIf { it > 0 } ?: return null
    val progress = episode.progress
    if (progress != null && progress > 0f && progress < 1f) {
        return Formats.remaining((duration * progress).toLong(), duration)
    }
    return durationLabel(duration)
}

/**
 * The library grids' columns: one per row in the List layout; else the owner's posters per row,
 * unless a pinch has just resized the grid; else fitted to the width.
 */
@Composable
private fun libraryColumns(minCell: Dp): GridCells {
    val pinched = pinchedCell
    val fixed = LocalPosterColumns.current.count
    return when {
        LocalLibraryLayout.current == LibraryLayout.LIST -> GridCells.Fixed(1)
        pinched != null -> GridCells.Adaptive(pinched)
        fixed > 0 -> GridCells.Fixed(fixed)
        else -> GridCells.Adaptive(minCell)
    }
}

/** A library title as a list row (Library layout: List): a small poster, the title and its caption. */
@Composable
private fun PhoneLibraryListRow(
    title: String,
    subtitle: String?,
    artwork: Artwork,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    progress: Float? = null,
    watched: Boolean = false,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(56.dp)
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            PosterArt(artwork = artwork, title = title, modifier = Modifier.fillMaxSize(), showTitleOnPlaceholder = false)
            ProgressStrip(progress, Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        WatchedMark(watched, Modifier.padding(start = Spacing.sm))
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
    /** When given, the cards are grouped under a small heading each ("Movies", "Shows"), in order of first appearance. */
    groupTitle: ((LibraryItemUi) -> String)? = null,
) {
    val dims = LocalNovaDimens.current
    val seen = remember { mutableSetOf<Any>() }
    val asList = LocalLibraryLayout.current == LibraryLayout.LIST
    LazyVerticalGrid(
        columns = libraryColumns(dims.gridMinCell),
        modifier = modifier.fillMaxSize().pinchColumns(state).iosScrollIndicator(state),
        state = state,
        contentPadding = PaddingValues(start = dims.gutter, end = dims.gutter, top = Spacing.sm, bottom = EndOfListGap + LocalBottomBarSpace.current),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        header()
        if (groupTitle == null) {
            libraryCards(items, actions, asList, seen)
        } else {
            items.groupBy(groupTitle).forEach { (title, group) ->
                item(key = "group:$title", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                libraryCards(group, actions, asList, seen)
            }
        }
        footer()
    }
}

/** [PhoneLibraryGrid]'s cards: a poster (or list row) per video or show, each with its menu. */
private fun LazyGridScope.libraryCards(items: List<LibraryItemUi>, actions: AppActions, asList: Boolean, seen: MutableSet<Any>) {
    itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
        val appear = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).staggeredAppear(item.key, index, seen)
        when (item) {
            is LibraryItemUi.Video -> VideoMenuBox(
                videoId = item.card.id,
                title = item.card.displayTitle(),
                actions = actions,
                showDetails = false,
                info = item.card.infoLine(),
                modifier = appear,
            ) { openMenu ->
                if (asList) {
                    PhoneLibraryListRow(
                        title = item.card.displayTitle(),
                        subtitle = item.card.displaySubtitle(),
                        artwork = item.card.artwork,
                        onClick = { actions.openVideo(item.card.id) },
                        onLongClick = openMenu,
                        progress = item.card.progress,
                        watched = item.card.watched,
                    )
                } else {
                    SelectablePosterCard(item.card, actions, openMenu)
                }
            }
            is LibraryItemUi.Show -> ShowMenuBox(showKey = item.card.key, actions = actions, modifier = appear) { openMenu ->
                if (asList) {
                    PhoneLibraryListRow(
                        title = item.card.title,
                        subtitle = item.card.watchedCaption ?: countLabel(item.card.episodeCount, "episode"),
                        artwork = item.card.artwork,
                        onClick = { actions.openShow(item.card.key) },
                        onLongClick = openMenu,
                    )
                } else {
                    PhoneShowCard(item.card, onClick = { actions.openShow(item.card.key) }, onLongClick = openMenu)
                }
            }
        }
    }
}

/** The Recently Added timeline: a month heading ("October", "March 2024") over each month's videos, as Photos groups. */
@Composable
internal fun PhoneTimelineGrid(
    months: List<TimelineMonth>,
    actions: AppActions,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
) {
    val dims = LocalNovaDimens.current
    val seen = remember { mutableSetOf<Any>() }
    // Where each month's heading sits in the grid, to know which month is under the top edge.
    val headings = remember(months) {
        var index = 0
        months.map { month -> index.also { index += 1 + month.cards.size } }
    }
    Box(modifier.fillMaxSize()) {
        val asList = LocalLibraryLayout.current == LibraryLayout.LIST
        LazyVerticalGrid(
            columns = libraryColumns(dims.gridMinCell),
            modifier = Modifier.fillMaxSize().pinchColumns(state).iosScrollIndicator(state),
            state = state,
            contentPadding = PaddingValues(start = dims.gutter, end = dims.gutter, top = 0.dp, bottom = EndOfListGap + LocalBottomBarSpace.current),
            horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
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
                    VideoMenuBox(videoId = card.id, title = card.displayTitle(), actions = actions, showDetails = false, info = card.infoLine(), modifier = appear) { openMenu ->
                        if (asList) {
                            PhoneLibraryListRow(
                                title = card.displayTitle(),
                                subtitle = card.displaySubtitle(),
                                artwork = card.artwork,
                                onClick = { actions.openVideo(card.id) },
                                onLongClick = openMenu,
                                progress = card.progress,
                                watched = card.watched,
                            )
                        } else {
                            SelectablePosterCard(card, actions, openMenu, terse = true)
                        }
                    }
                }
            }
        }
        PinnedMonth(months, headings, state)
    }
}

/**
 * Search's poster wall, as Apple TV's Search tab draws it (owner): the videos as three columns of
 * bare posters in the order given (newest first), no headings and nothing written under them,
 * each read out by its title. Tap opens the video, a long press its menu. [header] goes above the
 * first row, full width (the recent searches).
 */
@Composable
internal fun PhoneSearchPosterGrid(
    cards: List<VideoCardUi>,
    actions: AppActions,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    header: (@Composable () -> Unit)? = null,
) {
    val dims = LocalNovaDimens.current
    val seen = remember { mutableSetOf<Any>() }
    LazyVerticalGrid(
        columns = GridCells.Fixed(SearchWallColumns),
        modifier = modifier.fillMaxSize().iosScrollIndicator(state),
        state = state,
        contentPadding = PaddingValues(start = dims.gutter, end = dims.gutter, top = 0.dp, bottom = EndOfListGap + LocalBottomBarSpace.current),
        horizontalArrangement = Arrangement.spacedBy(SearchWallGap),
        verticalArrangement = Arrangement.spacedBy(SearchWallGap),
    ) {
        if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
        itemsIndexed(cards, key = { _, card -> "v:" + card.id }) { index, card ->
            val appear = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).staggeredAppear("v:" + card.id, index, seen)
            VideoMenuBox(videoId = card.id, title = card.displayTitle(), actions = actions, showDetails = false, modifier = appear) { openMenu ->
                PhonePosterCard(card, onClick = { actions.openVideo(card.id) }, onLongClick = openMenu, bare = true)
            }
        }
    }
}

private const val SearchWallColumns = 3
private val SearchWallGap = 10.dp

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
    // Once pinned the heading eases down a size, as a large title settles into a bar (iOS).
    val squeeze = remember { Animatable(1f) }
    LaunchedEffect(Unit) { squeeze.animateTo(PinnedMonthScale, tween(220)) }
    Box(
        Modifier
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
    ) {
        Text(
            month.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer {
                scaleX = squeeze.value
                scaleY = squeeze.value
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
        )
    }
}

/** How small the pinned month heading settles to. */
private const val PinnedMonthScale = 0.88f

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
    // The title keeps clear of however many buttons the page puts on the right.
    val density = LocalDensity.current
    var actionsWidth by remember { mutableStateOf(0.dp) }
    val side = maxOf(TOP_BAR_TITLE_INSET, actionsWidth + 8.dp)
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
                    .padding(horizontal = side),
            )
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp, end = side),
            )
        }
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .onSizeChanged { actionsWidth = with(density) { it.width.toDp() } },
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

private val TOP_BAR_TITLE_INSET = 100.dp

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
 * An empty page's whole message: one quiet centred grey line and, only where something obvious can
 * be done, one small text button under it. No icon, no title, no illustration.
 */
@Composable
internal fun PhoneEmptyLine(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = IosColors.SecondaryLabel,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            IosTextButton(onClick = onAction) { Text(actionLabel) }
        }
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
private const val WatchedEpisodeAlpha = 0.42f

/**
 * Picking several videos at once (owner): while a page selects ([LocalVideoSelection]), a tap or
 * a hold ticks a video instead of opening it, and a tick circle shows on every poster.
 */
@Stable
internal class VideoSelection {
    val selected = mutableStateListOf<Long>()

    fun toggle(videoId: Long) {
        if (!selected.remove(videoId)) selected += videoId
    }
}

/** The page's selection while it is selecting; null otherwise. */
internal val LocalVideoSelection = staticCompositionLocalOf<VideoSelection?> { null }

/** A poster that opens its video, or, while selecting, ticks it. */
@Composable
private fun SelectablePosterCard(card: VideoCardUi, actions: AppActions, openMenu: () -> Unit, terse: Boolean = false) {
    val selection = LocalVideoSelection.current
    if (selection == null) {
        PhonePosterCard(card, onClick = { actions.openVideo(card.id) }, onLongClick = openMenu, terse = terse)
        return
    }
    val checked = card.id in selection.selected
    Box {
        PhonePosterCard(card, onClick = { selection.toggle(card.id) }, onLongClick = { selection.toggle(card.id) }, terse = terse)
        IosCheckCircle(
            checked = checked,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = if (checked) 0f else 0.35f), CircleShape),
        )
    }
}
