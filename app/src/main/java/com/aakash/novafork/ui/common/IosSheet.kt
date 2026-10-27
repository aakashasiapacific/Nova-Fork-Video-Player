package com.aakash.novafork.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import kotlinx.coroutines.launch

/**
 * iOS 27's sheet over Material's modal bottom sheet: a rounded dark glass card that floats a
 * little in from the sides and bottom, its corners following the screen's own (concentric); a
 * sheet tall enough to fill the screen instead runs edge to edge under the status bar with only
 * its top corners rounded, as iOS's large sheets do. A grabber sits on top; with [title] (and
 * [subtitle]) a centred header follows, and with [done] a round tick button at its end closes
 * the sheet (iOS 26/27's glass tick in place of a "Done" label). [content] gets `close`, which
 * slides the sheet away and then calls [onDismissRequest]. While it is up, the page behind sinks
 * back ([sinkBehindSheets]).
 */
@Composable
fun IosSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    done: Boolean = title != null,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
    }
    val depth = LocalSheetDepth.current
    val up = sheetState.targetValue != SheetValue.Hidden
    DisposableEffect(depth, up) {
        if (depth != null && up) depth.open += 1
        onDispose { if (depth != null && up) depth.open -= 1 }
    }
    val corner = rememberConcentricCorner(SheetGap)
    val cardColor = IosColors.GroupedCard.copy(alpha = 0.86f + 0.12f * LocalGlassTint.current)
    // Decided once, from the first layout: a sheet that fills the screen stays full width.
    var tall by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = Color.Transparent,
        contentColor = Color.White,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fullHeight = constraints.maxHeight
            val shape = if (tall) IosSheetShape else RoundedCornerShape(corner)
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { if (!tall && constraints.hasBoundedHeight && it.height >= fullHeight - 2) tall = true }
                    .then(
                        if (tall) {
                            Modifier
                                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                                .padding(top = SheetGap)
                        } else {
                            Modifier
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                                .padding(start = SheetGap, end = SheetGap, bottom = SheetGap)
                        },
                    )
                    .shadow(elevation = 18.dp, shape = shape, clip = false)
                    .clip(shape)
                    .background(cardColor)
                    .border(PopoverRim, shape)
                    .then(if (tall) Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)) else Modifier),
            ) {
                IosGrabber()
                if (title != null || done) {
                    SheetHeader(title = title, subtitle = subtitle, done = done, onDone = close)
                }
                content(close)
            }
        }
    }
}

/** The sheet's centred title (and line under it) with the tick at the end. */
@Composable
private fun SheetHeader(title: String?, subtitle: String?, done: Boolean, onDone: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(start = 16.dp, end = 14.dp, bottom = 8.dp),
    ) {
        if (title != null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = IosColors.SecondaryLabel,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (done) SheetDoneButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/**
 * iOS 26/27's Done: a round blue glass button with a tick (no "Done" label), read out as "Done".
 */
@Composable
fun SheetDoneButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(press, pressed = 0.88f)
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(Hairline * 2, Color.White.copy(alpha = 0.24f), CircleShape)
            .clickable(interactionSource = press, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Done" },
        contentAlignment = Alignment.Center,
    ) {
        NovaIcon(R.drawable.ic_check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** How many [IosSheet]s are up over the app's pages. */
@Stable
class SheetDepth {
    var open by mutableIntStateOf(0)
}

/** The app's [SheetDepth]; null where nothing sinks (the video player, whose picture can't). */
val LocalSheetDepth = staticCompositionLocalOf<SheetDepth?> { null }

/**
 * iOS's page behind a sheet: while any [IosSheet] is up it sinks back a little, its corners
 * rounding like the screen's, and comes forward again as the sheet goes. Nothing without a
 * [LocalSheetDepth].
 */
fun Modifier.sinkBehindSheets(): Modifier = composed {
    val depth = LocalSheetDepth.current ?: return@composed Modifier
    val sunk by animateFloatAsState(
        targetValue = if (depth.open > 0) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
        label = "sheetSink",
    )
    val corner = rememberScreenCornerRadius()
    Modifier.graphicsLayer {
        val scale = 1f - SINK_SCALE * sunk
        scaleX = scale
        scaleY = scale
        clip = sunk > 0f
        shape = if (sunk > 0f) RoundedCornerShape(corner * sunk) else RectangleShape
    }
}

/** How far a floating sheet stands in from the screen's sides and bottom. */
private val SheetGap = 8.dp

/** How much smaller the page behind a sheet gets. */
private const val SINK_SCALE = 0.06f
