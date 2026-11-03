package com.aakash.novafork.ui.common

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/**
 * The app's motion, in one place (owner: the movement looked cheap). iOS moves with springs that
 * settle rather than timed curves that stop: a spring keeps its speed when it is turned around
 * mid-way (tap Back during a push), so nothing jumps. Critically damped, so nothing bounces.
 */
object Motion {
    /** Pages: iOS's push, about a third of a second to settle. */
    private const val PAGE_STIFFNESS = 420f

    /** Things a finger touches (presses, toggles): quick to follow, quick to let go. */
    private const val PRESS_STIFFNESS = 1_400f

    /** Content changing in place (items moving, sizes changing). */
    private const val CONTENT_STIFFNESS = Spring.StiffnessMediumLow

    fun <T> page(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = PAGE_STIFFNESS, visibilityThreshold = visibilityThreshold)

    fun <T> press(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = PRESS_STIFFNESS, visibilityThreshold = visibilityThreshold)

    fun <T> content(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = CONTENT_STIFFNESS, visibilityThreshold = visibilityThreshold)

    /** How far the page underneath drifts (a share of the width) and how dark it gets. */
    const val UNDERLAY_LAG = 0.3f
    const val UNDERLAY_SHADE = 0.35f

    /**
     * iOS's push: the new page slides in over the old one from the right edge; the old one stays
     * solid, drifting a little left and darkening (fading toward the black behind it). Never a
     * cross-fade of two whole screens, which dips the screen dark for a moment.
     */
    fun push(): ContentTransform = ContentTransform(
        targetContentEnter = slideInHorizontally(page(IntOffset.VisibilityThreshold)) { it },
        initialContentExit = slideOutHorizontally(page(IntOffset.VisibilityThreshold)) { -(it * UNDERLAY_LAG).toInt() } +
            fadeOut(page(), targetAlpha = 1f - UNDERLAY_SHADE),
        targetContentZIndex = 1f,
        sizeTransform = null,
    )

    /** Back: the push undone. The top page slides off to the right, uncovering the one under it. */
    fun pop(): ContentTransform = ContentTransform(
        targetContentEnter = slideInHorizontally(page(IntOffset.VisibilityThreshold)) { -(it * UNDERLAY_LAG).toInt() } +
            fadeIn(page(), initialAlpha = 1f - UNDERLAY_SHADE),
        initialContentExit = slideOutHorizontally(page(IntOffset.VisibilityThreshold)) { it },
        targetContentZIndex = -1f,
        sizeTransform = null,
    )

    /** Changing tab: instant, as in Apple TV and Apple Music. */
    fun instant(): ContentTransform = ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
}
