package com.aakash.novafork.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.adaptive.LocalDeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.theme.LocalNovaPalette
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaMotion
import com.aakash.novafork.ui.theme.NovaType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Sizes of the floating top bar (back / more / account circles) under the status bar. */
object NavBarDefaults {
    /** Height of the bar row below the status bar. */
    val Height = 52.dp
    /** Side padding of the circles in the bar. */
    val HorizontalPadding = 16.dp
    /** Gap between the phone tab bar capsule and its search circle. */
    val TabBarSearchGap = 12.dp
    /** Gap between the tab bar and the navigation-bar inset. */
    val TabBarBottomGap = 8.dp
}

/** One phone tab: icon above a Caption 2 label. */
@Immutable
data class GlassTabItem(val label: String, @DrawableRes val icon: Int)

/**
 * PHONE ONLY. The iOS 26 tab bar: a floating 64dp Liquid Glass capsule 20dp from the sides and
 * above the navigation bar, with icon+label items (selected = label colour on a lighter lens,
 * others secondary), plus a separate 64dp glass circle for Search. Draw it over the page (in
 * GlassPage's chrome) and pad scrolling content with glassTabBarClearance().
 * [selectedIndex] = -1 selects no tab (e.g. while Search is open with [searchSelected]).
 */
@Composable
fun GlassTabBar(
    items: List<GlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    searchSelected: Boolean = false,
    glass: GlassState? = LocalGlassState.current,
) {
    val height = LocalNovaDimens.current.tabBarHeight
    val palette = LocalNovaPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = NavBarDefaults.TabBarBottomGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhoneTabCapsule(
            items = items,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            glass = glass,
            modifier = Modifier
                .weight(1f)
                .height(height),
        )
        Spacer(Modifier.width(NavBarDefaults.TabBarSearchGap))
        val source = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(height)
                .selectable(
                    selected = searchSelected,
                    interactionSource = source,
                    indication = null,
                    role = Role.Tab,
                    onClick = onSearch,
                )
                .pressEffect(source, scale = 0.92f, alpha = 1f)
                .glass(CircleShape, glass, elevation = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (searchSelected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(lensColor()),
                )
            }
            Icon(NovaGlyphs.Search, contentDescription = "Search", modifier = Modifier.size(24.dp), tint = palette.label)
        }
    }
}

