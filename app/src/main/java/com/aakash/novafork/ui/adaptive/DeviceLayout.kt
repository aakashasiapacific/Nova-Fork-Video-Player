package com.aakash.novafork.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.data.LayoutMode

/**
 * Which UI family to draw. Decided by screen density-independent size, not by orientation:
 *  PHONE            – smallest width < 600dp (any phone, portrait or landscape).
 *  TABLET_COMPACT   – smallest width ≥ 600dp and current width < 840dp (tablet in portrait, foldables).
 *  TABLET_EXPANDED  – smallest width ≥ 600dp and current width ≥ 840dp (tablet in landscape).
 */
enum class DeviceLayout {
    PHONE, TABLET_COMPACT, TABLET_EXPANDED;

    val isTablet: Boolean get() = this != PHONE
    val isExpanded: Boolean get() = this == TABLET_EXPANDED
}

fun deviceLayoutFor(smallestWidthDp: Int, widthDp: Int, mode: LayoutMode = LayoutMode.AUTO): DeviceLayout {
    val tablet = when (mode) {
        LayoutMode.AUTO -> smallestWidthDp >= 600
        LayoutMode.PHONE -> false
        LayoutMode.TABLET -> true
    }
    return when {
        !tablet -> DeviceLayout.PHONE
        widthDp >= 840 -> DeviceLayout.TABLET_EXPANDED
        else -> DeviceLayout.TABLET_COMPACT
    }
}

@Composable
fun rememberDeviceLayout(mode: LayoutMode = LayoutMode.AUTO): DeviceLayout {
    val config = LocalConfiguration.current
    return deviceLayoutFor(config.smallestScreenWidthDp, config.screenWidthDp, mode)
}

/**
 * Size tokens per UI family (docs/APPLE-TV-SPEC.md, docs/DESIGN.md). Phone and tablet components
 * read these instead of hard-coding sizes.
 */
@Immutable
data class NovaDimens(
    /** Horizontal page margin: row headers, the first card of a row, large titles. */
    val gutter: Dp,
    /** Width of a 2:3 poster card in a horizontal row. Posters live in Library/category grids. */
    val posterWidth: Dp,
    /** Minimum cell width for adaptive poster grids. */
    val gridMinCell: Dp,
    /** Width of a 16:9 card in an ordinary row (Recently Added, Movies, TV Shows, Home Videos). */
    val landscapeCardWidth: Dp,
    /** Artwork corner radius of cards. */
    val cardRadius: Dp,
    /** Gap between cards in a row and between grid cells. */
    val rowSpacing: Dp,
    /** Vertical space between rows / page sections. */
    val sectionSpacing: Dp,
    /** Poster on legacy details screens. */
    val detailPosterWidth: Dp,
    /** Width of a Continue Watching 16:9 card. */
    val continueCardWidth: Dp,
    /** Width of a 16:9 episode card in the details episode carousel. */
    val episodeCardWidth: Dp,
    /** Fixed column count for poster grids, or null to use [gridMinCell] adaptively. */
    val posterGridColumns: Int?,
    /** Margin of the text and buttons that sit on hero / details artwork. */
    val heroMargin: Dp,
    /** Max width of the title, meta line and description on the Home hero. */
    val heroTextMaxWidth: Dp,
    /** Max width of the description and About text on details pages. */
    val descriptionMaxWidth: Dp,
    /** White primary capsule ("▶ Play") height. */
    val primaryButtonHeight: Dp,
    /** Glass circles next to the primary button ("+", info, Start Over…). */
    val glassButtonSize: Dp,
    /** Glass circles in the top bar (back, more, account). */
    val toolbarButtonSize: Dp,
    /** Space under a row header before the cards. */
    val rowHeaderBottom: Dp,
    /** Side margin of inset-grouped lists. */
    val listInset: Dp,
    /** Height of the floating tab bar (phone bottom capsule / iPad top capsule). */
    val tabBarHeight: Dp,
    /** Hero page dots. */
    val pageDotSize: Dp,
)

val PhoneDimens = NovaDimens(
    gutter = 20.dp,
    posterWidth = 112.dp,
    gridMinCell = 104.dp,
    landscapeCardWidth = 220.dp,
    cardRadius = 10.dp,
    rowSpacing = 12.dp,
    sectionSpacing = 32.dp,
    detailPosterWidth = 112.dp,
    continueCardWidth = 300.dp,
    episodeCardWidth = 300.dp,
    posterGridColumns = 3,
    heroMargin = 20.dp,
    heroTextMaxWidth = 520.dp,
    descriptionMaxWidth = 600.dp,
    primaryButtonHeight = 50.dp,
    glassButtonSize = 50.dp,
    toolbarButtonSize = 44.dp,
    rowHeaderBottom = 12.dp,
    listInset = 16.dp,
    tabBarHeight = 64.dp,
    pageDotSize = 6.dp,
)

val TabletDimens = NovaDimens(
    gutter = 40.dp,
    posterWidth = 170.dp,
    gridMinCell = 170.dp,
    landscapeCardWidth = 300.dp,
    cardRadius = 14.dp,
    rowSpacing = 20.dp,
    sectionSpacing = 40.dp,
    detailPosterWidth = 240.dp,
    continueCardWidth = 400.dp,
    episodeCardWidth = 360.dp,
    posterGridColumns = null,
    heroMargin = 48.dp,
    heroTextMaxWidth = 520.dp,
    descriptionMaxWidth = 600.dp,
    primaryButtonHeight = 56.dp,
    glassButtonSize = 56.dp,
    toolbarButtonSize = 48.dp,
    rowHeaderBottom = 12.dp,
    listInset = 16.dp,
    tabBarHeight = 48.dp,
    pageDotSize = 7.dp,
)

val LocalDeviceLayout = staticCompositionLocalOf { DeviceLayout.PHONE }
val LocalNovaDimens = staticCompositionLocalOf { PhoneDimens }

fun dimensFor(layout: DeviceLayout): NovaDimens = if (layout.isTablet) TabletDimens else PhoneDimens

/**
 * Height of the Home hero and of the details artwork.
 * Phone: width × 1.25. Tablet: 78% of the height in landscape, 60% in portrait.
 */
fun heroHeightFor(layout: DeviceLayout, maxWidth: Dp, maxHeight: Dp): Dp = when {
    !layout.isTablet -> maxWidth * 1.25f
    maxWidth > maxHeight -> maxHeight * 0.78f
    else -> maxHeight * 0.6f
}
