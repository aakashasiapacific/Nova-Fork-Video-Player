package com.aakash.novafork.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.aakash.novafork.ui.adaptive.LocalDeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.theme.LocalNovaPalette
import com.aakash.novafork.ui.theme.NovaMotion
import com.aakash.novafork.ui.theme.NovaRadius
import com.aakash.novafork.ui.theme.NovaType
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// ───────────────────────────── Sheets ─────────────────────────────

/**
 * Sheet heights. Fit: as tall as its content (up to Large). Medium: opens at half the screen and
 * drags (or scrolls) up to Large. Large: full height below the status bar.
 * On tablets every detent is a centred form sheet as tall as its content (max 85%).
 */
enum class SheetDetent { Fit, Medium, Large }

/** Shared between the Dialog (back press) and its content (animations). */
private class SheetMotion {
    var closeRequested by mutableStateOf(false)
    var closing = false
    var job: Job? = null
}

/**
 * iOS sheet in its own Dialog window: grabber (36×5, white 30%), optional centred Headline
 * [title] with a glass ✕, dense glass (#1C1C1E-like) with 12dp top corners. It slides up, drags
 * down to dismiss (also from a scrolled-to-top list inside it), and dims the page behind.
 * [content] gets `dismiss`, which animates the sheet away and then calls [onDismissRequest].
 * Keep it in composition while visible: `if (open) GlassSheet(onDismissRequest = { open = false }) { … }`.
 */
@Composable
fun GlassSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    detent: SheetDetent = SheetDetent.Fit,
    title: String? = null,
    showClose: Boolean = title != null,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val motion = remember { SheetMotion() }
    Dialog(
        onDismissRequest = { if (dismissible) motion.closeRequested = true },
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        SideEffect {
            // The sheet draws its own animated dim.
            (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        }
        val palette = LocalNovaPalette.current
        CompositionLocalProvider(
            LocalGlassState provides null,
            LocalGroupedCellColor provides if (palette.dark) palette.raised else palette.cell,
        ) {
            SheetLayout(motion, onDismissRequest, modifier, detent, title, showClose, dismissible, content)
        }
    }
}

