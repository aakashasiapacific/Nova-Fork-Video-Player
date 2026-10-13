package com.aakash.novafork.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.ArtImage
import com.aakash.novafork.ui.common.ArtPlaceholder
import com.aakash.novafork.ui.common.GlassMenuItem
import com.aakash.novafork.ui.common.LandscapeArt
import com.aakash.novafork.ui.common.PosterArt
import com.aakash.novafork.ui.common.ProgressBar
import com.aakash.novafork.ui.common.RowHeader
import com.aakash.novafork.ui.common.SmallCapsText
import com.aakash.novafork.ui.common.WatchedBadge
import com.aakash.novafork.ui.common.novaClickable
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.EpisodeUi
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaType

/*
 * Phone cards. Artwork has no border and no shadow and is clipped to the card radius; the text
 * sits below it (title Subhead semibold, caption Footnote secondary). 16:9 everywhere except the
 * Library category grids, which use 2:3 posters.
 */

/**
 * 16:9 artwork for a card: episode stills first for episodes, backdrops first for everything else,
 * then a frame of the video, then the poster cropped, then the placeholder.
 */
@Composable
internal fun PhoneWideArt(
    artwork: Artwork,
    title: String,
    modifier: Modifier = Modifier,
    preferStill: Boolean = false,
    showTitleOnPlaceholder: Boolean = true,
) {
    val candidates = remember(artwork, preferStill) {
        val wide = if (preferStill) listOf(artwork.stillUrl, artwork.backdropUrl) else listOf(artwork.backdropUrl, artwork.stillUrl)
        (wide + listOf(artwork.frameSource, artwork.posterUrl)).filterNotNull().distinct()
    }
    ArtImage(
        candidates = candidates,
        contentDescription = title,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        frameSource = artwork.frameSource,
    ) {
        ArtPlaceholder(seed = artwork.seed.ifEmpty { title }, title = if (showTitleOnPlaceholder) title else null)
    }
}

/**
 * A 16:9 card: artwork (with the Continue Watching progress capsule inside it, 6dp from the
 * bottom with 12dp insets), then title and caption. [width] null fills the given width (grids).
 * Long press opens [menuItems] as a context menu.
 */
@Composable
internal fun PhoneWideCard(
    title: String,
    caption: String?,
    artwork: Artwork,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = LocalNovaDimens.current.landscapeCardWidth,
    progress: Float? = null,
    preferStill: Boolean = false,
    menuItems: List<GlassMenuItem> = emptyList(),
) {
    val dims = LocalNovaDimens.current
    val shape = RoundedCornerShape(dims.cardRadius)
    val menu = rememberPhoneMenuState()
    Column(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .phoneContextTarget(menu)
            .novaClickable(onLongClick = if (menuItems.isNotEmpty()) ({ menu.open = true }) else null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape),
        ) {
            PhoneWideArt(artwork, title, Modifier.fillMaxSize(), preferStill = preferStill)
            if (progress != null && progress > 0f) {
                ProgressBar(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                )
            }
        }
        CardText(title, caption)
        PhoneContextMenu(menu, menuItems, cornerRadius = dims.cardRadius)
    }
}

/** A 2:3 poster cell of the Library category grids: poster, then title and year. */
@Composable
internal fun PhonePosterCard(
    title: String,
    caption: String?,
    artwork: Artwork,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    watched: Boolean = false,
    menuItems: List<GlassMenuItem> = emptyList(),
) {
    val dims = LocalNovaDimens.current
    val shape = RoundedCornerShape(dims.cardRadius)
    val menu = rememberPhoneMenuState()
    Column(
        modifier
            .fillMaxWidth()
            .phoneContextTarget(menu)
            .novaClickable(onLongClick = if (menuItems.isNotEmpty()) ({ menu.open = true }) else null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape),
        ) {
            PosterArt(artwork = artwork, title = title, modifier = Modifier.fillMaxSize())
            if (watched) {
                WatchedBadge(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
            if (progress != null && progress > 0f) {
                ProgressBar(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                )
            }
        }
        CardText(title, caption)
        PhoneContextMenu(menu, menuItems, cornerRadius = dims.cardRadius)
    }
}

/** Title (Subhead semibold) and caption (Footnote secondary) 8dp under a card's artwork. */
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
    // Always laid out so cards in a row or grid line up.
    Text(
        text = caption.orEmpty(),
        style = NovaType.footnote,
        color = NovaColors.secondaryLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * An episode in the details carousel: 16:9 still with the progress capsule, "EPISODE 3" in small
 * caps, the title, a 3-line description and the duration. Tap plays it.
 */
@Composable
internal fun PhoneEpisodeCard(
    episode: EpisodeUi,
    onClick: () -> Unit,
    menuItems: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
    width: Dp = LocalNovaDimens.current.episodeCardWidth,
    label: String = episode.smallCapsLabel(),
) {
    val dims = LocalNovaDimens.current
    val shape = RoundedCornerShape(dims.cardRadius)
    val menu = rememberPhoneMenuState()
    Column(
        modifier
            .width(width)
            .phoneContextTarget(menu)
            .novaClickable(onLongClick = { menu.open = true }, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape),
        ) {
            LandscapeArt(artwork = episode.artwork, title = episode.title, modifier = Modifier.fillMaxSize(), showTitleOnPlaceholder = true)
            if (episode.watched) {
                WatchedBadge(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
            val progress = episode.progress
            if (progress != null && progress > 0f) {
                ProgressBar(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        SmallCapsText(label)
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
            minLines = 3,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val duration = minutesLabel(episode.durationMs)
        Spacer(Modifier.height(4.dp))
        Text(
            text = duration.orEmpty(),
            style = NovaType.footnote,
            color = NovaColors.secondaryLabel,
            maxLines = 1,
        )
        PhoneContextMenu(menu, menuItems, cornerRadius = dims.cardRadius)
    }
}

/**
 * A titled horizontal row: "Title ›" header (tappable when [onHeaderClick] is set), then cards
 * starting at the page gutter, 12dp apart, snapping to card edges.
 */
@Composable
internal fun <T> PhoneCardRow(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    onHeaderClick: (() -> Unit)? = null,
    card: @Composable (T) -> Unit,
) {
    val dims = LocalNovaDimens.current
    val state = rememberLazyListState()
    Column(modifier.fillMaxWidth()) {
        RowHeader(title, onClick = onHeaderClick)
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = dims.gutter),
            horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
        ) {
            items(items, key = key) { item -> card(item) }
        }
    }
}

/** Two 16:9 cards side by side (the 2-column grids of Library › Recently Added and full rows). */
@Composable
internal fun <T> PhoneTwoColumnRow(
    pair: List<T>,
    modifier: Modifier = Modifier,
    card: @Composable (T, Modifier) -> Unit,
) {
    val dims = LocalNovaDimens.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = dims.gutter),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
    ) {
        pair.forEach { item -> card(item, Modifier.weight(1f)) }
        repeat(2 - pair.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** The 64dp 16:9 thumbnail of list rows (search results, folder videos). */
@Composable
internal fun PhoneRowThumbnail(artwork: Artwork, title: String, kind: MediaKind?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(64.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(6.dp)),
    ) {
        PhoneWideArt(artwork, title, Modifier.fillMaxSize(), preferStill = kind == MediaKind.EPISODE, showTitleOnPlaceholder = false)
    }
}
