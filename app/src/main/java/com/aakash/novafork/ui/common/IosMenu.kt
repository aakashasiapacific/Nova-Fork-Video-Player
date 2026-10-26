package com.aakash.novafork.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.aakash.novafork.R
import kotlin.math.max
import kotlin.math.min

/**
 * iOS 27's menu in place of Material's DropdownMenu: a wide, rounded, dark glass popover with
 * 17pt rows that grows out of the button it came from (from its nearest corner, with a spring)
 * and shrinks back into it when it closes. [title], when set, heads the menu in small grey
 * type ("Sort by", "Sleep Timer"). Pair with [NovaMenuItem], [NovaMenuDivider] and
 * [NovaMenuQuickActions].
 */
@Composable
fun NovaDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = expanded
    if (shown.currentState || shown.targetState) {
        val origin = remember { mutableStateOf(TransformOrigin(0f, 0f)) }
        val density = LocalDensity.current
        val position = remember(offset, density) {
            MenuPosition(offset, density) { anchor, menu -> origin.value = growOrigin(anchor, menu) }
        }
        Popup(
            popupPositionProvider = position,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true),
        ) {
            // Material's own menu still drives its open/close this way.
            @Suppress("DEPRECATION")
            val transition = updateTransition(shown, label = "novaMenu")
            val scale by transition.animateFloat(
                transitionSpec = {
                    if (false isTransitioningTo true) spring<Float>(dampingRatio = 0.72f, stiffness = 520f) else tween<Float>(MENU_CLOSE_MS)
                },
                label = "novaMenuScale",
            ) { open -> if (open) 1f else MENU_CLOSED_SCALE }
            val alpha by transition.animateFloat(
                transitionSpec = { if (false isTransitioningTo true) tween<Float>(MENU_FADE_IN_MS) else tween<Float>(MENU_CLOSE_MS) },
                label = "novaMenuAlpha",
            ) { open -> if (open) 1f else 0f }
            Surface(
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = origin.value
                },
                shape = MenuShape,
                color = IosColors.Popover,
                tonalElevation = 0.dp,
                shadowElevation = 16.dp,
                border = PopoverRim,
            ) {
                Column(
                    modifier
                        .widthIn(min = 250.dp)
                        .padding(vertical = 6.dp)
                        .width(IntrinsicSize.Max)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (title != null) {
                        Text(
                            title,
                            style = MaterialTheme.typography.bodySmall,
                            color = IosColors.SecondaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                        )
                        NovaMenuDivider()
                    }
                    content()
                }
            }
        }
    }
}

/**
 * A row of [NovaDropdownMenu]: 17pt label, white glyph, red when [destructive]. With [checked]
 * set it is one choice of a pick-one menu (iOS 27): the current choice is ticked on the leading
 * side, the others leave the tick's room so the labels line up, and its own icon moves to the end.
 */
@Composable
fun NovaMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    checked: Boolean? = null,
) {
    val tint = if (destructive) IosColors.Red else Color.White
    // iOS's grey row highlight while pressed (the ripple is off).
    val press = remember { MutableInteractionSource() }
    val lead: (@Composable () -> Unit)? = if (checked != null) {
        { MenuTick(on = checked == true) }
    } else {
        leadingIcon
    }
    val trail: (@Composable () -> Unit)? = if (checked != null && trailingIcon == null) leadingIcon else trailingIcon
    DropdownMenuItem(
        text = { ProvideTextStyle(MaterialTheme.typography.bodyLarge) { text() } },
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp).indication(press, LocalIndication.current),
        interactionSource = press,
        leadingIcon = lead,
        trailingIcon = trail,
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = tint,
            leadingIconColor = tint,
            trailingIconColor = tint,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    )
}

/** The line between a menu's sections (and under its title). */
@Composable
fun NovaMenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(Hairline)
            .background(IosColors.Separator),
    )
}

/**
 * iOS 27's long-press menus open on a row of round quick actions (Play, Favorite, …) above the
 * list; [NovaMenuQuickAction]s go in it, a line under them. Goes first in a [NovaDropdownMenu].
 */
@Composable
fun NovaMenuQuickActions(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
    NovaMenuDivider()
}