@Composable
private fun SheetLayout(
    motion: SheetMotion,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    detent: SheetDetent,
    title: String?,
    showClose: Boolean,
    dismissible: Boolean,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val tablet = LocalDeviceLayout.current.isTablet
    val palette = LocalNovaPalette.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dismissed by rememberUpdatedState(onDismissRequest)
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        val fullPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val maxSheet: Dp = if (tablet) maxHeight * 0.85f else maxHeight - topInset - 10.dp
        val fixedHeight = !tablet && detent != SheetDetent.Fit
        val mediumRestPx = if (!tablet && detent == SheetDetent.Medium) {
            with(density) { (maxSheet - maxHeight * 0.5f).toPx() }.coerceAtLeast(0f)
        } else {
            0f
        }
        val flingPx = with(density) { 1200.dp.toPx() }
        val maxDragPx = with(density) { 180.dp.toPx() }
        // Translation of the sheet from its open position: 0 = fully open, fullPx = off screen.
        var pos by remember { mutableFloatStateOf(fullPx) }
        var sheetPx by remember { mutableFloatStateOf(0f) }

        fun animateTo(target: Float, velocity: Float = 0f, then: (() -> Unit)? = null) {
            motion.job?.cancel()
            motion.job = scope.launch {
                animate(pos, target, velocity, spring(dampingRatio = 0.9f, stiffness = 420f)) { value, _ -> pos = value }
                then?.invoke()
            }
        }

        fun close(velocity: Float = 0f) {
            if (motion.closing) return
            motion.closing = true
            animateTo(fullPx, velocity.coerceAtLeast(0f)) { dismissed() }
        }

        fun dragBy(dy: Float) {
            if (motion.closing) return
            motion.job?.cancel()
            pos = (pos + dy).coerceAtLeast(0f)
        }

        fun settle(velocity: Float) {
            val hide = if (tablet) fullPx else sheetPx.coerceAtLeast(1f)
            val farEnough = pos > mediumRestPx + min(hide * 0.3f, maxDragPx)
            when {
                dismissible && farEnough -> close(velocity)
                velocity > flingPx -> when {
                    mediumRestPx > 0f && pos < mediumRestPx -> animateTo(mediumRestPx, velocity)
                    dismissible -> close(velocity)
                    else -> animateTo(mediumRestPx, velocity)
                }
                velocity < -flingPx -> animateTo(0f, velocity)
                mediumRestPx > 0f && pos > mediumRestPx / 2f -> animateTo(mediumRestPx, velocity)
                else -> animateTo(0f, velocity)
            }
        }

        fun atRest(): Boolean = abs(pos) < 0.5f || abs(pos - mediumRestPx) < 0.5f

        LaunchedEffect(Unit) { animateTo(mediumRestPx) }
        LaunchedEffect(motion.closeRequested) {
            if (motion.closeRequested) close()
        }

        val connection = remember(mediumRestPx, fullPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // Pulling up while the sheet is lowered raises the sheet before the list scrolls.
                    if (available.y < 0f && pos > 0f) {
                        val before = pos
                        dragBy(available.y)
                        return Offset(0f, pos - before)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    // The list is at its top and the finger keeps pulling down: move the sheet.
                    // (Finger only: a fling that reaches the top must not push the sheet down.)
                    if (available.y > 0f && source == NestedScrollSource.UserInput) {
                        dragBy(available.y)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!atRest()) {
                        settle(available.y)
                        return available
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (!atRest()) settle(available.y)
                    return Velocity.Zero
                }
            }
        }

        // Dim behind the sheet; tapping it dismisses.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val hide = if (tablet) fullPx else sheetPx.coerceAtLeast(1f)
                    alpha = (1f - (pos - mediumRestPx) / hide).coerceIn(0f, 1f)
                }
                .background(Color.Black.copy(alpha = if (palette.dark) 0.48f else 0.25f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (dismissible) close()
                },
        )

        val shape = if (tablet) {
            RoundedCornerShape(NovaRadius.Sheet)
        } else {
            RoundedCornerShape(topStart = NovaRadius.Sheet, topEnd = NovaRadius.Sheet)
        }
        val sheetWidth = if (tablet) Modifier.widthIn(max = 560.dp).fillMaxWidth() else Modifier.fillMaxWidth()
        val sheetHeight = if (fixedHeight) Modifier.height(maxSheet) else Modifier.heightIn(max = maxSheet)
        val dragState = rememberDraggableState { dy -> dragBy(dy) }
        Column(
            modifier
                .align(if (tablet) Alignment.Center else Alignment.BottomCenter)
                .then(sheetWidth)
                .then(sheetHeight)
                .onSizeChanged { sheetPx = it.height.toFloat() }
                .offset { IntOffset(0, pos.roundToInt()) }
                .glass(shape, null, GlassStyle.Dense, elevation = 16.dp)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    enabled = dismissible || mediumRestPx > 0f,
                    onDragStopped = { velocity -> settle(velocity) },
                )
                .nestedScroll(connection),
        ) {
            Box(
                Modifier
                    .padding(top = 5.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(if (palette.dark) Color(0x4DFFFFFF) else Color(0x33000000))
                    .align(Alignment.CenterHorizontally),
            )
            if (title != null || showClose) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = NovaType.headline,
                            color = palette.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 56.dp),
                        )
                    }
                    if (showClose) {
                        GlassCircleButton(
                            onClick = { close() },
                            icon = NovaGlyphs.Close,
                            contentDescription = "Close",
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 12.dp),
                            size = 32.dp,
                            iconSize = 12.dp,
                            glass = null,
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = fixedHeight),
            ) {
                content { close() }
            }
            if (!tablet) {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            } else {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ───────────────────────────── Alerts ─────────────────────────────

enum class AlertActionStyle { Default, Cancel, Destructive }

/**
 * One alert button. [preferred] makes it bold (the default action); without a preferred action
 * the Cancel button is bold, as on iOS.
 */
class AlertAction(
    val label: String,
    val style: AlertActionStyle = AlertActionStyle.Default,
    val preferred: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The iOS alert: a centred 270dp dense-glass card, Headline [title], Footnote [message], optional
 * [content] (a text field), and text buttons separated by hairlines — side by side for two
 * short actions, stacked otherwise. Blue labels, red for Destructive. Tapping outside does not
 * dismiss; back calls [onDismissRequest]. Buttons do not hide the alert themselves.
 */
@Composable
fun IosAlert(
    title: String,
    actions: List<AlertAction>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        val view = LocalView.current
        val dim = if (LocalNovaPalette.current.dark) 0.48f else 0.2f
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(dim) }
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 520f)) }
        CompositionLocalProvider(LocalGlassState provides null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                AlertCard(title, message, actions, content, modifier.graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = 1.12f - 0.12f * p
                    scaleY = 1.12f - 0.12f * p
                })
            }
        }
    }
}

