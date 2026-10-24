package com.aakash.novafork.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.aakash.novafork.R
import com.aakash.novafork.ui.model.AppActions

/**
 * Long-press menu of a library video's card or row: [content] gets an `openMenu` callback to use as
 * its long-click. The menu offers Details and Add to playlist, then [extraItems] (each gets a
 * `close` callback), and opens under the card.
 */
@Composable
fun VideoMenuBox(
    videoId: Long,
    title: String,
    actions: AppActions,
    modifier: Modifier = Modifier,
    showDetails: Boolean = true,
    extraItems: (@Composable (close: () -> Unit) -> Unit)? = null,
    content: @Composable (openMenu: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        ContextMenuLift(open = open) { copy -> content(if (copy) ({}) else ({ open = true })) }
        NovaDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (showDetails) {
                NovaMenuItem(
                    text = { Text("Details") },
                    leadingIcon = { NovaIcon(R.drawable.ic_info, contentDescription = null) },
                    onClick = {
                        open = false
                        actions.openVideo(videoId)
                    },
                )
            }
            NovaMenuItem(
                text = { Text("Add to Playlist") },
                leadingIcon = { NovaIcon(R.drawable.ic_playlist_add, contentDescription = null) },
                onClick = {
                    open = false
                    actions.addToPlaylist(listOf(videoId), title)
                },
            )
            extraItems?.invoke { open = false }
        }
    }
}

/**
 * iOS's context menu look for whatever [content] is (a poster, a row, a cover) while [open]: it
 * lifts a little with a soft haptic knock, and in the phone shell the page behind blurs and dims
 * (LocalMenuFocus) while a sharp copy of it floats over the blur, growing to [grow], with the
 * menu under it. [content] is told whether it is drawing that copy; [copyBackground] backs a copy
 * that is see-through (a list row), so the blurred original does not show through it.
 */
@Composable
fun ContextMenuLift(
    open: Boolean,
    modifier: Modifier = Modifier,
    grow: Float = 1.08f,
    copyBackground: Color? = null,
    content: @Composable (copy: Boolean) -> Unit,
) {
    val lift by animateFloatAsState(if (open) 1f + (grow - 1f) / 2f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "menuLift")
    val focus = LocalMenuFocus.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(open) { if (open) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    DisposableEffect(open, focus) {
        if (open && focus != null) focus.value += 1
        onDispose { if (open && focus != null) focus.value -= 1 }
    }
    Box(modifier.onSizeChanged { size = it }) {
        Box(
            Modifier.graphicsLayer {
                scaleX = lift
                scaleY = lift
            },
        ) {
            content(false)
        }
        if (open && focus != null && size != IntSize.Zero) {
            Popup(popupPositionProvider = OverAnchor, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
                val density = LocalDensity.current
                val growing = remember { Animatable(1f) }
                LaunchedEffect(Unit) { growing.animateTo(grow, spring(dampingRatio = 0.6f, stiffness = 400f)) }
                Box(
                    Modifier
                        .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
                        .graphicsLayer {
                            scaleX = growing.value
                            scaleY = growing.value
                        }
                        .then(
                            if (copyBackground != null) {
                                Modifier.clip(RoundedCornerShape(12.dp)).background(copyBackground)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    content(true)
                }
            }
        }
    }
}

/**
 * How many context menus are open in the phone shell, which blurs and dims its page while any
 * is; null elsewhere (the menus then only lift their card).
 */
val LocalMenuFocus = staticCompositionLocalOf<MutableState<Int>?> { null }

/** Puts a popup exactly over its anchor. */
private object OverAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset(anchorBounds.left, anchorBounds.top)
}
