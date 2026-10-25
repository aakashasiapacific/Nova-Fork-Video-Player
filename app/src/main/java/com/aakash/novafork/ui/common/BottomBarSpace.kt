package com.aakash.novafork.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much of the bottom of the screen the phone's floating tab bar covers (bar, its margins and
 * the system navigation bar). Pages scroll on behind it, as on iOS; scrolling lists add this to
 * their bottom padding so their last item can still scroll clear of it. 0 on tablets.
 */
val LocalBottomBarSpace = staticCompositionLocalOf { 0.dp }

/**
 * The little room a page's list leaves after its last item, above the bars (owner: no empty
 * space at the end of pages).
 */
val EndOfListGap = 8.dp
