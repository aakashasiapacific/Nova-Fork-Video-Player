package com.aakash.novafork.ui.phone

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.ArtImage
import com.aakash.novafork.ui.common.ArtPlaceholder
import com.aakash.novafork.ui.common.ArtViewer
import com.aakash.novafork.ui.common.BackdropArt
import com.aakash.novafork.ui.common.Hairline
import com.aakash.novafork.ui.common.HeroTitle
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.IosIconButton
import com.aakash.novafork.ui.common.NovaDropdownMenu
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.PosterArt
import com.aakash.novafork.ui.common.RatingPill
import com.aakash.novafork.ui.common.bottomScrim
import com.aakash.novafork.ui.model.Artwork
import kotlin.math.roundToInt

/**
 * How far the poster reaches up into the backdrop's faded lower edge, on the Home hero and the
 * details pages alike (so opening a title from the hero keeps the poster where it was).
 */
internal val PosterOverlap = 40.dp

/** At most half the page for the picture, so a phone in landscape still shows the title. */
internal fun detailsBackdropHeight(pageHeight: Dp): Dp = pageHeight * 0.5f

/**
 * The phone's title header, shared by the Home hero and the details pages so both look the same:
 * the backdrop at 16:9 (at most [maxBackdropHeight]) below the status bar, with a darkened sliver
 * of it running under the bar, fading into the page; the poster reaching [PosterOverlap] up into
 * the faded edge; [info] beside the poster, starting below the picture.
 */
@Composable
internal fun BackdropPosterHeader(
    artwork: Artwork,
    title: String,
    modifier: Modifier = Modifier,
    maxBackdropHeight: Dp = Dp.Infinity,
    /** Home's hero: the backdrop drifts in a slow zoom, so the page feels alive. */
    drift: Boolean = false,
    /** What the backdrop fades into at its foot: the page, or clear over a tinted hero. */
    fadeTo: Color? = null,
    /** How far the page has scrolled past its top, in px: the backdrop moves at half that (parallax). */
    scrolled: () -> Float = { 0f },
    /** How far the page is pulled down past its top, in px: the backdrop grows taller by that (Home). */
    stretch: () -> Float = { 0f },
    /** A title's page: holding the poster shows it large ([ArtViewer]). */
    enlargePoster: Boolean = false,
    info: @Composable ColumnScope.() -> Unit,
) {
    val dims = LocalNovaDimens.current
    val posterShape = RoundedCornerShape(10.dp)
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val background = MaterialTheme.colorScheme.background
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val pageWidth = maxWidth
        val backdropHeight = statusBar + minOf(pageWidth * 9f / 16f, maxBackdropHeight)
        if (artwork.frameOnly) {
            // A camera-roll or saved video: one picture, the frame in its own shape, and the
            // title under it; no second copy of it as a poster.
            Column(Modifier.fillMaxWidth()) {
                FramePicture(artwork = artwork, modifier = Modifier.height(backdropHeight))
                Column(
                    Modifier.padding(start = dims.gutter, end = dims.gutter, top = 12.dp),
                    content = info,
                )
            }
            return@BoxWithConstraints
        }
        Box(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val tall = backdropHeight.roundToPx() + stretch().coerceAtLeast(0f).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, tall))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                // iOS parallax: as the page scrolls up, the backdrop follows at half the speed.
                .graphicsLayer {
                    translationY = scrolled() * 0.5f
                    // Over a tinted hero the picture itself fades out at its foot (a mask), so it
                    // melts into the tint below instead of ending in a line.
                    if (fadeTo == Color.Transparent) compositingStrategy = CompositingStrategy.Offscreen
                }
                .then(
                    if (fadeTo == Color.Transparent) {
                        Modifier.drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(0.5f to Color.Black, 1f to Color.Transparent),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            BackdropArt(artwork = artwork, modifier = Modifier.fillMaxSize().then(if (drift) Modifier.slowZoom() else Modifier))
            StatusBarShade(statusBar)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to (fadeTo ?: background),
                        ),
                    ),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = dims.gutter, end = dims.gutter, top = backdropHeight - PosterOverlap),
        ) {
            var enlarged by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .width(dims.detailPosterWidth)
                    .aspectRatio(2f / 3f)
                    .shadow(12.dp, posterShape)
                    .clip(posterShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), posterShape)
                    .then(
                        if (enlargePoster) {
                            Modifier.combinedClickable(onClick = {}, onLongClickLabel = "Show the poster large", onLongClick = { enlarged = true })
                        } else {
                            Modifier
                        },
                    ),
            ) {
                PosterArt(artwork = artwork, title = title, modifier = Modifier.fillMaxSize())
            }
            if (enlarged) {
                ArtViewer(onDismiss = { enlarged = false }) { art ->
                    Box(
                        art
                            .fillMaxWidth(0.82f)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        PosterArt(artwork = artwork, title = title, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = PosterOverlap + 8.dp),
                content = info,
            )
        }
    }
}