@Composable
private fun AlertCard(
    title: String,
    message: String?,
    actions: List<AlertAction>,
    content: (@Composable ColumnScope.() -> Unit)?,
    modifier: Modifier,
) {
    val palette = LocalNovaPalette.current
    val anyPreferred = actions.any { it.preferred }
    fun bold(a: AlertAction) = a.preferred || (!anyPreferred && a.style == AlertActionStyle.Cancel)
    Column(
        modifier
            .width(270.dp)
            .glass(RoundedCornerShape(NovaRadius.Alert), null, GlassStyle.Dense, elevation = 24.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 19.dp, bottom = 17.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, style = NovaType.headline, color = palette.label, textAlign = TextAlign.Center)
            if (message != null) {
                Spacer(Modifier.height(3.dp))
                Text(text = message, style = NovaType.footnote, color = palette.label, textAlign = TextAlign.Center)
            }
            if (content != null) {
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
        Hairline()
        val sideBySide = actions.size == 2 && actions.all { it.label.length <= 14 }
        if (sideBySide) {
            Row(Modifier.fillMaxWidth()) {
                AlertButton(actions[0], bold(actions[0]), Modifier.weight(1f))
                Box(
                    Modifier
                        .width(0.5.dp)
                        .height(44.dp)
                        .background(palette.separator),
                )
                AlertButton(actions[1], bold(actions[1]), Modifier.weight(1f))
            }
        } else {
            actions.forEachIndexed { index, action ->
                if (index > 0) Hairline()
                AlertButton(action, bold(action), Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AlertButton(action: AlertAction, bold: Boolean, modifier: Modifier) {
    val palette = LocalNovaPalette.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val color = when {
        !action.enabled -> palette.tertiaryLabel
        action.style == AlertActionStyle.Destructive -> palette.red
        else -> palette.tint
    }
    Box(
        modifier
            .height(44.dp)
            .background(if (pressed) palette.fill else Color.Transparent)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = action.enabled,
                role = Role.Button,
                onClick = action.onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = action.label,
            style = NovaType.body.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

// ───────────────────────────── Menus ─────────────────────────────

/**
 * A menu row. Icons sit on the right (iOS), a checkmark on the left when [checked];
 * [startsSection] draws the thick section gap above the row.
 */
class GlassMenuItem(
    val label: String,
    @DrawableRes val icon: Int? = null,
    val checked: Boolean = false,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val startsSection: Boolean = false,
    val onClick: () -> Unit,
)

private class MenuPositionProvider(
    private val alignEnd: Boolean,
    private val gapPx: Int,
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val end = alignEnd != (layoutDirection == LayoutDirection.Rtl)
        val rawX = if (end) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = rawX.coerceIn(marginPx, maxX)
        val below = anchorBounds.bottom + gapPx
        val y = if (below + popupContentSize.height <= windowSize.height - marginPx) {
            below
        } else {
            (anchorBounds.top - gapPx - popupContentSize.height).coerceAtLeast(marginPx)
        }
        return IntOffset(x, y)
    }
}

/**
 * iOS pull-down / context menu: a dense glass panel that grows out of its anchor. Call it inside
 * the Box of the control that opens it (like a DropdownMenu); [alignEnd] lines its right edge up
 * with the anchor's. Selecting an item runs it and closes the menu. Scrolls past ~8 rows.
 */
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    title: String? = null,
    width: Dp = 250.dp,
) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = expanded
    if (!visible.currentState && !visible.targetState) return
    val density = LocalDensity.current
    val provider = remember(alignEnd, density) {
        MenuPositionProvider(
            alignEnd = alignEnd,
            gapPx = with(density) { 8.dp.roundToPx() },
            marginPx = with(density) { 8.dp.roundToPx() },
        )
    }
    val origin = TransformOrigin(if (alignEnd) 1f else 0f, 0f)
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        CompositionLocalProvider(LocalGlassState provides null) {
            AnimatedVisibility(
                visibleState = visible,
                enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 600f), initialScale = 0.8f, transformOrigin = origin) +
                    fadeIn(tween(110)),
                exit = scaleOut(tween(130), targetScale = 0.9f, transformOrigin = origin) + fadeOut(tween(110)),
            ) {
                MenuCard(items, title, width, modifier, onDismissRequest)
            }
        }
    }
}

@Composable
private fun MenuCard(
    items: List<GlassMenuItem>,
    title: String?,
    width: Dp,
    modifier: Modifier,
    onDone: () -> Unit,
) {
    val palette = LocalNovaPalette.current
    val showChecks = items.any { it.checked }
    Column(
        modifier
            .width(width)
            .glass(RoundedCornerShape(NovaRadius.Menu), null, GlassStyle.Dense, elevation = 20.dp)
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = NovaType.footnote,
                color = palette.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            )
            Hairline()
        }
        items.forEachIndexed { index, item ->
            if (index > 0) {
                if (item.startsSection) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(palette.fill),
                    )
                } else {
                    Hairline()
                }
            }
            MenuRow(item, showChecks) {
                item.onClick()
                onDone()
            }
        }
    }
}

