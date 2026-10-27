package com.aakash.novafork.ui.common

import androidx.compose.runtime.compositionLocalOf

/**
 * How tinted the app's glass is, from 0 (clear: the page shows through) to 1 (tinted: nearly
 * solid), as iOS 27's Liquid Glass slider in Settings sets it. Preferences › Appearance › Glass
 * sets it for the app; the glass buttons and bars read it.
 */
val LocalGlassTint = compositionLocalOf { DEFAULT_GLASS_TINT }

/** Tinted, the right end of the slider (owner): the most readable glass. */
const val DEFAULT_GLASS_TINT = 1f
