package com.aakash.novafork.ui.tablet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.activeColor
import com.aakash.novafork.ui.common.frostedGlass
import com.aakash.novafork.ui.common.tabIconRes
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

/** The side bar's width, and the room pages leave for it on the right. */
internal val TabletSideBarWidth = 72.dp
internal val TabletSideBarSpace = TabletSideBarWidth + 16.dp

/**
 * iPhone Duo's tab bar on its big screen (owner): the four tabs stand in a slim glass bar along
 * the right edge, centred top to bottom, each an icon over its name; the chosen one in its
 * colour. Pages keep [TabletSideBarSpace] free on the right for it.
 */
@Composable
internal fun TabletSideBar(
    selected: TopDestination,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier
            .padding(end = 8.dp)
            .width(TabletSideBarWidth)
            .frostedGlass(RoundedCornerShape(percent = 50))
            // 14 + 10 at the ends equals 10 + 4 + 10 between tabs: the bar's ends match its gaps.
            .padding(vertical = 14.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopDestination.entries.forEach { tab ->
            val active = tab == selected
            val tint = if (active) tab.activeColor() else Color.White
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(percent = 50))
                    // Only the colour marks the chosen tab (owner: no pill).
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        onClick = {
                            if (!active) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(tab)
                        },
                    )
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NovaIcon(tab.tabIconRes(), contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}
