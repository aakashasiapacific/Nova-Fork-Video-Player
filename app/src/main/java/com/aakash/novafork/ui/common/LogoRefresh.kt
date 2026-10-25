package com.aakash.novafork.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pull to refresh after X's (owner): pulling the page down past its top (the page's own stretch,
 * [LocalPagePull]) brings the app's logo out in white in the gap, growing with the pull and
 * stretching once letting go will refresh (a tick says so). Let go there and the page stays
 * down by the logo's height ([LocalPageHold]) while its colours rise inside it, from the bottom
 * up; full once the refresh is over (or after a few seconds, the scan banner takes it from there),
 * then the page springs back and the logo goes with the gap. The logo never leaves the gap, so
 * it is never drawn over the page. No spinner.
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
    val hold = LocalPageHold.current
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val logoPx = with(density) { LogoSize.toPx() }
    val marginPx = with(density) { LogoMargin.toPx() }
    // Held open just tall enough for the logo below the status bar; refreshing takes a little more.
    val holdPx = statusTop + logoPx + 2 * marginPx
    val armPx = holdPx + with(density) { ArmBeyondHold.toPx() }
    val haptics = LocalHapticFeedback.current
    val refresh by rememberUpdatedState(onRefresh)
    val currentRefreshing by rememberUpdatedState(refreshing)
    // A refresh this pull started is under way, from letting go until the page is let go of.
    var started by remember { mutableStateOf(false) }
    val connection = remember(armPx, holdPx) {
        object : NestedScrollConnection {
            // The page's bounce lets go first and springs back from the next frame, so the pull
            // still reads as it was when the finger came up.
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!started && pull() >= armPx) {
                    started = true
                    hold?.floatValue = holdPx
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
    val fill = remember { Animatable(0f) }
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        fill.snapTo(0f)
        // The colours rise while it works, slowing as they go, and top up once it is over.
        val rise = launch { fill.animateTo(RISE_TO, tween(RISE_MS, easing = LinearOutSlowInEasing)) }
        withTimeoutOrNull(MAX_HOLD_MS) {
            delay(MIN_HOLD_MS)
            snapshotFlow { currentRefreshing }.first { !it }
        }
        rise.cancel()
        fill.animateTo(1f, tween(FILL_FINISH_MS))
        delay(FULL_MS)
        hold?.floatValue = 0f
        // It goes with the gap as the page springs back.
        withTimeoutOrNull(SPRING_BACK_MS) { snapshotFlow { pull() }.first { it < 1f } }
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
                    // The gap the pull opens below the status bar; the logo stays inside it.
                    val gap = p - statusTop
                    val room = ((gap - 2 * marginPx) / logoPx).coerceIn(0f, 1f)
                    val reach = (p / armPx).coerceIn(0f, 1f)
                    val scale = minOf(0.5f + 0.5f * reach, room)
                    // Past the point of refreshing it stretches with the pull, taller and narrower.
                    val stretch = if (started) 0f else ((p - armPx) / armPx).coerceIn(0f, 1f) * MAX_STRETCH
                    val tallest = ((gap - 2 * marginPx) / logoPx).coerceAtLeast(0f)
                    scaleX = scale * (1f - stretch * 0.35f)
                    scaleY = minOf(scale * (1f + stretch), tallest)
                    translationY = statusTop + gap / 2f - logoPx / 2f - (restTop + p)
                    alpha = (gap / logoPx).coerceIn(0f, 1f)
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

/** How far past the hold the page is pulled (after its resistance) before letting go refreshes. */
private val ArmBeyondHold = 16.dp
private val LogoSize = 40.dp
private val LogoMargin = 8.dp
private const val MAX_STRETCH = 0.35f
private const val RISE_TO = 0.8f
private const val RISE_MS = 1_400
private const val MIN_HOLD_MS = 500L
private const val MAX_HOLD_MS = 3_000L
private const val FILL_FINISH_MS = 260
private const val FULL_MS = 280L
private const val SPRING_BACK_MS = 900L

/**
 * The app opening (owner): the launch screen's white logo (the system splash shows the same
 * glyph, at the same place and size) fills with its blue from the bottom up, as the refresh does,
 * then the black screen fades away into Home. [onDone] once it has gone.
 */
@Composable
fun LaunchLogo(onDone: () -> Unit) {
    val fill = remember { Animatable(0f) }
    val shown = remember { Animatable(1f) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        delay(LAUNCH_START_MS)
        fill.animateTo(1f, tween(LAUNCH_FILL_MS, easing = LinearOutSlowInEasing))
        delay(LAUNCH_HOLD_MS)
        shown.animateTo(0f, tween(LAUNCH_FADE_MS))
        done()
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown.value }
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // The splash draws a plain icon 288dp wide whose middle 192dp shows; this matches it.
        LogoMark(fill = { fill.value }, modifier = Modifier.size(LaunchLogoSize))
    }
}

private val LaunchLogoSize = 192.dp
private const val LAUNCH_START_MS = 80L
private const val LAUNCH_FILL_MS = 420
private const val LAUNCH_HOLD_MS = 140L
private const val LAUNCH_FADE_MS = 280
