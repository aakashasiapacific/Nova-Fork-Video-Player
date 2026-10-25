package com.aakash.novafork.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * iOS's edge bounce for a page (owner): a list dragged past its top or bottom follows the finger
 * with growing resistance and springs back on release, and a fling that hits an end overshoots a
 * little and settles, instead of Android's stretch (turned off where this is used).
 *
 * With [onPullClose] (a movie or show page), pulling down past the top also shrinks the page
 * toward its poster, and letting go far enough closes it (the zoom back takes it from there).
 * [pullState] shares how far it is pulled (down positive) with the page (see [LocalPagePull]).
 */
@Composable
internal fun Modifier.iosOverscroll(
    onPullClose: (() -> Unit)? = null,
    pullState: MutableFloatState? = null,
    holdState: MutableFloatState? = null,
): Modifier {
    val scope = rememberCoroutineScope()
    val closePx = with(LocalDensity.current) { 120.dp.toPx() }
    val close by rememberUpdatedState(onPullClose)
    val haptics = LocalHapticFeedback.current
    val ownPull = remember { mutableFloatStateOf(0f) }
    var pull by (pullState ?: ownPull)
    // A pull-to-refresh can hold the page down this far (see LocalPageHold) until it lets go.
    val ownHold = remember { mutableFloatStateOf(0f) }
    val hold by (holdState ?: ownHold)
    var heightPx by remember { mutableFloatStateOf(1f) }
    val connection = remember {
        object : NestedScrollConnection {
            var settle: Job? = null

            fun springTo(target: Float, velocity: Float) {
                settle?.cancel()
                settle = scope.launch {
                    animate(pull, target, velocity, spring(dampingRatio = 0.82f, stiffness = 320f)) { value, _ -> pull = value }
                }
            }

            /** Back to rest, or to the refresh's hold while it holds the page down. */
            fun springBack(velocity: Float) = springTo(if (pull > 0f) hold else 0f, velocity)

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || pull == 0f) return Offset.Zero
                settle?.cancel()
                // Dragging back toward rest takes the pull up before the list scrolls again.
                val back = (pull > 0f && available.y < 0f) || (pull < 0f && available.y > 0f)
                if (!back) return Offset.Zero
                val next = pull + available.y
                // A held refresh keeps its gap open; the list scrolls on under it.
                val clamped = if (pull > 0f) next.coerceAtLeast(hold.coerceAtMost(pull)) else next.coerceAtMost(0f)
                val used = clamped - pull
                pull = clamped
                if (armed && pull <= closePx) armed = false
                return Offset(0f, used)
            }

            /** Pulled far enough that letting go closes the page: a tick says so. */
            var armed = false

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                settle?.cancel()
                // Rubber band: the further it is pulled, the harder it gets.
                val resistance = 0.55f / (1f + abs(pull) / (heightPx * 0.2f))
                pull += available.y * resistance
                val nowArmed = close != null && pull > closePx
                if (nowArmed != armed) {
                    armed = nowArmed
                    if (armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                armed = false
                if (pull == 0f) return Velocity.Zero
                val onClose = close
                if (onClose != null && pull > closePx) {
                    onClose()
                    return available
                }
                springBack(0f)
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // A fling that ran into an end: a small overshoot and back, as iOS lists do.
                if (pull == 0f && abs(available.y) > 600f) {
                    springBack(available.y * 0.12f)
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    // The refresh holding or letting go of the page moves it there.
    LaunchedEffect(connection) {
        snapshotFlow { hold }.collect { target ->
            if (abs(pull - target) > 0.5f && (target > 0f || pull > 0f)) connection.springTo(target, 0f)
        }
    }
    return this
        .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
        .nestedScroll(connection)
        .graphicsLayer {
            translationY = pull
            if (onPullClose != null && pull > 0f) {
                val shrink = 1f - (pull / heightPx).coerceIn(0f, 1f) * 0.4f
                scaleX = shrink
                scaleY = shrink
                shape = RoundedCornerShape((1f - shrink) * 120.dp.toPx())
                clip = true
            }
        }
}

/**
 * How far the page around is pulled past its top or bottom by [iosOverscroll] (down positive),
 * for headers that stretch into the gap; read it in a draw or layer block.
 */
internal val LocalPagePull = staticCompositionLocalOf<() -> Float> { { 0f } }

/**
 * How far a refresh holds the page around pulled down (0 = not held), set by LogoPullToRefresh
 * while the logo fills in the gap; null where the page has no bounce to hold.
 */
internal val LocalPageHold = staticCompositionLocalOf<MutableFloatState?> { null }

/**
 * iOS's large title stretch: pulling the page down past its top makes its big title grow a
 * little from its left edge, and it settles back with the bounce.
 */
@Composable
internal fun Modifier.stretchWithPull(): Modifier {
    val pull = LocalPagePull.current
    return graphicsLayer {
        val grow = 1f + (pull() / TITLE_STRETCH_PX_PER_PERCENT / 100f).coerceIn(0f, MAX_TITLE_STRETCH)
        scaleX = grow
        scaleY = grow
        transformOrigin = TransformOrigin(0f, 1f)
    }
}

/** Pulled 4 px per percent of growth, up to 12% larger. */
private const val TITLE_STRETCH_PX_PER_PERCENT = 4f
private const val MAX_TITLE_STRETCH = 0.12f

/**
 * A grid's items fade up one after another the first time they are shown (iOS), keyed so an item
 * scrolled away and back does not do it again; [seen] is the grid's own record.
 */
@Composable
internal fun Modifier.staggeredAppear(key: Any, index: Int, seen: MutableSet<Any>): Modifier {
    val first = remember(key) { seen.add(key) && index < STAGGER_ITEMS }
    if (!first) return this
    val shown = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(index * STAGGER_STEP_MS)
        shown.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = shown.value
        translationY = (1f - shown.value) * 18.dp.toPx()
    }
}

/** Only the first screenful staggers; later items are already there when scrolled to. */
private const val STAGGER_ITEMS = 18
private const val STAGGER_STEP_MS = 35L
