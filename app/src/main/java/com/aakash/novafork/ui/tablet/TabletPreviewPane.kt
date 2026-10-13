package com.aakash.novafork.ui.tablet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.data.ThemeMode
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.BackdropArt
import com.aakash.novafork.ui.common.Badge
import com.aakash.novafork.ui.common.GlassConfig
import com.aakash.novafork.ui.common.NovaGlyphs
import com.aakash.novafork.ui.common.PrimaryCapsuleButton
import com.aakash.novafork.ui.common.ProgressBar
import com.aakash.novafork.ui.common.RatingBadge
import com.aakash.novafork.ui.common.SmallCapsText
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.common.novaClickable
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.ShowDetailsUi
import com.aakash.novafork.ui.model.VideoDetailsUi
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaTheme
import com.aakash.novafork.ui.theme.NovaType

private val PaneRadius = 22.dp
private val PanePadding = 24.dp
private val PaneBlur = 48.dp

@Immutable
private data class PaneContent(
    val key: String?,
    val item: LibraryItemUi?,
    val video: VideoDetailsUi?,
    val show: ShowDetailsUi?,
)

/**
 * Right-hand pane of the landscape Library: frosted glass over a blurred copy of the selected
 * item's backdrop, with the artwork, title, meta, badges, the Play capsule, "Details ›" and the
 * description. [video]/[show] are the loaded details for [previewKey] (null while loading);
 * [item] is the grid entry, shown until they arrive.
 */
@Composable
internal fun TabletPreviewPane(
    previewKey: String?,
    item: LibraryItemUi?,
    video: VideoDetailsUi?,
    show: ShowDetailsUi?,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val artwork = video?.artwork ?: show?.artwork ?: item?.artwork
    val shape = RoundedCornerShape(PaneRadius)
    // The pane sits on the item's own artwork, so it stays dark in either appearance.
    NovaTheme(ThemeMode.DARK) {
        Box(
            modifier
                .clip(shape)
                .background(NovaColors.cell),
        ) {
            Crossfade(
                targetState = artwork,
                modifier = Modifier.matchParentSize(),
                animationSpec = tween(durationMillis = 400),
                label = "paneBackdrop",
            ) { art ->
                if (art != null) {
                    BackdropArt(
                        artwork = art,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(PaneBlur),
                    )
                }
            }
            // The frost: darkens the blurred art (more where Android cannot blur), plus the sheen
            // along the top edge and the light rim of Liquid Glass.
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = if (GlassConfig.backdropSupported) 0.42f else 0.62f)),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.3f to Color.Transparent)),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .border(0.5.dp, Color.White.copy(alpha = 0.18f), shape),
            )
            // Keyed by item: details arriving for the same item update in place, a new item fades in.
            AnimatedContent(
                targetState = PaneContent(previewKey, item, video, show),
                modifier = Modifier.matchParentSize(),
                transitionSpec = { fadeIn(tween(durationMillis = 220, delayMillis = 60)) togetherWith fadeOut(tween(durationMillis = 120)) },
                contentKey = { it.key },
                label = "previewPane",
            ) { content ->
                when {
                    content.video != null -> VideoPreview(content.video, actions)
                    content.show != null -> ShowPreview(content.show, actions)
                    content.item != null -> ItemPreview(content.item, actions)
                    else -> PaneHint()
                }
            }
        }
    }
}

@Composable
private fun VideoPreview(details: VideoDetailsUi, actions: AppActions) {
    val started = (details.progress ?: 0f) > 0f
    PaneLayout(
        artwork = details.artwork,
        title = details.title,
        eyebrow = if (details.kind == MediaKind.EPISODE) {
            metaLine(details.showTitle, episodeTag(details.season, details.episode)).ifEmpty { null }
        } else {
            null
        },
        meta = metaLine(details.genres.firstOrNull(), details.year?.toString(), details.runtimeLabel),
        quality = qualityBadge(details.resolutionLabel),
        rating = details.rating,
        playLabel = details.playLabel,
        onPlay = { actions.play(details.id) },
        progress = if (started) details.progress else null,
        progressLabel = remainingLabel(details.progress, details.durationMs, details.positionMs),
        onDetails = { actions.openVideo(details.id) },
        overview = details.overview,
    )
}

