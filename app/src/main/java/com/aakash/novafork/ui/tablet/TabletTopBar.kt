package com.aakash.novafork.ui.tablet

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.common.NovaTabCapsule
import com.aakash.novafork.ui.model.TopDestination

/** Height of the tablet's top bar, and the room pages below it leave for it. */
internal val TabletTopBarHeight = 56.dp
internal val TabletTopBarTop = 8.dp
internal val TabletTopBarSpace = TabletTopBarTop + TabletTopBarHeight + 8.dp

/**
 * The iPad Apple TV app's tab bar: the four tabs in a floating glass capsule at the top centre,
 * over the hero (the same capsule as the phone's dock, without its shrinking).
 */
@Composable
internal fun TabletTopBar(
    selected: TopDestination,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = TabletTopBarTop),
        contentAlignment = Alignment.TopCenter,
    ) {
        NovaTabCapsule(
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier
                .width(minOf(480.dp, maxWidth - 48.dp))
                .height(TabletTopBarHeight),
        )
    }
}
