package com.aakash.novafork.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Sets the status bar's icons dark (true) or light (false). The app's bars are clear and its
 * theme dark, so icons are light, except where a page shows a bright picture under the status
 * bar (the Home hero). MainActivity provides it; previews and tests get a no-op.
 */
val LocalStatusBarIcons = staticCompositionLocalOf<(darkIcons: Boolean) -> Unit> { {} }

/**
 * The Home hero may play muted video previews (libVLC). Only the real app turns this on:
 * previews and screenshot tests have no native player.
 */
val LocalHeroPreviewEnabled = staticCompositionLocalOf { false }

/**
 * Which picture last set the status bar's icons. A page going away puts them back to light only
 * while it is still the one that set them, so the page coming in (which sets them first, during
 * the transition) keeps its own.
 */
private object StatusBarIconsOwner {
    var owner: Any? = null
}

/**
 * iOS's status bar over a picture (owner: no black shade under the clock): while [active] (the
 * picture at [model] runs under the status bar) its icons turn dark when the top of the picture is
 * bright, and stay light over a dark one; light again once it scrolls away or the page goes.
 */
@Composable
internal fun PictureStatusBar(model: String?, active: Boolean) {
    val setDarkIcons = LocalStatusBarIcons.current
    val brightness = rememberTopBrightness(model.takeIf { active })
    val dark = active && (brightness ?: 0f) > BRIGHT_TOP
    val me = remember { Any() }
    LaunchedEffect(dark, active) {
        if (active) {
            StatusBarIconsOwner.owner = me
            setDarkIcons(dark)
        } else if (StatusBarIconsOwner.owner === me) {
            StatusBarIconsOwner.owner = null
            setDarkIcons(false)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (StatusBarIconsOwner.owner === me) {
                StatusBarIconsOwner.owner = null
                setDarkIcons(false)
            }
        }
    }
}

/** A top of a picture brighter than this gets dark status bar icons. */
private const val BRIGHT_TOP = 0.62f

/** Hides the status bar while [hidden] (Home at rest), showing it again after. */
@Composable
internal fun HideStatusBar(hidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(hidden, view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (hidden) controller?.hide(WindowInsetsCompat.Type.statusBars()) else controller?.show(WindowInsetsCompat.Type.statusBars())
        onDispose { if (hidden) controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
