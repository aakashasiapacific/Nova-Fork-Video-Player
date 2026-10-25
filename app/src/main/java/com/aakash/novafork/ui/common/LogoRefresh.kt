package com.aakash.novafork.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pull to refresh after X's (owner): pulling the page down past its top (the page's own stretch,
 * [LocalPagePull]) brings the app's logo out in white in the gap, growing with the pull and
 * stretching once letting go will refresh (a tick says so). Let go there and it goes back up with
 * the page; when the refresh is over the logo comes back for a moment and fills with its colours,
 * from the bottom up, to say the library is fresh. No spinner.
 *
 * [refreshing] is true from [onRefresh] until the refresh it started is over.
 */
@Composable
fun LogoPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val pull = LocalPagePull.current
    val density = LocalDensity.current
    val armPx = with(density) { RefreshPull.toPx() }
    val logoPx = with(density) { LogoSize.toPx() }
    val doneTopPx = with(density) { DoneGap.toPx() }
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val haptics = LocalHapticFeedback.current
    val refresh by rememberUpdatedState(onRefresh)
    // A refresh this pull started is under way (the logo fills once it is over).
    var started by remember { mutableStateOf(false) }
    val connection = remember(armPx) {
        object : NestedScrollConnection {
            // The page's bounce lets go first and springs back from the next frame, so the pull
            // still reads as it was when the finger came up.
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!started && pull() >= armPx) {
                    started = true
                    refresh()
                }
                return Velocity.Zero
            }
        }
    }
    LaunchedEffect(pull, armPx) {
        snapshotFlow { pull() >= armPx }.collect { armed ->
            if (armed && !started) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val shown = remember { Animatable(0f) }
    val fill = remember { Animatable(0f) }
    LaunchedEffect(started, refreshing) {
        if (!started || refreshing) return@LaunchedEffect
        // Over: the logo drops in under the status bar, fills with colour, and goes.
        launch { shown.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
        fill.animateTo(1f, tween(FILL_MS))
        delay(HOLD_MS)
        shown.animateTo(0f, tween(FADE_MS))
        fill.snapTo(0f)
        started = false
    }
    // Where this box sits on the page at rest, to find the gap above the page from inside it.
    var restTop by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .nestedScroll(connection)
            .onGloballyPositioned { if (pull() == 0f) restTop = it.positionInRoot().y },
    ) {
        content()
        LogoMark(
            fill = { fill.value },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(LogoSize)
                .graphicsLayer {
                    val p = pull().coerceAtLeast(0f)
                    val done = shown.value
                    if (done > 0f) {
                        translationY = statusTop + doneTopPx - (restTop + p)
                        val scale = 0.6f + 0.4f * done
                        scaleX = scale
                        scaleY = scale
                        alpha = done.coerceIn(0f, 1f)
                    } else {
                        // Centred in the gap between the status bar and the page's top.
                        val center = (statusTop + p) / 2f
                        translationY = center - logoPx / 2f - (restTop + p)
                        val reach = (p / armPx).coerceIn(0f, 1f)
                        // Past the point of refreshing it stretches with the pull, taller and narrower.
                        val stretch = ((p - armPx) / armPx).coerceIn(0f, 1f) * MAX_STRETCH
                        val scale = 0.4f + 0.6f * reach
                        scaleX = scale * (1f - stretch * 0.35f)
                        scaleY = scale * (1f + stretch)
                        // Let go, it rides back up with the page and is gone.
                        alpha = reach
                    }
                },
        )
    }
}

/**
 * The app's icon: its white glyph on its own, and its colours rising behind it as [fill] goes to 1,
 * inside the launcher's rounded square.
 */
@Composable
private fun LogoMark(fill: () -> Float, modifier: Modifier = Modifier) {
    val background = painterResource(R.drawable.ic_launcher_background)
    val glyph = painterResource(R.drawable.ic_launcher_foreground)
    Box(
        modifier.drawBehind {
            // Adaptive icon art is 108 units, of which the launcher shows the middle 72.
            val art = size.width * 108f / 72f
            val inset = (art - size.width) / 2f
            val f = fill()
            if (f > 0f) {
                val square = Path().apply {
                    addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(size.width * 0.225f)))
                }
                clipPath(square) {
                    clipRect(top = size.height * (1f - f)) {
                        translate(-inset, -inset) { with(background) { draw(Size(art, art)) } }
                    }
                }
            }
            translate(-inset, -inset) { with(glyph) { draw(Size(art, art)) } }
        },
    )
}

/** How far the page is pulled (after its resistance) before letting go refreshes. */
private val RefreshPull = 84.dp
private val LogoSize = 52.dp
private val DoneGap = 10.dp
private const val MAX_STRETCH = 0.35f
private const val FILL_MS = 420
private const val HOLD_MS = 380L
private const val FADE_MS = 260
