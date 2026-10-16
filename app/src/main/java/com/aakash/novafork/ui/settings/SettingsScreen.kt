package com.aakash.novafork.ui.settings

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.SettingsUi

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
        PhoneSettings(state, actions, modifier, contentPadding)
    }
}

@Composable
private fun PhoneSettings(state: SettingsUi, actions: AppActions, modifier: Modifier, contentPadding: PaddingValues) {
    val sections = remember(state, actions) {
        SettingsSection.entries.map { section -> section to settingsGroups(section, state, actions) }
    }
    LazyColumn(
        // imePadding keeps the TMDB key field above the keyboard: the window does not resize edge-to-edge.
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = contentPadding.extend(horizontal = PhoneMargin, top = 8.dp, bottom = 16.dp),
    ) {
        item(key = "title") {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
            )
        }
        for ((section, groups) in sections) {
            groups.forEachIndexed { index, group ->
                item(key = "${section.name}/${group.key}") {
                    SettingsGroupBlock(
                        group = group,
                        title = if (index == 0) section.title else group.title,
                        modifier = Modifier.padding(top = if (index == 0 || group.title != null) 8.dp else GroupGap),
                    )
                }
            }
        }
    }
}

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

@Composable
private fun SectionListItem(section: SettingsSection, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.secondaryContainer else colors.background,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NovaIcon(section.icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(section.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = section.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
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