@Composable
private fun MenuRow(item: GlassMenuItem, showChecks: Boolean, onClick: () -> Unit) {
    val palette = LocalNovaPalette.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val color = when {
        !item.enabled -> palette.tertiaryLabel
        item.destructive -> palette.red
        else -> palette.label
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(if (pressed) palette.fill else Color.Transparent)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = item.enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(start = if (showChecks) 10.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showChecks) {
            Box(Modifier.width(26.dp), contentAlignment = Alignment.CenterStart) {
                if (item.checked) {
                    Icon(NovaGlyphs.Check, contentDescription = "Selected", modifier = Modifier.size(15.dp), tint = color)
                }
            }
        }
        Text(
            text = item.label,
            style = NovaType.body,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.icon != null) {
            Spacer(Modifier.width(10.dp))
            Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(20.dp), tint = color)
        }
    }
}

/** "Sort ▾" / "Season 1 ▾": a glass capsule that opens a GlassMenu under itself. */
@Composable
fun PullDownButton(
    label: String,
    items: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
    menuTitle: String? = null,
    alignEnd: Boolean = false,
    height: Dp = 40.dp,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        GlassCapsuleButton(
            text = label,
            onClick = { open = true },
            trailingChevron = true,
            height = height,
            style = style,
            glass = glass,
        )
        GlassMenu(
            expanded = open,
            onDismissRequest = { open = false },
            items = items,
            alignEnd = alignEnd,
            title = menuTitle,
        )
    }
}

/** A glass circle ("…" by default) that opens a GlassMenu: details "more", player menus. */
@Composable
fun GlassMenuButton(
    items: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
    icon: ImageVector = NovaGlyphs.Ellipsis,
    contentDescription: String = "More",
    size: Dp = LocalNovaDimens.current.toolbarButtonSize,
    menuTitle: String? = null,
    alignEnd: Boolean = true,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        GlassCircleButton(
            onClick = { open = true },
            icon = icon,
            contentDescription = contentDescription,
            size = size,
            style = style,
            glass = glass,
        )
        GlassMenu(
            expanded = open,
            onDismissRequest = { open = false },
            items = items,
            alignEnd = alignEnd,
            title = menuTitle,
        )
    }
}
