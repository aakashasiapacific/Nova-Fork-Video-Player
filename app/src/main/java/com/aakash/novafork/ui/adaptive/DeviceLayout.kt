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

/** Size tokens per UI family. Phone and tablet components read these instead of hard-coding sizes. */
@Immutable
data class NovaDimens(
    /** Horizontal page margin. */
    val gutter: Dp,
    /** Width of a poster card in a horizontal row (2:3 art). */
    val posterWidth: Dp,
    /** Minimum cell width for poster grids. */
    val gridMinCell: Dp,
    /** Width of a 16:9 card (continue watching, episodes) in a row. */
    val landscapeCardWidth: Dp,
    val cardRadius: Dp,
    val rowSpacing: Dp,
    val sectionSpacing: Dp,
    /** Large poster on details screens. */
    val detailPosterWidth: Dp,
)

val PhoneDimens = NovaDimens(
    gutter = 16.dp,
    posterWidth = 112.dp,
    gridMinCell = 104.dp,
    landscapeCardWidth = 248.dp,
    cardRadius = 12.dp,
    rowSpacing = 10.dp,
    sectionSpacing = 24.dp,
    detailPosterWidth = 112.dp,
)

val TabletDimens = NovaDimens(
    gutter = 32.dp,
    posterWidth = 156.dp,
    gridMinCell = 150.dp,
    landscapeCardWidth = 320.dp,
    cardRadius = 16.dp,
    rowSpacing = 16.dp,
    sectionSpacing = 36.dp,
    detailPosterWidth = 240.dp,
)

val LocalDeviceLayout = staticCompositionLocalOf { DeviceLayout.PHONE }
val LocalNovaDimens = staticCompositionLocalOf { PhoneDimens }

fun dimensFor(layout: DeviceLayout): NovaDimens = if (layout.isTablet) TabletDimens else PhoneDimens
