package com.aakash.novafork.ui.audio

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.theme.NovaColors
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * iOS's swipe actions on a song (owner): swiped right it shows a blue Play Next and, in a playlist
 * ([onRemove]), swiped left a red Remove, each a capsule under the row as it slides (iOS 26/27's
 * rounded actions rather than a full-height block); past [Trigger] a tick says letting go will do
 * it, and the row springs back either way. A swipe starting at the left edge is left to going
 * back.
 */
@Composable
internal fun SwipeActionsRow(
    onPlayNext: () -> Unit,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val playNext by rememberUpdatedState(onPlayNext)
    val remove by rememberUpdatedState(onRemove)
    Box(modifier) {
        val shown = offset.value
        if (shown != 0f) {
            SwipeAction(
                icon = if (shown > 0f) R.drawable.ic_play_next else R.drawable.ic_remove_circle,
                color = if (shown > 0f) NovaColors.IosBlue else IosColors.Red,
                alignment = if (shown > 0f) Alignment.CenterStart else Alignment.CenterEnd,
                revealed = abs(shown),
            )
        }
        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
                // Solid while it slides, so the action shows beside it rather than through it.
                .background(if (shown != 0f) Color.Black else Color.Transparent)
                .pointerInput(Unit) {
                    val trigger = Trigger.toPx()
                    val edge = BackEdge.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.position.x < edge) return@awaitEachGesture
                        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            ?: return@awaitEachGesture
                        var x = offset.value
                        var armed = false
                        horizontalDrag(start.id) { change ->
                            change.consume()
                            // Past the trigger it follows with resistance, as iOS's rows do.
                            x += change.positionChange().x
                            // Nothing waits under a left swipe outside a playlist.
                            if (remove == null) x = x.coerceAtLeast(0f)
                            val travel = if (abs(x) > trigger) (abs(x) - trigger) * 0.35f + trigger else abs(x)
                            scope.launch { offset.snapTo(if (x < 0f) -travel else travel) }
                            val nowArmed = abs(x) > trigger
                            if (nowArmed != armed) {
                                armed = nowArmed
                                if (armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        if (armed) {
                            if (x > 0f) playNext() else remove?.invoke()
                        }
                        scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
                    }
                },
            content = content,
        )
    }
}

/**
 * The action under a sliding row, in iOS 26/27's form: a capsule set in from the row's edges that
 * fills the gap the row leaves ([revealed], px), its glyph showing once there is room for it.
 */
@Composable
private fun BoxScope.SwipeAction(@DrawableRes icon: Int, color: Color, alignment: Alignment, revealed: Float) {
    val density = LocalDensity.current
    val gapPx = with(density) { PillGap.toPx() }
    val glyphRoomPx = with(density) { GlyphRoom.toPx() }
    val pillWidthPx = (revealed - gapPx * 2f).coerceAtLeast(0f)
    if (pillWidthPx <= 0f) return
    Box(
        Modifier
            .matchParentSize()
            .padding(PillGap),
        contentAlignment = alignment,
    ) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val pillWidth = pillWidthPx.toInt().coerceAtMost(constraints.maxWidth)
                    val placeable = measurable.measure(Constraints.fixed(pillWidth, constraints.maxHeight))
                    layout(pillWidth, constraints.maxHeight) { placeable.place(0, 0) }
                }
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            NovaIcon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { alpha = (pillWidthPx / glyphRoomPx).coerceIn(0f, 1f) },
            )
        }
    }
}

/** How far a row is swiped before letting go acts. */
private val Trigger = 88.dp

/** Swipes starting this close to the left edge are a swipe back, not an action. */
private val BackEdge = 28.dp

/** The margin round an action's capsule. */
private val PillGap = 6.dp

/** How wide a capsule is before its glyph shows in full. */
private val GlyphRoom = 48.dp
