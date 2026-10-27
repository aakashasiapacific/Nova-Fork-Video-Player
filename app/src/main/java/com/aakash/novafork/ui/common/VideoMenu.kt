package com.aakash.novafork.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.theme.IosPalette
import com.aakash.novafork.ui.theme.NovaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Long-press menu of a library video's card or row: [content] gets an `openMenu` callback to use as
 * its long-click. The menu opens under the card on iOS 27's row of round quick actions (Play,
 * Favorite and Watchlist, which show and change whether the title is saved, and Details), then
 * offers Add to playlist, then [extraItems] (each gets a `close` callback).
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
            // Details is the quick row's (i), so the list starts at Add to Playlist.
            VideoQuickActions(videoId = videoId, actions = actions, showDetails = showDetails, close = { open = false })
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
 * The quick actions atop a video's long-press menu. Favorite and Watchlist save the video, or its
 * show for an episode ([AppActions.saveKeyFor]), and are left out where it can't be saved; they
 * fill in at once and the menu closes a beat later, so the change is seen without a pop-up.
 */
@Composable
private fun VideoQuickActions(videoId: Long, actions: AppActions, showDetails: Boolean, close: () -> Unit) {
    val saveKey = remember(videoId) { actions.saveKeyFor(videoId) }
    var favorite by remember(saveKey) { mutableStateOf(saveKey != null && actions.isFavorite(saveKey)) }
    var saved by remember(saveKey) { mutableStateOf(saveKey != null && actions.isOnWatchlist(saveKey)) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val closeSoon: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            delay(QUICK_CLOSE_MS)
            close()
        }
    }
    NovaMenuQuickActions {
        NovaMenuQuickAction(
            icon = R.drawable.ic_play_arrow,
            description = "Play",
            onClick = {
                close()
                actions.play(videoId)
            },
        )
        if (saveKey != null) {
            NovaMenuQuickAction(
                icon = if (favorite) R.drawable.ic_star else R.drawable.ic_star_outline,
                description = if (favorite) "Remove from Favorites" else "Add to Favorites",
                on = favorite,
                fill = IosPalette.Red,
                onClick = {
                    favorite = !favorite
                    actions.toggleFavorite(saveKey)
                    closeSoon()
                },
            )
            NovaMenuQuickAction(
                icon = if (saved) R.drawable.ic_bookmark_fill else R.drawable.ic_bookmark,
                description = if (saved) "Remove from Watchlist" else "Add to Watchlist",
                on = saved,
                fill = NovaColors.IosBlue,
                onClick = {
                    saved = !saved
                    actions.toggleWatchlist(saveKey)
                    closeSoon()
                },
            )
        }
        if (showDetails) {
            NovaMenuQuickAction(
                icon = R.drawable.ic_info,
                description = "Details",
                onClick = {
                    close()
                    actions.openVideo(videoId)
                },
            )
        }
    }
}

/** How long a quick action's new state shows before its menu closes. */
private const val QUICK_CLOSE_MS = 420L

/**
 * iOS's context menu look for whatever [content] is (a poster, a row, a cover) while [open]: it
 * lifts a little with a soft haptic knock, and in the phone shell the page behind blurs and dims
 * (LocalMenuFocus) while a sharp copy of it floats over the blur, growing to [grow], with the
 * menu under it. The copy sits on iOS 27's glass platter: a little bigger than it, with the
 * glass edge and corners that follow the card's own (concentric); [copyBackground] instead backs
 * a copy that is see-through and full width (a list row), so the blurred original does not show
 * through it. [content] is told whether it is drawing that copy.
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
            val density = LocalDensity.current
            // A platter round a card; a row's own background is its platter.
            val pad = if (copyBackground == null) PeekPad else 0.dp
            val padPx = with(density) { pad.roundToPx() }
            val corner = if (copyBackground == null) LocalNovaDimens.current.cardRadius + pad else RowPeekCorner
            val platter = RoundedCornerShape(corner)
            val position = remember(padPx) { OverAnchor(padPx) }
            Popup(popupPositionProvider = position, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
                val growing = remember { Animatable(1f) }
                LaunchedEffect(Unit) { growing.animateTo(grow, spring(dampingRatio = 0.6f, stiffness = 400f)) }
                Box(
                    Modifier
                        .size(with(density) { size.width.toDp() } + pad * 2, with(density) { size.height.toDp() } + pad * 2)
                        .graphicsLayer {
                            scaleX = growing.value
                            scaleY = growing.value
                        }
                        .shadow(elevation = 20.dp, shape = platter, clip = false)
                        .clip(platter)
                        .background(copyBackground ?: PeekPlatter)
                        .border(PopoverRim, platter)
                        .padding(pad),
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

/** Puts a popup over its anchor, [inset] px out from it on every side. */
private class OverAnchor(private val inset: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset(anchorBounds.left - inset, anchorBounds.top - inset)
}

/** How far the peek's glass platter reaches round a card. */
private val PeekPad = 8.dp

/** The platter's dark glass. */
private val PeekPlatter = Color(0xF2252527)

/** A lifted row's corners. */
private val RowPeekCorner = 16.dp
