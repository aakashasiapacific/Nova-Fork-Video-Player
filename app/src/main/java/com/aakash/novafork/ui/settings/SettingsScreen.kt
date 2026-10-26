package com.aakash.novafork.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.common.CollapsedTitleBar
import com.aakash.novafork.ui.common.Hairline
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.OnTabReselect
import com.aakash.novafork.ui.common.largeTitle
import com.aakash.novafork.ui.common.rememberLargeTitleState
import com.aakash.novafork.ui.common.stretchWithPull
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.SettingsUi
import com.aakash.novafork.ui.phone.PhoneTopBar

private val PhoneMargin = 16.dp
private val TabletListWidth = 300.dp
private val TabletContentMaxWidth = 720.dp
private val GroupGap = 12.dp

/**
 * Settings for both UI families: one scrolling list of groups on a phone, a section list with the
 * selected section beside it on a tablet. [contentPadding] is the space the caller's chrome needs.
 */
@Composable
fun SettingsScreen(
    state: SettingsUi,
    actions: AppActions,
    layout: DeviceLayout,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (layout.isTablet) {
        TabletSettings(state, actions, modifier, contentPadding)
    } else {
        PhoneSettings(actions, modifier, contentPadding)
    }
}

/**
 * iOS Settings (owner: one long page was too dense): the top page is a short list of the
 * sections, icon and name only, each opening its own page ([SettingsSectionScreen]).
 */
@Composable
private fun PhoneSettings(actions: AppActions, modifier: Modifier, contentPadding: PaddingValues) {
    // iOS's large title: "Preferences" scrolls away and comes back small in a bar at the top.
    val listState = rememberLazyListState()
    OnTabReselect { listState.animateScrollToItem(0) }
    val titleState = rememberLargeTitleState(listState)
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding.extend(horizontal = PhoneMargin, top = 8.dp, bottom = 16.dp),
        ) {
            item(key = "title") {
                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp).stretchWithPull().largeTitle(titleState),
                )
            }
            PhoneSectionCards.forEachIndexed { index, sections ->
                item(key = "sections:$index") {
                    SectionCard(
                        sections = sections,
                        onOpen = { actions.navigate(Route.SettingsPage(it.name)) },
                        modifier = Modifier.padding(top = if (index == 0) 16.dp else SectionCardGap),
                    )
                }
            }
        }
        CollapsedTitleBar(state = titleState, title = "Preferences")
    }
}

/** The top page's cards: what the library is made of, then the app itself. */
private val PhoneSectionCards = listOf(
    listOf(SettingsSection.LIBRARY, SettingsSection.ARTWORK, SettingsSection.PLAYER),
    listOf(SettingsSection.APPEARANCE, SettingsSection.ABOUT),
)
private val SectionCardGap = 32.dp

/** One card of section rows: icon tile, name and chevron, hairlines inset under the names. */
@Composable
private fun SectionCard(sections: List<SettingsSection>, onOpen: (SettingsSection) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(IosColors.GroupedCard),
    ) {
        sections.forEachIndexed { index, section ->
            if (index > 0) {
                Box(
                    Modifier
                        .padding(start = SectionRowInset)
                        .fillMaxWidth()
                        .height(Hairline)
                        .background(IosColors.Separator),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Open ${section.title}") { onOpen(section) }
                    .heightIn(min = 58.dp)
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconTile(section.icon, tileColor(section.icon))
                Text(section.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f))
                NovaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = IosColors.TertiaryLabel)
            }
        }
    }
}

/** Where the hairlines between section rows start: under the names, past the icon tiles. */
private val SectionRowInset = 16.dp + 29.dp + 14.dp

/**
 * One section of Preferences on its own page (phone), opened from the top page as iOS Settings
 * opens its rows: a bar with the way back and the section's name, then its groups, well apart.
 */
@Composable
fun SettingsSectionScreen(
    section: String,
    state: SettingsUi,
    actions: AppActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val shown = SettingsSection.entries.firstOrNull { it.name == section } ?: SettingsSection.LIBRARY
    val groups = remember(shown, state, actions) { settingsGroups(shown, state, actions) }
    Column(modifier.fillMaxSize()) {
        PhoneTopBar(title = shown.title, onBack = actions::back)
        LazyColumn(
            // imePadding keeps the TMDB key field above the keyboard: the window does not resize edge-to-edge.
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = contentPadding.extend(horizontal = PhoneMargin, top = 4.dp, bottom = 16.dp),
        ) {
            groups.forEachIndexed { index, group ->
                item(key = group.key) {
                    SettingsGroupBlock(
                        group = group,
                        title = group.title,
                        modifier = Modifier.padding(top = if (index == 0) 8.dp else SectionGroupGap),
                    )
                }
            }
        }
    }
}

/** Room between a section page's groups (iOS leaves about this much). */
private val SectionGroupGap = 24.dp

@Composable
private fun TabletSettings(state: SettingsUi, actions: AppActions, modifier: Modifier, contentPadding: PaddingValues) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val selected = SettingsSection.entries.getOrElse(selectedIndex) { SettingsSection.LIBRARY }
    val groups = remember(selected, state, actions) { settingsGroups(selected, state, actions) }
    Row(modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(TabletListWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding.extend(horizontal = 16.dp, top = 24.dp, bottom = 16.dp)),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 12.dp, bottom = 16.dp),
            )
            SettingsSection.entries.forEach { section ->
                SectionListItem(
                    section = section,
                    selected = section == selected,
                    onClick = { selectedIndex = section.ordinal },
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = TabletContentMaxWidth)
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = contentPadding.extend(horizontal = 24.dp, top = 24.dp, bottom = 24.dp),
            ) {
                item(key = "${selected.name}/title") {
                    Text(
                        text = selected.title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                }
                groups.forEachIndexed { index, group ->
                    item(key = "${selected.name}/${group.key}") {
                        SettingsGroupBlock(
                            group = group,
                            title = group.title,
                            modifier = Modifier.padding(top = if (index == 0 || group.title != null) 0.dp else GroupGap),
                        )
                    }
                }
            }
        }
    }
}

/** iPadOS Settings' sidebar row: icon tile and title, filled blue when selected. */
@Composable
private fun SectionListItem(section: SettingsSection, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.primary else Color.Transparent,
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(section.icon, tileColor(section.icon))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(section.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = section.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) Color.White.copy(alpha = 0.8f) else IosColors.SecondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** [this] plus fixed margins on each side. */
@Composable
private fun PaddingValues.extend(horizontal: Dp, top: Dp, bottom: Dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + horizontal,
        top = calculateTopPadding() + top,
        end = calculateEndPadding(direction) + horizontal,
        bottom = calculateBottomPadding() + bottom,
    )
}
