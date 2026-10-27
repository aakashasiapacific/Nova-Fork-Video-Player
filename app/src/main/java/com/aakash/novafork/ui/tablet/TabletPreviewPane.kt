package com.aakash.novafork.ui.tablet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.BackdropArt
import com.aakash.novafork.ui.common.IosTextButton
import com.aakash.novafork.ui.common.LandscapeArt
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.ProgressStrip
import com.aakash.novafork.ui.common.RatingPill
import com.aakash.novafork.ui.common.bottomScrim
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.EpisodeUi
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.ShowDetailsUi
import com.aakash.novafork.ui.model.VideoDetailsUi

private val PaneShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
private val PanePadding = 28.dp

@Immutable
private data class PaneContent(
    val key: String?,
    val item: LibraryItemUi?,
    val video: VideoDetailsUi?,
    val show: ShowDetailsUi?,
)

/**
 * Right-hand pane of the landscape library. [video]/[show] are the loaded details for
 * [previewKey] (null while loading); [item] is the grid entry, shown until they arrive.
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
    val paneColor = MaterialTheme.colorScheme.surfaceContainerLow
    Surface(modifier = modifier, shape = PaneShape, color = paneColor) {
        // Keyed by item: details arriving for the same item update in place, a new item fades in.
        AnimatedContent(
            targetState = PaneContent(previewKey, item, video, show),
            transitionSpec = { fadeIn(tween(durationMillis = 220)) togetherWith fadeOut(tween(durationMillis = 120)) },
            contentKey = { it.key },
            label = "previewPane",
        ) { content ->
            when {
                content.video != null -> VideoPreview(content.video, paneColor, actions)
                content.show != null -> ShowPreview(content.show, paneColor, actions)
                content.item != null -> ItemPreview(content.item, paneColor)
                else -> PreviewHint()
            }
        }
    }
}

@Composable
private fun VideoPreview(details: VideoDetailsUi, paneColor: Color, actions: AppActions) {
    PreviewLayout(
        artwork = details.artwork,
        title = details.title,
        meta = metaLine(details.year?.toString(), details.runtimeLabel, details.episodeCode),
        rating = details.rating,
        paneColor = paneColor,
    ) {
        PreviewGenres(details.genres)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TabletPlayButton(label = details.playLabel, onClick = { actions.play(details.id) })
            Spacer(Modifier.width(8.dp))
            IosTextButton(onClick = { actions.openVideo(details.id) }) { Text("Open Details") }
        }
        WatchProgress(progress = details.progress, positionMs = details.positionMs, durationMs = details.durationMs)
        if (details.kind == MediaKind.EPISODE && !details.showTitle.isNullOrBlank()) {
            val showKey = details.showKey
            IosTextButton(
                onClick = { if (showKey != null) actions.openShow(showKey) },
                enabled = showKey != null,
            ) {
                ButtonLabel(R.drawable.ic_tv, "From ${details.showTitle}")
            }
        }
        PreviewText(tagline = details.tagline, overview = details.overview)
    }
}

@Composable
private fun ShowPreview(details: ShowDetailsUi, paneColor: Color, actions: AppActions) {
    val episodeCount = details.seasons.sumOf { it.episodes.size }
    val playId = details.nextUp?.videoId ?: details.seasons.firstOrNull()?.episodes?.firstOrNull()?.videoId
    PreviewLayout(
        artwork = details.artwork,
        title = details.title,
        meta = metaLine(
            details.year?.toString(),
            if (details.seasons.isNotEmpty()) countLabel(details.seasons.size, "season") else null,
            if (episodeCount > 0) countLabel(episodeCount, "episode") else null,
        ),
        rating = details.rating,
        paneColor = paneColor,
    ) {
        PreviewGenres(details.genres)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (playId != null) {
                TabletPlayButton(label = details.playLabel, onClick = { actions.play(playId) })
                Spacer(Modifier.width(8.dp))
            }
            IosTextButton(onClick = { actions.openShow(details.key) }) { Text("Open Details") }
        }
        val nextUp = details.nextUp
        if (nextUp != null) NextUpRow(nextUp, onClick = { actions.play(nextUp.videoId) })
        PreviewText(tagline = details.tagline, overview = details.overview)
    }
}

/** Shown for the moment between selecting an item and its details arriving. */
@Composable
private fun ItemPreview(item: LibraryItemUi, paneColor: Color) {
    when (item) {
        is LibraryItemUi.Video -> PreviewLayout(
            artwork = item.card.artwork,
            title = item.card.primaryLine(),
            meta = item.card.secondaryLine().orEmpty(),
            rating = item.card.rating,
            paneColor = paneColor,
        ) {}
        is LibraryItemUi.Show -> PreviewLayout(
            artwork = item.card.artwork,
            title = item.card.title,
            meta = item.card.subtitleLine(),
            rating = item.card.rating,
            paneColor = paneColor,
        ) {}
    }
}

/** Backdrop on top with the poster and title overlapping its faded bottom, then [content]. */
@Composable
private fun PreviewLayout(
    artwork: Artwork,
    title: String,
    meta: String,
    rating: Float?,
    paneColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = navigationBarBottom() + PanePadding),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val artHeight = maxWidth * 9f / 16f
            BackdropArt(
                artwork = artwork,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(artHeight)
                    .bottomScrim(paneColor, startFraction = 0.3f),
            )
            // The poster and title overlap the faded bottom of the backdrop.
            Row(
                Modifier.padding(start = PanePadding, end = PanePadding, top = (artHeight - 76.dp).coerceAtLeast(0.dp)),
                verticalAlignment = Alignment.Bottom,
            ) {
                TabletDetailsPoster(artwork = artwork, title = title, width = 112.dp)
                Spacer(Modifier.width(20.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(bottom = 4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (meta.isNotEmpty() || (rating ?: 0f) > 0f) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (meta.isNotEmpty()) {
                                Text(
                                    text = meta,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                            RatingPill(rating)
                        }
                    }
                }
            }
        }
        Column(
            Modifier.padding(start = PanePadding, end = PanePadding, top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

@Composable
private fun PreviewGenres(genres: List<String>) {
    if (genres.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        genres.take(4).forEach { TabletTag(it) }
    }
}

@Composable
private fun PreviewText(tagline: String?, overview: String?) {
    if (!tagline.isNullOrBlank()) {
        Text(
            text = tagline,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (!overview.isNullOrBlank()) {
        Text(
            text = overview,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NextUpRow(episode: EpisodeUi, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(136.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                LandscapeArt(artwork = episode.artwork, title = episode.title, modifier = Modifier.fillMaxSize())
                ProgressStrip(episode.progress, Modifier.align(Alignment.BottomCenter))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Next up",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = metaLine("S${episode.season}", episode.code.ifBlank { "E${episode.episode}" }),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NovaIcon(
                R.drawable.ic_play_arrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(28.dp),
            )
        }
    }
}

@Composable
private fun PreviewHint() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(PanePadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NovaIcon(
            R.drawable.ic_movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Select a title", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Pick something on the left to see it here. Tap it again to open its page.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}
