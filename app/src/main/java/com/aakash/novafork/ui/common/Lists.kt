package com.aakash.novafork.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.theme.LocalNovaPalette
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaMotion
import com.aakash.novafork.ui.theme.NovaRadius
import com.aakash.novafork.ui.theme.NovaType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Sizes of iOS inset-grouped lists. */
object InsetGroupDefaults {
    /** Vertical gap between two groups (put it between InsetGroups yourself). */
    val SectionSpacing = 28.dp
    val RowMinHeight = 44.dp
    /** Leading/trailing padding inside a row. */
    val RowPadding = 16.dp
    /** Settings icon square. */
    val IconSize = 29.dp
    /** Gap between a leading icon and the title. */
    val IconGap = 14.dp
}

/**
 * Colour of InsetGroup cards (and SwipeToDeleteRow backgrounds) in this subtree; null = the page
 * cell colour (#1C1C1E / white). GlassSheet raises it to #2C2C2E in dark mode so groups stand out
 * on the sheet, as on iOS.
 */
val LocalGroupedCellColor = staticCompositionLocalOf<Color?> { null }

/** The cell colour InsetGroup uses here (see LocalGroupedCellColor). */
@Composable
@ReadOnlyComposable
fun groupedCellColor(): Color = LocalGroupedCellColor.current ?: LocalNovaPalette.current.cell

/** Footnote, uppercase, secondary: the header above an inset group. */
@Composable
fun ListSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = NovaType.footnote,
        color = NovaColors.secondaryLabel,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 7.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Footnote, secondary: the explanation under an inset group. */
@Composable
fun ListSectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = NovaType.footnote,
        color = NovaColors.secondaryLabel,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
    )
}

/**
 * iOS inset-grouped section: optional uppercase [header], a 10dp-corner #1C1C1E (white in light
 * mode; groupedCellColor()) card holding the rows, optional [footer]. Rows draw their own hairlines; the one under
 * the last row is hidden here, as on iOS.
 */
@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    horizontalPadding: Dp = LocalNovaDimens.current.listInset,
    containerColor: Color = groupedCellColor(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
    ) {
        if (header != null) ListSectionHeader(header)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NovaRadius.Group))
                .background(containerColor)
                .drawWithContent {
                    drawContent()
                    val h = 1.dp.toPx()
                    drawRect(containerColor, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                },
            content = content,
        )
        if (footer != null) ListSectionFooter(footer)
    }
}

/**
 * One iOS list row (44dp min): optional [leading] (usually ListRowIcon), title (+ subtitle),
 * [value] in secondary on the right, then [trailing] (e.g. IosToggle), a blue checkmark when
 * [checked], and a "›" when [chevron]. Highlights while pressed when clickable. The hairline
 * under it is inset to the title ([dividerInset] overrides, e.g. for 16:9 thumbnails).
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = Color.Unspecified,
    titleStyle: TextStyle = NovaType.body,
    titleMaxLines: Int = 1,
    chevron: Boolean = false,
    checked: Boolean = false,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    dividerInset: Dp? = null,
    horizontalPadding: Dp = InsetGroupDefaults.RowPadding,
    minHeight: Dp = InsetGroupDefaults.RowMinHeight,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalNovaPalette.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val clickable = enabled && (onClick != null || onLongClick != null)
    val highlight by animateColorAsState(
        if (pressed && clickable) palette.cellPressed else Color.Transparent,
        tween(durationMillis = if (pressed) 40 else 300),
        label = "rowHighlight",
    )
    val click = if (clickable) {
        Modifier.combinedClickable(
            interactionSource = source,
            indication = null,
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = { onClick?.invoke() },
        )
    } else {
        Modifier
    }
    val inset = dividerInset
        ?: if (leading != null) horizontalPadding + InsetGroupDefaults.IconSize + InsetGroupDefaults.IconGap else horizontalPadding
    Box(
        modifier
            .fillMaxWidth()
            .background(highlight)
            .then(click),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .alpha(if (enabled) 1f else 0.4f)
                .padding(start = horizontalPadding, end = horizontalPadding, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(InsetGroupDefaults.IconGap))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = titleStyle,
                    color = if (titleColor != Color.Unspecified) titleColor else palette.label,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = NovaType.footnote,
                        color = palette.secondaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            if (value != null) {
                Text(
                    text = value,
                    style = NovaType.body,
                    color = palette.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .widthIn(max = 220.dp),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            if (checked) {
                Spacer(Modifier.width(8.dp))
                Icon(NovaGlyphs.Check, contentDescription = "Selected", modifier = Modifier.size(17.dp), tint = palette.tint)
            }
            if (chevron) {
                Spacer(Modifier.width(8.dp))
                Icon(NovaGlyphs.ChevronRight, contentDescription = null, modifier = Modifier.size(13.dp), tint = palette.tertiaryLabel)
            }
        }
        if (showDivider) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = inset)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(palette.separator),
            )
        }
    }
}

/** A row whose only control is an iOS switch (tapping the title does nothing, as on iOS). */
@Composable
fun ListToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
) {
    ListRow(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        leading = leading,
        trailing = { IosToggle(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        showDivider = showDivider,
    )
}

/** iOS Settings icon: a coloured 29dp rounded square with a white glyph from res/drawable. */
@Composable
fun ListRowIcon(
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = InsetGroupDefaults.IconSize,
) {
    IconSquare(color, modifier, size) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(size * 0.62f), tint = Color.White)
    }
}

