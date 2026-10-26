package com.aakash.novafork.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * iOS 26/27's scroll edge with a page's small title: no solid band and no hairline, the content
 * fading out softly under the clock and the title instead (black at the top, clear a little
 * below the title). [edge] (0 to 1) is how strong the fade is; [showTitle] whether the small
 * title shows, fading in and out unless [instant] (a large title shrinking into it hands over
 * in place).
 */
@Composable
internal fun ScrollEdgeTitleBar(
    title: String,
    showTitle: Boolean,
    edge: () -> Float,
    modifier: Modifier = Modifier,
    instant: Boolean = false,
) {
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val strength = edge()
                if (strength <= 0f) return@drawBehind
                val solid = ((size.height - EdgeFadeDepth.toPx()) / size.height).coerceIn(0f, 1f)
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.96f * strength),
                        solid * 0.6f to Color.Black.copy(alpha = 0.9f * strength),
                        solid to Color.Black.copy(alpha = 0.7f * strength),
                        1f to Color.Transparent,
                    ),
                )
            },
    ) {
        // Always as tall as the bar, so the fade has its depth while the title is hidden.
        val line = with(LocalDensity.current) { MaterialTheme.typography.titleMedium.lineHeight.toDp() }
        Spacer(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(BarTitlePadding * 2 + line + EdgeFadeDepth),
        )
        val text: @Composable () -> Unit = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 56.dp, end = 56.dp, top = BarTitlePadding),
            )
        }
        if (instant) {
            if (showTitle) text()
        } else {
            AnimatedVisibility(visible = showTitle, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) { text() }
        }
    }
}

/**
 * Where a page's large title is on its way into the small title bar: 0 large in its place, 1
 * arrived (small, centred under the clock). [scrolled] is how far, in px, the page has moved
 * the title up from where it rests (a large value once it has scrolled out of sight).
 */
@Stable
class LargeTitleState internal constructor(private val scrolled: () -> Float) {
    /** How far the page scrolls while the title shrinks (px); 0 until it has been laid out. */
    internal var distance by mutableFloatStateOf(0f)

    /** Set (px) when the title's own header decides how far it collapses ([collapsingHeader]). */
    internal var fixedDistance = 0f

    /** How far the title's middle rests below the small title's middle (px). */
    internal var travel by mutableFloatStateOf(0f)

    /** How far its middle moves sideways to the middle of the screen (px). */
    internal var shift by mutableFloatStateOf(0f)

    internal fun scrolledNow(): Float = scrolled()

    val progress: Float
        get() {
            val distance = distance
            // Not laid out: large while the page is at its top, arrived once it has scrolled.
            if (distance <= 0f) return if (scrolled() > 0f) 1f else 0f
            return (scrolled() / distance).coerceIn(0f, 1f)
        }

    val collapsed: Boolean get() = progress >= 1f
}

/** A large title moved by a list that [scrolled] reads (see [LargeTitleState]). */
@Composable
fun rememberLargeTitleState(scrolled: () -> Float): LargeTitleState = remember { LargeTitleState(scrolled) }

/**
 * A large title in a lazy list's first item: how far the list has scrolled into that item, and
 * a large value once it is gone.
 */
@Composable
fun rememberLargeTitleState(list: LazyListState, titleIndex: Int = 0): LargeTitleState =
    rememberLargeTitleState {
        when {
            list.firstVisibleItemIndex < titleIndex -> 0f
            list.firstVisibleItemIndex == titleIndex -> list.firstVisibleItemScrollOffset.toFloat()
            else -> Float.MAX_VALUE
        }
    }

/**
 * iOS 27's large title: as the page scrolls it rides up with it a little slower than the
 * content, shrinking and sliding to the middle, and arrives exactly where the small title of
 * [CollapsedTitleBar] sits, which takes over. Goes last on the title's [androidx.compose.material3.Text]
 * so it measures the text itself.
 */
@Composable
fun Modifier.largeTitle(state: LargeTitleState, smallRatio: Float = SmallTitleRatio): Modifier {
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.getTop(density)
    val lineHeight = MaterialTheme.typography.titleMedium.lineHeight
    val barMiddle = with(density) { statusBar + BarTitlePadding.toPx() + lineHeight.toPx() / 2f }
    val minDistance = with(density) { MinCollapse.toPx() }
    return this
        .onGloballyPositioned { coords ->
            val position = coords.positionInRoot()
            val rootWidth = coords.findRootCoordinates().size.width
            val restMiddle = position.y + coords.size.height / 2f + state.scrolledNow().coerceAtMost(MaxTrackedScroll)
            val travel = (restMiddle - barMiddle).roundToInt().toFloat().coerceAtLeast(0f)
            state.travel = travel
            state.distance = state.fixedDistance.takeIf { it > 0f } ?: maxOf(travel, minDistance)
            state.shift = (rootWidth / 2f - (position.x + coords.size.width / 2f)).roundToInt().toFloat()
        }
        .graphicsLayer {
            val p = state.progress
            if (p > 0f) {
                val scale = 1f + (smallRatio - 1f) * p
                scaleX = scale
                scaleY = scale
                translationX = state.shift * p
                // The page moves it up by what it scrolled; this keeps it on its own, slower path.
                val scrolled = state.scrolledNow().coerceAtMost(state.distance)
                translationY = scrolled - p * state.travel
                // The small title takes over exactly where it arrives.
                alpha = if (p >= 1f) 0f else 1f
            }
        }
}