/**
 * One round quick action of [NovaMenuQuickActions]: a glass disc with the glyph on it, filled
 * with [fill] while its thing is on (a starred title red, a saved one blue). The glyph swaps
 * with SF Symbols' replace effect and pops when [on] changes.
 */
@Composable
fun NovaMenuQuickAction(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    on: Boolean = false,
    fill: Color? = null,
) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .pressScale(press, pressed = 0.88f)
            .size(QuickActionSize)
            .clip(CircleShape)
            .background(if (on && fill != null) fill else Color.White.copy(alpha = 0.12f))
            .border(Hairline * 2, Color.White.copy(alpha = if (on && fill != null) 0f else 0.16f), CircleShape)
            .clickable(interactionSource = press, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        SymbolSwap(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp).popOnChange(on))
    }
}

/** The tick of a pick-one menu's current choice; room for it on the others. */
@Composable
private fun MenuTick(on: Boolean) {
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        if (on) NovaIcon(R.drawable.ic_check, contentDescription = "Selected", modifier = Modifier.size(15.dp))
    }
}

/**
 * Where a menu goes, as Material's menus place theirs: under its button (lined up with its start,
 * else its end), else above it, else against the window's edge; [onPositioned] gets the button's
 * and the menu's bounds so the menu can grow out of the button.
 */
private class MenuPosition(
    private val offset: DpOffset,
    private val density: Density,
    private val onPositioned: (anchor: IntRect, menu: IntRect) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val gap = with(density) { MenuGap.roundToPx() }
        val margin = with(density) { MenuVerticalMargin.roundToPx() }
        val edge = with(density) { MenuSideMargin.roundToPx() }
        val dx = with(density) { offset.x.roundToPx() }
        val dy = with(density) { offset.y.roundToPx() }
        val width = popupContentSize.width
        val height = popupContentSize.height
        val ltr = layoutDirection == LayoutDirection.Ltr
        val alignStart = if (ltr) anchorBounds.left + dx else anchorBounds.right - width - dx
        val alignEnd = if (ltr) anchorBounds.right - width - dx else anchorBounds.left + dx
        val x = when {
            alignStart >= edge && alignStart + width <= windowSize.width - edge -> alignStart
            alignEnd >= edge && alignEnd + width <= windowSize.width - edge -> alignEnd
            else -> alignStart.coerceIn(edge, max(edge, windowSize.width - width - edge))
        }
        val below = anchorBounds.bottom + dy + gap
        val above = anchorBounds.top - dy - gap - height
        val y = when {
            below + height <= windowSize.height - margin -> below
            above >= margin -> above
            anchorBounds.center.y < windowSize.height / 2 -> margin.coerceAtMost((windowSize.height - height).coerceAtLeast(0))
            else -> (windowSize.height - margin - height).coerceAtLeast(0)
        }
        onPositioned(anchorBounds, IntRect(IntOffset(x, y), popupContentSize))
        return IntOffset(x, y)
    }
}

/** The point of the menu nearest its button, which it grows from (Material's own rule). */
private fun growOrigin(anchor: IntRect, menu: IntRect): TransformOrigin {
    val pivotX = when {
        menu.left >= anchor.right -> 0f
        menu.right <= anchor.left -> 1f
        menu.width == 0 -> 0f
        else -> ((max(anchor.left, menu.left) + min(anchor.right, menu.right)) / 2 - menu.left).toFloat() / menu.width
    }
    val pivotY = when {
        menu.top >= anchor.bottom -> 0f
        menu.bottom <= anchor.top -> 1f
        menu.height == 0 -> 0f
        else -> ((max(anchor.top, menu.top) + min(anchor.bottom, menu.bottom)) / 2 - menu.top).toFloat() / menu.height
    }
    return TransformOrigin(pivotX, pivotY)
}

/** iOS 27's glass edge: brighter along the top, fading down the sides. */
internal val PopoverRim = BorderStroke(
    width = 0.75.dp,
    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.05f))),
)

private val MenuShape = RoundedCornerShape(22.dp)
private val MenuGap = 6.dp
private val MenuVerticalMargin = 48.dp
private val MenuSideMargin = 8.dp
private val QuickActionSize = 44.dp
private const val MENU_CLOSED_SCALE = 0.2f
private const val MENU_FADE_IN_MS = 120
private const val MENU_CLOSE_MS = 160