/**
 * A frame of a video in its own shape (a vertical camera clip stays vertical) on a blurred,
 * darkened copy of itself that fills the height [modifier] gives it, from the top of the screen
 * (the status bar strip shaded), fading into the page.
 */
@Composable
internal fun FramePicture(artwork: Artwork, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val shape = RoundedCornerShape(12.dp)
    val candidates = listOfNotNull(artwork.frameSource)
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val height = maxHeight
        ArtImage(
            candidates = candidates,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(40.dp),
            frameSource = artwork.frameSource,
        ) {
            ArtPlaceholder(seed = artwork.seed, title = null, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
        )
        // The sharp frame, as large as fits under the status bar, never wider than the page, and
        // centred in the room below the status bar (a wide frame would otherwise hang at the top
        // over an empty band).
        val aspect = (artwork.frameAspect ?: (16f / 9f)).coerceIn(0.4f, 2.4f)
        val room = height - statusBar - 24.dp
        val frameWidth = minOf(room * aspect, maxWidth - 40.dp)
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = statusBar),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(frameWidth)
                    .height(frameWidth / aspect)
                    .shadow(16.dp, shape)
                    .clip(shape),
            ) {
                ArtImage(
                    candidates = candidates,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    frameSource = artwork.frameSource,
                ) {
                    ArtPlaceholder(seed = artwork.seed, title = null, modifier = Modifier.fillMaxSize())
                }
            }
        }
        StatusBarShade(statusBar)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.88f to Color.Transparent,
                        1f to background,
                    ),
                ),
        )
    }
}

/** Darkens the status bar strip of a picture that runs under it: soft, darker at the very top. */
@Composable
internal fun StatusBarShade(statusBar: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(statusBar * 2)
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.85f),
                    0.5f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Transparent,
                ),
            ),
    )
}

/** The details pages' header: [BackdropPosterHeader] with the title, meta, rating and genres. */
@Composable
internal fun PhoneDetailsHeader(
    artwork: Artwork,
    title: String,
    meta: String,
    rating: Float?,
    genres: List<String>,
    modifier: Modifier = Modifier,
    originalTitle: String? = null,
    maxBackdropHeight: Dp = Dp.Infinity,
    scrolled: () -> Float = { 0f },
) {
    // The backdrop melts into the page's tint (see rememberDetailsTint) rather than ending in black.
    BackdropPosterHeader(
        artwork = artwork,
        title = title,
        modifier = modifier,
        maxBackdropHeight = maxBackdropHeight,
        fadeTo = Color.Transparent,
        scrolled = scrolled,
        enlargePoster = true,
    ) {
        // The title's TMDB logo, as on Home's hero (owner): the same artwork on both pages.
        HeroTitle(
            title = title,
            logoUrl = artwork.logoUrl,
            textStyle = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Start,
            logoAlignment = Alignment.BottomStart,
            maxWidthFraction = 1f,
            logoHeight = 48.dp,
        )
        if (!originalTitle.isNullOrBlank() && originalTitle != title) {
            Text(
                text = originalTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (rating != null && rating > 0f) {
            Spacer(Modifier.height(8.dp))
            RatingPill(rating)
        }
        if (genres.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = genres.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A details page's tint, as Home's hero has (owner: the page was blank black under the backdrop):
 * the colour of the foot of the backdrop, taken much deeper so it reads as the picture's own dark
 * on an AMOLED screen, never grey. Clear until known.
 */
@Composable
internal fun rememberDetailsTint(artwork: Artwork): Color {
    val base = rememberArtTint(artwork.backdropUrl ?: artwork.stillUrl ?: artwork.posterUrl)
    val deep = remember(base) {
        if (base.alpha == 0f) {
            base
        } else {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(base.toArgb(), hsv)
            hsv[2] *= DetailsTintDepth
            Color(android.graphics.Color.HSVToColor(hsv))
        }
    }
    val shown by animateColorAsState(deep, tween(600), label = "detailsTint")
    return shown
}

/**
 * One stretch of a details page's tint, from [from] of its strength at the top to [to] at the
 * foot; the header, Play and summary each carry one, so it runs on down to plain black.
 */
internal fun Modifier.tintBand(tint: Color, from: Float, to: Float): Modifier =
    if (tint.alpha == 0f) {
        this
    } else {
        background(Brush.verticalGradient(listOf(tint.copy(alpha = tint.alpha * from), tint.copy(alpha = tint.alpha * to))))
    }

/** How much of the backdrop colour's brightness the details tint keeps. */
private const val DetailsTintDepth = 0.6f

/** Icon button on a dark translucent circle so it stays visible over any artwork. */
@Composable
internal fun ScrimIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IosIconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.4f),
            contentColor = Color.White,
        ),
    ) {
        NovaIcon(icon, contentDescription = contentDescription)
    }
}

/**
 * Back button and optional overflow menu floating over the details page. [menu] receives a
 * `close` callback for its items. Once the page's own big [title] has scrolled away ([collapsed]
 * going from 0 to 1), the bar turns into iOS's: dark, with a hairline and the title small in
 * the middle.
 */
@Composable
internal fun PhoneDetailsTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    collapsed: () -> Float = { 0f },
    menu: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            // Keeps the status bar icons readable over bright artwork and scrolled content.
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
            .drawBehind {
                val shown = collapsed()
                if (shown > 0f) {
                    drawRect(CollapsedBarColor.copy(alpha = CollapsedBarColor.alpha * shown))
                    drawRect(
                        IosColors.Separator.copy(alpha = IosColors.Separator.alpha * shown),
                        topLeft = Offset(0f, size.height - Hairline.toPx()),
                        size = Size(size.width, Hairline.toPx()),
                    )
                }
            }
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp)
                    .graphicsLayer {
                        val shown = collapsed()
                        alpha = shown
                        // It rises into place as it fades in, as iOS's does.
                        translationY = (1f - shown) * 6.dp.toPx()
                    },
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ScrimIconButton(R.drawable.ic_arrow_back, contentDescription = "Back", onClick = onBack)
            Spacer(Modifier.weight(1f))
            if (menu != null) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    ScrimIconButton(R.drawable.ic_more_vert, contentDescription = "More options", onClick = { expanded = true })
                    NovaDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        menu { expanded = false }
                    }
                }
            }
        }
    }
}