@Composable
private fun PhoneTabCapsule(
    items: List<GlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    glass: GlassState?,
    modifier: Modifier,
) {
    val palette = LocalNovaPalette.current
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    val padPx = with(density) { 4.dp.toPx() }
    val count = items.size.coerceAtLeast(1)
    val itemPx = if (widthPx > 0) (widthPx - padPx * 2f) / count else 0f
    val showLens = selectedIndex in items.indices
    val lensX = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(selectedIndex, itemPx) {
        if (itemPx <= 0f || !showLens) return@LaunchedEffect
        val target = selectedIndex * itemPx
        if (!placed) {
            lensX.snapTo(target)
            placed = true
        } else {
            lensX.animateTo(target, NovaMotion.bouncy())
        }
    }
    Box(
        modifier
            .glass(CircleShape, glass, elevation = 10.dp)
            .onSizeChanged { widthPx = it.width }
            .padding(4.dp),
    ) {
        if (showLens && placed && itemPx > 0f) {
            Box(
                Modifier
                    .offset { IntOffset(lensX.value.roundToInt(), 0) }
                    .width(with(density) { itemPx.toDp() })
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(lensColor()),
            )
        }
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val tint by animateColorAsState(
                    if (selected) palette.label else palette.secondaryLabel,
                    NovaMotion.fade(NovaMotion.FastMs),
                    label = "tabTint",
                )
                val source = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            interactionSource = source,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                        .pressEffect(source, scale = 0.92f, alpha = 1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = tint)
                    Spacer(Modifier.height(2.dp))
                    Text(text = item.label, style = NovaType.tabLabel, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Bottom padding that lets the last row of a phone page scroll clear of GlassTabBar. */
@Composable
fun glassTabBarClearance(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        LocalNovaDimens.current.tabBarHeight + NavBarDefaults.TabBarBottomGap + 16.dp

/**
 * TABLET ONLY. The iPadOS 26 tab bar: a floating glass capsule of text tabs centred at the top
 * (selected = label text on a lighter glass pill that slides), a separate Search glass circle
 * right after it, and an account circle at the far right (→ Settings). Draw it over the page.
 */
@Composable
fun GlassTopTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier,
    searchSelected: Boolean = false,
    accountSelected: Boolean = false,
    glass: GlassState? = LocalGlassState.current,
) {
    val dims = LocalNovaDimens.current
    val height = dims.tabBarHeight
    Layout(
        content = {
            TextTabCapsule(items, selectedIndex, onSelect, glass, Modifier.height(height))
            GlassCircleButton(
                onClick = onSearch,
                icon = NovaGlyphs.Search,
                contentDescription = "Search",
                size = height,
                iconSize = 20.dp,
                selected = searchSelected,
                glass = glass,
            )
            AccountButton(onClick = onAccount, size = height, selected = accountSelected, glass = glass)
        },
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = dims.gutter, end = dims.gutter, top = 10.dp),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val capsule = measurables[0].measure(loose)
        val search = measurables[1].measure(loose)
        val account = measurables[2].measure(loose)
        val width = constraints.maxWidth
        val barHeight = maxOf(capsule.height, search.height, account.height)
        layout(width, barHeight) {
            val gap = 12.dp.roundToPx()
            val accountX = width - account.width
            val latestCapsuleX = accountX - gap - search.width - gap - capsule.width
            val capsuleX = minOf((width - capsule.width) / 2, latestCapsuleX).coerceAtLeast(0)
            capsule.place(capsuleX, (barHeight - capsule.height) / 2)
            search.place(capsuleX + capsule.width + gap, (barHeight - search.height) / 2)
            account.place(accountX, (barHeight - account.height) / 2)
        }
    }
}

@Composable
private fun TextTabCapsule(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    glass: GlassState?,
    modifier: Modifier,
) {
    val palette = LocalNovaPalette.current
    val density = LocalDensity.current
    val xs = remember(items.size) { List(items.size) { mutableFloatStateOf(0f) } }
    val widths = remember(items.size) { List(items.size) { mutableFloatStateOf(0f) } }
    val showLens = selectedIndex in items.indices
    val targetX = if (showLens) xs[selectedIndex].floatValue else 0f
    val targetW = if (showLens) widths[selectedIndex].floatValue else 0f
    val lensX = remember { Animatable(0f) }
    val lensW = remember { Animatable(0f) }
    LaunchedEffect(selectedIndex, targetX, targetW) {
        if (!showLens || targetW <= 0f) return@LaunchedEffect
        if (lensW.value <= 0f) {
            lensX.snapTo(targetX)
            lensW.snapTo(targetW)
        } else {
            launch { lensX.animateTo(targetX, NovaMotion.bouncy()) }
            lensW.animateTo(targetW, NovaMotion.bouncy())
        }
    }
    Box(
        modifier
            .glass(CircleShape, glass, elevation = 10.dp)
            .padding(4.dp),
    ) {
        if (showLens && lensW.value > 0f) {
            Box(
                Modifier
                    .offset { IntOffset(lensX.value.roundToInt(), 0) }
                    .width(with(density) { lensW.value.toDp() })
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(lensColor()),
            )
        }
        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val source = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxHeight()
                        .onPlaced { coordinates ->
                            xs[index].floatValue = coordinates.positionInParent().x
                            widths[index].floatValue = coordinates.size.width.toFloat()
                        }
                        .selectable(
                            selected = selected,
                            interactionSource = source,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                        .pressEffect(source, scale = 0.94f, alpha = 1f)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = NovaType.subheadSemibold,
                        color = if (selected) palette.label else palette.label.copy(alpha = 0.82f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Top padding that puts a large title under the floating top bar (phone) or top tabs (iPad). */
@Composable
fun largeTitleTopPadding(): Dp {
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return if (LocalDeviceLayout.current.isTablet) {
        status + 10.dp + LocalNovaDimens.current.tabBarHeight + 12.dp
    } else {
        status + NavBarDefaults.Height
    }
}

/**
 * The iOS large title ("Library", "Search", "Settings"): Large Title 34 bold at the page gutter,
 * placed as the first item of the scrolling content. When [collapsed] (see rememberTitleCollapsed)
 * it fades out and GlassNavBar shows the inline title instead.
 */
@Composable
fun LargeTitleHeader(
    title: String,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    horizontalPadding: Dp = LocalNovaDimens.current.gutter,
    topPadding: Dp = largeTitleTopPadding(),
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val fade = animateFloatAsState(if (collapsed) 0f else 1f, NovaMotion.fade(NovaMotion.FastMs), label = "largeTitle")
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, top = topPadding, bottom = 8.dp)
            .graphicsLayer { alpha = fade.value },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            style = NovaType.largeTitle,
            color = NovaColors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (trailing != null) trailing()
    }
}

/**
 * The floating bar at the top of a page: [leading] and [trailing] glass circles are always
 * visible; the glass background and the inline Headline [title] fade in when [showBackground]
 * (the large title has scrolled away). Over artwork pass showBackground = false and use
 * GlassStyle.Clear circles.
 */
@Composable
fun GlassNavBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    showBackground: Boolean = true,
    glass: GlassState? = LocalGlassState.current,
    horizontalPadding: Dp = NavBarDefaults.HorizontalPadding,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val fade = animateFloatAsState(if (showBackground) 1f else 0f, NovaMotion.fade(NovaMotion.FastMs), label = "navBar")
    Box(modifier.fillMaxWidth()) {
        if (showBackground || fade.value > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = fade.value }
                    .glass(RectangleShape, glass, elevation = 0.dp, rim = false),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(NavBarDefaults.Height)
                .padding(horizontal = horizontalPadding),
        ) {
            Row(
                Modifier.align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = leading,
            )
            if (title != null) {
                Text(
                    text = title,
                    style = NovaType.headline,
                    color = NovaColors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 72.dp)
                        .graphicsLayer { alpha = fade.value },
                )
            }
            Row(
                Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        }
    }
}

/** True once the large title (first item) has scrolled under the bar. */
@Composable
fun rememberTitleCollapsed(state: LazyListState, threshold: Dp = 40.dp): Boolean {
    val px = with(LocalDensity.current) { threshold.roundToPx() }
    val collapsed by remember(state, px) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > px }
    }
    return collapsed
}

