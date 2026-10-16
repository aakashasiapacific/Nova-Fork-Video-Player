package com.aakash.novafork.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFramePercent
import com.aakash.novafork.ui.model.Artwork
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

/** Two colours picked from [seed] so the same title always gets the same placeholder. */
fun placeholderColors(seed: String): Pair<Color, Color> {
    val palette = listOf(
        Color(0xFF3B2A55) to Color(0xFF14101F),
        Color(0xFF15405A) to Color(0xFF0B1620),
        Color(0xFF5A2A2A) to Color(0xFF1C0E0E),
        Color(0xFF254D3A) to Color(0xFF0C1A13),
        Color(0xFF5A4520) to Color(0xFF1D160A),
        Color(0xFF2B3A6B) to Color(0xFF0E1324),
        Color(0xFF5B2A4D) to Color(0xFF1E0E1A),
        Color(0xFF304A4F) to Color(0xFF0F1819),
    )
    return palette[abs(seed.hashCode() % palette.size)]
}

/** Gradient tile with the title, used when no artwork loads. */
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
                modifier = Modifier.padding(10.dp),
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, lineHeight = 18.sp),
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Darkens the bottom of an image so text on top stays readable, fading into [color]
 * (usually the page background so the art melts into the page).
 */
fun Modifier.bottomScrim(color: Color, startFraction: Float = 0.35f): Modifier = drawWithContent {
    drawContent()
    drawRect(
        Brush.verticalGradient(
            0f to Color.Transparent,
            startFraction to Color.Transparent,
            1f to color,
        ),
    )
}

/** Darkens the start edge (tablet heroes keep text on the left over the backdrop). */
fun Modifier.startScrim(color: Color, endFraction: Float = 0.7f): Modifier = drawWithContent {
    drawContent()
    drawRect(
        Brush.horizontalGradient(
            0f to color,
            endFraction to Color.Transparent,
        ),
    )
}
