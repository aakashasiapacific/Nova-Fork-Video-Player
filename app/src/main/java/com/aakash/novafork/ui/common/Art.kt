package com.aakash.novafork.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import android.content.Context
import androidx.compose.ui.layout.onSizeChanged
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.transition.TransitionTarget
import coil.transition.Transition
import coil.transition.CrossfadeTransition
import coil.request.SuccessResult
import coil.request.ImageResult
import coil.decode.DataSource
import coil.request.videoFrameMillis
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
    alignment: Alignment = Alignment.Center,
    /** Grab [frameSource]'s frame at this time instead of 10% in. */
    frameAtMs: Long? = null,
    /** TMDB posters: load the smallest poster size that still fills the card (smoother grids). */
    rightSizePosters: Boolean = false,
    placeholder: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var index by remember(candidates) { mutableIntStateOf(0) }
    val current = candidates.getOrNull(index)
    // A soft sweep of light while the picture loads, instead of a flat grey box.
    var loading by remember(current) { mutableStateOf(current != null) }
    var widthPx by remember { mutableIntStateOf(0) }
    Box(if (rightSizePosters) modifier.onSizeChanged { widthPx = it.width } else modifier) {
        // While a picture is on its way: a plain dark tile (Apple TV), not the titled stand-in,
        // which only shows once there is no picture to come.
        if (current == null) placeholder() else Box(Modifier.fillMaxSize().background(LoadingTile))
        // The light sweep only for a picture that is slow to come; one from the cache never flashes it.
        if (loading) Box(Modifier.fillMaxSize().shimmer(delayMs = SHIMMER_DELAY_MS))
        // A resizable poster waits one layout pass for its width (grids measure ahead while scrolling).
        val sized = when {
            current == null -> null
            !rightSizePosters || current == frameSource -> current
            widthPx == 0 -> null
            else -> remember(current, rightSizedPoster(current, widthPx)) { cachedOrSized(context, current, widthPx) }
        }
        if (sized != null) {
            val model = remember(sized) {
                ImageRequest.Builder(context)
                    .data(sized)
                    // Fades in only when it came over the network: from the cache it is simply there.
                    .transitionFactory(NetworkCrossfade)
                    .apply {
                        if (current == frameSource) {
                            if (frameAtMs != null) videoFrameMillis(frameAtMs) else videoFramePercent(0.1)
                        }
                    }
                    .build()
            }
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                alignment = alignment,
                onSuccess = { loading = false },
                onError = {
                    loading = false
                    index += 1
                },
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
        rightSizePosters = true,
    ) {
        ArtPlaceholder(seed = artwork.seed.ifEmpty { title }, title = if (showTitleOnPlaceholder) title else null)
    }
}

/** TMDB poster widths, smallest first (all are real TMDB poster sizes). */
private val POSTER_WIDTHS = intArrayOf(185, 342, 500)
private const val TMDB_POSTER_MARKER = "/t/p/w500/"

/**
 * [url] with its TMDB size swapped for the smallest poster width that still fills [widthPx]; never
 * larger than the w500 it was asked with. Other URLs come back unchanged.
 */
internal fun rightSizedPoster(url: String, widthPx: Int): String {
    val at = url.indexOf(TMDB_POSTER_MARKER)
    if (at < 0 || widthPx <= 0) return url
    val width = POSTER_WIDTHS.firstOrNull { it >= widthPx } ?: return url
    return url.replaceRange(at, at + TMDB_POSTER_MARKER.length, "/t/p/w$width/")
}

/** The right-sized poster, unless the full one is already on disk (so offline art never goes missing). */
@OptIn(ExperimentalCoilApi::class)
private fun cachedOrSized(context: Context, url: String, widthPx: Int): String {
    val sized = rightSizedPoster(url, widthPx)
    if (sized == url) return url
    val cached = try {
        context.imageLoader.diskCache?.openSnapshot(url)?.use { true } ?: false
    } catch (e: Exception) {
        false
    }
    return if (cached) url else sized
}

/** 16:9 art for episode/continue-watching cards: still, backdrop, a frame of the video, placeholder. */
@Composable
fun LandscapeArt(
    artwork: Artwork,
    title: String,
    modifier: Modifier = Modifier,
    showTitleOnPlaceholder: Boolean = false,
) {
    val candidates = remember(artwork) {
        // Where it was left first, when that frame is asked for (Continue Watching).
        if (artwork.frameAtMs != null) {
            listOfNotNull(artwork.frameSource, artwork.stillUrl, artwork.backdropUrl)
        } else {
            listOfNotNull(artwork.stillUrl, artwork.backdropUrl, artwork.frameSource)
        }
    }
    ArtImage(
        candidates = candidates,
        contentDescription = title,
        modifier = modifier,
        frameSource = artwork.frameSource,
        frameAtMs = artwork.frameAtMs,
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

/**
 * A soft band of light sweeping once across a picture that is still loading (over its placeholder,
 * in the title's own colours), then gone: one pass, not a loop, so a slow load is not a flicker.
 */
@Composable
internal fun Modifier.shimmer(delayMs: Long = 0L): Modifier {
    val sweep = remember { Animatable(-1f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0L) kotlinx.coroutines.delay(delayMs)
        sweep.animateTo(2f, tween(durationMillis = 1_100, easing = FastOutSlowInEasing))
    }
    return drawWithContent {
        drawContent()
        val at = sweep.value
        if (at >= 2f) return@drawWithContent
        val band = size.width * 0.6f
        val x = size.width * at
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent),
                start = androidx.compose.ui.geometry.Offset(x - band, 0f),
                end = androidx.compose.ui.geometry.Offset(x, size.height),
            ),
        )
    }
}

/** How long a poster or backdrop takes to fade in as it arrives. */
private const val ART_FADE_MS = 250

/** A picture that takes longer than this to come gets the light sweep. */
private const val SHIMMER_DELAY_MS = 220L

/** The tile under a picture that is still loading: iOS's dark secondary grey. */
private val LoadingTile = Color(0xFF1C1C1E)

/** Crossfades a picture that came over the network; one from either cache appears at once. */
private object NetworkCrossfade : Transition.Factory {
    private val fade = CrossfadeTransition.Factory(ART_FADE_MS)

    override fun create(target: TransitionTarget, result: ImageResult): Transition =
        if (result is SuccessResult && result.dataSource == DataSource.NETWORK) {
            fade.create(target, result)
        } else {
            Transition.Factory.NONE.create(target, result)
        }
}