@Composable
fun rememberTitleCollapsed(state: LazyGridState, threshold: Dp = 40.dp): Boolean {
    val px = with(LocalDensity.current) { threshold.roundToPx() }
    val collapsed by remember(state, px) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > px }
    }
    return collapsed
}

@Composable
fun rememberTitleCollapsed(state: ScrollState, threshold: Dp = 40.dp): Boolean {
    val px = with(LocalDensity.current) { threshold.roundToPx() }
    val collapsed by remember(state, px) { derivedStateOf { state.value > px } }
    return collapsed
}

/**
 * Row header: Title 2 bold with a secondary "›" right after it; the whole header opens the full
 * list when [onClick] is set (no chevron otherwise). Includes the page gutter and the 12dp gap
 * below it.
 */
@Composable
fun RowHeader(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    horizontalPadding: Dp = LocalNovaDimens.current.gutter,
    bottomPadding: Dp = LocalNovaDimens.current.rowHeaderBottom,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, bottom = bottomPadding),
    ) {
        val click = if (onClick != null) Modifier.novaClickable(onClick = onClick) else Modifier
        Row(
            click.semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = NovaType.title2,
                color = NovaColors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(NovaGlyphs.ChevronRight, contentDescription = null, modifier = Modifier.size(15.dp), tint = NovaColors.secondaryLabel)
            }
        }
    }
}

/** "EPISODE 3", "SEASON 1": Caption 1 semibold, uppercase, +0.6 tracking, secondary. */
@Composable
fun SmallCapsText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NovaColors.secondaryLabel,
    maxLines: Int = 1,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = NovaType.smallCaps,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
