package com.aakash.novafork.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.aakash.novafork.data.DetailsHeaderStyle
import com.aakash.novafork.data.HeroStyle

/** Preferences › Appearance › Home hero; the Home heroes (phone and tablet) read it. */
val LocalHeroStyle = compositionLocalOf { HeroStyle.BACKDROP }

/** Preferences › Appearance › Title page header; the movie, episode and show pages read it. */
val LocalDetailsHeaderStyle = compositionLocalOf { DetailsHeaderStyle.BACKDROP }
