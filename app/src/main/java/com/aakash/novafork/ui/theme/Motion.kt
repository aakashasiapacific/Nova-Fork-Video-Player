package com.aakash.novafork.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

/**
 * iOS-like motion: springs that move fast and settle gently (about 250–350 ms), and the press
 * response every control shares (scale 0.96, alpha 0.85).
 */
object NovaMotion {
    /** Fast out, gentle settle: the curve of UIKit's default spring animations. */
    val EaseOut: Easing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)

    const val DurationMs = 300
    const val FastMs = 180

    const val PressScale = 0.96f
    const val PressAlpha = 0.85f

    /** Context-menu lift of the pressed card. */
    const val LiftScale = 1.03f

    /** Layout and position changes (tab lenses, sheets, segment thumbs). */
    fun <T> standard(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = 380f)

    /** Slightly bouncy, for things that pop in (alerts, menus, the selected-tab lens). */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.74f, stiffness = 420f)

    /** Press-down response. */
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 0.62f, stiffness = 700f)

    /** Fades and colour changes. */
    fun <T> fade(durationMs: Int = DurationMs): TweenSpec<T> = tween(durationMillis = durationMs, easing = EaseOut)
}

/** Corner radii from the spec. Card radii differ per form factor: read LocalNovaDimens.current.cardRadius. */
object NovaRadius {
    /** Inset-grouped lists and About cards. */
    val Group = 10.dp
    /** Search field. */
    val Field = 10.dp
    /** Sheet top corners. */
    val Sheet = 12.dp
    /** Alerts and menus. */
    val Alert = 14.dp
    val Menu = 14.dp
    /** Settings icon squares (29dp). */
    val IconSquare = 7.dp
    /** Outlined "4K" / "CC" badges. */
    val Badge = 4.dp
}
