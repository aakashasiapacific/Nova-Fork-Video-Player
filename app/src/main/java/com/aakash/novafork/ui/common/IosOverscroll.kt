package com.aakash.novafork.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
 */
@Composable
internal fun Modifier.iosOverscroll(onPullClose: (() -> Unit)? = null): Modifier {
    val scope = rememberCoroutineScope()
    val closePx = with(LocalDensity.current) { 120.dp.toPx() }
    val close by rememberUpdatedState(onPullClose)
    var pull by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    val connection = remember {
        object : NestedScrollConnection {
            var settle: Job? = null

            fun springBack(velocity: Float) {
                settle?.cancel()
                settle = scope.launch {
                    animate(pull, 0f, velocity, spring(dampingRatio = 0.82f, stiffness = 320f)) { value, _ -> pull = value }
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || pull == 0f) return Offset.Zero
                settle?.cancel()
                // Dragging back toward rest takes the pull up before the list scrolls again.
                val back = (pull > 0f && available.y < 0f) || (pull < 0f && available.y > 0f)
                if (!back) return Offset.Zero
                val next = pull + available.y
                val clamped = if (pull > 0f) next.coerceAtLeast(0f) else next.coerceAtMost(0f)
                val used = clamped - pull
                pull = clamped
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                settle?.cancel()
                // Rubber band: the further it is pulled, the harder it gets.
                val resistance = 0.55f / (1f + abs(pull) / (heightPx * 0.2f))
                pull += available.y * resistance
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
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
