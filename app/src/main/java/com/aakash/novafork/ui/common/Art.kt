package com.aakash.novafork.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFramePercent
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.theme.NovaType
import kotlin.math.abs

/**
 * Loads the first candidate that works. On error it moves to the next one; when all fail (or the
 * list is empty) only [placeholder] is visible. A candidate that is a local video URI
 * ([Artwork.frameSource]) is decoded as a frame 10% into the video.
 */
@Composable
fun ArtImage(
    candidates: List<String>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    frameSource: String? = null,
    placeholder: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var index by remember(candidates) { mutableIntStateOf(0) }
    Box(modifier) {
        placeholder()
        val current = candidates.getOrNull(index)
        if (current != null) {
            val model = remember(current) {
                ImageRequest.Builder(context)
                    .data(current)
                    .crossfade(true)
                    .apply { if (current == frameSource) videoFramePercent(0.1) }
                    .build()
            }
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onError = { index += 1 },
            )
        }
    }
}

/** 2:3 poster art: poster, then a frame of the video, then a titled placeholder. */
@Composable
fun PosterArt(
    artwork: Artwork,
    title: String,
    modifier: Modifier = Modifier,
    showTitleOnPlaceholder: Boolean = true,
) {
    val candidates = remember(artwork) { listOfNotNull(artwork.posterUrl, artwork.frameSource) }
    ArtImage(
        candidates = candidates,
        contentDescription = title,
        modifier = modifier,
        frameSource = artwork.frameSource,
    ) {
        ArtPlaceholder(seed = artwork.seed.ifEmpty { title }, title = if (showTitleOnPlaceholder) title else null)
    }
}

/** 16:9 art for episode/continue-watching cards: still, backdrop, a frame of the video, placeholder. */
@Composable
fun LandscapeArt(
    artwork: Artwork,
    title: String,
    modifier: Modifier = Modifier,
    showTitleOnPlaceholder: Boolean = false,
) {
    val candidates = remember(artwork) { listOfNotNull(artwork.stillUrl, artwork.backdropUrl, artwork.frameSource) }
    ArtImage(
        candidates = candidates,
        contentDescription = title,
        modifier = modifier,
        frameSource = artwork.frameSource,
    ) {
        ArtPlaceholder(seed = artwork.seed.ifEmpty { title }, title = if (showTitleOnPlaceholder) title else null)
    }
}

/** Full-bleed backdrop: backdrop, still, video frame, poster; placeholder gradient underneath. */
@Composable
fun BackdropArt(artwork: Artwork, modifier: Modifier = Modifier) {
    val candidates = remember(artwork) {
        listOfNotNull(artwork.backdropUrl, artwork.stillUrl, artwork.frameSource, artwork.posterUrl)
    }
    ArtImage(
        candidates = candidates,
        contentDescription = null,
        modifier = modifier,
        frameSource = artwork.frameSource,
    ) {
        ArtPlaceholder(seed = artwork.seed, title = null)
    }
}

/**
 * Two colours picked from [seed] so the same title always gets the same placeholder: a subtle,
 * nearly black gradient with a faint hue, like an empty Apple TV tile.
 */
fun placeholderColors(seed: String): Pair<Color, Color> {
    val palette = listOf(
        Color(0xFF2C2C30) to Color(0xFF141416),
        Color(0xFF282C34) to Color(0xFF111317),
        Color(0xFF2F2A30) to Color(0xFF141214),
        Color(0xFF282F2D) to Color(0xFF111413),
        Color(0xFF302D28) to Color(0xFF151412),
        Color(0xFF292B35) to Color(0xFF121318),
        Color(0xFF2F292D) to Color(0xFF151214),
        Color(0xFF272E31) to Color(0xFF111416),
    )
    return palette[abs(seed.hashCode() % palette.size)]
}

/** Gradient tile with the title (Subhead semibold, white 85%), used when no artwork loads. */
@Composable
fun ArtPlaceholder(seed: String, title: String?, modifier: Modifier = Modifier) {
    val (top, bottom) = placeholderColors(seed)
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                modifier = Modifier.padding(12.dp),
                color = Color.White.copy(alpha = 0.85f),
                style = NovaType.subheadSemibold,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Darkens the bottom of an image so text on top stays readable, fading into [color]
 * (usually the page background so the art melts into the page). The ramp is eased, so there is
 * no visible band where the gradient starts. Apple TV heroes: startFraction = 0.55 (lower 45%).
 */
fun Modifier.bottomScrim(color: Color, startFraction: Float = 0.35f): Modifier = drawWithContent {
    drawContent()
    val s = startFraction.coerceIn(0f, 0.99f)
    val span = 1f - s
    drawRect(
        Brush.verticalGradient(
            0f to Color.Transparent,
            s to Color.Transparent,
            s + span * 0.25f to color.copy(alpha = color.alpha * 0.18f),
            s + span * 0.5f to color.copy(alpha = color.alpha * 0.5f),
            s + span * 0.75f to color.copy(alpha = color.alpha * 0.84f),
            1f to color,
        ),
    )
}

/** Darkens the start edge (iPad heroes keep text on the left over the backdrop). Eased like bottomScrim. */
fun Modifier.startScrim(color: Color, endFraction: Float = 0.7f): Modifier = drawWithContent {
    drawContent()
    val e = endFraction.coerceIn(0.01f, 1f)
    drawRect(
        Brush.horizontalGradient(
            0f to color,
            e * 0.3f to color.copy(alpha = color.alpha * 0.8f),
            e * 0.65f to color.copy(alpha = color.alpha * 0.35f),
            e to Color.Transparent,
        ),
    )
}

/** Darkens the top of artwork so the status bar and floating glass buttons stay legible. */
fun Modifier.topScrim(color: Color = Color.Black.copy(alpha = 0.45f), endFraction: Float = 0.25f): Modifier = drawWithContent {
    drawContent()
    drawRect(
        Brush.verticalGradient(
            0f to color,
            endFraction.coerceIn(0.01f, 1f) to Color.Transparent,
        ),
    )
}