@Composable
private fun ShowPreview(details: ShowDetailsUi, actions: AppActions) {
    val episodeCount = details.seasons.sumOf { it.episodes.size }
    val nextUp = details.nextUp
    val playId = nextUp?.videoId ?: details.seasons.firstOrNull()?.episodes?.firstOrNull()?.videoId
    val started = (nextUp?.progress ?: 0f) > 0f
    PaneLayout(
        artwork = details.artwork,
        title = details.title,
        meta = metaLine(
            details.genres.firstOrNull(),
            details.year?.toString(),
            when {
                details.seasons.size > 1 -> countLabel(details.seasons.size, "Season")
                episodeCount > 0 -> countLabel(episodeCount, "Episode")
                else -> null
            },
        ),
        rating = details.rating,
        playLabel = details.playLabel,
        onPlay = if (playId != null) {
            { actions.play(playId) }
        } else {
            null
        },
        progress = if (started) nextUp?.progress else null,
        progressLabel = if (nextUp != null) {
            metaLine(episodeTag(nextUp.season, nextUp.episode), remainingLabel(nextUp.progress, nextUp.durationMs))
        } else {
            null
        },
        onDetails = { actions.openShow(details.key) },
        footnote = if (nextUp != null && !started) {
            metaLine("Next", episodeTag(nextUp.season, nextUp.episode), nextUp.title)
        } else {
            null
        },
        overview = details.overview,
    )
}

/** The moment between selecting an item and its details arriving: what the grid already knows. */
@Composable
private fun ItemPreview(item: LibraryItemUi, actions: AppActions) {
    when (item) {
        is LibraryItemUi.Video -> PaneLayout(
            artwork = item.card.artwork,
            title = item.card.primaryLine(),
            meta = item.card.caption().orEmpty(),
            rating = item.card.rating,
            playLabel = if ((item.card.progress ?: 0f) > 0f) "Resume" else "Play",
            onPlay = { actions.play(item.card.id) },
            onDetails = { actions.openVideo(item.card.id) },
        )
        is LibraryItemUi.Show -> PaneLayout(
            artwork = item.card.artwork,
            title = item.card.title,
            meta = item.card.caption(),
            rating = item.card.rating,
            onDetails = { actions.openShow(item.card.key) },
        )
    }
}

@Composable
private fun PaneLayout(
    artwork: Artwork,
    title: String,
    meta: String,
    onDetails: () -> Unit,
    eyebrow: String? = null,
    quality: String? = null,
    rating: Float? = null,
    playLabel: String? = null,
    onPlay: (() -> Unit)? = null,
    progress: Float? = null,
    progressLabel: String? = null,
    footnote: String? = null,
    overview: String? = null,
) {
    val dims = LocalNovaDimens.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PanePadding),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(dims.cardRadius)),
        ) {
            BackdropArt(artwork = artwork, modifier = Modifier.matchParentSize())
        }
        Spacer(Modifier.height(20.dp))
        if (!eyebrow.isNullOrBlank()) {
            SmallCapsText(eyebrow, color = NovaColors.OnMediaSecondary)
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = title,
            style = NovaType.title1,
            color = NovaColors.OnMedia,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = meta,
                style = NovaType.subhead,
                color = NovaColors.OnMediaSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val hasRating = rating != null && rating > 0f
        if (quality != null || hasRating) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (quality != null) Badge(quality, color = NovaColors.OnMediaSecondary)
                RatingBadge(rating, color = NovaColors.OnMediaSecondary)
            }
        }
        Spacer(Modifier.height(20.dp))
        if (onPlay != null && playLabel != null) {
            PrimaryCapsuleButton(text = playLabel, onClick = onPlay, modifier = Modifier.fillMaxWidth())
            if (progress != null && progress > 0f) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressBar(progress = progress, modifier = Modifier.width(96.dp))
                    if (!progressLabel.isNullOrBlank()) {
                        Spacer(Modifier.width(10.dp))
                        Text(progressLabel, style = NovaType.footnote, color = NovaColors.OnMediaSecondary, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        DetailsLink(onClick = onDetails)
        if (!footnote.isNullOrBlank()) {
            Text(
                text = footnote,
                style = NovaType.footnote,
                color = NovaColors.OnMediaSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!overview.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = overview,
                style = NovaType.subhead,
                color = NovaColors.OnMediaSecondary,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "Details ›": opens the full page. */
@Composable
private fun DetailsLink(onClick: () -> Unit) {
    Row(
        Modifier
            .novaClickable(onClickLabel = "Details", pressScale = 1f, pressAlpha = 0.5f, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Details", style = NovaType.headline, color = NovaColors.OnMedia)
        Spacer(Modifier.width(4.dp))
        Icon(NovaGlyphs.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = NovaColors.OnMediaSecondary)
    }
}

@Composable
private fun PaneHint() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(PanePadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_movie),
            contentDescription = null,
            tint = NovaColors.OnMediaSecondary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("Select a Title", style = NovaType.headline, color = NovaColors.OnMedia)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap a poster to see it here. Tap it again to open it.",
            style = NovaType.footnote,
            color = NovaColors.OnMediaSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 260.dp),
        )
    }
}
