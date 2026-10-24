package com.aakash.novafork.ui.common

import androidx.compose.runtime.staticCompositionLocalOf

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
