package com.aakash.novafork.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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
    // iOS lifts what a context menu is for while the menu is open.
    val lift by animateFloatAsState(if (open) 1.04f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "menuLift")
    // In the phone shell the page behind blurs and dims (LocalMenuFocus) while a sharp copy of
    // the card floats over it, a little larger, with the menu under it: iOS's context menu.
    val focus = LocalMenuFocus.current
    // The soft knock iOS gives as a context menu opens.
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
            content { open = true }
        }
        if (open && focus != null && size != IntSize.Zero) {
            Popup(popupPositionProvider = OverAnchor, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
                val density = LocalDensity.current
                val grow = remember { Animatable(1f) }
                LaunchedEffect(Unit) { grow.animateTo(1.08f, spring(dampingRatio = 0.6f, stiffness = 400f)) }
                Box(
                    Modifier
                        .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
                        .graphicsLayer {
                            scaleX = grow.value
                            scaleY = grow.value
                        },
                ) {
                    content {}
                }
            }
        }
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
 * How many context menus are open in the phone shell, which blurs and dims its page while any
 * is; null elsewhere (the menus then only lift their card).
 */
val LocalMenuFocus = staticCompositionLocalOf<MutableState<Int>?> { null }

/** Puts a popup exactly over its anchor. */
private object OverAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset(anchorBounds.left, anchorBounds.top)
}
