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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.theme.NovaColors
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * iOS's swipe actions on a song (owner): swiped right it shows a blue Play Next, swiped left a red
 * Delete, each under the row as it slides; past [Trigger] a tick says letting go will do it, and
 * the row springs back either way. A swipe starting at the left edge is left to going back.
 */
@Composable
internal fun SwipeActionsRow(
    onPlayNext: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val playNext by rememberUpdatedState(onPlayNext)
    val delete by rememberUpdatedState(onDelete)
    Box(modifier) {
        val shown = offset.value
        if (shown != 0f) {
            SwipeAction(
                icon = if (shown > 0f) R.drawable.ic_play_next else R.drawable.ic_delete,
                color = if (shown > 0f) NovaColors.IosBlue else IosColors.Red,
                alignment = if (shown > 0f) Alignment.CenterStart else Alignment.CenterEnd,
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
                            val travel = if (abs(x) > trigger) (abs(x) - trigger) * 0.35f + trigger else abs(x)
                            scope.launch { offset.snapTo(if (x < 0f) -travel else travel) }
                            val nowArmed = abs(x) > trigger
                            if (nowArmed != armed) {
                                armed = nowArmed
                                if (armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        if (armed) {
                            if (x > 0f) playNext() else delete()
                        }
                        scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
                    }
                },
            content = content,
        )
    }
}

@Composable
private fun BoxScope.SwipeAction(@DrawableRes icon: Int, color: Color, alignment: Alignment) {
    Box(
        Modifier
            .matchParentSize()
            .background(color),
        contentAlignment = alignment,
    ) {
        NovaIcon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.padding(horizontal = 24.dp).size(22.dp))
    }
}

/** How far a row is swiped before letting go acts. */
private val Trigger = 88.dp

/** Swipes starting this close to the left edge are a swipe back, not an action. */
private val BackEdge = 28.dp
