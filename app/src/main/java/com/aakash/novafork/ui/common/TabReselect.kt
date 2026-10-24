package com.aakash.novafork.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Counts taps on the tab that is already selected (iOS: back to the top of its first page). */
@Stable
class TabReselect {
    var count by mutableIntStateOf(0)
        private set

    fun fire() {
        count++
    }
}

/** The phone shell's [TabReselect]; null elsewhere (the tablet, previews), where nothing happens. */
val LocalTabReselect = staticCompositionLocalOf<TabReselect?> { null }

/**
 * Runs [action] each time the selected tab is tapped again while this is shown: a tab's first
 * page scrolls back to its top, as on iOS. Taps from before it was shown are not replayed.
 */
@Composable
fun OnTabReselect(action: suspend () -> Unit) {
    val reselect = LocalTabReselect.current ?: return
    val count = reselect.count
    val seen = remember { count }
    val current by rememberUpdatedState(action)
    LaunchedEffect(count) {
        if (count != seen) current()
    }
}
