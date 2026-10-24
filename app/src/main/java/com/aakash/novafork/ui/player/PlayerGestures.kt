package com.aakash.novafork.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState
import kotlin.math.abs
import kotlin.math.min

/** Pointers that go down this close to the top or bottom edge are the system's (shade, home), not ours. */
private val SystemGestureEdge = 48.dp

/** A drag across the full width seeks at most this far, so long films stay precise. */
private const val MAX_SCRUB_SPAN_MS = 600_000L

/**
 * Full-screen touch layer under the controls.
 *
 * Unlocked: tap, double tap ([onDoubleTap] with the third of the screen it hit), horizontal drag
 * scrubs. Vertical drags do nothing: brightness and volume are the phone's own (the volume keys
 * show the player's indicator, see PlayerController.onVolumeKey).
 * Locked: only [onLockedTap].
 */
@Composable
internal fun PlayerGestureLayer(
    state: PlayerUiState,
    actions: PlayerActions,
    onTap: (TapZone) -> Unit,
    onDoubleTap: (TapZone) -> Unit,
    onLockedTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Gesture coroutines outlive recompositions (state changes every 250 ms); read the latest through these.
    val currentState by rememberUpdatedState(state)
    val currentActions by rememberUpdatedState(actions)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnLockedTap by rememberUpdatedState(onLockedTap)
    val locked = state.locked

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(locked) {
                if (locked) {
                    detectTapGestures(onTap = { currentOnLockedTap() })
                } else {
                    detectTapGestures(
                        onDoubleTap = { offset -> currentOnDoubleTap(tapZone(offset.x, size.width.toFloat())) },
                        onTap = { offset -> currentOnTap(tapZone(offset.x, size.width.toFloat())) },
                    )
                }
            }
            .pointerInput(locked) {
                if (!locked) detectPlayerDrags(state = { currentState }, actions = { currentActions })
            },
    )
}

/** Reports whether any pointer is down anywhere below this node, without consuming anything. */
internal fun Modifier.observeTouches(onTouchingChanged: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            onTouchingChanged(event.changes.any { it.pressed })
        }
    }
}

private fun tapZone(x: Float, width: Float): TapZone = when {
    x < width / 3f -> TapZone.LEFT
    x > width * 2f / 3f -> TapZone.RIGHT
    else -> TapZone.CENTER
}

private suspend fun PointerInputScope.detectPlayerDrags(
    state: () -> PlayerUiState,
    actions: () -> PlayerActions,
) {
    val edge = SystemGestureEdge.toPx()
    awaitEachGesture {
        // The tap detector consumes downs, so this one must look at consumed downs too.
        val down = awaitFirstDown(requireUnconsumed = false)
        val height = size.height.toFloat()
        if (down.position.y < edge || down.position.y > height - edge) return@awaitEachGesture

        var overSlop = Offset.Zero
        val start = awaitTouchSlopOrCancellation(down.id) { change, slop ->
            // Consuming marks the slop as reached and cancels the pending tap.
            change.consume()
            overSlop = slop
        } ?: return@awaitEachGesture

        val moved = start.position - down.position
        if (abs(moved.x) > abs(moved.y)) {
            scrub(start, overSlop.x, state(), actions)
        } else {
            // Swallowed, so a vertical swipe is not read as a tap either.
            drag(start.id) { it.consume() }
        }
    }
}

private suspend fun AwaitPointerEventScope.scrub(
    start: PointerInputChange,
    initialDx: Float,
    state: PlayerUiState,
    actions: () -> PlayerActions,
) {
    val duration = state.durationMs
    if (!state.seekable || duration <= 0L) {
        drag(start.id) { it.consume() }
        return
    }
    val startMs = state.positionMs
    val msPerPx = min(duration, MAX_SCRUB_SPAN_MS).toFloat() / size.width.coerceAtLeast(1)
    var dx = initialDx
    fun target(): Long = (startMs + dx * msPerPx).toLong().coerceIn(0L, duration)

    var committed = false
    try {
        actions().scrub(target(), false)
        drag(start.id) { change ->
            dx += change.positionChange().x
            change.consume()
            actions().scrub(target(), false)
        }
        actions().scrub(target(), true)
        committed = true
    } finally {
        // Cancelled mid-drag (lock, PiP, leaving the screen): drop the preview instead of seeking.
        if (!committed) actions().endGesture()
    }
}