/** iOS Settings icon with a NovaGlyphs vector. */
@Composable
fun ListRowIcon(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = InsetGroupDefaults.IconSize,
) {
    IconSquare(color, modifier, size) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.58f), tint = Color.White)
    }
}

@Composable
private fun IconSquare(color: Color, modifier: Modifier, size: Dp, glyph: @Composable () -> Unit) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(NovaRadius.IconSquare))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        glyph()
    }
}

/** A 0.5dp separator line. */
@Composable
fun Hairline(modifier: Modifier = Modifier, startInset: Dp = 0.dp, color: Color = NovaColors.separator) {
    Box(
        modifier
            .padding(start = startInset)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(color),
    )
}

/**
 * iOS swipe-to-delete: swiping the row left reveals a red [actionLabel] button; a long swipe
 * deletes straight away. The row slides back after [onDelete] (if it is still there). Give the
 * row its opaque [containerColor] (the group's colour) so the red stays hidden at rest.
 * TalkBack users get the same action as a custom accessibility action.
 */
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    actionLabel: String = "Delete",
    containerColor: Color = groupedCellColor(),
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier.fillMaxWidth()) { content() }
        return
    }
    val palette = LocalNovaPalette.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    val revealPx = with(density) { 88.dp.toPx() }
    val delete by rememberUpdatedState(onDelete)

    fun close() {
        scope.launch { offset.animateTo(0f, NovaMotion.standard()) }
    }

    fun deleteAndReset() {
        scope.launch {
            if (widthPx > 0) offset.animateTo(-widthPx.toFloat(), NovaMotion.standard())
            delete()
            offset.animateTo(0f, NovaMotion.standard())
        }
    }

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            val min = if (widthPx > 0) -widthPx.toFloat() else -revealPx
            offset.snapTo((offset.value + delta).coerceIn(min, 0f))
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width }
            .clipToBounds()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(actionLabel) {
                        delete()
                        true
                    },
                )
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(palette.red)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { deleteAndReset() },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = actionLabel,
                style = NovaType.body,
                color = Color.White,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(88.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(containerColor)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val v = offset.value
                        when {
                            widthPx > 0 && v < -widthPx * 0.6f -> deleteAndReset()
                            v < -revealPx / 2f || velocity < -800f -> offset.animateTo(-revealPx, NovaMotion.standard())
                            else -> offset.animateTo(0f, NovaMotion.standard())
                        }
                    },
                ),
        ) {
            content()
            if (offset.value < -1f) {
                // While the button shows, a tap on the row closes it instead of opening the row.
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { close() },
                )
            }
        }
    }
}