/**
 * How far the details page's own title has scrolled out from under the top bar, eased: 0 while the
 * header shows, 1 once the bar should carry the title.
 */
@Composable
internal fun rememberTitleCollapse(listState: LazyListState, backdropHeight: Dp): () -> Float {
    val thresholdPx = with(LocalDensity.current) { backdropHeight.toPx() }
    val gone by remember(listState, thresholdPx) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > thresholdPx }
    }
    val shown by animateFloatAsState(if (gone) 1f else 0f, tween(200), label = "titleCollapse")
    return { shown }
}

/** iOS's bar material once content runs under it. */
private val CollapsedBarColor = Color(0xF2121214)

/** Shown while details for the current route are loading. */
@Composable
internal fun PhoneDetailsLoading(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        LoadingBox()
        PhoneDetailsTopBar(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
    }
}

/**
 * Who made a title, under its synopsis as Apple TV's iPhone pages list it: each role ("Starring",
 * "Director") in white, the names after it in grey; roles without names are left out.
 */
@Composable
internal fun Credits(lines: List<Pair<String, String?>>, modifier: Modifier = Modifier) {
    val shown = lines.mapNotNull { (label, names) -> names?.let { label to it } }
    if (shown.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { (label, names) ->
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) { append(label) }
                    append("  ")
                    append(names)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Tagline and an overview clamped to a few lines with a More/Less toggle. */
@Composable
internal fun OverviewBlock(
    tagline: String?,
    overview: String?,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 3,
) {
    if (tagline.isNullOrBlank() && overview.isNullOrBlank()) return
    Column(modifier.fillMaxWidth()) {
        if (!tagline.isNullOrBlank()) {
            Text(
                text = tagline,
                // Apple TV sets its one-line summaries upright and bold, never in italics.
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (!overview.isNullOrBlank()) {
            var expanded by rememberSaveable(overview) { mutableStateOf(false) }
            var overflows by remember(overview) { mutableStateOf(false) }
            val canToggle = expanded || overflows
            Column(
                Modifier
                    // MORE opens the synopsis by sliding it down, LESS closes it the same way.
                    .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 380f))
                    .clickable(
                        enabled = canToggle,
                        onClickLabel = if (expanded) "Show less" else "Show more",
                        onClick = { expanded = !expanded },
                    ),
            ) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result -> if (!expanded) overflows = result.hasVisualOverflow },
                )
                if (canToggle) {
                    Text(
                        // Apple TV's caps "MORE" in white after a cut-off synopsis.
                        text = if (expanded) "LESS" else "MORE",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}


/** A slow zoom in and back out over half a minute (Ken Burns), on a picture clipped to its box. */
@Composable
internal fun Modifier.slowZoom(): Modifier {
    val zoom by rememberInfiniteTransition(label = "slowZoom").animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 15_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "slowZoomScale",
    )
    return clipToBounds().graphicsLayer {
        scaleX = zoom
        scaleY = zoom
    }
}