/** The small title bar for a page whose large title shrinks into it ([largeTitle]). */
@Composable
fun CollapsedTitleBar(state: LargeTitleState, title: String, modifier: Modifier = Modifier) {
    val collapsed by remember(state) { derivedStateOf { state.collapsed } }
    ScrollEdgeTitleBar(title = title, showTitle = collapsed, edge = { state.progress }, modifier = modifier, instant = true)
}

/**
 * A large title that sits above its page's list rather than in it (Search): scrolling the list up
 * first shrinks the title into the bar, the page moving up with it, and scrolling back down at the
 * list's top brings it back; let go halfway, it settles to the nearer end. Put [connection] on
 * a parent of the list and [collapsingHeader] on the title; [state] drives [largeTitle].
 */
@Stable
class CollapsingTitle {
    internal var offset by mutableFloatStateOf(0f)
    internal var limit = 0f

    val state = LargeTitleState { offset }

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Content moving up: the title gives way first.
            if (available.y >= 0f || limit <= 0f) return Offset.Zero
            val take = minOf(-available.y, limit - offset)
            if (take <= 0f) return Offset.Zero
            offset += take
            return Offset(0f, -take)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // Pulled down with the list already at its top: the title comes back before any bounce.
            if (available.y <= 0f || offset <= 0f) return Offset.Zero
            val give = minOf(available.y, offset)
            offset -= give
            return Offset(0f, give)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (offset > 0f && offset < limit) {
                val target = if (offset > limit / 2f) limit else 0f
                animate(offset, target, animationSpec = spring(dampingRatio = 1f, stiffness = 600f)) { value, _ -> offset = value }
            }
            return Velocity.Zero
        }
    }
}

/**
 * The title's place shrinks by how far [title] has collapsed (so what is below moves up with it)
 * and the title moves up by as much; it collapses until what is below meets the small title bar.
 */
@Composable
fun Modifier.collapsingHeader(title: CollapsingTitle): Modifier {
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.getTop(density)
    val lineHeight = MaterialTheme.typography.titleMedium.lineHeight
    val bar = with(density) { statusBar + BarTitlePadding.toPx() * 2 + lineHeight.toPx() }
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val limit = (placeable.height - bar).coerceAtLeast(0f)
        title.limit = limit
        title.state.fixedDistance = limit
        val shift = title.offset.coerceIn(0f, limit).roundToInt()
        layout(placeable.width, placeable.height - shift) { placeable.placeRelative(0, -shift) }
    }
}

/** A [CollapsingTitle] kept with the page. */
@Composable
fun rememberCollapsingTitle(): CollapsingTitle = remember { CollapsingTitle() }

/**
 * The end of a sideways row fades into the page (iOS 27) while there is more to scroll to, so a
 * card peeking in at the edge fades out instead of being cut off; at the row's end it shows whole.
 * The page is black, so the fade is a black wash over the row's last [width].
 */
@Composable
fun Modifier.fadeEndEdge(state: LazyListState, width: Dp = EdgeFadeWidth): Modifier {
    val more by remember(state) { derivedStateOf { state.canScrollForward } }
    val strength by animateFloatAsState(if (more) 1f else 0f, tween(220), label = "rowEdgeFade")
    val background = MaterialTheme.colorScheme.background
    return drawWithContent {
        drawContent()
        if (strength <= 0f) return@drawWithContent
        val fade = width.toPx().coerceAtMost(size.width)
        val rtl = layoutDirection == LayoutDirection.Rtl
        val left = if (rtl) 0f else size.width - fade
        val clear = background.copy(alpha = 0f)
        val solid = background.copy(alpha = strength)
        drawRect(
            Brush.horizontalGradient(
                colors = if (rtl) listOf(solid, clear) else listOf(clear, solid),
                startX = left,
                endX = left + fade,
            ),
            topLeft = Offset(left, 0f),
            size = Size(fade, size.height),
        )
    }
}

/** The small title's own padding, above and below it, in the bar. */
private val BarTitlePadding = 12.dp

/** How far below the small title the content still fades. */
private val EdgeFadeDepth = 20.dp

/** A large title shrinks over at least this much scrolling (iOS's ~52pt). */
private val MinCollapse = 48.dp

/** Scrolled further than this, a title's rest place is no longer worked out from it. */
private const val MaxTrackedScroll = 100_000f

/** The small title (17pt) is half the large one (34pt). */
private const val SmallTitleRatio = 0.5f

/** How wide the fade at a row's end is. */
private val EdgeFadeWidth = 32.dp
