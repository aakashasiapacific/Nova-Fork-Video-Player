package com.aakash.novafork.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * Apple TV's card tilt: while a finger rests on a poster, the card leans a few degrees toward
 * where it is pressed, and settles flat again on release. It only watches the touch (never
 * consumes it), so taps, long presses and scrolling work as before.
 */
@Composable
fun Modifier.pressTilt(maxDegrees: Float = TILT_DEGREES): Modifier {
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                fun lean(x: Float, y: Float) {
                    if (size.width == 0 || size.height == 0) return
                    // -1..1 from the centre; pressing the right edge tips that edge down (away).
                    val dx = ((x / size.width) * 2f - 1f).coerceIn(-1f, 1f)
                    val dy = ((y / size.height) * 2f - 1f).coerceIn(-1f, 1f)
                    scope.launch { tiltY.animateTo(dx * maxDegrees, Motion.press()) }
                    scope.launch { tiltX.animateTo(-dy * maxDegrees, Motion.press()) }
                }
                lean(down.position.x, down.position.y)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    // Moved like a scroll: lie flat and leave it to the list.
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    lean(change.position.x, change.position.y)
                }
                scope.launch { tiltX.animateTo(0f, Motion.content()) }
                scope.launch { tiltY.animateTo(0f, Motion.content()) }
            }
        }
        .graphicsLayer {
            rotationX = tiltX.value
            rotationY = tiltY.value
            cameraDistance = 12f * density
        }
}

private const val TILT_DEGREES = 5f
